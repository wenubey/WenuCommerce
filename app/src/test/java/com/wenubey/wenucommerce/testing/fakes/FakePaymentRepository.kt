package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.ShippingAddress
import com.wenubey.domain.repository.PaymentIntentResult
import com.wenubey.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePaymentRepository : PaymentRepository {

    data class PaymentIntentCall(
        val userId: String,
        val cartItems: List<CartItem>,
        val shippingAddress: ShippingAddress,
        val couponCode: String?,
    )

    val createPaymentIntentCalls = mutableListOf<PaymentIntentCall>()
    val createOrderInRoomCalls = mutableListOf<Order>()
    val updateOrderStatusCalls = mutableListOf<Pair<String, OrderStatus>>()

    var createPaymentIntentResult: Result<PaymentIntentResult> = Result.success(
        PaymentIntentResult(
            clientSecret = "pi_secret_123",
            amountCents = 5_000,
            orderId = "order-1",
            discountAmountCents = 0,
        )
    )
    var updateOrderStatusResult: Result<Unit> = Result.success(Unit)
    var createOrderInRoomThrows: Throwable? = null

    private val ordersById = MutableStateFlow<Map<String, Order>>(emptyMap())

    override suspend fun createPaymentIntent(
        userId: String,
        cartItems: List<CartItem>,
        shippingAddress: ShippingAddress,
        couponCode: String?,
    ): Result<PaymentIntentResult> {
        createPaymentIntentCalls.add(PaymentIntentCall(userId, cartItems, shippingAddress, couponCode))
        return createPaymentIntentResult
    }

    override suspend fun createOrderInRoom(order: Order) {
        createOrderInRoomCalls.add(order)
        createOrderInRoomThrows?.let { throw it }
        ordersById.value = ordersById.value + (order.id to order)
    }

    override suspend fun getOrderById(orderId: String): Order? = ordersById.value[orderId]

    override fun observeOrderById(orderId: String): Flow<Order?> =
        ordersById.map { it[orderId] }

    override suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Result<Unit> {
        updateOrderStatusCalls.add(orderId to status)
        return updateOrderStatusResult
    }
}
