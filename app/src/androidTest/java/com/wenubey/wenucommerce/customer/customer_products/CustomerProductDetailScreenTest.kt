package com.wenubey.wenucommerce.customer.customer_products

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class CustomerProductDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: CustomerProductDetailState = CustomerProductDetailState(isLoading = false),
    ): CustomerProductDetailViewModel {
        val vm: CustomerProductDetailViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        composeTestRule.setContent {
            CustomerProductDetailScreen(viewModel = vm)
        }
        return vm
    }

    @Test
    fun renders_not_found_when_product_is_null_and_no_error() {
        renderScreen(state = CustomerProductDetailState(isLoading = false, product = null))

        composeTestRule.onNodeWithText("Product not found").assertIsDisplayed()
    }

    @Test
    fun renders_error_message_when_load_fails() {
        renderScreen(
            state = CustomerProductDetailState(
                isLoading = false,
                product = null,
                errorMessage = "Fetch failed",
            ),
        )

        composeTestRule.onNodeWithText("Fetch failed").assertIsDisplayed()
    }

    @Test
    fun login_prompt_dialog_renders_when_flag_is_set() {
        renderScreen(state = CustomerProductDetailState(showLoginPrompt = true))

        composeTestRule.onNodeWithText("Sign in required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please sign in to add items to your cart.").assertIsDisplayed()
    }
}
