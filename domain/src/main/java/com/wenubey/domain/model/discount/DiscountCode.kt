package com.wenubey.domain.model.discount

data class DiscountCode(
    val code: String = "",
    val type: DiscountType = DiscountType.PERCENTAGE,
    val value: Double = 0.0,
    val maxDiscountCap: Double? = null,
    val minimumOrderAmount: Double? = null,
    val targetProductIds: List<String> = emptyList(),
    val sellerId: String = "",
    val expiresAt: String? = null,
    val usageLimit: Int? = null,
    val usageCount: Int = 0,
    val isActive: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
)
