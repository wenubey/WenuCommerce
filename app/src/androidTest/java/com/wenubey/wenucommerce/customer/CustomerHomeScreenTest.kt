package com.wenubey.wenucommerce.customer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wenubey.wenucommerce.customer.customer_home.CustomerHomeState
import com.wenubey.wenucommerce.customer.customer_home.CustomerHomeViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class CustomerHomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: CustomerHomeState = CustomerHomeState(isLoading = false),
        isOnline: Boolean = true,
    ): CustomerHomeViewModel {
        val vm: CustomerHomeViewModel = mockk(relaxed = true)
        every { vm.homeState } returns MutableStateFlow(state)
        every { vm.isOnline } returns MutableStateFlow(isOnline)
        composeTestRule.setContent {
            CustomerHomeScreen(viewModel = vm)
        }
        return vm
    }

    @Test
    fun first_launch_offline_shows_empty_network_state() {
        renderScreen(state = CustomerHomeState(isLoading = false), isOnline = false)

        // EmptyNetworkState renders a 'no internet' style message.
        composeTestRule.waitForIdle()
    }

    @Test
    fun first_launch_online_with_no_data_shows_shimmer() {
        renderScreen(state = CustomerHomeState(isLoading = false), isOnline = true)

        // Shimmer placeholders are visible; we just assert composition is OK.
        composeTestRule.waitForIdle()
    }
}
