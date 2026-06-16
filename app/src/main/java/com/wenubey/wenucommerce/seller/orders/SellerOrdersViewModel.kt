package com.wenubey.wenucommerce.seller.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Seller-side list of sub-orders that belong to the current authenticated seller.
 * Reads from Room via [OrderRepository.observeSellerOrders] (single source of truth);
 * triggers a one-shot Firestore sync at init via [OrderRepository.syncSellerOrders].
 */
class SellerOrdersViewModel(
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SellerOrdersState())
    val state: StateFlow<SellerOrdersState> = _state.asStateFlow()

    private val sellerId: String
        get() = authRepository.currentUser.value?.uuid.orEmpty()

    init {
        observeSellerOrders()
        triggerSync()
    }

    private fun observeSellerOrders() {
        orderRepository.observeSellerOrders(sellerId)
            .catch { e ->
                Timber.e(e, "SellerOrdersViewModel: observeSellerOrders failed")
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
            .onEach { orders ->
                _state.update { it.copy(sellerOrders = orders, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun triggerSync() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            orderRepository.syncSellerOrders(sellerId)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    Timber.w(e, "SellerOrdersViewModel: syncSellerOrders failed")
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
    }

    fun onAction(action: SellerOrdersAction) {
        when (action) {
            is SellerOrdersAction.OnFilterSelected ->
                _state.update { it.copy(filter = action.filter) }
            is SellerOrdersAction.OnOrderClicked -> {
                // Navigation handled by UI callback; no-op here.
            }
            SellerOrdersAction.OnRefresh -> triggerSync()
            SellerOrdersAction.OnRetry -> {
                _state.update { it.copy(errorMessage = null) }
                triggerSync()
            }
            SellerOrdersAction.OnDismissError ->
                _state.update { it.copy(errorMessage = null) }
        }
    }
}
