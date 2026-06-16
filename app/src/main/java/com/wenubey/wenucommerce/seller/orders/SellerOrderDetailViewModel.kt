package com.wenubey.wenucommerce.seller.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.repository.OrderRepository
import com.wenubey.wenucommerce.navigation.SellerOrderDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Seller-side detail VM for a single sub-order. Reads via [OrderRepository.observeSellerOrderById]
 * (Room flow); writes status forward via [OrderRepository.updateSellerOrderStatus]; cancellations
 * routed through [OrderRepository.cancelSellerOrder] (callable + Stripe partial refund).
 *
 * SavedStateHandle carries the navigation arg [SellerOrderDetail.sellerOrderId]. The forward-only
 * UI gate is computed at render time via [OrderStatus.allowedNext]; the Firestore rule (06-01) is
 * the security authority.
 */
class SellerOrderDetailViewModel(
    private val orderRepository: OrderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Read the sellerOrderId arg. Prefer the explicit `"sellerOrderId"` key
     * (used by tests + back-compat fallback). When absent, fall back to the
     * type-safe Navigation route decoding via [toRoute]; if that yields a
     * blank id, treat as missing (defensive — production injection always
     * provides a non-blank id via the [SellerOrderDetail] route).
     */
    private val sellerOrderId: String = run {
        val direct = savedStateHandle.get<String>("sellerOrderId")
        if (!direct.isNullOrBlank()) direct
        else runCatching { savedStateHandle.toRoute<SellerOrderDetail>().sellerOrderId }
            .getOrNull()
            .orEmpty()
    }

    private val _state = MutableStateFlow(SellerOrderDetailState())
    val state: StateFlow<SellerOrderDetailState> = _state.asStateFlow()

    init {
        observeSellerOrder()
    }

    private fun observeSellerOrder() {
        orderRepository.observeSellerOrderById(sellerOrderId)
            .catch { e ->
                Timber.e(e, "SellerOrderDetailViewModel: observe failed")
                _state.update { it.copy(errorMessage = e.message) }
            }
            .onEach { order ->
                _state.update { it.copy(sellerOrder = order) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SellerOrderDetailAction) {
        when (action) {
            is SellerOrderDetailAction.OnAdvanceClicked -> handleAdvance(action.next)
            SellerOrderDetailAction.OnCancelClicked ->
                _state.update { it.copy(showCancelDialog = true) }
            is SellerOrderDetailAction.OnConfirmShipped -> handleConfirmShipped(action.trackingNumber)
            SellerOrderDetailAction.OnConfirmCancel -> handleConfirmCancel()
            SellerOrderDetailAction.OnDismissShippedDialog ->
                _state.update { it.copy(showShippedDialog = false) }
            SellerOrderDetailAction.OnDismissCancelDialog ->
                _state.update { it.copy(showCancelDialog = false) }
            SellerOrderDetailAction.OnConsumeToast ->
                _state.update { it.copy(toastMessage = null) }
            SellerOrderDetailAction.OnDismissError ->
                _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun handleAdvance(next: OrderStatus) {
        // Defer to user-supplied tracking via dialog when moving to SHIPPED.
        if (next == OrderStatus.SHIPPED) {
            _state.update { it.copy(showShippedDialog = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true) }
            orderRepository.updateSellerOrderStatus(sellerOrderId, next, null, null)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isMutating = false,
                            toastMessage = "Status updated to ${next.displayName}",
                        )
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "updateSellerOrderStatus failed")
                    _state.update {
                        it.copy(isMutating = false, errorMessage = e.localizedMessage ?: e.message)
                    }
                }
        }
    }

    private fun handleConfirmShipped(trackingNumber: String?) {
        _state.update { it.copy(showShippedDialog = false) }
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true) }
            val track = trackingNumber?.takeIf { it.isNotBlank() }
            orderRepository.updateSellerOrderStatus(sellerOrderId, OrderStatus.SHIPPED, track, null)
                .onSuccess {
                    _state.update {
                        it.copy(isMutating = false, toastMessage = "Marked as shipped")
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "mark shipped failed")
                    _state.update {
                        it.copy(isMutating = false, errorMessage = e.localizedMessage ?: e.message)
                    }
                }
        }
    }

    private fun handleConfirmCancel() {
        _state.update { it.copy(showCancelDialog = false) }
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true) }
            orderRepository.cancelSellerOrder(sellerOrderId)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isMutating = false,
                            toastMessage = "Order cancelled — refund issued",
                        )
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "cancelSellerOrder failed")
                    _state.update {
                        it.copy(isMutating = false, errorMessage = e.localizedMessage ?: e.message)
                    }
                }
        }
    }
}
