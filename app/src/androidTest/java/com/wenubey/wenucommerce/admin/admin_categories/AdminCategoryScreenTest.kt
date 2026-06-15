package com.wenubey.wenucommerce.admin.admin_categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AdminCategoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderScreen(
        state: AdminCategoryState = AdminCategoryState(),
    ): AdminCategoryViewModel {
        val vm: AdminCategoryViewModel = mockk(relaxed = true)
        every { vm.categoryState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            AdminCategoryScreen(viewModel = vm)
        }
        return vm
    }

    @Test
    fun renders_title_and_create_fab() {
        renderScreen()

        composeTestRule.onNodeWithText("Category Management").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Create Category").assertIsDisplayed()
    }

    @Test
    fun create_fab_dispatches_OnShowCreateDialog_action() {
        val vm = renderScreen()

        composeTestRule.onNodeWithContentDescription("Create Category").performClick()
        composeTestRule.waitForIdle()

        verify { vm.onAction(AdminCategoryAction.OnShowCreateDialog) }
        assertThat(true).isTrue()
    }
}
