package com.wenubey.wenucommerce.customer.customer_products

import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductReview
import com.wenubey.domain.model.product.ProductVariant

data class CustomerProductDetailState(
    val product: Product? = null,
    val reviews: List<ProductReview> = listOf(),
    val selectedVariant: ProductVariant? = null,
    val isLoading: Boolean = false,
    val isLoadingReviews: Boolean = false,
    val errorMessage: String? = null,
    // Cart-related state
    val cartQuantity: Int = 0,       // 0 if not in cart, else current cart quantity
    val isInCart: Boolean = false,
    val selectedQuantity: Int = 1,   // stepper quantity for adding to cart
    val isAddingToCart: Boolean = false,
    val cartMessage: String? = null, // snackbar message after add-to-cart
    val showLoginPrompt: Boolean = false, // auth gate for unauthenticated users
    // Wishlist state
    val isWishlisted: Boolean = false,
    // Review form / eligibility state (Phase 7 — 07-02)
    val hasDeliveredOrder: Boolean = false,      // D-07: gates the write/edit affordance
    val existingReview: ProductReview? = null,   // D-05: pre-fill for edit-in-place
    val reviewSortOrder: ReviewSortOrder = ReviewSortOrder.MOST_RECENT, // REVW-06 default
    val isSubmittingReview: Boolean = false,     // submit spinner
    val showReviewForm: Boolean = false,         // review form visibility
    val reviewSubmitError: String? = null,       // submit error snackbar
    val isCheckingEligibility: Boolean = false,  // delivered-order lookup in flight
    val helpfulVotedIds: Set<String> = emptySet(), // D-04: optimistic helpful disable
)

/**
 * Sort order for the product-detail reviews list (REVW-06). Default is
 * [MOST_RECENT]; [HIGHEST_RATED] breaks ties by recency.
 */
enum class ReviewSortOrder { MOST_RECENT, HIGHEST_RATED }
