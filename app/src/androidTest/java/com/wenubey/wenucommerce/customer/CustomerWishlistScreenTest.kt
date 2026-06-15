package com.wenubey.wenucommerce.customer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.wenubey.wenucommerce.customer.customer_wishlist.WishlistState
import com.wenubey.wenucommerce.customer.customer_wishlist.WishlistViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class CustomerWishlistScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: WishlistState = WishlistState(isLoading = false),
        onHome: () -> Unit = {},
        onProduct: (String) -> Unit = {},
    ): WishlistViewModel {
        val vm: WishlistViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        composeTestRule.setContent {
            CustomerWishlistScreen(
                onNavigateToProduct = onProduct,
                onNavigateToHome = onHome,
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun renders_empty_state_when_no_items() {
        renderScreen()

        composeTestRule.onNodeWithText("Nothing saved yet!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Shopping").assertIsDisplayed()
    }

    @Test
    fun start_shopping_button_invokes_navigateToHome() {
        var clicked = false
        renderScreen(onHome = { clicked = true })

        composeTestRule.onNodeWithText("Start Shopping").performClick()
        composeTestRule.waitForIdle()

        assertThat(clicked).isTrue()
    }

    @Test
    fun error_state_renders_message() {
        renderScreen(state = WishlistState(isLoading = false, error = "Boom"))

        composeTestRule.onNodeWithText("Boom").assertIsDisplayed()
    }
}
