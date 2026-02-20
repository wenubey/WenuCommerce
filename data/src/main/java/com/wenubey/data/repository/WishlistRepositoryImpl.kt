package com.wenubey.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.local.dao.WishlistItemDao
import com.wenubey.data.local.entity.WishlistItemEntity
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.domain.model.WishlistItem
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.WishlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant

class WishlistRepositoryImpl(
    private val wishlistItemDao: WishlistItemDao,
    private val firestore: FirebaseFirestore,
    private val dispatcherProvider: DispatcherProvider,
) : WishlistRepository {

    override fun observeWishlistItems(userId: String): Flow<List<WishlistItem>> =
        wishlistItemDao.observeWishlistItems(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun isWishlisted(userId: String, productId: String): Flow<Boolean> =
        wishlistItemDao.isWishlisted(userId, productId)

    override suspend fun toggleWishlist(userId: String?, product: Product) =
        withContext(dispatcherProvider.io()) {
            val effectiveUserId = userId ?: ""
            val existing = wishlistItemDao.getWishlistItem(effectiveUserId, product.id)

            if (existing != null) {
                // Remove from wishlist
                wishlistItemDao.deleteItem(effectiveUserId, product.id)
                if (effectiveUserId.isNotEmpty()) {
                    try {
                        firestore
                            .collection("users")
                            .document(effectiveUserId)
                            .collection("wishlist")
                            .document(product.id)
                            .delete()
                            .await()
                        Timber.d("WishlistRepository: removed ${product.id} from Firestore for user $effectiveUserId")
                    } catch (e: Exception) {
                        Timber.e(e, "WishlistRepository: failed to delete from Firestore (Room still updated)")
                    }
                }
            } else {
                // Add to wishlist
                val entity = WishlistItemEntity(
                    userId = effectiveUserId,
                    productId = product.id,
                    productTitle = product.title,
                    productImageUrl = product.images.firstOrNull()?.downloadUrl ?: "",
                    productPrice = product.basePrice,
                    availableStock = product.totalStockQuantity,
                    isProductDeleted = false,
                    addedAt = Instant.now().toString()
                )
                wishlistItemDao.upsert(entity)
                if (effectiveUserId.isNotEmpty()) {
                    try {
                        val data = mapOf(
                            "productId" to product.id,
                            "productTitle" to product.title,
                            "productPrice" to product.basePrice,
                            "addedAt" to entity.addedAt
                        )
                        firestore
                            .collection("users")
                            .document(effectiveUserId)
                            .collection("wishlist")
                            .document(product.id)
                            .set(data)
                            .await()
                        Timber.d("WishlistRepository: added ${product.id} to Firestore for user $effectiveUserId")
                    } catch (e: Exception) {
                        Timber.e(e, "WishlistRepository: failed to write to Firestore (Room still updated)")
                    }
                }
            }
        }

    override suspend fun removeFromWishlist(userId: String, productId: String) =
        withContext(dispatcherProvider.io()) {
            wishlistItemDao.deleteItem(userId, productId)
            if (userId.isNotEmpty()) {
                try {
                    firestore
                        .collection("users")
                        .document(userId)
                        .collection("wishlist")
                        .document(productId)
                        .delete()
                        .await()
                    Timber.d("WishlistRepository: removed $productId from Firestore for user $userId")
                } catch (e: Exception) {
                    Timber.e(e, "WishlistRepository: failed to delete from Firestore (Room still updated)")
                }
            }
        }

    override suspend fun syncAnonymousOnLogin(userId: String) =
        withContext(dispatcherProvider.io()) {
            try {
                val anonymousItems = wishlistItemDao.getItemsForUser("")
                if (anonymousItems.isEmpty()) {
                    // No anonymous items; fetch existing Firestore items and sync to Room
                    fetchAndMergeFirestoreWishlist(userId)
                    return@withContext
                }

                // Migrate anonymous items to the logged-in user
                for (item in anonymousItems) {
                    val migratedItem = item.copy(userId = userId)
                    wishlistItemDao.upsert(migratedItem)
                    try {
                        val data = mapOf(
                            "productId" to item.productId,
                            "productTitle" to item.productTitle,
                            "productPrice" to item.productPrice,
                            "addedAt" to item.addedAt
                        )
                        firestore
                            .collection("users")
                            .document(userId)
                            .collection("wishlist")
                            .document(item.productId)
                            .set(data)
                            .await()
                    } catch (e: Exception) {
                        Timber.e(e, "WishlistRepository: failed to sync anonymous item ${item.productId} to Firestore")
                    }
                }

                // Clean up anonymous rows from Room
                wishlistItemDao.deleteAllForUser("")
                Timber.d("WishlistRepository: migrated ${anonymousItems.size} anonymous wishlist items to user $userId")

                // Also fetch and merge Firestore items (e.g., from another device)
                fetchAndMergeFirestoreWishlist(userId)
            } catch (e: Exception) {
                Timber.e(e, "WishlistRepository: syncAnonymousOnLogin failed for user $userId")
            }
        }

    private suspend fun fetchAndMergeFirestoreWishlist(userId: String) {
        try {
            val snapshot = firestore
                .collection("users")
                .document(userId)
                .collection("wishlist")
                .get()
                .await()

            for (doc in snapshot.documents) {
                try {
                    val productId = doc.getString("productId") ?: doc.id
                    val productTitle = doc.getString("productTitle") ?: ""
                    val productPrice = doc.getDouble("productPrice") ?: 0.0
                    val addedAt = doc.getString("addedAt") ?: Instant.now().toString()

                    val entity = WishlistItemEntity(
                        userId = userId,
                        productId = productId,
                        productTitle = productTitle,
                        productImageUrl = "",
                        productPrice = productPrice,
                        availableStock = 0,
                        isProductDeleted = false,
                        addedAt = addedAt
                    )
                    wishlistItemDao.upsert(entity)
                } catch (e: Exception) {
                    Timber.e(e, "WishlistRepository: failed to parse Firestore wishlist doc ${doc.id}")
                }
            }
            Timber.d("WishlistRepository: merged ${snapshot.documents.size} Firestore wishlist items for user $userId")
        } catch (e: Exception) {
            Timber.e(e, "WishlistRepository: failed to fetch Firestore wishlist for user $userId")
        }
    }
}
