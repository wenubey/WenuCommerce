package com.wenubey.wenucommerce.seller.orders

sealed interface SellerOrdersAction {
    data class OnFilterSelected(val filter: SellerOrderFilter) : SellerOrdersAction
    data class OnOrderClicked(val sellerOrderId: String) : SellerOrdersAction
    data object OnRefresh : SellerOrdersAction
    data object OnRetry : SellerOrdersAction
    data object OnDismissError : SellerOrdersAction
}
