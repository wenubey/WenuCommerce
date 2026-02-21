package com.wenubey.domain.repository

import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.ShippingAddress
import kotlinx.coroutines.flow.Flow

data class PaymentIntentResult(
    val clientSecret: String,
    val amountCents: Int,
    val orderId: String
)

interface PaymentRepository {

    suspend fun createPaymentIntent(
        userId: String,
        cartItems: List<CartItem>,
        shippingAddress: ShippingAddress,
    ): Result<PaymentIntentResult>

    suspend fun createOrderInRoom(order: Order)

    suspend fun getOrderById(orderId: String): Order?

    fun observeOrderById(orderId: String): Flow<Order?>

    suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Result<Unit>
}
