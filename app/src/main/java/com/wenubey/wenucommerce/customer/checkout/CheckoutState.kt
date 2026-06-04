package com.wenubey.wenucommerce.customer.checkout

import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.discount.DiscountType
import com.wenubey.domain.model.order.ShippingAddress

data class CheckoutState(
    val currentStep: Int = 0, // 0=Address, 1=Review, 2=Payment
    val cartItems: List<CartItem> = emptyList(),
    val savedAddresses: List<ShippingAddress> = emptyList(),
    val selectedAddress: ShippingAddress? = null,
    val subtotal: Double = 0.0,
    val shippingTotal: Double = 0.0,
    val total: Double = 0.0,
    val clientSecret: String = "",
    val orderId: String = "",
    val amountCents: Int = 0,
    val isCreatingPaymentIntent: Boolean = false,
    val isProcessingPayment: Boolean = false,
    val paymentError: String? = null,
    val stockError: String? = null, // server-side stock check failure message
    val isOnline: Boolean = true,
    val isLoading: Boolean = true,
    // Coupon fields
    val couponInput: String = "",
    val appliedCouponCode: String? = null,
    val appliedCouponType: DiscountType? = null,
    val discountAmountCents: Int = 0,
    val couponError: String? = null,
    val isValidatingCoupon: Boolean = false,
) {
    val canProceedToReview: Boolean
        get() = selectedAddress != null

    val canProceedToPayment: Boolean
        get() = clientSecret.isNotEmpty() && !isCreatingPaymentIntent
}
