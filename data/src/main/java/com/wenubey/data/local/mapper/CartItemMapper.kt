package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.CartItemEntity
import com.wenubey.domain.model.CartItem

fun CartItemEntity.toDomain(): CartItem = CartItem(
    productId = productId,
    productTitle = productTitle,
    productImageUrl = productImageUrl,
    quantity = quantity,
    snapshotPrice = snapshotPrice,
    availableStock = availableStock,
    isProductDeleted = isProductDeleted,
    addedAt = addedAt,
    updatedAt = updatedAt
)

fun CartItem.toEntity(userId: String): CartItemEntity = CartItemEntity(
    userId = userId,
    productId = productId,
    productTitle = productTitle,
    productImageUrl = productImageUrl,
    quantity = quantity,
    snapshotPrice = snapshotPrice,
    availableStock = availableStock,
    isProductDeleted = isProductDeleted,
    addedAt = addedAt,
    updatedAt = updatedAt
)
