package com.wenubey.wenucommerce.seller.orders

import com.wenubey.domain.model.order.SellerOrder

data class SellerOrderDetailState(
    val sellerOrder: SellerOrder? = null,
    val isMutating: Boolean = false,
    val showShippedDialog: Boolean = false,
    val showCancelDialog: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
)
