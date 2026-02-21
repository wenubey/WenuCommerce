package com.wenubey.wenucommerce.customer.checkout

import com.wenubey.domain.model.order.ShippingAddress

sealed interface CheckoutAction {
    data class SelectAddress(val address: ShippingAddress) : CheckoutAction
    data object NavigateToAddAddress : CheckoutAction
    data class GoToStep(val step: Int) : CheckoutAction
    data object NextStep : CheckoutAction
    data object PreviousStep : CheckoutAction
    data object CreatePaymentIntent : CheckoutAction
    data object RetryPayment : CheckoutAction
    data object DismissPaymentError : CheckoutAction
    data object DismissStockError : CheckoutAction
}
