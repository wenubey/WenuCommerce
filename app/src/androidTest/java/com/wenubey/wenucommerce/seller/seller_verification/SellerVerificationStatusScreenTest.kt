package com.wenubey.wenucommerce.seller.seller_verification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SellerVerificationStatusScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: SellerVerificationState = SellerVerificationState(isLoading = false),
        onDashboard: () -> Unit = {},
        onBack: () -> Unit = {},
    ): SellerVerificationViewModel {
        val vm: SellerVerificationViewModel = mockk(relaxed = true)
        every { vm.sellerVerificationState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            SellerVerificationStatusScreen(
                onViewDashboardClick = onDashboard,
                onNavigateBack = onBack,
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun renders_screen_title_and_navigation() {
        renderScreen()

        composeTestRule.onNodeWithText("Verification Status").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate Back").assertIsDisplayed()
    }

    @Test
    fun navigate_back_icon_invokes_callback() {
        var backClicked = false
        renderScreen(onBack = { backClicked = true })

        composeTestRule.onNodeWithContentDescription("Navigate Back").performClick()
        composeTestRule.waitForIdle()

        assertThat(backClicked).isTrue()
    }
}
