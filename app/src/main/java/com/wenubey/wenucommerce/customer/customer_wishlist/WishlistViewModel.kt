package com.wenubey.wenucommerce.customer.customer_wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.WishlistItem
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductCondition
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.model.product.ShippingType

import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.CartRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.WishlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class WishlistViewModel(
    private val wishlistRepository: WishlistRepository,
    private val cartRepository: CartRepository,
    private val authRepository: AuthRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(WishlistState())
    val state: StateFlow<WishlistState> = _state.asStateFlow()

    private val ioDispatcher = dispatcherProvider.io()

    init {
        val userId = authRepository.currentUser.value?.uuid ?: ""
        observeWishlistItems(userId)
    }

    private fun observeWishlistItems(userId: String) {
        viewModelScope.launch(ioDispatcher) {
            wishlistRepository.observeWishlistItems(userId)
                .catch { error ->
                    Timber.e(error, "WishlistViewModel: failed to observe wishlist items")
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
                .collect { items ->
                    _state.update { it.copy(wishlistItems = items, isLoading = false, error = null) }
                }
        }
    }

    fun onAction(action: WishlistAction) {
        val userId = authRepository.currentUser.value?.uuid ?: ""
        when (action) {
            is WishlistAction.RemoveFromWishlist -> removeFromWishlist(userId, action.productId)
            is WishlistAction.UndoRemove -> undoRemove(action.item)
            is WishlistAction.AddItemToCart -> addItemToCart(userId, action.item)
            is WishlistAction.AddAllToCart -> addAllToCart(userId)
            is WishlistAction.AddSelectedToCart -> addSelectedToCart(userId)
            is WishlistAction.ToggleSelection -> toggleSelection(action.productId)
            is WishlistAction.ClearSelection -> _state.update {
                it.copy(selectedItems = emptySet(), isSelectionMode = false)
            }
            is WishlistAction.NavigateToProduct -> { /* handled by UI */ }
            is WishlistAction.NavigateToHome -> { /* handled by UI */ }
        }
    }

    private fun removeFromWishlist(userId: String, productId: String) {
        val item = _state.value.wishlistItems.find { it.productId == productId } ?: return
        _state.update { it.copy(undoItem = item) }
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                wishlistRepository.removeFromWishlist(userId.ifEmpty { "" }, productId)
            }.onFailure { error ->
                Timber.e(error, "WishlistViewModel: failed to remove from wishlist $productId")
                _state.update { it.copy(undoItem = null) }
            }
        }
    }

    private fun undoRemove(item: WishlistItem) {
        _state.update { it.copy(undoItem = null) }
        viewModelScope.launch(ioDispatcher) {
            // Reconstruct a minimal Product from WishlistItem snapshot data to re-toggle
            val minimalProduct = buildMinimalProduct(item)
            runCatching {
                val effectiveUserId = authRepository.currentUser.value?.uuid
                wishlistRepository.toggleWishlist(effectiveUserId, minimalProduct)
            }.onFailure { error ->
                Timber.e(error, "WishlistViewModel: failed to undo remove for ${item.productId}")
            }
        }
    }

    private fun addItemToCart(userId: String, item: WishlistItem) {
        if (userId.isEmpty()) {
            // Anonymous users cannot add to cart (cart requires auth)
            _state.update { it.copy(error = "Please sign in to add items to your cart") }
            return
        }
        if (item.isProductDeleted || item.availableStock <= 0) return

        viewModelScope.launch(ioDispatcher) {
            runCatching {
                val minimalProduct = buildMinimalProduct(item)
                cartRepository.addToCart(userId, minimalProduct, 1)
            }.onFailure { error ->
                Timber.e(error, "WishlistViewModel: failed to add item ${item.productId} to cart")
            }
        }
    }

    private fun addAllToCart(userId: String) {
        if (userId.isEmpty()) {
            _state.update { it.copy(error = "Please sign in to add items to your cart") }
            return
        }
        val availableItems = _state.value.wishlistItems.filter {
            !it.isProductDeleted && it.availableStock > 0
        }
        viewModelScope.launch(ioDispatcher) {
            var addedCount = 0
            availableItems.forEach { item ->
                runCatching {
                    val minimalProduct = buildMinimalProduct(item)
                    cartRepository.addToCart(userId, minimalProduct, 1)
                    addedCount++
                }.onFailure { error ->
                    Timber.e(error, "WishlistViewModel: failed to add ${item.productId} to cart in bulk")
                }
            }
            Timber.d("WishlistViewModel: added $addedCount of ${availableItems.size} items to cart")
        }
    }

    private fun addSelectedToCart(userId: String) {
        if (userId.isEmpty()) {
            _state.update { it.copy(error = "Please sign in to add items to your cart") }
            return
        }
        val selectedIds = _state.value.selectedItems
        val selectedItems = _state.value.wishlistItems.filter { item ->
            item.productId in selectedIds && !item.isProductDeleted && item.availableStock > 0
        }
        viewModelScope.launch(ioDispatcher) {
            selectedItems.forEach { item ->
                runCatching {
                    val minimalProduct = buildMinimalProduct(item)
                    cartRepository.addToCart(userId, minimalProduct, 1)
                }.onFailure { error ->
                    Timber.e(error, "WishlistViewModel: failed to add selected item ${item.productId} to cart")
                }
            }
            _state.update { it.copy(selectedItems = emptySet(), isSelectionMode = false) }
        }
    }

    private fun toggleSelection(productId: String) {
        _state.update { current ->
            val selected = current.selectedItems.toMutableSet()
            if (productId in selected) selected.remove(productId) else selected.add(productId)
            current.copy(
                selectedItems = selected,
                isSelectionMode = selected.isNotEmpty(),
            )
        }
    }

    /**
     * Builds a minimal Product from WishlistItem snapshot data.
     * Used for undo-remove (re-toggle) and add-to-cart operations
     * where only the product ID, title, price, and image are needed.
     */
    private fun buildMinimalProduct(item: WishlistItem): Product {
        return Product(
            id = item.productId,
            title = item.productTitle,
            description = "",
            basePrice = item.productPrice,
            totalStockQuantity = item.availableStock,
            status = if (item.isProductDeleted) ProductStatus.ARCHIVED else ProductStatus.ACTIVE,
            condition = ProductCondition.NEW,
            shipping = ProductShipping(shippingType = ShippingType.PAID_SHIPPING),
            sellerId = "",
            sellerName = "",
        )
    }

    fun clearUndoItem() {
        _state.update { it.copy(undoItem = null) }
    }

    fun enterSelectionMode(productId: String) {
        _state.update { current ->
            current.copy(
                isSelectionMode = true,
                selectedItems = setOf(productId),
            )
        }
    }
}
