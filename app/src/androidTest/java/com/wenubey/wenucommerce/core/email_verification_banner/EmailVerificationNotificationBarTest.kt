package com.wenubey.wenucommerce.core.email_verification_banner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class EmailVerificationNotificationBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderBar(
        state: EmailVerificationBannerState = EmailVerificationBannerState(isVisible = true),
    ): EmailVerificationBannerViewModel {
        val vm: EmailVerificationBannerViewModel = mockk(relaxed = true)
        every { vm.emailVerificationBannerState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            EmailVerificationNotificationBar(
                onNavigateToProfile = {},
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun renders_actions_needed_message() {
        renderBar()

        // The annotated string is rendered as one Text. We assert the
        // 'Actions Needed!' prefix is present.
        composeTestRule.onNodeWithText("Actions Needed! Go To Settings").assertIsDisplayed()
    }
}
