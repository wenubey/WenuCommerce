package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.OrderEntity
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.ShippingAddress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun OrderEntity.toDomain(): Order = Order(
    id = id,
    userId = userId,
    status = runCatching { OrderStatus.valueOf(status) }.getOrElse { OrderStatus.PENDING },
    subtotal = subtotal,
    shippingTotal = shippingTotal,
    totalAmount = totalAmount,
    currency = currency,
    stripePaymentIntentId = stripePaymentIntentId,
    shippingAddress = runCatching {
        json.decodeFromString<ShippingAddress>(shippingAddressJson)
    }.getOrElse { ShippingAddress.default() },
    items = runCatching {
        json.decodeFromString<List<OrderItem>>(itemsJson)
    }.getOrElse { emptyList() },
    discountAmount = discountAmount,
    discountCode = discountCode,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Order.toEntity(): OrderEntity = OrderEntity(
    id = id,
    userId = userId,
    status = status.name,
    subtotal = subtotal,
    shippingTotal = shippingTotal,
    totalAmount = totalAmount,
    currency = currency,
    stripePaymentIntentId = stripePaymentIntentId,
    shippingAddressJson = json.encodeToString(shippingAddress),
    itemsJson = json.encodeToString(items),
    discountAmount = discountAmount,
    discountCode = discountCode,
    createdAt = createdAt,
    updatedAt = updatedAt
)
