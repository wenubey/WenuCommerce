package com.wenubey.wenucommerce.seller.seller_discounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SellerDiscountCreateEditScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: DiscountCreateEditState = DiscountCreateEditState(),
        code: String? = null,
        isSeller: Boolean = true,
        onBack: () -> Unit = {},
    ): DiscountCreateEditViewModel {
        val vm: DiscountCreateEditViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        composeTestRule.setContent {
            SellerDiscountCreateEditScreen(
                code = code,
                isSeller = isSeller,
                onNavigateBack = onBack,
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun create_mode_renders_create_title_and_button_copy() {
        renderScreen(state = DiscountCreateEditState(isEditMode = false))

        // "Create Discount" is rendered twice — TopAppBar title + Save button.
        val nodes = composeTestRule.onAllNodesWithText("Create Discount").fetchSemanticsNodes()
        assertThat(nodes).hasSize(2)
    }

    @Test
    fun edit_mode_renders_edit_title_and_button_copy() {
        renderScreen(state = DiscountCreateEditState(isEditMode = true))

        composeTestRule.onNodeWithText("Edit Discount").assertIsDisplayed()
        composeTestRule.onNodeWithText("Update Discount").assertIsDisplayed()
    }

    @Test
    fun renders_coupon_code_input_label() {
        renderScreen()

        composeTestRule.onNodeWithText("Coupon Code").assertIsDisplayed()
    }
}
