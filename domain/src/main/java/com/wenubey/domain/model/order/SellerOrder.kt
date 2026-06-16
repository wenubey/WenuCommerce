package com.wenubey.domain.model.order

import kotlinx.serialization.Serializable

/**
 * Child sub-order under a parent Order. One SellerOrder per seller per cart.
 * Created by `createPaymentIntent` fan-out (RESEARCH §2.6); status transitions
 * authorised by Firestore rules; cancellations routed through `cancelSellerOrder`
 * callable (atomic Stripe refund + status flip).
 */
@Serializable
data class SellerOrder(
    val id: String = "",
    val parentOrderId: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerLogoUrl: String = "",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val shippingShare: Double = 0.0,
    val discountShare: Double = 0.0,
    val status: OrderStatus = OrderStatus.PENDING,
    val statusHistory: List<StatusEntry> = emptyList(),
    val trackingNumber: String? = null,
    val refundId: String? = null,
    val refundedAmount: Double? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    companion object {
        fun default() = SellerOrder()
    }
}
