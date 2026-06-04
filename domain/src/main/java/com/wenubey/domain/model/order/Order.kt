package com.wenubey.domain.model.order

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String = "",
    val userId: String = "",
    val status: OrderStatus = OrderStatus.PENDING,
    val subtotal: Double = 0.0,
    val shippingTotal: Double = 0.0,
    val totalAmount: Double = 0.0,
    val currency: String = "USD",
    val stripePaymentIntentId: String = "",
    val shippingAddress: ShippingAddress = ShippingAddress.default(),
    val items: List<OrderItem> = emptyList(),
    val discountAmount: Double = 0.0,
    val discountCode: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    companion object {
        fun default() = Order()
    }
}
