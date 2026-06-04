package com.wenubey.wenucommerce.seller.seller_discounts

import com.wenubey.domain.model.discount.DiscountType

sealed interface DiscountCreateEditAction {
    data class UpdateCode(val code: String) : DiscountCreateEditAction
    data object GenerateCode : DiscountCreateEditAction
    data class UpdateType(val type: DiscountType) : DiscountCreateEditAction
    data class UpdateValue(val value: String) : DiscountCreateEditAction
    data class UpdateMaxCap(val cap: String) : DiscountCreateEditAction
    data class UpdateMinOrder(val amount: String) : DiscountCreateEditAction
    data class UpdateUsageLimit(val limit: String) : DiscountCreateEditAction
    data class UpdateExpiryDate(val millis: Long?) : DiscountCreateEditAction
    data class UpdateProductSearch(val query: String) : DiscountCreateEditAction
    data class ToggleProduct(val productId: String) : DiscountCreateEditAction
    data object Save : DiscountCreateEditAction
    data object DismissError : DiscountCreateEditAction
}
