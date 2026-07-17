package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.NotificationEntity
import com.wenubey.domain.model.Notification

/**
 * NotificationEntity <-> Notification mappers. Direct scalar field mapping — the
 * notification entity has no JSON columns, so no kotlinx.serialization round-trip
 * is needed (same style as ReviewMapper). `createdAt` stays a String
 * (epoch-millis) and `isRead` maps directly to the Boolean flag.
 */
fun NotificationEntity.toDomain(): Notification = Notification(
    id = id,
    userId = userId,
    type = type,
    title = title,
    body = body,
    orderId = orderId,
    sellerOrderId = sellerOrderId,
    productId = productId,
    productTitle = productTitle,
    isRead = isRead,
    createdAt = createdAt,
)

fun Notification.toEntity(): NotificationEntity = NotificationEntity(
    id = id,
    userId = userId,
    type = type,
    title = title,
    body = body,
    orderId = orderId,
    sellerOrderId = sellerOrderId,
    productId = productId,
    productTitle = productTitle,
    isRead = isRead,
    createdAt = createdAt,
)
