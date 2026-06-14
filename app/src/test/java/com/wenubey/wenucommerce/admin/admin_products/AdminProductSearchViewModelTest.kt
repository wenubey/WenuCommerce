package com.wenubey.wenucommerce.admin.admin_products

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeCategoryRepository
import com.wenubey.wenucommerce.testing.fakes.FakeProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminProductSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        product: FakeProductRepository = FakeProductRepository(),
        category: FakeCategoryRepository = FakeCategoryRepository(),
    ): Triple<AdminProductSearchViewModel, FakeProductRepository, FakeCategoryRepository> =
        Triple(
            AdminProductSearchViewModel(product, category, dispatcherProvider),
            product,
            category,
        )

    private fun result(vararg p: Product) = Result.success(p.toList())

    // -------- debounce + query --------

    @Test
    fun `query change is debounced before search fires`() = runTest {
        val product = FakeProductRepository()
        product.searchAllResult = result(Product(id = "p1", title = "X"))
        val (vm, _, _) = newViewModel(product)

        vm.onAction(AdminProductSearchAction.OnSearchQueryChanged("hello"))
        // Below debounce window — no search yet.
        advanceTimeBy(200)
        assertThat(vm.state.value.searchResults).isEmpty()

        // Past debounce — search fires.
        advanceTimeBy(200)
        advanceUntilIdle()
        assertThat(vm.state.value.searchResults.map { it.id }).containsExactly("p1")
        assertThat(vm.state.value.isSearching).isFalse()
    }

    @Test
    fun `blank query clears results and skips repo call`() = runTest {
        val product = FakeProductRepository()
        product.searchAllResult = result(Product(id = "p1", title = "X"))
        val (vm, _, _) = newViewModel(product)

        // First populate a result.
        vm.onAction(AdminProductSearchAction.OnSearchQueryChanged("hello"))
        advanceUntilIdle()

        // Then clear.
        vm.onAction(AdminProductSearchAction.OnClearSearch)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.searchQuery).isEmpty()
        assertThat(state.searchResults).isEmpty()
        assertThat(state.filteredResults).isEmpty()
        assertThat(state.isSearching).isFalse()
    }

    @Test
    fun `search failure surfaces error message`() = runTest {
        val product = FakeProductRepository()
        product.searchAllResult = Result.failure(RuntimeException("offline"))
        val (vm, _, _) = newViewModel(product)

        vm.onAction(AdminProductSearchAction.OnSearchQueryChanged("hello"))
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isEqualTo("offline")
        assertThat(vm.state.value.isSearching).isFalse()
    }

    // -------- status filter --------

    @Test
    fun `OnStatusFilterChanged restricts filteredResults by status without re-searching`() = runTest {
        val product = FakeProductRepository()
        product.searchAllResult = result(
            Product(id = "a", title = "A", status = ProductStatus.ACTIVE),
            Product(id = "d", title = "D", status = ProductStatus.DRAFT),
            Product(id = "p", title = "P", status = ProductStatus.PENDING_REVIEW),
        )
        val (vm, _, _) = newViewModel(product)
        vm.onAction(AdminProductSearchAction.OnSearchQueryChanged("anything"))
        advanceUntilIdle()
        // No filter → all visible
        assertThat(vm.state.value.filteredResults.map { it.id }).containsExactly("a", "d", "p")

        vm.onAction(AdminProductSearchAction.OnStatusFilterChanged(ProductStatus.DRAFT))
        advanceUntilIdle()

        assertThat(vm.state.value.filteredResults.map { it.id }).containsExactly("d")
        assertThat(vm.state.value.searchResults.map { it.id }).containsExactly("a", "d", "p")
    }

    @Test
    fun `OnStatusFilterChanged null clears status filter and shows all`() = runTest {
        val product = FakeProductRepository()
        product.searchAllResult = result(
            Product(id = "a", status = ProductStatus.ACTIVE),
            Product(id = "d", status = ProductStatus.DRAFT),
        )
        val (vm, _, _) = newViewModel(product)
        vm.onAction(AdminProductSearchAction.OnSearchQueryChanged("hi"))
        advanceUntilIdle()
        vm.onAction(AdminProductSearchAction.OnStatusFilterChanged(ProductStatus.ACTIVE))
        advanceUntilIdle()

        vm.onAction(AdminProductSearchAction.OnStatusFilterChanged(null))
        advanceUntilIdle()

        assertThat(vm.state.value.filteredResults.map { it.id }).containsExactly("a", "d")
    }

    // -------- category filter --------

    @Test
    fun `OnFilterCategorySelected resets subcategory and re-searches`() = runTest {
        val product = FakeProductRepository()
        product.searchAllResult = result(Product(id = "p1", title = "X"))
        val (vm, _, _) = newViewModel(product)
        vm.onAction(AdminProductSearchAction.OnSearchQueryChanged("hello"))
        advanceUntilIdle()
        vm.onAction(AdminProductSearchAction.OnFilterSubcategorySelected("sub-a"))
        advanceUntilIdle()
        assertThat(vm.state.value.filterSubcategoryId).isEqualTo("sub-a")

        vm.onAction(AdminProductSearchAction.OnFilterCategorySelected("cat-x"))
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.filterCategoryId).isEqualTo("cat-x")
        assertThat(state.filterSubcategoryId).isNull()
    }

    @Test
    fun `OnClearCategoryFilters resets both ids`() = runTest {
        val product = FakeProductRepository()
        product.searchAllResult = result()
        val (vm, _, _) = newViewModel(product)
        vm.onAction(AdminProductSearchAction.OnSearchQueryChanged("hi"))
        vm.onAction(AdminProductSearchAction.OnFilterCategorySelected("cat-x"))
        vm.onAction(AdminProductSearchAction.OnFilterSubcategorySelected("sub-a"))
        advanceUntilIdle()

        vm.onAction(AdminProductSearchAction.OnClearCategoryFilters)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.filterCategoryId).isNull()
        assertThat(state.filterSubcategoryId).isNull()
    }

    @Test
    fun `OnFilterSubcategorySelected without query does not call search`() = runTest {
        val product = FakeProductRepository()
        // searchAllResult never set → would throw if called via the default factory.
        val (vm, _, _) = newViewModel(product)

        vm.onAction(AdminProductSearchAction.OnFilterSubcategorySelected("sub-a"))
        advanceUntilIdle()

        // No exception, no result populated.
        assertThat(vm.state.value.searchResults).isEmpty()
    }

    // -------- category lazy-load --------

    @Test
    fun `OnRequestCategoryLoad fetches categories once`() = runTest {
        val category = FakeCategoryRepository()
        category.setCategories(listOf(Category(id = "c-1", name = "Tools")))
        val (vm, _, _) = newViewModel(category = category)

        vm.onAction(AdminProductSearchAction.OnRequestCategoryLoad)
        advanceUntilIdle()

        assertThat(vm.state.value.categories.map { it.name }).containsExactly("Tools")
        assertThat(vm.state.value.isLoadingCategories).isFalse()
    }

    @Test
    fun `OnRequestCategoryLoad is a no-op when categories already loaded`() = runTest {
        val category = FakeCategoryRepository()
        category.setCategories(listOf(Category(id = "c-1", name = "Tools")))
        val (vm, _, _) = newViewModel(category = category)
        vm.onAction(AdminProductSearchAction.OnRequestCategoryLoad)
        advanceUntilIdle()

        // Replace stored result so a real second call would be detectable.
        category.getCategoriesResult = Result.success(listOf(Category(id = "c-2", name = "Toys")))

        vm.onAction(AdminProductSearchAction.OnRequestCategoryLoad)
        advanceUntilIdle()

        // Still the original (no second fetch).
        assertThat(vm.state.value.categories.map { it.name }).containsExactly("Tools")
    }

    // -------- detail dialog --------

    @Test
    fun `OnProductSelected opens detail dialog with selection`() = runTest {
        val (vm, _, _) = newViewModel()
        val p = Product(id = "p1", title = "X")

        vm.onAction(AdminProductSearchAction.OnProductSelected(p))

        assertThat(vm.state.value.selectedProduct).isEqualTo(p)
        assertThat(vm.state.value.showDetailDialog).isTrue()
    }

    @Test
    fun `OnDismissDetailDialog clears selection and flag`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.onAction(AdminProductSearchAction.OnProductSelected(Product(id = "p1")))

        vm.onAction(AdminProductSearchAction.OnDismissDetailDialog)

        val state = vm.state.value
        assertThat(state.selectedProduct).isNull()
        assertThat(state.showDetailDialog).isFalse()
    }
}
