package com.wenubey.wenucommerce.customer.orders

import com.wenubey.domain.model.order.AggregateOrderStatus
import com.wenubey.domain.model.order.Order

enum class OrderFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled"),
}

/**
 * Single immutable UI state for the customer order history screen.
 *
 * `orders` is the full set observed from Room (Flow). `visibleOrders` applies the
 * current filter + newest-first sort.
 */
data class CustomerOrderHistoryState(
    val orders: List<Order> = emptyList(),
    val filter: OrderFilter = OrderFilter.ALL,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Filtered + sorted projection for rendering.
 *
 * PARTIALLY_CANCELLED appears in both ACTIVE and CANCELLED lists (CONTEXT D4,
 * Open Question 5 resolution).
 */
fun CustomerOrderHistoryState.visibleOrders(): List<Order> {
    val matched = when (filter) {
        OrderFilter.ALL -> orders
        OrderFilter.ACTIVE -> orders.filter {
            it.aggregateStatus in setOf(
                AggregateOrderStatus.PENDING,
                AggregateOrderStatus.CONFIRMED,
                AggregateOrderStatus.SHIPPED,
                AggregateOrderStatus.PARTIALLY_CANCELLED,
            )
        }
        OrderFilter.DELIVERED -> orders.filter { it.aggregateStatus == AggregateOrderStatus.DELIVERED }
        OrderFilter.CANCELLED -> orders.filter {
            it.aggregateStatus in setOf(
                AggregateOrderStatus.CANCELLED,
                AggregateOrderStatus.PARTIALLY_CANCELLED,
            )
        }
    }
    return matched.sortedByDescending { it.createdAt }
}
