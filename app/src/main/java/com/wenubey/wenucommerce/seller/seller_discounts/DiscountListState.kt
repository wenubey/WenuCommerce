package com.wenubey.wenucommerce.seller.seller_discounts

import com.wenubey.domain.model.discount.DiscountCode

data class DiscountListState(
    val discounts: List<DiscountCode> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
