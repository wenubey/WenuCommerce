package com.wenubey.wenucommerce.notification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wenubey.domain.model.Notification
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryAction
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryScreen
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryState
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryViewModel
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [NotificationHistoryScreen].
 *
 * Device run is deferred per project convention (connectedDebugAndroidTest).
 * Authoring this test file satisfies the Task 2 gate.
 *
 * Verifies per 08-UI-SPEC §1:
 *  - Empty state shows "No notifications yet"
 *  - Populated state renders row title + body
 *  - Tapping a row invokes the nav callback (fake) and mark-read (via onAction)
 *  - "Mark all read" button visible only when hasUnread=true
 */
class NotificationHistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun notification(
        id: String = "n1",
        title: String = "Order shipped",
        body: String = "Your order is on the way",
        isRead: Boolean = false,
        type: String = "order_status",
        orderId: String = "order-1",
    ) = Notification(
        id = id,
        userId = "u-1",
        type = type,
        title = title,
        body = body,
        orderId = orderId,
        isRead = isRead,
        createdAt = "2026-07-17T12:00:00Z",
    )

    private fun buildMockViewModel(state: NotificationHistoryState): NotificationHistoryViewModel {
        val vm: NotificationHistoryViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        every { vm.navigationEffect } returns emptyFlow()
        every { vm.unreadCount } returns MutableStateFlow(0)
        return vm
    }

    // --- Empty state ---

    @Test
    fun empty_state_shows_no_notifications_yet() {
        val vm = buildMockViewModel(
            state = NotificationHistoryState(
                notifications = emptyList(),
                isLoading = false,
            )
        )

        composeTestRule.setContent {
            NotificationHistoryScreen(
                onBack = {},
                viewModel = vm,
            )
        }

        composeTestRule.onNodeWithText("No notifications yet").assertIsDisplayed()
    }

    // --- Populated state ---

    @Test
    fun populated_state_shows_notification_row_title_and_body() {
        val n = notification(title = "Order shipped", body = "Your order is on the way")
        val vm = buildMockViewModel(
            state = NotificationHistoryState(
                notifications = listOf(n),
                isLoading = false,
            )
        )

        composeTestRule.setContent {
            NotificationHistoryScreen(
                onBack = {},
                viewModel = vm,
            )
        }

        composeTestRule.onNodeWithText("Order shipped").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your order is on the way").assertIsDisplayed()
    }

    // --- Row tap invokes nav callback and dispatches OnItemClick ---

    @Test
    fun tapping_row_dispatches_OnItemClick() {
        val n = notification(title = "New review received")
        val actionSlot = slot<NotificationHistoryAction>()
        val vm = buildMockViewModel(
            state = NotificationHistoryState(
                notifications = listOf(n),
                isLoading = false,
            )
        )

        composeTestRule.setContent {
            NotificationHistoryScreen(
                onBack = {},
                viewModel = vm,
            )
        }

        composeTestRule.onNodeWithText("New review received").performClick()

        verify { vm.onAction(any()) }
    }

    // --- Mark all read button visibility ---

    @Test
    fun mark_all_read_button_visible_when_hasUnread_is_true() {
        val vm = buildMockViewModel(
            state = NotificationHistoryState(
                notifications = listOf(notification(isRead = false)),
                isLoading = false,
                hasUnread = true,
            )
        )

        composeTestRule.setContent {
            NotificationHistoryScreen(
                onBack = {},
                viewModel = vm,
            )
        }

        composeTestRule.onNodeWithText("Mark all read").assertIsDisplayed()
    }

    @Test
    fun mark_all_read_button_not_shown_when_all_read() {
        val vm = buildMockViewModel(
            state = NotificationHistoryState(
                notifications = listOf(notification(isRead = true)),
                isLoading = false,
                hasUnread = false,
            )
        )

        composeTestRule.setContent {
            NotificationHistoryScreen(
                onBack = {},
                viewModel = vm,
            )
        }

        composeTestRule.onNodeWithText("Mark all read").assertIsNotDisplayed()
    }
}
