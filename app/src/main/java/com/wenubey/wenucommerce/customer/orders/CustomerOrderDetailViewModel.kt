package com.wenubey.wenucommerce.customer.orders

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Customer-side order detail ViewModel.
 *
 * Reads parent + sub-orders from Room via [OrderRepository.observeOrderWithSubOrders].
 * Single sub-order orders auto-expand; multi-seller orders start collapsed
 * (CONTEXT D4).
 *
 * Collects [SyncEvent.OrderStatusChanged] from [SyncBus], filtering by the
 * matching orderId so events for unrelated orders don't trigger redundant
 * syncs.
 */
class CustomerOrderDetailViewModel(
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val syncBus: SyncBus,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"]) {
        "CustomerOrderDetailViewModel requires an orderId in SavedStateHandle"
    }

    private val _state = MutableStateFlow(CustomerOrderDetailState(isLoading = true))
    val state: StateFlow<CustomerOrderDetailState> = _state.asStateFlow()

    private val userId: String?
        get() = authRepository.currentUser.value?.uuid

    init {
        observeOrder()
        triggerInitialSync()
        observeSyncBus()
    }

    private fun observeOrder() {
        orderRepository.observeOrderWithSubOrders(orderId)
            .catch { error ->
                Timber.e(error, "CustomerOrderDetailViewModel: observeOrderWithSubOrders failed")
                _state.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
            .onEach { pair ->
                _state.update { current ->
                    val subs = pair?.second.orEmpty()
                    val expanded = if (subs.size == 1) setOf(subs.first().id)
                        else current.expandedSellerIds
                    current.copy(
                        order = pair?.first,
                        sellerOrders = subs,
                        expandedSellerIds = expanded,
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun triggerInitialSync() {
        val uid = userId ?: return
        viewModelScope.launch {
            orderRepository.syncCustomerOrders(uid).onFailure { error ->
                Timber.e(error, "CustomerOrderDetailViewModel: initial sync failed")
            }
        }
    }

    private fun observeSyncBus() {
        syncBus.events
            .filterIsInstance<SyncEvent.OrderStatusChanged>()
            .filter { it.orderId == orderId }
            .onEach {
                val uid = userId ?: return@onEach
                orderRepository.syncCustomerOrders(uid).onFailure { error ->
                    Timber.e(error, "CustomerOrderDetailViewModel: syncBus-triggered sync failed")
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: CustomerOrderDetailAction) {
        when (action) {
            is CustomerOrderDetailAction.OnToggleSection -> {
                _state.update { current ->
                    val next = current.expandedSellerIds.toMutableSet().apply {
                        if (!add(action.sellerOrderId)) remove(action.sellerOrderId)
                    }
                    current.copy(expandedSellerIds = next)
                }
            }
            CustomerOrderDetailAction.OnRetry -> triggerInitialSync()
            CustomerOrderDetailAction.OnDismissError -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }
}
