package com.wenubey.wenucommerce.customer.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.OrderRepository
import com.wenubey.wenucommerce.notification.SyncBus
import com.wenubey.wenucommerce.notification.SyncEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Customer-side order history ViewModel.
 *
 * Reads from Room via [OrderRepository.observeCustomerOrders] (single source of
 * truth), triggers one-shot Firestore→Room sync on init + on pull-to-refresh,
 * and reacts to FCM-driven sync triggers by collecting every
 * [SyncEvent.OrderStatusChanged] published on [SyncBus] (CONTEXT D4 trigger c).
 */
class CustomerOrderHistoryViewModel(
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val syncBus: SyncBus,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerOrderHistoryState(isLoading = true))
    val state: StateFlow<CustomerOrderHistoryState> = _state.asStateFlow()

    private val userId: String?
        get() = authRepository.currentUser.value?.uuid

    init {
        observeOrders()
        triggerInitialSync()
        observeSyncBus()
    }

    private fun observeOrders() {
        val uid = userId ?: run {
            _state.update { it.copy(isLoading = false) }
            return
        }
        orderRepository.observeCustomerOrders(uid)
            .catch { error ->
                Timber.e(error, "CustomerOrderHistoryViewModel: observeCustomerOrders failed")
                _state.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
            .onEach { orders ->
                _state.update { it.copy(orders = orders, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun triggerInitialSync() {
        val uid = userId ?: return
        viewModelScope.launch {
            orderRepository.syncCustomerOrders(uid)
                .onFailure { error ->
                    Timber.e(error, "CustomerOrderHistoryViewModel: initial sync failed")
                    _state.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    private fun observeSyncBus() {
        syncBus.events
            .filterIsInstance<SyncEvent.OrderStatusChanged>()
            .onEach {
                val uid = userId ?: return@onEach
                orderRepository.syncCustomerOrders(uid)
                    .onFailure { e -> Timber.e(e, "CustomerOrderHistoryViewModel: syncBus-triggered sync failed") }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: CustomerOrderHistoryAction) {
        when (action) {
            is CustomerOrderHistoryAction.OnFilterSelected -> {
                _state.update { it.copy(filter = action.filter) }
            }
            is CustomerOrderHistoryAction.OnOrderClicked -> {
                // UI navigates via callback — no state change.
            }
            CustomerOrderHistoryAction.OnRefresh -> refresh()
            CustomerOrderHistoryAction.OnRetry -> refresh()
            CustomerOrderHistoryAction.OnDismissError -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun refresh() {
        val uid = userId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
            orderRepository.syncCustomerOrders(uid)
                .onFailure { error ->
                    Timber.e(error, "CustomerOrderHistoryViewModel: refresh failed")
                    _state.update { it.copy(errorMessage = error.message) }
                }
            _state.update { it.copy(isRefreshing = false) }
        }
    }
}
