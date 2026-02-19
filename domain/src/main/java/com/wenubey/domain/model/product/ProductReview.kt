package com.wenubey.domain.model.product

import kotlinx.serialization.Serializable

@Serializable
data class ProductReview(
    val id: String = "",
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

fun ProductReview.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "productId" to productId,
    "reviewerId" to reviewerId,
    "reviewerName" to reviewerName,
    "reviewerPhotoUrl" to reviewerPhotoUrl,
    "purchaseId" to purchaseId,
    "rating" to rating,
    "title" to title,
    "body" to body,
    "isVerifiedPurchase" to isVerifiedPurchase,
    "helpfulCount" to helpfulCount,
    "isVisible" to isVisible,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)
