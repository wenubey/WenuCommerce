package com.wenubey.wenucommerce.customer.customer_home

import com.google.common.truth.Truth.assertThat
import com.wenubey.data.connectivity.ConnectivityObserver
import com.wenubey.data.local.SyncManager
import com.wenubey.domain.model.WishlistItem
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeCategoryRepository
import com.wenubey.wenucommerce.testing.fakes.FakeProductRepository
import com.wenubey.wenucommerce.testing.fakes.FakeWishlistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerHomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private val testUserId = "u-1"

    private fun mockConnectivity(online: Boolean = true): ConnectivityObserver {
        val mock: ConnectivityObserver = mockk(relaxed = true)
        every { mock.isOnline } returns MutableStateFlow(online).asStateFlow()
        return mock
    }

    private fun mockSyncManager(): SyncManager {
        val mock: SyncManager = mockk(relaxed = true)
        coEvery { mock.manualSync() } returns Unit
        return mock
    }

    private fun newViewModel(
        category: FakeCategoryRepository = FakeCategoryRepository(),
        product: FakeProductRepository = FakeProductRepository(),
        wishlist: FakeWishlistRepository = FakeWishlistRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = User(uuid = testUserId)),
        syncManager: SyncManager = mockSyncManager(),
        connectivity: ConnectivityObserver = mockConnectivity(),
    ) = CustomerHomeViewModel(
        category, product, wishlist, auth, syncManager, connectivity, dispatcherProvider,
    )

    private fun p(id: String, categoryId: String = "c-1", subcategoryId: String = "s-1") =
        Product(
            id = id,
            title = "Title $id",
            categoryId = categoryId,
            subcategoryId = subcategoryId,
            status = ProductStatus.ACTIVE,
        )

    // --- categories / auto-select ---

    @Test
    fun `categories populate and auto-select the first category`() = runTest {
        val category = FakeCategoryRepository().apply {
            setCategories(listOf(Category(id = "c-1", name = "Clothing"), Category(id = "c-2", name = "Home")))
        }
        val product = FakeProductRepository()
        val vm = newViewModel(category = category, product = product)
        advanceUntilIdle()

        assertThat(vm.homeState.value.categories).hasSize(2)
        assertThat(vm.homeState.value.isLoading).isFalse()
        assertThat(vm.homeState.value.selectedCategoryId).isEqualTo("c-1")
    }

    @Test
    fun `empty category list does NOT auto-select anything`() = runTest {
        val category = FakeCategoryRepository().apply { setCategories(emptyList()) }
        val vm = newViewModel(category = category)
        advanceUntilIdle()
        assertThat(vm.homeState.value.selectedCategoryId).isNull()
    }

    @Test
    fun `OnCategorySelected updates selection and clears subcategory`() = runTest {
        val category = FakeCategoryRepository().apply {
            setCategories(listOf(Category(id = "c-1"), Category(id = "c-2")))
        }
        val vm = newViewModel(category = category)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnSubcategorySelected("s-X"))
        advanceUntilIdle()
        assertThat(vm.homeState.value.selectedSubcategoryId).isEqualTo("s-X")

        vm.onAction(CustomerHomeAction.OnCategorySelected("c-2"))
        advanceUntilIdle()
        assertThat(vm.homeState.value.selectedCategoryId).isEqualTo("c-2")
        assertThat(vm.homeState.value.selectedSubcategoryId).isNull()
    }

    @Test
    fun `OnSubcategorySelected without a selected category is a no-op`() = runTest {
        // No categories emitted → selectedCategoryId stays null.
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(CustomerHomeAction.OnSubcategorySelected("s-1"))
        advanceUntilIdle()

        assertThat(vm.homeState.value.selectedSubcategoryId).isNull()
    }

    // --- products feed by category ---

    @Test
    fun `selecting a category populates products via observeActiveProductsByCategoryAndSubcategory`() = runTest {
        val category = FakeCategoryRepository().apply {
            setCategories(listOf(Category(id = "c-1")))
        }
        val product = FakeProductRepository().apply {
            emitSellerProducts("seller-x", listOf(p("p-1", categoryId = "c-1")))
        }
        val vm = newViewModel(category = category, product = product)
        advanceUntilIdle()

        assertThat(vm.homeState.value.products.map { it.id }).containsExactly("p-1")
        assertThat(vm.homeState.value.isLoadingProducts).isFalse()
    }

    // --- search debounce ---

    @Test
    fun `OnSearchQueryChanged debounces and runs search via repository`() = runTest {
        val product = FakeProductRepository().apply {
            searchActiveResult = Result.success(listOf(p("hit-1")))
        }
        val category = FakeCategoryRepository().apply { setCategories(emptyList()) }
        val vm = newViewModel(category = category, product = product)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnSearchQueryChanged("red"))
        runCurrent()
        // Before 300ms debounce: isSearching true, no result yet.
        assertThat(vm.homeState.value.isSearching).isTrue()

        advanceTimeBy(350)
        advanceUntilIdle()

        assertThat(vm.homeState.value.searchResults.map { it.id }).containsExactly("hit-1")
        assertThat(vm.homeState.value.isSearching).isFalse()
        assertThat(vm.homeState.value.searchError).isNull()
    }

    @Test
    fun `blank search query clears results and resets isSearching`() = runTest {
        val category = FakeCategoryRepository().apply { setCategories(emptyList()) }
        val product = FakeProductRepository().apply {
            searchActiveResult = Result.success(listOf(p("hit-1")))
        }
        val vm = newViewModel(category = category, product = product)
        advanceUntilIdle()

        // Pull some results in
        vm.onAction(CustomerHomeAction.OnSearchQueryChanged("red"))
        advanceTimeBy(350)
        advanceUntilIdle()
        assertThat(vm.homeState.value.searchResults).isNotEmpty()

        // Now blank the query
        vm.onAction(CustomerHomeAction.OnSearchQueryChanged("   "))
        advanceTimeBy(350)
        advanceUntilIdle()

        assertThat(vm.homeState.value.searchResults).isEmpty()
        assertThat(vm.homeState.value.isSearching).isFalse()
    }

    @Test
    fun `search failure surfaces searchError and clears isSearching`() = runTest {
        val category = FakeCategoryRepository().apply { setCategories(emptyList()) }
        val product = FakeProductRepository().apply {
            searchActiveResult = Result.failure(RuntimeException("offline"))
        }
        val vm = newViewModel(category = category, product = product)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnSearchQueryChanged("red"))
        advanceTimeBy(350)
        advanceUntilIdle()

        assertThat(vm.homeState.value.searchError).isEqualTo("offline")
        assertThat(vm.homeState.value.isSearching).isFalse()
    }

    @Test
    fun `OnClearSearch resets search-related state`() = runTest {
        val category = FakeCategoryRepository().apply { setCategories(emptyList()) }
        val product = FakeProductRepository().apply {
            searchActiveResult = Result.success(listOf(p("hit-1")))
        }
        val vm = newViewModel(category = category, product = product)
        advanceUntilIdle()
        vm.onAction(CustomerHomeAction.OnSearchQueryChanged("red"))
        advanceTimeBy(350)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnClearSearch)
        advanceUntilIdle()

        val s = vm.homeState.value
        assertThat(s.searchQuery).isEmpty()
        assertThat(s.searchResults).isEmpty()
        assertThat(s.isSearching).isFalse()
        assertThat(s.searchError).isNull()
    }

    // --- search filter sheet ---

    @Test
    fun `selecting a search filter category clears the prior subcategory and immediately re-runs search`() = runTest {
        val category = FakeCategoryRepository().apply { setCategories(emptyList()) }
        val product = FakeProductRepository().apply {
            searchActiveResult = Result.success(listOf(p("hit-1")))
        }
        val vm = newViewModel(category = category, product = product)
        advanceUntilIdle()
        vm.onAction(CustomerHomeAction.OnSearchQueryChanged("red"))
        advanceTimeBy(350)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnSearchFilterSubcategorySelected("s-1"))
        advanceUntilIdle()
        assertThat(vm.homeState.value.filterSheetSubcategoryId).isEqualTo("s-1")

        // Selecting a new filter category clears the subcategory.
        vm.onAction(CustomerHomeAction.OnSearchFilterCategorySelected("c-9"))
        advanceUntilIdle()
        assertThat(vm.homeState.value.filterSheetCategoryId).isEqualTo("c-9")
        assertThat(vm.homeState.value.filterSheetSubcategoryId).isNull()
    }

    @Test
    fun `OnClearSearchFilters drops both filter ids`() = runTest {
        val category = FakeCategoryRepository().apply { setCategories(emptyList()) }
        val vm = newViewModel(category = category)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnSearchFilterCategorySelected("c-1"))
        vm.onAction(CustomerHomeAction.OnSearchFilterSubcategorySelected("s-1"))
        vm.onAction(CustomerHomeAction.OnClearSearchFilters)
        advanceUntilIdle()

        assertThat(vm.homeState.value.filterSheetCategoryId).isNull()
        assertThat(vm.homeState.value.filterSheetSubcategoryId).isNull()
    }

    // --- pull-to-refresh ---

    @Test
    fun `OnPullToRefresh calls SyncManager and toggles isRefreshing`() = runTest {
        val sync: SyncManager = mockk(relaxed = true)
        coEvery { sync.manualSync() } returns Unit
        val vm = newViewModel(syncManager = sync)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnPullToRefresh)
        advanceUntilIdle()

        coVerify(exactly = 1) { sync.manualSync() }
        assertThat(vm.homeState.value.isRefreshing).isFalse()
    }

    @Test
    fun `OnPullToRefresh resets isRefreshing even when sync throws (TB-5)`() = runTest {
        val sync: SyncManager = mockk(relaxed = true)
        coEvery { sync.manualSync() } throws RuntimeException("boom")
        val vm = newViewModel(syncManager = sync)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnPullToRefresh)
        advanceUntilIdle()

        // runCatching swallows the throw so the UI can recover cleanly.
        assertThat(vm.homeState.value.isRefreshing).isFalse()
    }

    // --- wishlist ---

    @Test
    fun `wishlistedProductIds mirrors the observed wishlist items`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        advanceUntilIdle()

        wishlist.emit(testUserId, listOf(
            WishlistItem(productId = "w-1"),
            WishlistItem(productId = "w-2"),
        ))
        advanceUntilIdle()

        assertThat(vm.homeState.value.wishlistedProductIds).containsExactly("w-1", "w-2")
    }

    @Test
    fun `OnToggleWishlist for a browse-feed product calls repository`() = runTest {
        val category = FakeCategoryRepository().apply { setCategories(listOf(Category(id = "c-1"))) }
        val product = FakeProductRepository().apply {
            emitSellerProducts("seller-x", listOf(p("p-1", categoryId = "c-1")))
        }
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(category = category, product = product, wishlist = wishlist)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnToggleWishlist("p-1"))
        advanceUntilIdle()

        assertThat(wishlist.toggleCalls).hasSize(1)
        assertThat(wishlist.toggleCalls[0].first).isEqualTo(testUserId)
        assertThat(wishlist.toggleCalls[0].second.id).isEqualTo("p-1")
    }

    @Test
    fun `OnToggleWishlist for a search-result product also calls repository`() = runTest {
        val category = FakeCategoryRepository().apply { setCategories(emptyList()) }
        val product = FakeProductRepository().apply {
            searchActiveResult = Result.success(listOf(p("search-1")))
        }
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(category = category, product = product, wishlist = wishlist)
        advanceUntilIdle()
        vm.onAction(CustomerHomeAction.OnSearchQueryChanged("red"))
        advanceTimeBy(350)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnToggleWishlist("search-1"))
        advanceUntilIdle()

        assertThat(wishlist.toggleCalls).hasSize(1)
        assertThat(wishlist.toggleCalls[0].second.id).isEqualTo("search-1")
    }

    @Test
    fun `OnToggleWishlist for an unknown product id is a no-op`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        advanceUntilIdle()

        vm.onAction(CustomerHomeAction.OnToggleWishlist("missing"))
        advanceUntilIdle()

        assertThat(wishlist.toggleCalls).isEmpty()
    }

    // --- connectivity ---

    @Test
    fun `isOnline mirrors connectivity observer`() = runTest {
        // isOnline is stateIn(WhileSubscribed) so a subscriber must exist for
        // the underlying flow to be collected.
        val vm = newViewModel(connectivity = mockConnectivity(online = false))
        val collectJob = launch { vm.isOnline.collect {} }
        advanceUntilIdle()

        assertThat(vm.isOnline.value).isFalse()
        collectJob.cancel()
    }
}
