package com.wenubey.wenucommerce.seller.orders

import com.wenubey.domain.model.order.OrderStatus

sealed interface SellerOrderDetailAction {
    data class OnAdvanceClicked(val next: OrderStatus) : SellerOrderDetailAction
    data object OnCancelClicked : SellerOrderDetailAction
    data class OnConfirmShipped(val trackingNumber: String?) : SellerOrderDetailAction
    data object OnConfirmCancel : SellerOrderDetailAction
    data object OnDismissShippedDialog : SellerOrderDetailAction
    data object OnDismissCancelDialog : SellerOrderDetailAction
    data object OnConsumeToast : SellerOrderDetailAction
    data object OnDismissError : SellerOrderDetailAction
}
