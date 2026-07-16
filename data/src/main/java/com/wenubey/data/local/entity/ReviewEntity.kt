package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of Firestore `PRODUCTS/{productId}/REVIEWS/{reviewId}` (schema
 * v9). All columns are scalar (no JSON) — the review has no nested collections
 * cached locally. Indices on productId + reviewerId back the product-scoped
 * observe query and the per-reviewer lookup respectively.
 *
 * Reviews are written server-only via the submitReview / markReviewHelpful
 * callables; Room caches them read-side per the established offline-first
 * pattern (product-scoped write-through listener in ProductReviewRepositoryImpl).
 */
@Entity(
    tableName = "reviews",
    indices = [
        Index("productId"),
        Index("reviewerId")
    ]
)
data class ReviewEntity(
    @PrimaryKey val id: String,
    val productId: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val reviewerPhotoUrl: String = "",
    val purchaseId: String = "",
    val rating: Int = 0,
    val title: String = "",
    val body: String = "",
    val isVerifiedPurchase: Boolean = true,
    val helpfulCount: Int = 0,
    val isVisible: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
)
