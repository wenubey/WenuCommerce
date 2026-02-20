package com.wenubey.wenucommerce.customer.customer_cart

import com.wenubey.domain.model.CartItem

data class CartState(
    val cartItems: List<CartItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedItems: Set<String> = emptySet(), // productIds for bulk select
    val isSelectionMode: Boolean = false,
    val undoItem: CartItem? = null, // for undo snackbar after removal
) {
    val subtotal: Double
        get() = cartItems
            .filter { !it.isProductDeleted && it.availableStock > 0 }
            .sumOf { it.snapshotPrice * it.quantity }

    val availableItemCount: Int
        get() = cartItems.count { !it.isProductDeleted && it.availableStock > 0 }

    val hasUnavailableItems: Boolean
        get() = cartItems.any { it.isProductDeleted || it.availableStock <= 0 }

    val canCheckout: Boolean
        get() = cartItems.isNotEmpty() && cartItems.all { !it.isProductDeleted && it.availableStock > 0 }
}
