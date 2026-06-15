package com.wenubey.wenucommerce.seller.seller_discounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.discount.DiscountCode
import com.wenubey.domain.model.discount.DiscountType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SellerDiscountListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: DiscountListState = DiscountListState(isLoading = false),
        onCreate: () -> Unit = {},
        onEdit: (String) -> Unit = {},
    ): DiscountListViewModel {
        val vm: DiscountListViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        composeTestRule.setContent {
            SellerDiscountListScreen(
                onNavigateToCreate = onCreate,
                onNavigateToEdit = onEdit,
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun renders_empty_state_when_no_discounts() {
        renderScreen()

        composeTestRule.onNodeWithText("No discount codes yet").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Create discount").assertIsDisplayed()
    }

    @Test
    fun create_fab_invokes_navigateToCreate() {
        var clicked = false
        renderScreen(onCreate = { clicked = true })

        composeTestRule.onNodeWithContentDescription("Create discount").performClick()
        composeTestRule.waitForIdle()

        assertThat(clicked).isTrue()
    }

    @Test
    fun renders_discounts_in_the_list() {
        renderScreen(
            state = DiscountListState(
                isLoading = false,
                discounts = listOf(
                    DiscountCode(
                        code = "SAVE20",
                        type = DiscountType.PERCENTAGE,
                        value = 20.0,
                        sellerId = "s-1",
                        isActive = true,
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("SAVE20").assertIsDisplayed()
    }

    @Test
    fun discount_row_click_invokes_navigateToEdit_with_code() {
        var editedCode: String? = null
        renderScreen(
            state = DiscountListState(
                isLoading = false,
                discounts = listOf(
                    DiscountCode(code = "SAVE20", sellerId = "s-1"),
                ),
            ),
            onEdit = { editedCode = it },
        )

        composeTestRule.onNodeWithText("SAVE20").performClick()
        composeTestRule.waitForIdle()

        assertThat(editedCode).isEqualTo("SAVE20")
    }
}
