package com.wenubey.wenucommerce.seller.orders

import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder

/**
 * Filter chips on the seller orders list. Maps 1:1 to the 5 [OrderStatus] values
 * plus an ALL pass-through.
 */
enum class SellerOrderFilter {
    ALL,
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
}

data class SellerOrdersState(
    val sellerOrders: List<SellerOrder> = emptyList(),
    val filter: SellerOrderFilter = SellerOrderFilter.ALL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Filtered + sorted (createdAt DESC) view of [SellerOrdersState.sellerOrders]
 * for direct LazyColumn consumption.
 */
fun SellerOrdersState.visibleSellerOrders(): List<SellerOrder> {
    val filtered = when (filter) {
        SellerOrderFilter.ALL -> sellerOrders
        SellerOrderFilter.PENDING -> sellerOrders.filter { it.status == OrderStatus.PENDING }
        SellerOrderFilter.CONFIRMED -> sellerOrders.filter { it.status == OrderStatus.CONFIRMED }
        SellerOrderFilter.SHIPPED -> sellerOrders.filter { it.status == OrderStatus.SHIPPED }
        SellerOrderFilter.DELIVERED -> sellerOrders.filter { it.status == OrderStatus.DELIVERED }
        SellerOrderFilter.CANCELLED -> sellerOrders.filter { it.status == OrderStatus.CANCELLED }
    }
    return filtered.sortedByDescending { it.createdAt }
}
