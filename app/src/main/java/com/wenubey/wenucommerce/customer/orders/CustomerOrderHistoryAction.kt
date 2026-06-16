package com.wenubey.wenucommerce.customer.orders

sealed interface CustomerOrderHistoryAction {
    data class OnFilterSelected(val filter: OrderFilter) : CustomerOrderHistoryAction
    data class OnOrderClicked(val orderId: String) : CustomerOrderHistoryAction
    data object OnRefresh : CustomerOrderHistoryAction
    data object OnRetry : CustomerOrderHistoryAction
    data object OnDismissError : CustomerOrderHistoryAction
}
