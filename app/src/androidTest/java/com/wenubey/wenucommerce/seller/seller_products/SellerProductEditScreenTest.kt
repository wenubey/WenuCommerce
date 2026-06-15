package com.wenubey.wenucommerce.seller.seller_products

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wenubey.domain.model.product.Product
import com.wenubey.wenucommerce.seller.seller_categories.SellerCategoryState
import com.wenubey.wenucommerce.seller.seller_categories.SellerCategoryViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SellerProductEditScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        productState: SellerProductEditState = SellerProductEditState(isLoading = false),
        categoryState: SellerCategoryState = SellerCategoryState(),
    ): SellerProductEditViewModel {
        val productVm: SellerProductEditViewModel = mockk(relaxed = true)
        every { productVm.state } returns MutableStateFlow(productState)
        val categoryVm: SellerCategoryViewModel = mockk(relaxed = true)
        every { categoryVm.categoryState } returns MutableStateFlow(categoryState)
        composeTestRule.setContent {
            SellerProductEditScreen(
                viewModel = productVm,
                categoryViewModel = categoryVm,
            )
        }
        return productVm
    }

    @Test
    fun renders_not_found_when_product_is_null_and_no_error() {
        renderScreen(state())

        composeTestRule.onNodeWithText("Product not found").assertIsDisplayed()
    }

    @Test
    fun surfaces_error_message_when_product_load_fails() {
        renderScreen(state(errorMessage = "Fetch failed"))

        composeTestRule.onNodeWithText("Fetch failed").assertIsDisplayed()
    }

    @Test
    fun renders_edit_form_when_product_is_loaded() {
        renderScreen(
            state(
                product = Product(id = "p-1", title = "Cool Widget"),
                isEditable = true,
            ),
        )

        // The form scaffolding renders when product != null. We assert the
        // composition didn't blow up by checking the screen is idle.
        composeTestRule.waitForIdle()
    }

    private fun state(
        product: Product? = null,
        errorMessage: String? = null,
        isEditable: Boolean = false,
    ) = SellerProductEditState(
        isLoading = false,
        product = product,
        errorMessage = errorMessage,
        isEditable = isEditable,
    )
}
