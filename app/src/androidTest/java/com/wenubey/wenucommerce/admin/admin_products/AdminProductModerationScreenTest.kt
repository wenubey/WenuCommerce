package com.wenubey.wenucommerce.admin.admin_products

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AdminProductModerationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: AdminProductModerationState = AdminProductModerationState(isLoading = false),
    ): AdminProductModerationViewModel {
        val vm: AdminProductModerationViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        composeTestRule.setContent {
            AdminProductModerationScreen(viewModel = vm)
        }
        return vm
    }

    @Test
    fun empty_state_shows_no_pending_message() {
        renderScreen()

        composeTestRule.onNodeWithText("No products pending review").assertIsDisplayed()
    }
}
