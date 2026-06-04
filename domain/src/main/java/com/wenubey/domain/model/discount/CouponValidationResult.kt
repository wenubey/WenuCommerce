package com.wenubey.domain.model.discount

data class CouponValidationResult(
    val code: String,
    val type: DiscountType,
    val discountAmountCents: Int,
    val description: String,
)
