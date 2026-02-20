package com.wenubey.domain.model

data class WishlistItem(
    val productId: String = "",
    val productTitle: String = "",
    val productImageUrl: String = "",
    val productPrice: Double = 0.0,
    val availableStock: Int = 0,
    val isProductDeleted: Boolean = false,
    val addedAt: String = ""
)
