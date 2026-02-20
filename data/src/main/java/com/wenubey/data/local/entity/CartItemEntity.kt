package com.wenubey.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "cart_items",
    primaryKeys = ["userId", "productId"]
)
data class CartItemEntity(
    val userId: String,
    val productId: String,
    val productTitle: String = "",
    val productImageUrl: String = "",
    val quantity: Int = 1,
    val snapshotPrice: Double = 0.0,
    val availableStock: Int = 0,
    val isProductDeleted: Boolean = false,
    val addedAt: String = "",
    val updatedAt: String = ""
)
