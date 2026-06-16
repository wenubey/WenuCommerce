package com.wenubey.wenucommerce

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.wenucommerce.seller.orders.SellerOrderDetailAction
import com.wenubey.wenucommerce.seller.orders.SellerOrderDetailViewModel
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.fakes.FakeOrderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerOrderDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sellerOrderId = "seller-order-1"

    private fun makeOrder(status: OrderStatus) = SellerOrder(
        id = sellerOrderId,
        parentOrderId = "parent-1",
        sellerId = "seller-1",
        status = status,
        subtotal = 100.0,
        shippingShare = 5.0,
        discountShare = 10.0,
        createdAt = "2026-06-15T10:00:00Z",
    )

    private fun newViewModel(
        order: FakeOrderRepository = FakeOrderRepository(),
    ): Pair<SellerOrderDetailViewModel, FakeOrderRepository> {
        val handle = SavedStateHandle(mapOf("sellerOrderId" to sellerOrderId))
        return SellerOrderDetailViewModel(order, handle) to order
    }

    @Test
    fun `OnAdvanceClicked CONFIRMED when status PENDING calls updateSellerOrderStatus`() = runTest {
        val order = FakeOrderRepository().apply { emitSellerOrders(listOf(makeOrder(OrderStatus.PENDING))) }
        val (vm, repo) = newViewModel(order = order)
        advanceUntilIdle()

        vm.onAction(SellerOrderDetailAction.OnAdvanceClicked(OrderStatus.CONFIRMED))
        advanceUntilIdle()

        assertThat(repo.updateSellerOrderStatusCalls).hasSize(1)
        val call = repo.updateSellerOrderStatusCalls.first()
        assertThat(call.id).isEqualTo(sellerOrderId)
        assertThat(call.next).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(call.trackingNumber).isNull()
        assertThat(call.note).isNull()
    }

    @Test
    fun `OnAdvanceClicked SHIPPED does not call repo immediately and opens shipped dialog`() = runTest {
        val order = FakeOrderRepository().apply { emitSellerOrders(listOf(makeOrder(OrderStatus.CONFIRMED))) }
        val (vm, repo) = newViewModel(order = order)
        advanceUntilIdle()

        vm.onAction(SellerOrderDetailAction.OnAdvanceClicked(OrderStatus.SHIPPED))
        advanceUntilIdle()

        assertThat(vm.state.value.showShippedDialog).isTrue()
        assertThat(repo.updateSellerOrderStatusCalls).isEmpty()
    }

    @Test
    fun `OnConfirmShipped with tracking calls updateSellerOrderStatus SHIPPED tracking`() = runTest {
        val order = FakeOrderRepository().apply { emitSellerOrders(listOf(makeOrder(OrderStatus.CONFIRMED))) }
        val (vm, repo) = newViewModel(order = order)
        advanceUntilIdle()

        vm.onAction(SellerOrderDetailAction.OnConfirmShipped("track-xyz"))
        advanceUntilIdle()

        assertThat(repo.updateSellerOrderStatusCalls).hasSize(1)
        val call = repo.updateSellerOrderStatusCalls.first()
        assertThat(call.next).isEqualTo(OrderStatus.SHIPPED)
        assertThat(call.trackingNumber).isEqualTo("track-xyz")
        assertThat(vm.state.value.showShippedDialog).isFalse()
    }

    @Test
    fun `OnConfirmShipped null calls updateSellerOrderStatus SHIPPED null`() = runTest {
        val order = FakeOrderRepository().apply { emitSellerOrders(listOf(makeOrder(OrderStatus.CONFIRMED))) }
        val (vm, repo) = newViewModel(order = order)
        advanceUntilIdle()

        vm.onAction(SellerOrderDetailAction.OnConfirmShipped(null))
        advanceUntilIdle()

        val call = repo.updateSellerOrderStatusCalls.first()
        assertThat(call.next).isEqualTo(OrderStatus.SHIPPED)
        assertThat(call.trackingNumber).isNull()
    }

    @Test
    fun `OnCancelClicked opens cancel dialog and does not call repo`() = runTest {
        val order = FakeOrderRepository().apply { emitSellerOrders(listOf(makeOrder(OrderStatus.PENDING))) }
        val (vm, repo) = newViewModel(order = order)
        advanceUntilIdle()

        vm.onAction(SellerOrderDetailAction.OnCancelClicked)
        advanceUntilIdle()

        assertThat(vm.state.value.showCancelDialog).isTrue()
        assertThat(repo.cancelSellerOrderCalls).isEmpty()
    }

    @Test
    fun `OnConfirmCancel calls cancelSellerOrder`() = runTest {
        val order = FakeOrderRepository().apply { emitSellerOrders(listOf(makeOrder(OrderStatus.PENDING))) }
        val (vm, repo) = newViewModel(order = order)
        advanceUntilIdle()

        vm.onAction(SellerOrderDetailAction.OnConfirmCancel)
        advanceUntilIdle()

        assertThat(repo.cancelSellerOrderCalls).containsExactly(sellerOrderId)
        assertThat(vm.state.value.showCancelDialog).isFalse()
    }

    @Test
    fun `repository failure populates errorMessage`() = runTest {
        val order = FakeOrderRepository().apply {
            emitSellerOrders(listOf(makeOrder(OrderStatus.PENDING)))
            updateSellerOrderStatusResult = Result.failure(IllegalStateException("rule denied"))
        }
        val (vm, _) = newViewModel(order = order)
        advanceUntilIdle()

        vm.onAction(SellerOrderDetailAction.OnAdvanceClicked(OrderStatus.CONFIRMED))
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).contains("rule denied")
        assertThat(vm.state.value.isMutating).isFalse()
    }

    @Test
    fun `isMutating toggles around update call`() = runTest {
        val order = FakeOrderRepository().apply { emitSellerOrders(listOf(makeOrder(OrderStatus.PENDING))) }
        val (vm, _) = newViewModel(order = order)
        advanceUntilIdle()

        // Before action: not mutating
        assertThat(vm.state.value.isMutating).isFalse()

        vm.onAction(SellerOrderDetailAction.OnAdvanceClicked(OrderStatus.CONFIRMED))
        advanceUntilIdle()

        // After action completes (success path): no longer mutating.
        assertThat(vm.state.value.isMutating).isFalse()
        assertThat(vm.state.value.toastMessage).contains("Confirmed")
    }
}
