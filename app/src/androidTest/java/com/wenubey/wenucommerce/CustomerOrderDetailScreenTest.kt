package com.wenubey.wenucommerce

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.domain.model.order.ShippingAddress
import com.wenubey.domain.model.order.StatusEntry
import com.wenubey.wenucommerce.customer.orders.CustomerOrderDetailScreen
import com.wenubey.wenucommerce.customer.orders.CustomerOrderDetailState
import com.wenubey.wenucommerce.customer.orders.CustomerOrderDetailViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class CustomerOrderDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val address = ShippingAddress(
        fullName = "Alice", line1 = "1 Main",
        city = "Istanbul", state = "IS", postalCode = "34000", country = "TR",
    )

    private fun renderScreen(state: CustomerOrderDetailState): CustomerOrderDetailViewModel {
        val vm: CustomerOrderDetailViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        composeTestRule.setContent {
            CustomerOrderDetailScreen(
                orderId = state.order?.id ?: "order-1",
                onBack = {},
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun single_seller_order_shows_stepper_inline_without_collapse_header() {
        val parent = Order(id = "o1", shippingAddress = address, totalAmount = 30.0)
        val sub = SellerOrder(
            id = "s1",
            parentOrderId = "o1",
            sellerName = "Acme",
            status = OrderStatus.CONFIRMED,
            statusHistory = listOf(
                StatusEntry(status = OrderStatus.PENDING, timestamp = "2026-06-15T10:00:00Z"),
                StatusEntry(status = OrderStatus.CONFIRMED, timestamp = "2026-06-15T11:00:00Z"),
            ),
        )
        renderScreen(
            CustomerOrderDetailState(
                order = parent,
                sellerOrders = listOf(sub),
                expandedSellerIds = setOf("s1"),
                isLoading = false,
            )
        )

        composeTestRule.onNodeWithText("Acme").assertIsDisplayed()
        // Stepper renders the four canonical labels
        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shipped").assertIsDisplayed()
    }

    @Test
    fun cancelled_sub_order_shows_refund_footer() {
        val parent = Order(id = "o1", shippingAddress = address, totalAmount = 10.0)
        val sub = SellerOrder(
            id = "s1",
            parentOrderId = "o1",
            sellerName = "Acme",
            status = OrderStatus.CANCELLED,
            refundedAmount = 7.50,
            statusHistory = listOf(
                StatusEntry(status = OrderStatus.PENDING, timestamp = "2026-06-15T10:00:00Z"),
                StatusEntry(status = OrderStatus.CANCELLED, timestamp = "2026-06-15T12:00:00Z"),
            ),
        )
        renderScreen(
            CustomerOrderDetailState(
                order = parent,
                sellerOrders = listOf(sub),
                expandedSellerIds = setOf("s1"),
                isLoading = false,
            )
        )

        composeTestRule.onNodeWithText(
            "Refund of \$7.50 issued — funds typically arrive within 5–10 business days"
        ).assertIsDisplayed()
    }

    @Test
    fun savings_line_visible_when_discountAmount_positive() {
        val parent = Order(
            id = "o1",
            shippingAddress = address,
            subtotal = 50.0,
            totalAmount = 40.0,
            discountAmount = 10.0,
            discountCode = "SAVE10",
        )
        renderScreen(
            CustomerOrderDetailState(
                order = parent,
                sellerOrders = emptyList(),
                isLoading = false,
            )
        )

        composeTestRule.onNodeWithText("You saved").assertIsDisplayed()
        composeTestRule.onNodeWithText("$10.00").assertIsDisplayed()
    }

    @Test
    fun multi_seller_order_shows_section_headers_for_each_seller() {
        val parent = Order(id = "o1", shippingAddress = address, totalAmount = 80.0)
        val sub1 = SellerOrder(id = "s1", parentOrderId = "o1", sellerName = "Acme", status = OrderStatus.PENDING)
        val sub2 = SellerOrder(id = "s2", parentOrderId = "o1", sellerName = "Globex", status = OrderStatus.SHIPPED)
        renderScreen(
            CustomerOrderDetailState(
                order = parent,
                sellerOrders = listOf(sub1, sub2),
                expandedSellerIds = emptySet(), // multi-seller starts collapsed
                isLoading = false,
            )
        )

        composeTestRule.onNodeWithText("Acme").assertIsDisplayed()
        composeTestRule.onNodeWithText("Globex").assertIsDisplayed()
    }
}
