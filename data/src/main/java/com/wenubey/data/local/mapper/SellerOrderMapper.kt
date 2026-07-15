package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.SellerOrderEntity
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.domain.model.order.StatusEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SellerOrder <-> SellerOrderEntity mappers. JSON columns deserialised via
 * kotlinx.serialization with `runCatching` + safe defaults — mirrors
 * OrderMapper.kt pattern. Forward-compat via `ignoreUnknownKeys = true`.
 */
private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun SellerOrderEntity.toDomain(): SellerOrder = SellerOrder(
    id = id,
    parentOrderId = parentOrderId,
    userId = userId,
    sellerId = sellerId,
    sellerName = sellerName,
    sellerLogoUrl = sellerLogoUrl,
    items = runCatching {
        json.decodeFromString<List<OrderItem>>(itemsJson)
    }.getOrElse { emptyList() },
    subtotal = subtotal,
    shippingShare = shippingShare,
    discountShare = discountShare,
    status = runCatching { OrderStatus.valueOf(status) }.getOrElse { OrderStatus.PENDING },
    statusHistory = runCatching {
        json.decodeFromString<List<StatusEntry>>(statusHistoryJson)
    }.getOrElse { emptyList() },
    trackingNumber = trackingNumber,
    refundId = refundId,
    refundedAmount = refundedAmount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SellerOrder.toEntity(): SellerOrderEntity = SellerOrderEntity(
    id = id,
    parentOrderId = parentOrderId,
    userId = userId,
    sellerId = sellerId,
    sellerName = sellerName,
    sellerLogoUrl = sellerLogoUrl,
    subtotal = subtotal,
    shippingShare = shippingShare,
    discountShare = discountShare,
    status = status.name,
    trackingNumber = trackingNumber,
    refundId = refundId,
    refundedAmount = refundedAmount,
    itemsJson = json.encodeToString(items),
    statusHistoryJson = json.encodeToString(statusHistory),
    createdAt = createdAt,
    updatedAt = updatedAt
)
