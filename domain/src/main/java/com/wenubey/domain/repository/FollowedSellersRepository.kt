package com.wenubey.domain.repository

import com.wenubey.domain.model.FollowedSeller
import kotlinx.coroutines.flow.Flow

/**
 * Follow relationship contract for the customer -> seller "favorite" feature.
 *
 * Deliberately has NO anonymous branch (D-03 in 09-CONTEXT.md): the auth guard
 * lives in the ViewModel (09-03). Callers guarantee a non-blank `userId`.
 * `syncAnonymousOnLogin` is intentionally omitted vs. WishlistRepository.
 */
interface FollowedSellersRepository {
    fun observeFollowedSellers(userId: String): Flow<List<FollowedSeller>>
    fun isFollowing(userId: String, sellerId: String): Flow<Boolean>
    suspend fun followSeller(
        userId: String,
        sellerId: String,
        sellerName: String,
        sellerLogoUrl: String,
    )
    suspend fun unfollowSeller(userId: String, sellerId: String)
}
