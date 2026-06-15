package com.wenubey.wenucommerce.seller.seller_storefront

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SellerStorefrontScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: SellerStorefrontState = SellerStorefrontState(),
    ): SellerStorefrontViewModel {
        val vm: SellerStorefrontViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        composeTestRule.setContent {
            SellerStorefrontScreen(viewModel = vm)
        }
        return vm
    }

    @Test
    fun renders_default_store_name_when_blank() {
        renderScreen(state = SellerStorefrontState(isLoading = false, sellerName = ""))

        composeTestRule.onNodeWithText("Seller Store").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 products").assertIsDisplayed()
    }

    @Test
    fun renders_provided_seller_name() {
        renderScreen(
            state = SellerStorefrontState(isLoading = false, sellerName = "Lovelace Looms"),
        )

        composeTestRule.onNodeWithText("Lovelace Looms").assertIsDisplayed()
    }

    @Test
    fun renders_no_products_empty_state() {
        renderScreen(state = SellerStorefrontState(isLoading = false))

        composeTestRule.onNodeWithText("No products available").assertIsDisplayed()
    }
}
