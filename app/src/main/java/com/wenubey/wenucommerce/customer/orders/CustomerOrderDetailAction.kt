package com.wenubey.wenucommerce.customer.orders

sealed interface CustomerOrderDetailAction {
    data class OnToggleSection(val sellerOrderId: String) : CustomerOrderDetailAction
    data object OnRetry : CustomerOrderDetailAction
    data object OnDismissError : CustomerOrderDetailAction
}
