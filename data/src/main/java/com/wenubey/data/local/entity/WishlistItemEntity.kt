package com.wenubey.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "wishlist_items",
    primaryKeys = ["userId", "productId"]
)
data class WishlistItemEntity(
    val userId: String,         // "" for anonymous users
    val productId: String,
    val productTitle: String = "",
    val productImageUrl: String = "",
    val productPrice: Double = 0.0,
    val availableStock: Int = 0,
    val isProductDeleted: Boolean = false,
    val addedAt: String = ""
)
