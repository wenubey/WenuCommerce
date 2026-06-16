package com.wenubey.data

import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.wenubey.data.local.dao.OrderDao
import com.wenubey.data.local.dao.SellerOrderDao
import com.wenubey.data.local.entity.OrderEntity
import com.wenubey.data.local.entity.SellerOrderEntity
import com.wenubey.data.repository.OrderRepositoryImpl
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.repository.DispatcherProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for OrderRepositoryImpl. Uses MockK for DAOs + Firebase clients —
 * no real Firestore. Pattern mirrors PaymentRepositoryImpl-style tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = object : DispatcherProvider {
        override fun io() = testDispatcher
        override fun main() = testDispatcher
        override fun default() = testDispatcher
    }

    private fun newRepo(
        orderDao: OrderDao = mockk(relaxed = true),
        sellerOrderDao: SellerOrderDao = mockk(relaxed = true),
        firestore: FirebaseFirestore = mockk(relaxed = true),
        functions: FirebaseFunctions = mockk(relaxed = true),
    ) = OrderRepositoryImpl(orderDao, sellerOrderDao, firestore, functions, dispatcherProvider)

    @Test
    fun `updateSellerOrderStatus rejects forbidden transition with failure Result`() = runTest(testDispatcher) {
        val sellerOrderDao: SellerOrderDao = mockk(relaxed = true)
        // current = SHIPPED. SHIPPED -> CONFIRMED is forbidden (forward-only).
        coEvery { sellerOrderDao.getById("so-1") } returns SellerOrderEntity(
            id = "so-1",
            parentOrderId = "ord-1",
            sellerId = "s-a",
            status = "SHIPPED"
        )
        val repo = newRepo(sellerOrderDao = sellerOrderDao)

        val result = repo.updateSellerOrderStatus("so-1", OrderStatus.CONFIRMED)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(result.exceptionOrNull()?.message)
            .contains("Forbidden transition: SHIPPED -> CONFIRMED")
    }

    @Test
    fun `updateSellerOrderStatus rejects post-SHIPPED cancel (ORDR-09)`() = runTest(testDispatcher) {
        val sellerOrderDao: SellerOrderDao = mockk(relaxed = true)
        coEvery { sellerOrderDao.getById("so-2") } returns SellerOrderEntity(
            id = "so-2",
            parentOrderId = "ord-1",
            sellerId = "s-a",
            status = "SHIPPED"
        )
        val repo = newRepo(sellerOrderDao = sellerOrderDao)

        val result = repo.updateSellerOrderStatus("so-2", OrderStatus.CANCELLED)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `cancelSellerOrder invokes callable with sellerOrderId map`() = runTest(testDispatcher) {
        val functions: FirebaseFunctions = mockk()
        val callable: HttpsCallableReference = mockk()
        val callResult: HttpsCallableResult = mockk(relaxed = true)
        every { functions.getHttpsCallable("cancelSellerOrder") } returns callable
        coEvery { callable.call(any<Map<String, Any?>>()) } returns Tasks.forResult(callResult)

        val repo = newRepo(functions = functions)
        val result = repo.cancelSellerOrder("so-9")

        assertThat(result.isSuccess).isTrue()
        coVerify { callable.call(mapOf("sellerOrderId" to "so-9")) }
    }

    @Test
    fun `observeCustomerOrders maps OrderDao flow through toDomain`() = runTest(testDispatcher) {
        val orderDao: OrderDao = mockk(relaxed = true)
        every { orderDao.observeOrdersByUser("u-1") } returns flowOf(
            listOf(
                OrderEntity(
                    id = "ord-1",
                    userId = "u-1",
                    status = "PENDING",
                    aggregateStatus = "PENDING"
                )
            )
        )
        val repo = newRepo(orderDao = orderDao)

        val orders = repo.observeCustomerOrders("u-1").first()

        assertThat(orders).hasSize(1)
        assertThat(orders.first().id).isEqualTo("ord-1")
        assertThat(orders.first().userId).isEqualTo("u-1")
    }

    @Test
    fun `observeOrderWithSubOrders combines parent + subs`() = runTest(testDispatcher) {
        val orderDao: OrderDao = mockk(relaxed = true)
        val sellerOrderDao: SellerOrderDao = mockk(relaxed = true)
        val parentFlow = MutableStateFlow<OrderEntity?>(
            OrderEntity(id = "ord-7", userId = "u-1")
        )
        val subsFlow = MutableStateFlow(
            listOf(
                SellerOrderEntity(id = "so-7a", parentOrderId = "ord-7", sellerId = "s-a"),
                SellerOrderEntity(id = "so-7b", parentOrderId = "ord-7", sellerId = "s-b"),
            )
        )
        every { orderDao.observeOrderById("ord-7") } returns parentFlow
        every { sellerOrderDao.observeByParent("ord-7") } returns subsFlow
        val repo = newRepo(orderDao = orderDao, sellerOrderDao = sellerOrderDao)

        val pair = repo.observeOrderWithSubOrders("ord-7").first()

        assertThat(pair).isNotNull()
        assertThat(pair!!.first.id).isEqualTo("ord-7")
        assertThat(pair.second).hasSize(2)
        assertThat(pair.second.map { it.sellerId }).containsExactly("s-a", "s-b").inOrder()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun keepDispatchersImport(d: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO) = Unit
}
