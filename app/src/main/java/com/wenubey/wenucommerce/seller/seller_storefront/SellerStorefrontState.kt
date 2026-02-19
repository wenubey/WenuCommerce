package com.wenubey.wenucommerce.seller.seller_storefront

import com.wenubey.domain.model.product.Product

data class SellerStorefrontState(
    val products: List<Product> = listOf(),
    val sellerName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
