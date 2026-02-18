package com.wenubey.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.util.CATEGORIES_COLLECTION
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.domain.model.product.toMap
import com.wenubey.domain.repository.CategoryRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID

class CategoryRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    dispatcherProvider: DispatcherProvider,
) : CategoryRepository {

    private val ioDispatcher = dispatcherProvider.io()

    private val categoriesCollection
        get() = firestore.collection(CATEGORIES_COLLECTION)

    override fun observeCategories(): Flow<List<Category>> = callbackFlow {
        val query = categoriesCollection.whereEqualTo("isActive", true)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.e(error, "Error observing categories")
                close(error)
                return@addSnapshotListener
            }

            val categories = snapshot?.documents?.mapNotNull { doc ->
                try {
                    doc.toObject(Category::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize category document: ${doc.id}")
                    null
                }
            } ?: emptyList()

            Timber.d("Categories updated: ${categories.size} categories")
            trySend(categories)
        }

        awaitClose {
            Timber.d("Removing categories listener")
            listener.remove()
        }
    }

    override suspend fun getCategories(): Result<List<Category>> = safeApiCall(ioDispatcher) {
        val snapshot = categoriesCollection
            .whereEqualTo("isActive", true)
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            try {
                doc.toObject(Category::class.java)
            } catch (e: Exception) {
                Timber.e(e, "Failed to deserialize category document: ${doc.id}")
                null
            }
        }
    }

    override suspend fun createCategory(category: Category): Result<Category> =
        safeApiCall(ioDispatcher) {
            val categoryId = UUID.randomUUID().toString()
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

            Timber.d("Category created successfully: $categoryId")
            newCategory
        }

    override suspend fun addSubcategory(
        categoryId: String,
        subcategory: Subcategory,
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        val currentTime = System.currentTimeMillis().toString()

        categoriesCollection.document(categoryId).update(
            mapOf(
                "subcategories" to FieldValue.arrayUnion(subcategory.toMap()),
                "updatedAt" to currentTime,
            )
        ).await()

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
