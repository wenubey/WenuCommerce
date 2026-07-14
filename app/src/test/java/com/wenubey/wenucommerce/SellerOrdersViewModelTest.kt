package com.wenubey.wenucommerce

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.seller.orders.SellerOrderFilter
import com.wenubey.wenucommerce.seller.orders.SellerOrdersAction
import com.wenubey.wenucommerce.seller.orders.SellerOrdersViewModel
import com.wenubey.wenucommerce.seller.orders.visibleSellerOrders
import com.wenubey.wenucommerce.notification.SyncBus
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeOrderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerOrdersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sellerId = "seller-1"

    private fun seller() = User(uuid = sellerId)

    private fun newViewModel(
        order: FakeOrderRepository = FakeOrderRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = seller()),
    ) = SellerOrdersViewModel(order, auth, SyncBus()) to order

    private fun fakeOrder(
        id: String,
        status: OrderStatus,
        sellerId: String = "seller-1",
        createdAt: String = "2026-06-15T10:00:00Z",
    ) = SellerOrder(
        id = id,
        parentOrderId = "parent-$id",
        sellerId = sellerId,
        status = status,
        createdAt = createdAt,
    )

    @Test
    fun `init triggers observeSellerOrders and syncSellerOrders with current uid`() = runTest {
        val (vm, order) = newViewModel()
        advanceUntilIdle()

        assertThat(order.observeSellerOrdersCalls).isEqualTo(1)
        assertThat(order.syncSellerOrdersCalls).containsExactly(sellerId)
        // touch vm to avoid unused warning
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `repository emissions flow into state`() = runTest {
        val (vm, order) = newViewModel()
        advanceUntilIdle()

        order.emitSellerOrders(
            listOf(
                fakeOrder("a", OrderStatus.PENDING),
                fakeOrder("b", OrderStatus.SHIPPED),
            )
        )
        advanceUntilIdle()

        assertThat(vm.state.value.sellerOrders).hasSize(2)
    }

    @Test
    fun `filter PENDING shows only PENDING sub-orders`() = runTest {
        val (vm, order) = newViewModel()
        advanceUntilIdle()
        order.emitSellerOrders(
            listOf(
                fakeOrder("a", OrderStatus.PENDING),
                fakeOrder("b", OrderStatus.SHIPPED),
                fakeOrder("c", OrderStatus.PENDING),
            )
        )
        advanceUntilIdle()

        vm.onAction(SellerOrdersAction.OnFilterSelected(SellerOrderFilter.PENDING))
        advanceUntilIdle()

        val visible = vm.state.value.visibleSellerOrders()
        assertThat(visible.map { it.id }).containsExactly("a", "c")
    }

    @Test
    fun `syncSellerOrders failure populates errorMessage`() = runTest {
        val order = FakeOrderRepository().apply {
            syncSellerOrdersResult = Result.failure(IllegalStateException("boom"))
        }
        val (vm, _) = newViewModel(order = order)
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).contains("boom")
    }
}
