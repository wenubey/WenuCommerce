package com.wenubey.wenucommerce.customer.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.wenubey.data.connectivity.ConnectivityObserver
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.repository.AddressRepository
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.CartRepository
import com.wenubey.domain.repository.DiscountRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class CheckoutViewModel(
    private val paymentRepository: PaymentRepository,
    private val cartRepository: CartRepository,
    private val addressRepository: AddressRepository,
    private val authRepository: AuthRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val discountRepository: DiscountRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val ioDispatcher = dispatcherProvider.io()

    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    // Emits orderId when checkout completes successfully
    private val _navigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    private val userId: String?
        get() = authRepository.currentUser.value?.uuid

    init {
        observeConnectivity()
        observeCartItems()
        observeSavedAddresses()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { isOnline ->
                _state.update { it.copy(isOnline = isOnline) }
            }
        }
    }

    private fun observeCartItems() {
        val currentUserId = userId ?: run {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            cartRepository.observeCartItems(currentUserId)
                .catch { error ->
                    Timber.e(error, "CheckoutViewModel: failed to observe cart items")
                    _state.update { it.copy(isLoading = false) }
                }
                .collect { items ->
                    val subtotal = items.sumOf { it.snapshotPrice * it.quantity }
                    _state.update { it.copy(cartItems = items, subtotal = subtotal, isLoading = false) }
                }
        }
    }

    private fun observeSavedAddresses() {
        val currentUserId = userId ?: return
        viewModelScope.launch(ioDispatcher) {
            addressRepository.observeSavedAddresses(currentUserId)
                .catch { error ->
                    Timber.e(error, "CheckoutViewModel: failed to observe saved addresses")
                }
                .collect { addresses ->
                    _state.update { current ->
                        val autoSelected = if (current.selectedAddress == null && addresses.isNotEmpty()) {
                            addresses.first()
                        } else {
                            current.selectedAddress
                        }
                        current.copy(savedAddresses = addresses, selectedAddress = autoSelected)
                    }
                }
        }
    }

    fun onAction(action: CheckoutAction) {
        when (action) {
            is CheckoutAction.SelectAddress -> {
                _state.update { it.copy(selectedAddress = action.address) }
            }
            is CheckoutAction.NavigateToAddAddress -> {
                // Handled by UI navigation callback
            }
            is CheckoutAction.GoToStep -> {
                val currentStep = _state.value.currentStep
                val targetStep = action.step
                when {
                    targetStep < currentStep -> {
                        // Free backward navigation
                        _state.update { it.copy(currentStep = targetStep) }
                    }
                    targetStep > currentStep -> {
                        // Forward navigation — validate prerequisites
                        advanceToStep(targetStep)
                    }
                }
            }
            is CheckoutAction.NextStep -> {
                val nextStep = _state.value.currentStep + 1
                advanceToStep(nextStep)
            }
            is CheckoutAction.PreviousStep -> {
                _state.update { it.copy(currentStep = maxOf(0, it.currentStep - 1)) }
            }
            is CheckoutAction.CreatePaymentIntent -> {
                createPaymentIntent()
            }
            is CheckoutAction.RetryPayment -> {
                _state.update { it.copy(paymentError = null) }
                createPaymentIntent()
            }
            is CheckoutAction.DismissPaymentError -> {
                _state.update { it.copy(paymentError = null) }
            }
            is CheckoutAction.DismissStockError -> {
                _state.update { it.copy(stockError = null) }
            }
            is CheckoutAction.UpdateCouponInput -> {
                _state.update { it.copy(couponInput = action.code, couponError = null) }
            }
            is CheckoutAction.ApplyCoupon -> {
                applyCoupon()
            }
            is CheckoutAction.RemoveCoupon -> {
                removeCoupon()
            }
            is CheckoutAction.DismissCouponError -> {
                _state.update { it.copy(couponError = null) }
            }
        }
    }

    private fun advanceToStep(targetStep: Int) {
        val current = _state.value
        when {
            targetStep == 1 && current.selectedAddress == null -> {
                // Cannot proceed to review without address
                Timber.d("CheckoutViewModel: cannot advance to step 1 — no address selected")
            }
            targetStep == 2 -> {
                // Advance to payment: create PaymentIntent
                _state.update { it.copy(currentStep = 2) }
                createPaymentIntent()
            }
            else -> {
                _state.update { it.copy(currentStep = targetStep) }
            }
        }
    }

    private fun applyCoupon() {
        val currentState = _state.value
        val input = currentState.couponInput.trim()
        if (input.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isValidatingCoupon = true, couponError = null) }
            val subtotalCents = (currentState.subtotal * 100).toInt()
            discountRepository.validateCoupon(input, currentState.cartItems, subtotalCents)
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            appliedCouponCode = result.code,
                            appliedCouponType = result.type,
                            discountAmountCents = result.discountAmountCents,
                            isValidatingCoupon = false,
                            couponInput = "",
                            couponError = null,
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "CheckoutViewModel: applyCoupon failed")
                    _state.update {
                        it.copy(
                            couponError = error.message ?: "Invalid coupon code",
                            isValidatingCoupon = false,
                        )
                    }
                }
        }
    }

    private fun removeCoupon() {
        // CRITICAL (Pitfall 4): Invalidate clientSecret — it was created with the discounted amount
        _state.update {
            it.copy(
                appliedCouponCode = null,
                appliedCouponType = null,
                discountAmountCents = 0,
                couponError = null,
                clientSecret = "",
                orderId = "",
                amountCents = 0,
            )
        }
    }

    private fun createPaymentIntent() {
        val currentUserId = userId ?: return
        val currentState = _state.value
        val address = currentState.selectedAddress ?: return
        val cartItems = currentState.cartItems

        if (cartItems.isEmpty()) {
            Timber.d("CheckoutViewModel: no cart items for payment intent")
            return
        }

        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isCreatingPaymentIntent = true, paymentError = null, stockError = null) }
            paymentRepository.createPaymentIntent(
                userId = currentUserId,
                cartItems = cartItems,
                shippingAddress = address,
                couponCode = currentState.appliedCouponCode,
            )
                .onSuccess { result ->
                    _state.update { it ->
                        it.copy(
                            clientSecret = result.clientSecret,
                            amountCents = result.amountCents,
                            orderId = result.orderId,
                            total = result.amountCents / 100.0,
                            discountAmountCents = result.discountAmountCents,
                            isCreatingPaymentIntent = false,
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "CheckoutViewModel: createPaymentIntent failed")
                    val errorMessage = error.message ?: "Failed to initialize payment"
                    // If it's a stock error, show it on review step and navigate back
                    if (errorMessage.contains("stock", ignoreCase = true) ||
                        errorMessage.contains("unavailable", ignoreCase = true) ||
                        errorMessage.contains("inventory", ignoreCase = true)
                    ) {
                        _state.update { it.copy(
                            stockError = errorMessage,
                            isCreatingPaymentIntent = false,
                            currentStep = 1,
                        )}
                    } else {
                        _state.update { it.copy(
                            paymentError = errorMessage,
                            isCreatingPaymentIntent = false,
                        )}
                    }
                }
        }
    }

    fun onPaymentResult(result: PaymentSheetResult) {
        when (result) {
            is PaymentSheetResult.Completed -> {
                handlePaymentSuccess()
            }
            is PaymentSheetResult.Canceled -> {
                // User canceled — stay on step 2, no state change needed
                Timber.d("CheckoutViewModel: payment canceled by user")
            }
            is PaymentSheetResult.Failed -> {
                val errorMessage = result.error.message ?: "Payment failed"
                Timber.e(result.error, "CheckoutViewModel: payment failed")
                _state.update { it.copy(paymentError = errorMessage, isProcessingPayment = false) }
            }
        }
    }

    private fun handlePaymentSuccess() {
        val currentState = _state.value
        val currentUserId = userId ?: return
        val orderId = currentState.orderId

        _state.update { it.copy(isProcessingPayment = true) }

        viewModelScope.launch(ioDispatcher) {
            runCatching {
                // Build the confirmed order for Room persistence
                val order = Order(
                    id = orderId,
                    userId = currentUserId,
                    status = OrderStatus.CONFIRMED,
                    subtotal = currentState.subtotal,
                    shippingTotal = currentState.shippingTotal,
                    totalAmount = currentState.total,
                    shippingAddress = currentState.selectedAddress ?: return@runCatching,
                    items = currentState.cartItems.map { cartItem ->
                            OrderItem(
                                productId = cartItem.productId,
                                productTitle = cartItem.productTitle,
                                quantity = cartItem.quantity,
                                snapshotPrice = cartItem.snapshotPrice,
                                lineTotal = cartItem.snapshotPrice * cartItem.quantity,
                            )
                        },
                    discountAmount = currentState.discountAmountCents / 100.0,
                    discountCode = currentState.appliedCouponCode ?: "",
                    createdAt = System.currentTimeMillis().toString(),
                    updatedAt = System.currentTimeMillis().toString(),
                )

                // Persist the confirmed order to Room
                paymentRepository.createOrderInRoom(order)

                // Update Firestore order status from PENDING to CONFIRMED
                paymentRepository.updateOrderStatus(orderId, OrderStatus.CONFIRMED)
                    .onFailure { error ->
                        Timber.e(error, "CheckoutViewModel: failed to update order status in Firestore")
                    }

                // Decrement coupon usage count after successful payment
                val couponCode = currentState.appliedCouponCode
                if (couponCode != null) {
                    discountRepository.decrementCouponUsage(couponCode)
                        .onFailure { error ->
                            Timber.e(error, "CheckoutViewModel: failed to decrement coupon usage for $couponCode")
                        }
                }

                // Clear the cart
                cartRepository.clearCart(currentUserId)

                // Signal navigation to order confirmation screen
                _navigationEvent.emit(orderId)
            }.onFailure { error ->
                Timber.e(error, "CheckoutViewModel: error handling payment success")
                _state.update { it.copy(isProcessingPayment = false) }
            }
        }
    }
}
