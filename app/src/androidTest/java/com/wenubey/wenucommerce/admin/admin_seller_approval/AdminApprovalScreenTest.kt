package com.wenubey.wenucommerce.admin.admin_seller_approval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AdminApprovalScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: AdminSellerApprovalState = AdminSellerApprovalState(),
    ): AdminApprovalViewModel {
        val vm: AdminApprovalViewModel = mockk(relaxed = true)
        every { vm.approvalState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            AdminApprovalScreen(viewModel = vm)
        }
        return vm
    }

    @Test
    fun renders_header_and_filter_chips() {
        renderScreen()

        composeTestRule.onNodeWithText("Seller Approvals").assertIsDisplayed()
        // 'Pending' appears as both the filter-chip label and (potentially)
        // a seller's status. At least one node is enough to prove the chip
        // row rendered.
        val pendingNodes = composeTestRule.onAllNodesWithText("Pending").fetchSemanticsNodes().size
        assert(pendingNodes >= 1)
    }
}
