package com.wenubey.wenucommerce

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wenubey.domain.model.order.AggregateOrderStatus
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.wenucommerce.customer.orders.CustomerOrderHistoryAction
import com.wenubey.wenucommerce.customer.orders.CustomerOrderHistoryScreen
import com.wenubey.wenucommerce.customer.orders.CustomerOrderHistoryState
import com.wenubey.wenucommerce.customer.orders.CustomerOrderHistoryViewModel
import com.wenubey.wenucommerce.customer.orders.OrderFilter
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class CustomerOrderHistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: CustomerOrderHistoryState = CustomerOrderHistoryState(),
        onOrderClick: (String) -> Unit = {},
        onBack: () -> Unit = {},
    ): CustomerOrderHistoryViewModel {
        val vm: CustomerOrderHistoryViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        composeTestRule.setContent {
            CustomerOrderHistoryScreen(
                onOrderClick = onOrderClick,
                onBack = onBack,
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun shows_all_four_filter_chip_labels() {
        renderScreen(state = CustomerOrderHistoryState(isLoading = false))

        composeTestRule.onNodeWithText("All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delivered").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelled").assertIsDisplayed()
    }

    @Test
    fun row_renders_short_id_total_and_status_badge() {
        val order = Order(
            id = "abcdefghIJKLMNOP",
            aggregateStatus = AggregateOrderStatus.SHIPPED,
            totalAmount = 49.99,
            items = listOf(OrderItem(productId = "p1", quantity = 2)),
            sellerOrderIds = listOf("s1", "s2"),
            createdAt = "2026-06-15T10:00:00Z",
        )
        renderScreen(state = CustomerOrderHistoryState(orders = listOf(order), isLoading = false))

        composeTestRule.onNodeWithText("#abcdefgh").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shipped").assertIsDisplayed()
        composeTestRule.onNodeWithText("$49.99").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 items from 2 sellers").assertIsDisplayed()
    }

    @Test
    fun tapping_cancelled_filter_emits_action() {
        val captured = slot<CustomerOrderHistoryAction>()
        val vm: CustomerOrderHistoryViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(CustomerOrderHistoryState(isLoading = false))
        every { vm.onAction(capture(captured)) } just runs
        composeTestRule.setContent {
            CustomerOrderHistoryScreen(
                onOrderClick = {},
                onBack = {},
                viewModel = vm,
            )
        }

        composeTestRule.onNodeWithText("Cancelled").performClick()

        assert(captured.isCaptured) { "Filter action not captured" }
        assert(
            captured.captured is CustomerOrderHistoryAction.OnFilterSelected &&
                (captured.captured as CustomerOrderHistoryAction.OnFilterSelected).filter == OrderFilter.CANCELLED
        )
    }

    @Test
    fun empty_state_shows_no_orders_yet() {
        renderScreen(state = CustomerOrderHistoryState(orders = emptyList(), isLoading = false))

        composeTestRule.onNodeWithText("No orders yet").assertIsDisplayed()
    }
}
