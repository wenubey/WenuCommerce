package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.ReviewEntity
import com.wenubey.domain.model.product.ProductReview

/**
 * ReviewEntity <-> ProductReview mappers. Direct scalar field mapping — the
 * review entity has no JSON columns, so no kotlinx.serialization round-trip is
 * needed (contrast SellerOrderMapper's itemsJson handling).
 */
fun ReviewEntity.toDomain(): ProductReview = ProductReview(
    id = id,
    productId = productId,
    reviewerId = reviewerId,
    reviewerName = reviewerName,
    reviewerPhotoUrl = reviewerPhotoUrl,
    purchaseId = purchaseId,
    rating = rating,
    title = title,
    body = body,
    isVerifiedPurchase = isVerifiedPurchase,
    helpfulCount = helpfulCount,
    isVisible = isVisible,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProductReview.toEntity(): ReviewEntity = ReviewEntity(
    id = id,
    productId = productId,
    reviewerId = reviewerId,
    reviewerName = reviewerName,
    reviewerPhotoUrl = reviewerPhotoUrl,
    purchaseId = purchaseId,
    rating = rating,
    title = title,
    body = body,
    isVerifiedPurchase = isVerifiedPurchase,
    helpfulCount = helpfulCount,
    isVisible = isVisible,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
