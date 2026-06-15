package com.wenubey.wenucommerce.seller.seller_dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SellerDashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: SellerDashboardState = SellerDashboardState(),
        isApproved: Boolean = false,
    ): SellerDashboardViewModel {
        val vm: SellerDashboardViewModel = mockk(relaxed = true)
        every { vm.sellerDashboardState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            SellerDashboardScreen(
                isApproved = isApproved,
                onNavigateToSellerVerification = {},
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun renders_dashboard_title_and_first_summary_cards() {
        renderScreen()

        composeTestRule.onNodeWithText("Seller Dashboard").assertIsDisplayed()
        // The stats live in a horizontal LazyRow; only the first card or two
        // are guaranteed visible without scrolling.
        composeTestRule.onNodeWithText("Total Sales").assertIsDisplayed()
    }
}
