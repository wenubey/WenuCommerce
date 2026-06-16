package com.wenubey.wenucommerce

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.order.AggregateOrderStatus
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.customer.orders.CustomerOrderHistoryAction
import com.wenubey.wenucommerce.customer.orders.CustomerOrderHistoryViewModel
import com.wenubey.wenucommerce.customer.orders.OrderFilter
import com.wenubey.wenucommerce.customer.orders.visibleOrders
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
class CustomerOrderHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userId = "u-1"

    private fun newViewModel(
        order: FakeOrderRepository = FakeOrderRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = User(uuid = userId)),
        bus: SyncBus = SyncBus(),
    ): Triple<CustomerOrderHistoryViewModel, FakeOrderRepository, SyncBus> {
        val vm = CustomerOrderHistoryViewModel(order, auth, bus)
        return Triple(vm, order, bus)
    }

    private fun fakeOrder(
        id: String,
        agg: AggregateOrderStatus,
        createdAt: String = "2026-06-15T10:00:00Z",
    ) = Order(
        id = id,
        userId = userId,
        aggregateStatus = agg,
        createdAt = createdAt,
    )

    @Test
    fun `init triggers observeCustomerOrders and syncCustomerOrders`() = runTest {
        val (_, order, _) = newViewModel()
        advanceUntilIdle()

        assertThat(order.observeCustomerOrdersCalls).isEqualTo(1)
        assertThat(order.syncCustomerOrdersCalls).containsExactly(userId)
    }

    @Test
    fun `OnFilterSelected updates filter state`() = runTest {
        val (vm, _, _) = newViewModel()
        advanceUntilIdle()

        vm.onAction(CustomerOrderHistoryAction.OnFilterSelected(OrderFilter.DELIVERED))
        advanceUntilIdle()

        assertThat(vm.state.value.filter).isEqualTo(OrderFilter.DELIVERED)
    }

    @Test
    fun `repository emissions flow into state orders`() = runTest {
        val (vm, order, _) = newViewModel()
        advanceUntilIdle()
        order.emitCustomerOrders(listOf(
            fakeOrder("a", AggregateOrderStatus.PENDING),
            fakeOrder("b", AggregateOrderStatus.DELIVERED),
        ))
        advanceUntilIdle()

        assertThat(vm.state.value.orders).hasSize(2)
    }

    @Test
    fun `Cancelled filter hides DELIVERED rows but includes PARTIALLY_CANCELLED`() = runTest {
        val (vm, order, _) = newViewModel()
        advanceUntilIdle()
        order.emitCustomerOrders(listOf(
            fakeOrder("a", AggregateOrderStatus.DELIVERED, createdAt = "2026-06-15T10:00:00Z"),
            fakeOrder("b", AggregateOrderStatus.CANCELLED, createdAt = "2026-06-15T11:00:00Z"),
            fakeOrder("c", AggregateOrderStatus.PARTIALLY_CANCELLED, createdAt = "2026-06-15T12:00:00Z"),
        ))
        advanceUntilIdle()
        vm.onAction(CustomerOrderHistoryAction.OnFilterSelected(OrderFilter.CANCELLED))
        advanceUntilIdle()

        val visible = vm.state.value.visibleOrders()
        assertThat(visible.map { it.id }).containsExactly("c", "b").inOrder()
    }

    @Test
    fun `OnRefresh re-calls syncCustomerOrders`() = runTest {
        val (vm, order, _) = newViewModel()
        advanceUntilIdle()
        // baseline: init triggered one sync
        val baseline = order.syncCustomerOrdersCalls.size

        vm.onAction(CustomerOrderHistoryAction.OnRefresh)
        advanceUntilIdle()

        assertThat(order.syncCustomerOrdersCalls.size).isEqualTo(baseline + 1)
        assertThat(vm.state.value.isRefreshing).isFalse()
    }

    @Test
    fun `syncCustomerOrders failure populates errorMessage`() = runTest {
        val order = FakeOrderRepository().apply {
            syncCustomerOrdersResult = Result.failure(IllegalStateException("boom"))
        }
        val (vm, _, _) = newViewModel(order = order)
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).contains("boom")
    }

    @Test
    fun `SyncBus OrderStatusChanged emission triggers syncCustomerOrders`() = runTest {
        val bus = SyncBus()
        val (_, order, _) = newViewModel(bus = bus)
        advanceUntilIdle()
        val baseline = order.syncCustomerOrdersCalls.size

        bus.emit(SyncEvent.OrderStatusChanged(orderId = "orderA", sellerOrderId = "subA"))
        advanceUntilIdle()

        assertThat(order.syncCustomerOrdersCalls.size).isAtLeast(baseline + 1)
    }
}
