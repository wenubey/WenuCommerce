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
)
