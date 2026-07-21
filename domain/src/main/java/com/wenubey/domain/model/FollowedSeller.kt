package com.wenubey.domain.model

/**
 * Snapshot of a seller a customer follows. Written to Room via
 * FollowedSellersRepositoryImpl (Room-first, fire-and-forget Firestore).
 *
 * `followerCount` is NOT stored here — the aggregate count lives on the seller
 * User document and is surfaced via FirestoreRepositoryImpl.getUser().
 */
data class FollowedSeller(
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerLogoUrl: String = "",
    val followedAt: String = "",
)
