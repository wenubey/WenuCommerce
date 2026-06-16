package com.wenubey.wenucommerce.customer.orders

import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.SellerOrder

data class CustomerOrderDetailState(
    val order: Order? = null,
    val sellerOrders: List<SellerOrder> = emptyList(),
    val expandedSellerIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
