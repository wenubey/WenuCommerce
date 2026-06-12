package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.product.ProductReview
import com.wenubey.domain.repository.ProductReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeProductReviewRepository : ProductReviewRepository {

    private val reviewsByProduct = MutableStateFlow<Map<String, List<ProductReview>>>(emptyMap())

    val markHelpfulCalls = mutableListOf<Pair<String, String>>()
    val submitReviewCalls = mutableListOf<ProductReview>()
    val setVisibilityCalls = mutableListOf<Triple<String, String, Boolean>>()
    var observeFlow: Flow<List<ProductReview>>? = null

    var submitReviewResult: (ProductReview) -> Result<ProductReview> = { Result.success(it) }
    var markHelpfulResult: Result<Unit> = Result.success(Unit)
    var setVisibilityResult: Result<Unit> = Result.success(Unit)

    fun emit(productId: String, list: List<ProductReview>) {
        reviewsByProduct.value = reviewsByProduct.value + (productId to list)
    }

    override fun observeReviewsForProduct(productId: String): Flow<List<ProductReview>> =
        observeFlow ?: reviewsByProduct.map { it[productId].orEmpty() }

    override suspend fun getReviewsForProduct(productId: String): Result<List<ProductReview>> =
        Result.success(reviewsByProduct.value[productId].orEmpty())

    override suspend fun submitReview(review: ProductReview): Result<ProductReview> {
        submitReviewCalls.add(review)
        return submitReviewResult(review)
    }

    override suspend fun markReviewHelpful(productId: String, reviewId: String): Result<Unit> {
        markHelpfulCalls.add(productId to reviewId)
        return markHelpfulResult
    }

    override suspend fun setReviewVisibility(
        productId: String,
        reviewId: String,
        isVisible: Boolean,
    ): Result<Unit> {
        setVisibilityCalls.add(Triple(productId, reviewId, isVisible))
        return setVisibilityResult
    }
}
