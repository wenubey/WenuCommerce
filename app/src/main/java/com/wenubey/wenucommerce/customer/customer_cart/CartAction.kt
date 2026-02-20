package com.wenubey.wenucommerce.customer.customer_cart

import com.wenubey.domain.model.CartItem

sealed interface CartAction {
    data class IncrementQuantity(val productId: String) : CartAction
    data class DecrementQuantity(val productId: String) : CartAction
    data class RemoveItem(val productId: String) : CartAction
    data class UndoRemove(val item: CartItem) : CartAction
    data class ToggleSelection(val productId: String) : CartAction
    data object DeleteSelected : CartAction
    data object ToggleSelectionMode : CartAction
    data object ClearSelection : CartAction
    data class NavigateToProduct(val productId: String) : CartAction
    data object NavigateToHome : CartAction
    data object Checkout : CartAction
}
