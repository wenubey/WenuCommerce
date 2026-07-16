package com.wenubey.wenucommerce.customer.customer_products

import com.wenubey.domain.model.product.ProductVariant

sealed interface CustomerProductDetailAction {
    data class OnVariantSelected(val variant: ProductVariant) : CustomerProductDetailAction
    data class OnMarkReviewHelpful(val reviewId: String) : CustomerProductDetailAction

    // Cart actions
    data class SetQuantity(val quantity: Int) : CustomerProductDetailAction
    data object AddToCart : CustomerProductDetailAction
    data class UpdateCartQuantity(val newQuantity: Int) : CustomerProductDetailAction
    data object DismissLoginPrompt : CustomerProductDetailAction
    data object DismissCartMessage : CustomerProductDetailAction
    // Wishlist actions
    data object ToggleWishlist : CustomerProductDetailAction

    // Review actions (Phase 7 — 07-02)
    data object OpenReviewForm : CustomerProductDetailAction
    data object DismissReviewForm : CustomerProductDetailAction
    data class SubmitReview(
        val rating: Int,
        val title: String,
        val body: String,
    ) : CustomerProductDetailAction
    data class OnSortOrderChanged(val order: ReviewSortOrder) : CustomerProductDetailAction
}
