package com.wenubey.wenucommerce.seller.seller_products

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wenubey.wenucommerce.seller.seller_categories.SellerCategoryState
import com.wenubey.wenucommerce.seller.seller_categories.SellerCategoryViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SellerProductCreateScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        productState: SellerProductCreateState = SellerProductCreateState(isSellerVerified = true),
        categoryState: SellerCategoryState = SellerCategoryState(),
    ): SellerProductCreateViewModel {
        val productVm: SellerProductCreateViewModel = mockk(relaxed = true)
        every { productVm.state } returns MutableStateFlow(productState)
        val categoryVm: SellerCategoryViewModel = mockk(relaxed = true)
        every { categoryVm.categoryState } returns MutableStateFlow(categoryState)
        composeTestRule.setContent {
            SellerProductCreateScreen(
                viewModel = productVm,
                categoryViewModel = categoryVm,
            )
        }
        return productVm
    }

    @Test
    fun renders_basic_info_section_when_seller_verified() {
        renderScreen()

        composeTestRule.onNodeWithText("Basic Info").assertIsDisplayed()
        composeTestRule.onNodeWithText("Product Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Description").assertIsDisplayed()
    }

    @Test
    fun shows_category_section_label() {
        renderScreen()

        composeTestRule.onNodeWithText("Category").assertIsDisplayed()
    }
}
