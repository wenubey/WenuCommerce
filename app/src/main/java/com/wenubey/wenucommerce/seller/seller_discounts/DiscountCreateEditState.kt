package com.wenubey.wenucommerce.seller.seller_discounts

import com.wenubey.domain.model.discount.DiscountType

data class DiscountCreateEditState(
    val isEditMode: Boolean = false,
    val code: String = "",
    val type: DiscountType = DiscountType.PERCENTAGE,
    val value: String = "",
    val maxDiscountCap: String = "",
    val minimumOrderAmount: String = "",
    val targetProductIds: List<String> = emptyList(),
    val expiresAt: Long? = null,
    val usageLimit: String = "",
    val availableProducts: List<ProductPickerItem> = emptyList(),
    val productSearchQuery: String = "",
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
)

data class ProductPickerItem(
    val productId: String,
    val title: String,
    val isSelected: Boolean,
)
