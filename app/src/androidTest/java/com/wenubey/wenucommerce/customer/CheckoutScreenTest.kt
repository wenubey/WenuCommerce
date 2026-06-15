package com.wenubey.wenucommerce.customer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.wenubey.wenucommerce.customer.checkout.CheckoutScreen
import com.wenubey.wenucommerce.customer.checkout.CheckoutState
import com.wenubey.wenucommerce.customer.checkout.CheckoutViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class CheckoutScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: CheckoutState = CheckoutState(isLoading = false),
        onBack: () -> Unit = {},
        onAddAddress: () -> Unit = {},
        onConfirmation: (String) -> Unit = {},
    ): CheckoutViewModel {
        val vm: CheckoutViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        every { vm.navigationEvent } returns MutableSharedFlow()
        composeTestRule.setContent {
            CheckoutScreen(
                onNavigateToAddAddress = onAddAddress,
                onNavigateToConfirmation = onConfirmation,
                onNavigateBack = onBack,
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun offline_state_shows_no_internet_message_and_back_button() {
        renderScreen(state = CheckoutState(isOnline = false, isLoading = false))

        composeTestRule.onNodeWithText("You need internet to complete checkout")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Go Back").assertIsDisplayed()
    }

    @Test
    fun offline_state_go_back_button_invokes_navigateBack() {
        var clicked = false
        renderScreen(
            state = CheckoutState(isOnline = false, isLoading = false),
            onBack = { clicked = true },
        )

        composeTestRule.onNodeWithText("Go Back").performClick()
        composeTestRule.waitForIdle()

        assertThat(clicked).isTrue()
    }

    @Test
    fun renders_when_online_and_not_loading() {
        renderScreen(state = CheckoutState(isLoading = false, isOnline = true))

        // The wizard scaffolding is composed without errors. The deeper
        // step-by-step flow (Address / Review / Payment) is covered by
        // CheckoutViewModelTest at the state-machine level.
        composeTestRule.waitForIdle()
    }
}
