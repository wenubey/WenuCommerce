package com.wenubey.wenucommerce.customer.customer_wishlist

import com.wenubey.domain.model.WishlistItem

data class WishlistState(
    val wishlistItems: List<WishlistItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedItems: Set<String> = emptySet(), // productIds for multi-select
    val isSelectionMode: Boolean = false,
    val undoItem: WishlistItem? = null,
)
