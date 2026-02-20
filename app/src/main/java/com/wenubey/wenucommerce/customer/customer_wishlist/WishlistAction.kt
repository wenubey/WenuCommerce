package com.wenubey.wenucommerce.customer.customer_wishlist

import com.wenubey.domain.model.WishlistItem

sealed interface WishlistAction {
    data class RemoveFromWishlist(val productId: String) : WishlistAction
    data class UndoRemove(val item: WishlistItem) : WishlistAction
    data class AddItemToCart(val item: WishlistItem) : WishlistAction
    data object AddAllToCart : WishlistAction
    data object AddSelectedToCart : WishlistAction
    data class ToggleSelection(val productId: String) : WishlistAction
    data object ClearSelection : WishlistAction
    data class NavigateToProduct(val productId: String) : WishlistAction
    data object NavigateToHome : WishlistAction
}
