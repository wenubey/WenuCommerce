package com.wenubey.wenucommerce.seller.seller_products

import com.wenubey.domain.model.product.ProductReview

/**
 * UI state for the read-only seller-facing reviews list (07-03, ROADMAP 07-03).
 *
 * Display-only: there are no submit/edit/moderation fields — the seller can only
 * *see* the reviews left on one of their own products. [reviews] contains only
 * visible reviews (the ViewModel filters `isVisible == false` out).
 */
data class SellerProductReviewsState(
    val reviews: List<ProductReview> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val productTitle: String = "",
)
