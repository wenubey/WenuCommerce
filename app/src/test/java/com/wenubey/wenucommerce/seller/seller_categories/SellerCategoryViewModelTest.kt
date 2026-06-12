package com.wenubey.wenucommerce.seller.seller_categories

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeCategoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerCategoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(category: FakeCategoryRepository = FakeCategoryRepository()) =
        SellerCategoryViewModel(category, dispatcherProvider)

    @Test
    fun `init loads categories successfully`() = runTest {
        val list = listOf(Category(id = "c-1", name = "Clothing"))
        val category = FakeCategoryRepository().apply { setCategories(list) }
        val vm = newViewModel(category)
        advanceUntilIdle()

        assertThat(vm.categoryState.value.categories).isEqualTo(list)
        assertThat(vm.categoryState.value.isLoading).isFalse()
        assertThat(vm.categoryState.value.errorMessage).isNull()
    }

    @Test
    fun `init load failure surfaces error and clears loading`() = runTest {
        val category = FakeCategoryRepository().apply {
            getCategoriesResult = Result.failure(RuntimeException("offline"))
        }
        val vm = newViewModel(category)
        advanceUntilIdle()

        assertThat(vm.categoryState.value.categories).isEmpty()
        assertThat(vm.categoryState.value.isLoading).isFalse()
        assertThat(vm.categoryState.value.errorMessage).isEqualTo("offline")
    }

    @Test
    fun `OnCategorySelected sets selectedCategory and clears the prior subcategory`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        val cat1 = Category(id = "c-1", name = "Clothing")
        vm.onAction(SellerCategoryAction.OnCategorySelected(cat1))
        vm.onAction(SellerCategoryAction.OnSubcategorySelected(Subcategory(id = "s-1", name = "Tops")))
        advanceUntilIdle()
        assertThat(vm.categoryState.value.selectedSubcategory).isNotNull()

        // Switching category clears the prior subcategory.
        val cat2 = Category(id = "c-2", name = "Home")
        vm.onAction(SellerCategoryAction.OnCategorySelected(cat2))
        advanceUntilIdle()
        assertThat(vm.categoryState.value.selectedCategory).isEqualTo(cat2)
        assertThat(vm.categoryState.value.selectedSubcategory).isNull()
    }

    @Test
    fun `OnSubcategorySelected stores the subcategory`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        val sub = Subcategory(id = "s-1", name = "Tops")
        vm.onAction(SellerCategoryAction.OnSubcategorySelected(sub))
        advanceUntilIdle()
        assertThat(vm.categoryState.value.selectedSubcategory).isEqualTo(sub)
    }

    @Test
    fun `OnShowCreateCategoryDialog flips the flag, OnDismissDialog clears it`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerCategoryAction.OnShowCreateCategoryDialog)
        advanceUntilIdle()
        assertThat(vm.categoryState.value.showCreateCategoryDialog).isTrue()

        vm.onAction(SellerCategoryAction.OnDismissDialog)
        advanceUntilIdle()
        assertThat(vm.categoryState.value.showCreateCategoryDialog).isFalse()
    }

    @Test
    fun `OnShowCreateSubcategoryDialog flips the flag, OnDismissDialog clears both dialog flags`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerCategoryAction.OnShowCreateCategoryDialog)
        vm.onAction(SellerCategoryAction.OnShowCreateSubcategoryDialog)
        advanceUntilIdle()
        assertThat(vm.categoryState.value.showCreateCategoryDialog).isTrue()
        assertThat(vm.categoryState.value.showCreateSubcategoryDialog).isTrue()

        vm.onAction(SellerCategoryAction.OnDismissDialog)
        advanceUntilIdle()
        assertThat(vm.categoryState.value.showCreateCategoryDialog).isFalse()
        assertThat(vm.categoryState.value.showCreateSubcategoryDialog).isFalse()
    }

    @Test
    fun `OnCreateNewCategory calls createCategory, dismisses dialog, refreshes list`() = runTest {
        val initial = listOf(Category(id = "c-1", name = "Existing"))
        val createdList = initial + Category(id = "c-2", name = "New")
        val category = FakeCategoryRepository().apply { setCategories(initial) }
        val vm = newViewModel(category)
        advanceUntilIdle()

        vm.onAction(SellerCategoryAction.OnShowCreateCategoryDialog)
        // Simulate the repository returning the new list after creation.
        category.setCategories(createdList)
        vm.onAction(SellerCategoryAction.OnCreateNewCategory("New", "Description"))
        advanceUntilIdle()

        assertThat(category.createCalls).hasSize(1)
        assertThat(category.createCalls[0].name).isEqualTo("New")
        assertThat(category.createCalls[0].description).isEqualTo("Description")
        assertThat(vm.categoryState.value.showCreateCategoryDialog).isFalse()
        assertThat(vm.categoryState.value.errorMessage).isNull()
        assertThat(vm.categoryState.value.categories).isEqualTo(createdList)
    }

    @Test
    fun `OnCreateNewCategory failure surfaces error, keeps dialog open implicitly, clears loading`() = runTest {
        val category = FakeCategoryRepository().apply {
            createCategoryResult = { Result.failure(RuntimeException("server")) }
        }
        val vm = newViewModel(category)
        advanceUntilIdle()
        vm.onAction(SellerCategoryAction.OnShowCreateCategoryDialog)
        vm.onAction(SellerCategoryAction.OnCreateNewCategory("New", ""))
        advanceUntilIdle()

        assertThat(vm.categoryState.value.errorMessage).isEqualTo("server")
        assertThat(vm.categoryState.value.showCreateCategoryDialog).isTrue() // intentionally not cleared on failure
        assertThat(vm.categoryState.value.isLoading).isFalse()
    }

    @Test
    fun `OnCreateNewSubcategory calls addSubcategory, dismisses dialog, refreshes list`() = runTest {
        val refreshed = listOf(Category(id = "c-1", name = "C", subcategories = listOf(Subcategory(id = "s-1", name = "Tops"))))
        val category = FakeCategoryRepository().apply { setCategories(emptyList()) }
        val vm = newViewModel(category)
        advanceUntilIdle()

        vm.onAction(SellerCategoryAction.OnShowCreateSubcategoryDialog)
        // Simulate the refresh result.
        category.setCategories(refreshed)
        vm.onAction(SellerCategoryAction.OnCreateNewSubcategory("c-1", Subcategory(id = "s-1", name = "Tops")))
        advanceUntilIdle()

        assertThat(category.addSubcategoryCalls).hasSize(1)
        assertThat(category.addSubcategoryCalls[0].first).isEqualTo("c-1")
        assertThat(category.addSubcategoryCalls[0].second.name).isEqualTo("Tops")
        assertThat(vm.categoryState.value.showCreateSubcategoryDialog).isFalse()
        assertThat(vm.categoryState.value.categories).isEqualTo(refreshed)
    }

    @Test
    fun `OnCreateNewSubcategory failure surfaces error`() = runTest {
        val category = FakeCategoryRepository().apply {
            addSubcategoryResult = Result.failure(RuntimeException("perm denied"))
        }
        val vm = newViewModel(category)
        advanceUntilIdle()
        vm.onAction(SellerCategoryAction.OnCreateNewSubcategory("c-1", Subcategory()))
        advanceUntilIdle()

        assertThat(vm.categoryState.value.errorMessage).isEqualTo("perm denied")
    }

    @Test
    fun `OnRefresh reloads categories`() = runTest {
        val first = listOf(Category(id = "c-1", name = "First"))
        val second = listOf(Category(id = "c-1", name = "First"), Category(id = "c-2", name = "Second"))
        val category = FakeCategoryRepository().apply { setCategories(first) }
        val vm = newViewModel(category)
        advanceUntilIdle()

        category.setCategories(second)
        vm.onAction(SellerCategoryAction.OnRefresh)
        advanceUntilIdle()

        assertThat(vm.categoryState.value.categories).isEqualTo(second)
    }
}
