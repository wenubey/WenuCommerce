package com.wenubey.domain.model.order

/**
 * Computed parent-Order aggregate across N SellerOrder children. Written by the
 * `onOrderStatusChange` Cloud Function trigger; the client only reads it.
 *
 * Mapping rule (RESEARCH §2.8): all CANCELLED -> CANCELLED;
 * hasCancelled + mixed -> PARTIALLY_CANCELLED; otherwise the minimum
 * (least-advanced) non-cancelled status across children.
 */
enum class AggregateOrderStatus(val displayName: String) {
    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled"),
    PARTIALLY_CANCELLED("Partially cancelled")
}
