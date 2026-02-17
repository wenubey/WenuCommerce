package com.wenubey.wenucommerce.seller.seller_dashboard

sealed interface SellerDashboardAction {
    data object OnAddProduct: SellerDashboardAction
    data object HideBanner: SellerDashboardAction
}