package com.wenubey.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.local.dao.FollowedSellerDao
import com.wenubey.data.local.entity.FollowedSellerEntity
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.util.USER_COLLECTION
import com.wenubey.domain.model.FollowedSeller
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FollowedSellersRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant

/**
 * Offline-first follow repository. Room is the source of truth; Firestore is
 * fire-and-forget (CD-02, D-03). No anonymous branch — caller (the ViewModel)
 * guarantees non-blank userId. Only the follow doc body is written; the
 * aggregate `followerCount` on the seller User is Admin-SDK-only (09-02).
 */
class FollowedSellersRepositoryImpl(
    private val followedSellerDao: FollowedSellerDao,
    private val firestore: FirebaseFirestore,
    private val dispatcherProvider: DispatcherProvider,
) : FollowedSellersRepository {

    override fun observeFollowedSellers(userId: String): Flow<List<FollowedSeller>> =
        followedSellerDao.observeFollowedSellers(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun isFollowing(userId: String, sellerId: String): Flow<Boolean> =
        followedSellerDao.isFollowing(userId, sellerId)

    override suspend fun followSeller(
        userId: String,
        sellerId: String,
        sellerName: String,
        sellerLogoUrl: String,
    ) = withContext(dispatcherProvider.io()) {
        val entity = FollowedSellerEntity(
            userId = userId,
            sellerId = sellerId,
            sellerName = sellerName,
            sellerLogoUrl = sellerLogoUrl,
            followedAt = Instant.now().toString(),
        )
        followedSellerDao.upsert(entity)
        try {
            val data = mapOf(
                "sellerId" to sellerId,
                "sellerName" to sellerName,
                "sellerLogoUrl" to sellerLogoUrl,
                "followedAt" to entity.followedAt,
            )
            firestore
                .collection(USER_COLLECTION)
                .document(userId)
                .collection("followed_sellers")
                .document(sellerId)
                .set(data)
                .await()
            Timber.d("FollowedSellersRepository: followed $sellerId for user $userId")
        } catch (e: Exception) {
            Timber.e(e, "FollowedSellersRepository: Firestore follow write failed (Room updated)")
        }
    }

    override suspend fun unfollowSeller(userId: String, sellerId: String) =
        withContext(dispatcherProvider.io()) {
            followedSellerDao.deleteItem(userId, sellerId)
            try {
                firestore
                    .collection(USER_COLLECTION)
                    .document(userId)
                    .collection("followed_sellers")
                    .document(sellerId)
                    .delete()
                    .await()
                Timber.d("FollowedSellersRepository: unfollowed $sellerId for user $userId")
            } catch (e: Exception) {
                Timber.e(e, "FollowedSellersRepository: Firestore unfollow delete failed (Room updated)")
            }
        }
}
