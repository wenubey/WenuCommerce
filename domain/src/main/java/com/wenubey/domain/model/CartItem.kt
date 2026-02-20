package com.wenubey.domain.model

data class CartItem(
    val productId: String = "",
    val productTitle: String = "",
    val productImageUrl: String = "",
    val quantity: Int = 1,
    val snapshotPrice: Double = 0.0,
    val availableStock: Int = 0,
    val isProductDeleted: Boolean = false,
    val addedAt: String = "",
    val updatedAt: String = ""
)
