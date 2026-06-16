package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory test double for [OrderRepository]. Supports observing customer orders,
 * a customer order with its sub-orders, seller-side sub-orders, and individual
 * sub-orders. Mutation methods record call args; behaviour is overridable via
 * `*Result` fields per the established Fake* convention (see [FakeAuthRepository]).
 */
class FakeOrderRepository : OrderRepository {

    val customerOrders = MutableStateFlow<List<Order>>(emptyList())
    val sellerOrders = MutableStateFlow<List<SellerOrder>>(emptyList())

    /** Observation count — read in tests to assert init wired observation. */
    var observeSellerOrdersCalls: Int = 0
    var observeSellerOrderByIdCalls: Int = 0
    var observeCustomerOrdersCalls: Int = 0
    var observeOrderWithSubOrdersCalls: Int = 0

    val syncCustomerOrdersCalls = mutableListOf<String>()
    val syncSellerOrdersCalls = mutableListOf<String>()
    val updateSellerOrderStatusCalls =
        mutableListOf<UpdateStatusCall>()
    val cancelSellerOrderCalls = mutableListOf<String>()

    data class UpdateStatusCall(
        val id: String,
        val next: OrderStatus,
        val trackingNumber: String?,
        val note: String?,
    )

    var syncCustomerOrdersResult: Result<Unit> = Result.success(Unit)
    var syncSellerOrdersResult: Result<Unit> = Result.success(Unit)
    var updateSellerOrderStatusResult: Result<Unit> = Result.success(Unit)
    var cancelSellerOrderResult: Result<Unit> = Result.success(Unit)

    fun emitSellerOrders(orders: List<SellerOrder>) {
        sellerOrders.value = orders
    }

    fun emitCustomerOrders(orders: List<Order>) {
        customerOrders.value = orders
    }

    override fun observeCustomerOrders(userId: String): Flow<List<Order>> {
        observeCustomerOrdersCalls++
        return customerOrders
    }

    override fun observeOrderWithSubOrders(
        orderId: String
    ): Flow<Pair<Order, List<SellerOrder>>?> {
        observeOrderWithSubOrdersCalls++
        return customerOrders.map { list ->
            val parent = list.firstOrNull { it.id == orderId } ?: return@map null
            parent to sellerOrders.value.filter { it.parentOrderId == orderId }
        }
    }

    override fun observeSellerOrders(sellerId: String): Flow<List<SellerOrder>> {
        observeSellerOrdersCalls++
        return sellerOrders.map { list -> list.filter { it.sellerId == sellerId || sellerId.isEmpty() } }
    }

    override fun observeSellerOrderById(id: String): Flow<SellerOrder?> {
        observeSellerOrderByIdCalls++
        return sellerOrders.map { list -> list.firstOrNull { it.id == id } }
    }

    override suspend fun updateSellerOrderStatus(
        id: String,
        next: OrderStatus,
        trackingNumber: String?,
        note: String?,
    ): Result<Unit> {
        updateSellerOrderStatusCalls.add(UpdateStatusCall(id, next, trackingNumber, note))
        return updateSellerOrderStatusResult
    }

    override suspend fun cancelSellerOrder(id: String): Result<Unit> {
        cancelSellerOrderCalls.add(id)
        return cancelSellerOrderResult
    }

    override suspend fun syncCustomerOrders(userId: String): Result<Unit> {
        syncCustomerOrdersCalls.add(userId)
        return syncCustomerOrdersResult
    }

    override suspend fun syncSellerOrders(sellerId: String): Result<Unit> {
        syncSellerOrdersCalls.add(sellerId)
        return syncSellerOrdersResult
    }
}
