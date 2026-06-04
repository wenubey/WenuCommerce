package com.wenubey.wenucommerce.seller.seller_discounts

sealed interface DiscountListAction {
    data class Delete(val code: String) : DiscountListAction
    data class Deactivate(val code: String) : DiscountListAction
    data object DismissError : DiscountListAction
}
