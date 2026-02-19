package com.wenubey.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.local.dao.CategoryDao
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.local.mapper.toEntity
import com.wenubey.data.util.CATEGORIES_COLLECTION
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.domain.model.product.toMap
import com.wenubey.domain.repository.CategoryRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID

class CategoryRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    dispatcherProvider: DispatcherProvider,
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    private val ioDispatcher = dispatcherProvider.io()

    private val categoriesCollection
        get() = firestore.collection(CATEGORIES_COLLECTION)

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeActiveCategories().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getCategories(): Result<List<Category>> = safeApiCall(ioDispatcher) {
        try {
            val snapshot = categoriesCollection.whereEqualTo("isActive", true).get().await()
            val categories = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Category::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize category: ${doc.id}")
                    null
                }
            }
            categoryDao.upsertAll(categories.map { it.toEntity() })
            categories
        } catch (e: Exception) {
            Timber.w(e, "Firestore getCategories failed, falling back to Room cache")
            categoryDao.observeActiveCategories().first().map { it.toDomain() }
        }
    }

    override suspend fun createCategory(category: Category): Result<Category> =
        safeApiCall(ioDispatcher) {
            val categoryId = if (category.id.isNotBlank()) category.id else UUID.randomUUID().toString()
            val currentTime = System.currentTimeMillis().toString()
            val createdBy = auth.currentUser?.uid ?: throw Exception("User not authenticated")

            val newCategory = category.copy(
                id = categoryId,
                createdBy = createdBy,
                createdAt = currentTime,
                updatedAt = currentTime,
                isActive = true,
            )

            categoriesCollection
                .document(categoryId)
                .set(newCategory.toMap())
                .await()

            categoryDao.upsert(newCategory.toEntity())

            Timber.d("Category created successfully: $categoryId")
            newCategory
        }

    override suspend fun addSubcategory(
        categoryId: String,
        subcategory: Subcategory,
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        firestore.runTransaction { transaction ->
            val docRef = categoriesCollection.document(categoryId)
            val snapshot = transaction.get(docRef)
            val existing = snapshot.toObject(Category::class.java)
                ?: throw Exception("Category not found")
            if (existing.subcategories.any { it.id == subcategory.id }) return@runTransaction
            val updated = existing.subcategories + subcategory
            val currentTime = System.currentTimeMillis().toString()
            transaction.update(docRef, mapOf(
                "subcategories" to updated.map { it.toMap() },
                "updatedAt" to currentTime,
            ))
        }.await()

        Timber.d("Subcategory added to category: $categoryId")
    }

    override suspend fun updateCategory(category: Category): Result<Unit> =
        safeApiCall(ioDispatcher) {
            val currentTime = System.currentTimeMillis().toString()
            val updatedCategory = category.copy(updatedAt = currentTime)

            categoriesCollection
                .document(category.id)
                .update(updatedCategory.toMap())
                .await()

            categoryDao.upsert(updatedCategory.toEntity())

            Timber.d("Category updated: ${category.id}")
        }

    override suspend fun deleteCategory(categoryId: String): Result<Unit> =
        safeApiCall(ioDispatcher) {
            val currentTime = System.currentTimeMillis().toString()

            categoriesCollection.document(categoryId).update(
                mapOf(
                    "isActive" to false,
                    "updatedAt" to currentTime,
                )
            ).await()

            categoryDao.deleteById(categoryId)

            Timber.d("Category soft-deleted: $categoryId")
        }

    override suspend fun uploadCategoryImage(
        imageUri: String,
        categoryId: String,
    ): Result<String> = safeApiCall(ioDispatcher) {
        val imageRef = storage.reference
            .child("category_images/${categoryId}_category_image.jpg")

        imageRef.putFile(Uri.parse(imageUri))
            .addOnSuccessListener { Timber.d("Category image uploaded: $categoryId") }
            .addOnFailureListener { Timber.e(it, "Category image upload failed: $categoryId") }
            .await()

        val downloadUrl = imageRef.downloadUrl.await().toString()
        Timber.d("Category image download URL: $downloadUrl")
        downloadUrl
    }
}
