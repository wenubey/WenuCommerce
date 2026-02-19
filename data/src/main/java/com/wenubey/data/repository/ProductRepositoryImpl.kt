package com.wenubey.data.repository

import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.local.dao.ProductDao
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.local.mapper.toEntity
import com.wenubey.data.util.PRODUCTS_COLLECTION
import com.wenubey.data.util.PRODUCT_IMAGES_FOLDER
import com.wenubey.data.util.USER_COLLECTION
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.model.product.toMap
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductRepository
import com.wenubey.domain.util.generateSearchKeywords
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID

class ProductRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    dispatcherProvider: DispatcherProvider,
    private val productDao: ProductDao,
) : ProductRepository {

    private val ioDispatcher = dispatcherProvider.io()

    private val productsCollection
        get() = firestore.collection(PRODUCTS_COLLECTION)

    override suspend fun createProduct(product: Product): Result<Product> =
        safeApiCall(ioDispatcher) {
            val productId = UUID.randomUUID().toString()
            val currentTime = System.currentTimeMillis().toString()
            val sellerId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

            val slug = product.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

            val keywords = generateSearchKeywords(
                title = product.title,
                categoryName = product.categoryName,
                subcategoryName = product.subcategoryName,
                tagNames = product.tagNames,
            )

            val newProduct = product.copy(
                id = productId,
                sellerId = sellerId,
                slug = slug,
                status = ProductStatus.DRAFT,
                createdAt = currentTime,
                updatedAt = currentTime,
                searchKeywords = keywords,
            )

            productsCollection
                .document(productId)
                .set(newProduct.toMap())
                .await()

            productDao.upsert(newProduct.toEntity())

            Timber.d("Product created successfully: $productId")
            newProduct
        }

    override suspend fun updateProduct(product: Product): Result<Unit> =
        safeApiCall(ioDispatcher) {
            val currentTime = System.currentTimeMillis().toString()
            val totalStock = product.variants.sumOf { it.stockQuantity }

            val keywords = generateSearchKeywords(
                title = product.title,
                categoryName = product.categoryName,
                subcategoryName = product.subcategoryName,
                tagNames = product.tagNames,
            )

            val updatedProduct = product.copy(
                updatedAt = currentTime,
                totalStockQuantity = totalStock,
                searchKeywords = keywords,
            )

            productsCollection
                .document(product.id)
                .update(updatedProduct.toMap())
                .await()

            productDao.upsert(updatedProduct.toEntity())

            Timber.d("Product updated: ${product.id}")
        }

    override suspend fun submitForReview(productId: String): Result<Unit> =
        safeApiCall(ioDispatcher) {
            val currentTime = System.currentTimeMillis().toString()

            productsCollection.document(productId).update(
                mapOf(
                    "status" to ProductStatus.PENDING_REVIEW.name,
                    "updatedAt" to currentTime,
                )
            ).await()

            Timber.d("Product submitted for review: $productId")
        }

    override suspend fun archiveProduct(productId: String): Result<Unit> =
        safeApiCall(ioDispatcher) {
            val currentTime = System.currentTimeMillis().toString()

            productsCollection.document(productId).update(
                mapOf(
                    "status" to ProductStatus.ARCHIVED.name,
                    "archivedAt" to currentTime,
                    "updatedAt" to currentTime,
                )
            ).await()

            Timber.d("Product archived: $productId")
        }

    override suspend fun unarchiveProduct(productId: String): Result<Unit> =
        safeApiCall(ioDispatcher) {
            val currentTime = System.currentTimeMillis().toString()
            productsCollection.document(productId).update(
                mapOf(
                    "status" to ProductStatus.DRAFT.name,
                    "archivedAt" to "",
                    "updatedAt" to currentTime,
                )
            ).await()
            Timber.d("Product unarchived: $productId")
        }

    override suspend fun uploadProductImage(
        localUri: String,
        productId: String,
        imageId: String,
    ): Result<String> = safeApiCall(ioDispatcher) {
        val imageRef = storage.reference
            .child("$PRODUCT_IMAGES_FOLDER/$productId/$imageId.jpg")

        imageRef.putFile(localUri.toUri())
            .addOnSuccessListener { Timber.d("Product image uploaded: $productId/$imageId") }
            .addOnFailureListener { Timber.e(it, "Product image upload failed: $productId/$imageId") }
            .await()

        val downloadUrl = imageRef.downloadUrl.await().toString()
        Timber.d("Product image download URL: $downloadUrl")
        downloadUrl
    }

    override suspend fun deleteProductImage(storagePath: String): Result<Unit> =
        safeApiCall(ioDispatcher) {
            storage.reference.child(storagePath).delete().await()
            Timber.d("Product image deleted: $storagePath")
        }

    override fun observeSellerProducts(sellerId: String): Flow<List<Product>> =
        productDao.observeSellerProducts(sellerId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getSellerProducts(sellerId: String): Result<List<Product>> =
        safeApiCall(ioDispatcher) {
            val snapshot = productsCollection
                .whereEqualTo("sellerId", sellerId)
                .get()
                .await()

            val products = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Product::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize product: ${doc.id}")
                    null
                }
            }
            productDao.upsertAll(products.map { it.toEntity() })
            products
        }

    override fun observeActiveProductsByCategory(categoryId: String): Flow<List<Product>> =
        productDao.observeActiveProductsByCategory(categoryId).map { entities -> entities.map { it.toDomain() } }

    override fun observeActiveProductsByCategoryAndSubcategory(
        categoryId: String,
        subcategoryId: String?,
    ): Flow<List<Product>> =
        if (subcategoryId.isNullOrBlank()) {
            productDao.observeActiveProductsByCategory(categoryId).map { entities -> entities.map { it.toDomain() } }
        } else {
            productDao.observeActiveProductsByCategoryAndSubcategory(categoryId, subcategoryId)
                .map { entities -> entities.map { it.toDomain() } }
        }

    override suspend fun getProductById(productId: String): Result<Product> =
        safeApiCall(ioDispatcher) {
            val cached = productDao.getProductById(productId)
            if (cached != null) {
                return@safeApiCall cached.toDomain()
            }

            val doc = productsCollection.document(productId).get().await()
            if (doc.exists()) {
                val product = doc.toObject(Product::class.java)
                    ?: throw Exception("Failed to parse product data")
                productDao.upsert(product.toEntity())
                product
            } else {
                throw Exception("Product not found")
            }
        }

    override suspend fun getStorefrontProducts(sellerId: String): Result<List<Product>> =
        safeApiCall(ioDispatcher) {
            val snapshot = productsCollection
                .whereEqualTo("sellerId", sellerId)
                .whereEqualTo("status", ProductStatus.ACTIVE.name)
                .get()
                .await()

            val products = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Product::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize product: ${doc.id}")
                    null
                }
            }
            productDao.upsertAll(products.map { it.toEntity() })
            products
        }

    override fun observeProductsByStatus(status: ProductStatus): Flow<List<Product>> =
        productDao.observeProductsByStatus(status.name).map { entities -> entities.map { it.toDomain() } }

    override suspend fun approveProduct(productId: String): Result<Unit> =
        safeApiCall(ioDispatcher) {
            val currentTime = System.currentTimeMillis().toString()

            firestore.runTransaction { transaction ->
                val docRef = productsCollection.document(productId)
                transaction.update(
                    docRef, mapOf(
                        "status" to ProductStatus.ACTIVE.name,
                        "publishedAt" to currentTime,
                        "updatedAt" to currentTime,
                        "moderationNotes" to "",
                    )
                )
            }.await()

            val cached = productDao.getProductById(productId)
            if (cached != null) {
                productDao.upsert(
                    cached.copy(
                        status = ProductStatus.ACTIVE.name,
                        publishedAt = currentTime,
                        updatedAt = currentTime,
                        moderationNotes = "",
                    )
                )
            }

            Timber.d("Product approved: $productId")
        }

    override suspend fun suspendProduct(
        productId: String,
        reason: String,
        adminId: String,
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        val currentTime = System.currentTimeMillis().toString()

        productsCollection.document(productId).update(
            mapOf(
                "status" to ProductStatus.SUSPENDED.name,
                "moderationNotes" to reason,
                "suspendedBy" to adminId,
                "suspendedAt" to currentTime,
                "updatedAt" to currentTime,
            )
        ).await()

        val cached = productDao.getProductById(productId)
        if (cached != null) {
            productDao.upsert(
                cached.copy(
                    status = ProductStatus.SUSPENDED.name,
                    moderationNotes = reason,
                    suspendedBy = adminId,
                    suspendedAt = currentTime,
                    updatedAt = currentTime,
                )
            )
        }

        Timber.d("Product suspended: $productId")
    }

    override suspend fun adminUpdateProduct(product: Product): Result<Unit> =
        safeApiCall(ioDispatcher) {
            val currentTime = System.currentTimeMillis().toString()

            val keywords = generateSearchKeywords(
                title = product.title,
                categoryName = product.categoryName,
                subcategoryName = product.subcategoryName,
                tagNames = product.tagNames,
            )

            val updatedProduct = product.copy(
                updatedAt = currentTime,
                searchKeywords = keywords,
            )

            productsCollection
                .document(product.id)
                .update(updatedProduct.toMap())
                .await()

            Timber.d("Admin updated product: ${product.id}")
        }

    override suspend fun incrementViewCount(productId: String): Result<Unit> =
        safeApiCall(ioDispatcher) {
            productsCollection.document(productId)
                .update("viewCount", FieldValue.increment(1))
                .await()

            Timber.d("View count incremented: $productId")
        }

    override suspend fun decrementStock(
        productId: String,
        variantId: String,
        quantity: Int,
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        firestore.runTransaction { transaction ->
            val docRef = productsCollection.document(productId)
            val snapshot = transaction.get(docRef)
            val product = snapshot.toObject(Product::class.java)
                ?: throw Exception("Product not found")

            val variantIndex = product.variants.indexOfFirst { it.id == variantId }
            if (variantIndex == -1) throw Exception("Variant not found")

            val variant = product.variants[variantIndex]
            if (variant.stockQuantity < quantity) {
                throw IllegalStateException("Out of stock")
            }

            val updatedVariants = product.variants.toMutableList()
            val newStock = variant.stockQuantity - quantity
            updatedVariants[variantIndex] = variant.copy(
                stockQuantity = newStock,
                inStock = newStock > 0,
            )

            val newTotalStock = updatedVariants.sumOf { it.stockQuantity }
            val currentTime = System.currentTimeMillis().toString()

            transaction.update(
                docRef, mapOf(
                    "variants" to updatedVariants.map { it.toMap() },
                    "totalStockQuantity" to newTotalStock,
                    "purchaseCount" to FieldValue.increment(1),
                    "updatedAt" to currentTime,
                )
            )
        }.await()

        Timber.d("Stock decremented for $productId variant $variantId by $quantity")
    }

    override suspend fun addProductToSellerDocument(
        sellerId: String,
        productId: String,
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        firestore.collection(USER_COLLECTION)
            .document(sellerId)
            .update("products", FieldValue.arrayUnion(productId))
            .await()

        Timber.d("Product $productId added to seller $sellerId document")
    }

    override suspend fun searchActiveProducts(
        query: String,
        categoryId: String?,
        subcategoryId: String?,
    ): Result<List<Product>> =
        safeApiCall(ioDispatcher) {
            val normalizedQuery = query.trim().lowercase()
            if (normalizedQuery.isBlank()) return@safeApiCall emptyList()

            val tokens = normalizedQuery.split(Regex("\\s+"))
                .map { it.replace(Regex("[^a-z0-9]"), "") }
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@safeApiCall emptyList()

            val primaryToken = tokens.first()
            val candidates = productDao.searchActiveProducts(primaryToken)

            candidates.map { it.toDomain() }.filter { product ->
                val searchable = buildString {
                    append(product.title.lowercase())
                    append(" ")
                    append(product.categoryName.lowercase())
                    append(" ")
                    append(product.subcategoryName.lowercase())
                    append(" ")
                    append(product.tagNames.joinToString(" ").lowercase())
                }
                tokens.all { token -> searchable.contains(token) }
            }.filter { product ->
                if (categoryId == null) true
                else {
                    val categoryMatches = product.categoryId == categoryId
                    val subcategoryMatches = subcategoryId == null || product.subcategoryId == subcategoryId
                    categoryMatches && subcategoryMatches
                }
            }
        }

    override suspend fun searchAllProducts(
        query: String,
        categoryId: String?,
        subcategoryId: String?,
    ): Result<List<Product>> =
        safeApiCall(ioDispatcher) {
            val normalizedQuery = query.trim().lowercase()
            if (normalizedQuery.isBlank()) return@safeApiCall emptyList()

            val tokens = normalizedQuery.split(Regex("\\s+"))
                .map { it.replace(Regex("[^a-z0-9]"), "") }
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@safeApiCall emptyList()

            val primaryToken = tokens.first()
            val candidates = productDao.searchAllProducts(primaryToken)

            candidates.map { it.toDomain() }.filter { product ->
                val searchable = buildString {
                    append(product.title.lowercase())
                    append(" ")
                    append(product.categoryName.lowercase())
                    append(" ")
                    append(product.subcategoryName.lowercase())
                    append(" ")
                    append(product.tagNames.joinToString(" ").lowercase())
                }
                tokens.all { token -> searchable.contains(token) }
            }.filter { product ->
                if (categoryId == null) true
                else {
                    val categoryMatches = product.categoryId == categoryId
                    val subcategoryMatches = subcategoryId == null || product.subcategoryId == subcategoryId
                    categoryMatches && subcategoryMatches
                }
            }
        }
}
