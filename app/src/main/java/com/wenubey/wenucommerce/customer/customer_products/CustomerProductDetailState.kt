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
)
