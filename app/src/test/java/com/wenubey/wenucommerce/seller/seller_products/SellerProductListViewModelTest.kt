package com.wenubey.wenucommerce.seller.seller_products

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeCategoryRepository
import com.wenubey.wenucommerce.testing.fakes.FakeProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerProductListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private val sellerId = "seller-1"
    private val seller = User(uuid = sellerId, role = UserRole.SELLER)

    private fun newViewModel(
        product: FakeProductRepository = FakeProductRepository(),
        category: FakeCategoryRepository = FakeCategoryRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = seller),
    ) = SellerProductListViewModel(product, category, auth, dispatcherProvider)

    private fun p(
        id: String,
        title: String = "Title $id",
        status: ProductStatus = ProductStatus.ACTIVE,
        categoryId: String = "cat-1",
        categoryName: String = "Clothing",
        subcategoryId: String = "sub-1",
        subcategoryName: String = "Tops",
        tagNames: List<String> = emptyList(),
    ) = Product(
        id = id,
        title = title,
        status = status,
        categoryId = categoryId,
        categoryName = categoryName,
        subcategoryId = subcategoryId,
        subcategoryName = subcategoryName,
        tagNames = tagNames,
    )

    // --- init / observe ---

    @Test
    fun `null user is no-op — products list stays empty and not loading`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val product = FakeProductRepository()
        val vm = newViewModel(product = product, auth = auth)
        advanceUntilIdle()

        assertThat(vm.state.value.products).isEmpty()
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `observed seller products populate state and clear loading`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(p("p-1"), p("p-2")))
        advanceUntilIdle()

        assertThat(vm.state.value.products).hasSize(2)
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.errorMessage).isNull()
    }

    @Test
    fun `filteredProducts initially mirrors products with no filters`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(p("p-1"), p("p-2")))
        advanceUntilIdle()

        assertThat(vm.state.value.filteredProducts).hasSize(2)
    }

    // --- search filter ---

    @Test
    fun `search query matches title case-insensitive`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(
            p("p-1", title = "Red Shirt"),
            p("p-2", title = "Blue Hat"),
        ))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnSearchQueryChanged("RED"))
        advanceUntilIdle()
        assertThat(vm.state.value.filteredProducts.map { it.id }).containsExactly("p-1")
    }

    @Test
    fun `search query matches across title, category, subcategory, and tag names`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(
            p("by-title", title = "summer dress"),
            p("by-category", title = "Sock", categoryName = "Summer Apparel"),
            p("by-subcategory", title = "Pen", subcategoryName = "Summer Tools"),
            p("by-tag", title = "Mug", tagNames = listOf("summer", "limited")),
            p("no-match", title = "Winter coat"),
        ))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnSearchQueryChanged("summer"))
        advanceUntilIdle()

        assertThat(vm.state.value.filteredProducts.map { it.id })
            .containsExactly("by-title", "by-category", "by-subcategory", "by-tag")
    }

    @Test
    fun `blank search returns everything`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(p("p-1"), p("p-2")))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnSearchQueryChanged("   "))
        advanceUntilIdle()
        assertThat(vm.state.value.filteredProducts).hasSize(2)
    }

    // --- status filter ---

    @Test
    fun `status filter keeps only matching products`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(
            p("p-1", status = ProductStatus.ACTIVE),
            p("p-2", status = ProductStatus.DRAFT),
            p("p-3", status = ProductStatus.ARCHIVED),
        ))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnStatusFilterSelected(ProductStatus.DRAFT))
        advanceUntilIdle()
        assertThat(vm.state.value.filteredProducts.map { it.id }).containsExactly("p-2")
    }

    @Test
    fun `null status filter clears status restriction`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(
            p("p-1", status = ProductStatus.ACTIVE),
            p("p-2", status = ProductStatus.DRAFT),
        ))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnStatusFilterSelected(ProductStatus.DRAFT))
        vm.onAction(SellerProductListAction.OnStatusFilterSelected(null))
        advanceUntilIdle()
        assertThat(vm.state.value.filteredProducts).hasSize(2)
    }

    // --- category filter ---

    @Test
    fun `category filter narrows by categoryId`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(
            p("p-1", categoryId = "cat-A"),
            p("p-2", categoryId = "cat-B"),
        ))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnFilterCategorySelected("cat-A"))
        advanceUntilIdle()
        assertThat(vm.state.value.filteredProducts.map { it.id }).containsExactly("p-1")
    }

    @Test
    fun `selecting a new category clears the previous subcategory`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(p("p-1")))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnFilterCategorySelected("cat-A"))
        vm.onAction(SellerProductListAction.OnFilterSubcategorySelected("sub-X"))
        vm.onAction(SellerProductListAction.OnFilterCategorySelected("cat-B"))
        advanceUntilIdle()

        assertThat(vm.state.value.filterCategoryId).isEqualTo("cat-B")
        assertThat(vm.state.value.filterSubcategoryId).isNull()
    }

    @Test
    fun `subcategory filter further narrows results`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(
            p("p-1", categoryId = "cat-A", subcategoryId = "sub-1"),
            p("p-2", categoryId = "cat-A", subcategoryId = "sub-2"),
        ))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnFilterCategorySelected("cat-A"))
        vm.onAction(SellerProductListAction.OnFilterSubcategorySelected("sub-1"))
        advanceUntilIdle()

        assertThat(vm.state.value.filteredProducts.map { it.id }).containsExactly("p-1")
    }

    @Test
    fun `OnClearCategoryFilters drops category and subcategory restrictions`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(p("p-1"), p("p-2")))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnFilterCategorySelected("cat-X"))
        vm.onAction(SellerProductListAction.OnFilterSubcategorySelected("sub-Y"))
        vm.onAction(SellerProductListAction.OnClearCategoryFilters)
        advanceUntilIdle()

        assertThat(vm.state.value.filterCategoryId).isNull()
        assertThat(vm.state.value.filterSubcategoryId).isNull()
    }

    @Test
    fun `multiple filters AND together (search + status + category)`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(
            p("good", title = "Red shirt", status = ProductStatus.ACTIVE, categoryId = "cat-A"),
            p("wrong-status", title = "Red shirt", status = ProductStatus.DRAFT, categoryId = "cat-A"),
            p("wrong-cat", title = "Red shirt", status = ProductStatus.ACTIVE, categoryId = "cat-B"),
            p("wrong-search", title = "Blue hat", status = ProductStatus.ACTIVE, categoryId = "cat-A"),
        ))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnSearchQueryChanged("red"))
        vm.onAction(SellerProductListAction.OnStatusFilterSelected(ProductStatus.ACTIVE))
        vm.onAction(SellerProductListAction.OnFilterCategorySelected("cat-A"))
        advanceUntilIdle()

        assertThat(vm.state.value.filteredProducts.map { it.id }).containsExactly("good")
    }

    // --- category load (lazy) ---

    @Test
    fun `OnRequestCategoryLoad fetches categories once and sets the loading flag back to false`() = runTest {
        val cats = listOf(Category(id = "c-1", name = "Clothing"))
        val category = FakeCategoryRepository().apply { setCategories(cats) }
        val vm = newViewModel(category = category)
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnRequestCategoryLoad)
        advanceUntilIdle()

        assertThat(vm.state.value.categories).isEqualTo(cats)
        assertThat(vm.state.value.isLoadingCategories).isFalse()
    }

    @Test
    fun `OnRequestCategoryLoad is no-op when categories already loaded`() = runTest {
        val cats = listOf(Category(id = "c-1", name = "Clothing"))
        val category = FakeCategoryRepository().apply { setCategories(cats) }
        val vm = newViewModel(category = category)
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnRequestCategoryLoad)
        advanceUntilIdle()

        // Reset call list, request again — repo should NOT be hit.
        category.getCategoriesResult = Result.success(listOf(Category(id = "c-2", name = "Other")))
        vm.onAction(SellerProductListAction.OnRequestCategoryLoad)
        advanceUntilIdle()

        // Original cats still there; second call ignored.
        assertThat(vm.state.value.categories).isEqualTo(cats)
    }

    @Test
    fun `OnRequestCategoryLoad failure resets isLoadingCategories without crashing`() = runTest {
        val category = FakeCategoryRepository().apply {
            getCategoriesResult = Result.failure(RuntimeException("offline"))
        }
        val vm = newViewModel(category = category)
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnRequestCategoryLoad)
        advanceUntilIdle()

        assertThat(vm.state.value.isLoadingCategories).isFalse()
        assertThat(vm.state.value.categories).isEmpty()
    }

    // --- product actions ---

    @Test
    fun `OnSubmitForReview success keeps errorMessage null and calls repo`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(p("p-1")))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnSubmitForReview("p-1"))
        advanceUntilIdle()

        assertThat(product.submitForReviewCalls).containsExactly("p-1")
        assertThat(vm.state.value.errorMessage).isNull()
    }

    @Test
    fun `OnUnarchiveProduct calls repository`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(p("p-1", status = ProductStatus.ARCHIVED)))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnUnarchiveProduct("p-1"))
        advanceUntilIdle()

        assertThat(product.unarchiveCalls).containsExactly("p-1")
    }

    // --- delete dialog flow ---

    @Test
    fun `OnShowDeleteDialog sets the dialog state with the target product`() = runTest {
        val target = p("p-1")
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(target))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnShowDeleteDialog(target))
        advanceUntilIdle()

        assertThat(vm.state.value.showDeleteDialog).isTrue()
        assertThat(vm.state.value.productToDelete).isEqualTo(target)
    }

    @Test
    fun `OnDismissDeleteDialog clears dialog state`() = runTest {
        val target = p("p-1")
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(target))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnShowDeleteDialog(target))
        vm.onAction(SellerProductListAction.OnDismissDeleteDialog)
        advanceUntilIdle()

        assertThat(vm.state.value.showDeleteDialog).isFalse()
        assertThat(vm.state.value.productToDelete).isNull()
    }

    @Test
    fun `OnConfirmDelete archives the pending product and clears the dialog`() = runTest {
        val target = p("p-1")
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, listOf(target))
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnShowDeleteDialog(target))
        vm.onAction(SellerProductListAction.OnConfirmDelete)
        advanceUntilIdle()

        assertThat(product.archiveCalls).containsExactly("p-1")
        assertThat(vm.state.value.showDeleteDialog).isFalse()
        assertThat(vm.state.value.productToDelete).isNull()
    }

    @Test
    fun `OnConfirmDelete with no pending product is a no-op`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        product.emitSellerProducts(sellerId, emptyList())
        advanceUntilIdle()

        vm.onAction(SellerProductListAction.OnConfirmDelete)
        advanceUntilIdle()

        assertThat(product.archiveCalls).isEmpty()
    }
}
