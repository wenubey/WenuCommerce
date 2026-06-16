package com.wenubey.wenucommerce

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.customer.orders.CustomerOrderDetailAction
import com.wenubey.wenucommerce.customer.orders.CustomerOrderDetailViewModel
import com.wenubey.wenucommerce.notification.SyncBus
import com.wenubey.wenucommerce.notification.SyncEvent
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeOrderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerOrderDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userId = "u-1"
    private val orderId = "order-42"

    private fun newViewModel(
        order: FakeOrderRepository = FakeOrderRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = User(uuid = userId)),
        bus: SyncBus = SyncBus(),
        oid: String = orderId,
    ): Triple<CustomerOrderDetailViewModel, FakeOrderRepository, SyncBus> {
        val handle = SavedStateHandle(mapOf("orderId" to oid))
        val vm = CustomerOrderDetailViewModel(order, auth, bus, handle)
        return Triple(vm, order, bus)
    }

    private fun parent(id: String = orderId) = Order(
        id = id,
        userId = userId,
        createdAt = "2026-06-15T10:00:00Z",
    )

    private fun sub(id: String, parent: String = orderId, status: OrderStatus = OrderStatus.PENDING) =
        SellerOrder(
            id = id,
            parentOrderId = parent,
            sellerId = "seller-$id",
            sellerName = "Seller $id",
            status = status,
        )

    @Test
    fun `observeOrderWithSubOrders flows into state`() = runTest {
        val (vm, order, _) = newViewModel()
        order.emitCustomerOrders(listOf(parent()))
        order.emitSellerOrders(listOf(sub("s1"), sub("s2")))
        advanceUntilIdle()

        assertThat(vm.state.value.order?.id).isEqualTo(orderId)
        assertThat(vm.state.value.sellerOrders).hasSize(2)
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `single sub-order auto-expands`() = runTest {
        val (vm, order, _) = newViewModel()
        order.emitCustomerOrders(listOf(parent()))
        order.emitSellerOrders(listOf(sub("s1")))
        advanceUntilIdle()

        assertThat(vm.state.value.expandedSellerIds).containsExactly("s1")
    }

    @Test
    fun `multi sub-order starts collapsed`() = runTest {
        val (vm, order, _) = newViewModel()
        order.emitCustomerOrders(listOf(parent()))
        order.emitSellerOrders(listOf(sub("s1"), sub("s2")))
        advanceUntilIdle()

        assertThat(vm.state.value.expandedSellerIds).isEmpty()
    }

    @Test
    fun `OnToggleSection flips expanded set`() = runTest {
        val (vm, order, _) = newViewModel()
        order.emitCustomerOrders(listOf(parent()))
        order.emitSellerOrders(listOf(sub("s1"), sub("s2")))
        advanceUntilIdle()

        vm.onAction(CustomerOrderDetailAction.OnToggleSection("s1"))
        advanceUntilIdle()
        assertThat(vm.state.value.expandedSellerIds).containsExactly("s1")

        vm.onAction(CustomerOrderDetailAction.OnToggleSection("s1"))
        advanceUntilIdle()
        assertThat(vm.state.value.expandedSellerIds).isEmpty()
    }

    @Test
    fun `SyncBus event with MATCHING orderId triggers syncCustomerOrders`() = runTest {
        val bus = SyncBus()
        val (_, order, _) = newViewModel(bus = bus)
        advanceUntilIdle()
        val baseline = order.syncCustomerOrdersCalls.size

        bus.emit(SyncEvent.OrderStatusChanged(orderId = orderId, sellerOrderId = "sub-x"))
        advanceUntilIdle()

        assertThat(order.syncCustomerOrdersCalls.size).isAtLeast(baseline + 1)
    }

    @Test
    fun `SyncBus event with NON-MATCHING orderId does NOT trigger sync`() = runTest {
        val bus = SyncBus()
        val (_, order, _) = newViewModel(bus = bus)
        advanceUntilIdle()
        val baseline = order.syncCustomerOrdersCalls.size

        bus.emit(SyncEvent.OrderStatusChanged(orderId = "different-order", sellerOrderId = "sub-y"))
        advanceUntilIdle()

        assertThat(order.syncCustomerOrdersCalls.size).isEqualTo(baseline)
    }
}
