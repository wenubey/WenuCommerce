package com.wenubey.wenucommerce.customer.customer_cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.CartRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class CartViewModel(
    private val cartRepository: CartRepository,
    private val authRepository: AuthRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    private val ioDispatcher = dispatcherProvider.io()

    init {
        val userId = authRepository.currentUser.value?.uuid
        if (userId == null) {
            _state.update { it.copy(isLoading = false, error = "Please log in to view your cart") }
        } else {
            observeCartItems(userId)
        }
    }

    private fun observeCartItems(userId: String) {
        viewModelScope.launch(ioDispatcher) {
            cartRepository.observeCartItems(userId)
                .catch { error ->
                    Timber.e(error, "CartViewModel: failed to observe cart items")
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
                .collect { items ->
                    _state.update { it.copy(cartItems = items, isLoading = false, error = null) }
                }
        }
    }

    fun onAction(action: CartAction) {
        val userId = authRepository.currentUser.value?.uuid ?: return
        when (action) {
            is CartAction.IncrementQuantity -> incrementQuantity(userId, action.productId)
            is CartAction.DecrementQuantity -> decrementQuantity(userId, action.productId)
            is CartAction.RemoveItem -> removeItem(userId, action.productId)
            is CartAction.UndoRemove -> undoRemove(userId, action.item)
            is CartAction.ToggleSelection -> toggleSelection(action.productId)
            is CartAction.DeleteSelected -> deleteSelected(userId)
            is CartAction.ToggleSelectionMode -> toggleSelectionMode()
            is CartAction.ClearSelection -> _state.update { it.copy(selectedItems = emptySet()) }
            is CartAction.NavigateToProduct -> { /* handled by UI */ }
            is CartAction.NavigateToHome -> { /* handled by UI */ }
            is CartAction.Checkout -> { /* handled by UI */ }
        }
    }

    private fun incrementQuantity(userId: String, productId: String) {
        val item = _state.value.cartItems.find { it.productId == productId } ?: return
        if (item.quantity >= item.availableStock) {
            Timber.d("CartViewModel: at stock limit for $productId (stock=${item.availableStock})")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                cartRepository.updateQuantity(userId, productId, item.quantity + 1)
            }.onFailure { error ->
                Timber.e(error, "CartViewModel: failed to increment quantity for $productId")
            }
        }
    }

    private fun decrementQuantity(userId: String, productId: String) {
        val item = _state.value.cartItems.find { it.productId == productId } ?: return
        if (item.quantity == 1) {
            // Decrement to 0 removes the item — store for undo
            _state.update { it.copy(undoItem = item) }
            viewModelScope.launch(ioDispatcher) {
                runCatching {
                    cartRepository.removeFromCart(userId, productId)
                }.onFailure { error ->
                    Timber.e(error, "CartViewModel: failed to remove item $productId on decrement-to-0")
                    _state.update { it.copy(undoItem = null) }
                }
            }
        } else {
            viewModelScope.launch(ioDispatcher) {
                runCatching {
                    cartRepository.updateQuantity(userId, productId, item.quantity - 1)
                }.onFailure { error ->
                    Timber.e(error, "CartViewModel: failed to decrement quantity for $productId")
                }
            }
        }
    }

    private fun removeItem(userId: String, productId: String) {
        val item = _state.value.cartItems.find { it.productId == productId } ?: return
        _state.update { it.copy(undoItem = item) }
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                cartRepository.removeFromCart(userId, productId)
            }.onFailure { error ->
                Timber.e(error, "CartViewModel: failed to remove item $productId")
                _state.update { it.copy(undoItem = null) }
            }
        }
    }

    private fun undoRemove(userId: String, item: CartItem) {
        _state.update { it.copy(undoItem = null) }
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                cartRepository.restoreCartItem(userId, item)
            }.onFailure { error ->
                Timber.e(error, "CartViewModel: failed to undo remove for ${item.productId}")
            }
        }
    }

    private fun toggleSelection(productId: String) {
        _state.update { current ->
            val selected = current.selectedItems.toMutableSet()
            if (productId in selected) selected.remove(productId) else selected.add(productId)
            current.copy(selectedItems = selected)
        }
    }

    private fun deleteSelected(userId: String) {
        val toDelete = _state.value.selectedItems.toList()
        _state.update { it.copy(selectedItems = emptySet(), isSelectionMode = false) }
        viewModelScope.launch(ioDispatcher) {
            toDelete.forEach { productId ->
                runCatching {
                    cartRepository.removeFromCart(userId, productId)
                }.onFailure { error ->
                    Timber.e(error, "CartViewModel: failed to delete selected item $productId")
                }
            }
        }
    }

    private fun toggleSelectionMode() {
        _state.update { it.copy(isSelectionMode = !it.isSelectionMode, selectedItems = emptySet()) }
    }

    fun clearUndoItem() {
        _state.update { it.copy(undoItem = null) }
    }
}
