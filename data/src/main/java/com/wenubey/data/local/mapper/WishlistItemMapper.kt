package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.WishlistItemEntity
import com.wenubey.domain.model.WishlistItem

fun WishlistItemEntity.toDomain(): WishlistItem = WishlistItem(
    productId = productId,
    productTitle = productTitle,
    productImageUrl = productImageUrl,
    productPrice = productPrice,
    availableStock = availableStock,
    isProductDeleted = isProductDeleted,
    addedAt = addedAt
)

fun WishlistItem.toEntity(userId: String): WishlistItemEntity = WishlistItemEntity(
    userId = userId,
    productId = productId,
    productTitle = productTitle,
    productImageUrl = productImageUrl,
    productPrice = productPrice,
    availableStock = availableStock,
    isProductDeleted = isProductDeleted,
    addedAt = addedAt
)
