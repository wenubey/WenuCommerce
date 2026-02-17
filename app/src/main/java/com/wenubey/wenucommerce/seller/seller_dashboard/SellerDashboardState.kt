package com.wenubey.wenucommerce.seller.seller_dashboard

import com.wenubey.domain.model.user.User

data class SellerDashboardState(
    val user: User? = null,
    val errorMessage: String? = null,
    val isBannerVisible: Boolean = true,
)
