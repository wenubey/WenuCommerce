package com.wenubey.domain.model.order

import kotlinx.serialization.Serializable

/**
 * Single entry in a SellerOrder's statusHistory timeline. ISO-8601 timestamp string
 * for parity with the existing Order.createdAt convention; the rule layer enforces
 * append-only growth.
 */
@Serializable
data class StatusEntry(
    val status: OrderStatus = OrderStatus.PENDING,
    val timestamp: String = "",
    val note: String? = null,
    val trackingNumber: String? = null
)
