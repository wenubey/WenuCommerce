package com.wenubey.domain.model.order

enum class OrderStatus(val displayName: String) {
    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled")
}

/**
 * Forward-only transition map mirrored in `firestore.rules` `allowedNext()`.
 * - PENDING   -> CONFIRMED | CANCELLED
 * - CONFIRMED -> SHIPPED   | CANCELLED
 * - SHIPPED   -> DELIVERED            (no cancellation post-SHIPPED)
 * - DELIVERED, CANCELLED are terminal.
 *
 * Source of truth = rules; this extension exists for client-side UI gating
 * (defense-in-depth) and for the repository's pre-write guard.
 */
fun OrderStatus.allowedNext(): Set<OrderStatus> = when (this) {
    OrderStatus.PENDING -> setOf(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)
    OrderStatus.CONFIRMED -> setOf(OrderStatus.SHIPPED, OrderStatus.CANCELLED)
    OrderStatus.SHIPPED -> setOf(OrderStatus.DELIVERED)
    OrderStatus.DELIVERED -> emptySet()
    OrderStatus.CANCELLED -> emptySet()
}
