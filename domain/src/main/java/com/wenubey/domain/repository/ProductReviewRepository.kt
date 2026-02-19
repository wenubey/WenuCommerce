package com.wenubey.domain.repository

import com.wenubey.domain.model.product.ProductReview
import kotlinx.coroutines.flow.Flow

interface ProductReviewRepository {
    fun observeReviewsForProduct(productId: String): Flow<List<ProductReview>>
    suspend fun getReviewsForProduct(productId: String): Result<List<ProductReview>>
    suspend fun submitReview(review: ProductReview): Result<ProductReview>
    suspend fun markReviewHelpful(productId: String, reviewId: String): Result<Unit>
    suspend fun setReviewVisibility(
        productId: String,
        reviewId: String,
        isVisible: Boolean,
    ): Result<Unit>
}
