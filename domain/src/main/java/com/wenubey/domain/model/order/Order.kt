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
    val updatedAt: String = "",
    // ── Phase 6 additions (defaults for back-compat with Phase 4 docs) ──
    /**
     * IDs of the N child sellerOrders fanned out at payment time. Empty for
     * legacy Phase 4 single-seller orders (Q1 resolution: Option B).
     */
    val sellerOrderIds: List<String> = emptyList(),
    /**
     * Server-computed roll-up across children. Written by `onOrderStatusChange`
     * trigger inside a Firestore transaction (RESEARCH §2.8, W5).
     */
    val aggregateStatus: AggregateOrderStatus = AggregateOrderStatus.PENDING
) {
    companion object {
        fun default() = Order()
    }
}
