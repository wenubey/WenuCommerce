package com.wenubey.wenucommerce

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.StatusEntry
import com.wenubey.wenucommerce.core.components.OrderStatusStepper
import org.junit.Rule
import org.junit.Test

class OrderStatusStepperTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renders_all_four_step_labels_for_confirmed_status() {
        composeTestRule.setContent {
            OrderStatusStepper(
                history = listOf(
                    StatusEntry(OrderStatus.PENDING, "2026-06-15T10:00:00Z"),
                    StatusEntry(OrderStatus.CONFIRMED, "2026-06-15T11:00:00Z"),
                ),
                currentStatus = OrderStatus.CONFIRMED,
                trackingNumber = null,
            )
        }

        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirmed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shipped").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delivered").assertIsDisplayed()
    }

    @Test
    fun shipped_status_with_tracking_number_renders_tracking_chip() {
        composeTestRule.setContent {
            OrderStatusStepper(
                history = listOf(
                    StatusEntry(OrderStatus.PENDING, "2026-06-15T10:00:00Z"),
                    StatusEntry(OrderStatus.CONFIRMED, "2026-06-15T11:00:00Z"),
                    StatusEntry(OrderStatus.SHIPPED, "2026-06-15T12:00:00Z", trackingNumber = "TRACK123"),
                ),
                currentStatus = OrderStatus.SHIPPED,
                trackingNumber = "TRACK123",
            )
        }

        composeTestRule.onNodeWithText("TRACK123").assertIsDisplayed()
    }

    @Test
    fun cancelled_status_shows_cancelled_marker() {
        composeTestRule.setContent {
            OrderStatusStepper(
                history = listOf(
                    StatusEntry(OrderStatus.PENDING, "2026-06-15T10:00:00Z"),
                    StatusEntry(OrderStatus.CANCELLED, "2026-06-15T12:00:00Z"),
                ),
                currentStatus = OrderStatus.CANCELLED,
                trackingNumber = null,
            )
        }

        composeTestRule.onNodeWithText("Cancelled on 2026-06-15T12:00:00Z").assertIsDisplayed()
        // Future-step labels should NOT be visible when cancelled (we only render history)
        composeTestRule.onNodeWithText("Shipped").assertDoesNotExist()
        composeTestRule.onNodeWithText("Delivered").assertDoesNotExist()
    }
}
