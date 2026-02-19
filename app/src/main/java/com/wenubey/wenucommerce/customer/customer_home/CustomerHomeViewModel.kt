package com.wenubey.wenucommerce.customer.customer_home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.data.connectivity.ConnectivityObserver
import com.wenubey.data.local.SyncManager
import com.wenubey.domain.repository.CategoryRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class CustomerHomeViewModel(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val syncManager: SyncManager,
    private val connectivityObserver: ConnectivityObserver,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()

    private val _homeState = MutableStateFlow(CustomerHomeState())
    val homeState: StateFlow<CustomerHomeState> = _homeState.asStateFlow()

    /**
     * Mirrors real-time connectivity status.
     * Defaults to true to avoid false-offline flash on startup
     * (per locked decision from plan 01-03).
     */
    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var categoryListenerJob: Job? = null
    private var productsListenerJob: Job? = null
    private var searchJob: Job? = null

    private val searchQueryFlow = MutableStateFlow("")

    init {
        observeCategories()
        setupSearchDebounce()
    }

    private fun setupSearchDebounce() {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _homeState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    searchError = null,
                )
            }
            return
        }

        _homeState.update { it.copy(isSearching = true, searchError = null) }

        val currentState = _homeState.value
        productRepository.searchActiveProducts(
            query = query,
            categoryId = currentState.filterSheetCategoryId,
            subcategoryId = currentState.filterSheetSubcategoryId,
        ).fold(
            onSuccess = { results ->
                _homeState.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false,
                        searchError = null,
                    )
                }
            },
            onFailure = { error ->
                _homeState.update {
                    it.copy(
                        isSearching = false,
                        searchError = error.message ?: "Search failed",
                    )
                }
            }
        )
    }

    /**
     * Re-runs search with the current query and filter state.
     * Used by filter action handlers to immediately apply filter changes
     * without waiting for the debounce pipeline.
     */
    private fun performSearchWithCurrentFilters() {
        val currentQuery = _homeState.value.searchQuery
        if (currentQuery.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch(mainDispatcher) {
            performSearch(currentQuery)
        }
    }

    private fun observeCategories() {
        categoryListenerJob?.cancel()

        categoryListenerJob = viewModelScope.launch(mainDispatcher) {
            _homeState.update { it.copy(isLoading = true, errorMessage = null) }

            categoryRepository.observeCategories()
                .catch { error ->
                    _homeState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load categories"
                        )
                    }
                }
                .collect { categories ->
                    _homeState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            categories = categories,
                        )
                    }
                    // Auto-select first category if none selected
                    if (_homeState.value.selectedCategoryId == null && categories.isNotEmpty()) {
                        onCategorySelected(categories.first().id)
                    }
                }
        }
    }

    private fun observeProducts(categoryId: String, subcategoryId: String?) {
        productsListenerJob?.cancel()
        productsListenerJob = viewModelScope.launch(mainDispatcher) {
            _homeState.update { it.copy(isLoadingProducts = true) }

            productRepository.observeActiveProductsByCategoryAndSubcategory(categoryId, subcategoryId)
                .catch { error ->
                    _homeState.update {
                        it.copy(
                            isLoadingProducts = false,
                            errorMessage = error.message ?: "Failed to load products"
                        )
                    }
                }
                .collect { products ->
                    _homeState.update {
                        it.copy(
                            isLoadingProducts = false,
                            products = products,
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        categoryListenerJob?.cancel()
        productsListenerJob?.cancel()
        searchJob?.cancel()
    }

    fun onAction(action: CustomerHomeAction) {
        when (action) {
            is CustomerHomeAction.OnCategorySelected -> onCategorySelected(action.categoryId)
            is CustomerHomeAction.OnSubcategorySelected -> onSubcategorySelected(action.subcategoryId)
            is CustomerHomeAction.OnSearchQueryChanged -> onSearchQueryChanged(action.query)
            is CustomerHomeAction.OnClearSearch -> onClearSearch()
            is CustomerHomeAction.OnSearchFilterCategorySelected -> {
                _homeState.update {
                    it.copy(
                        filterSheetCategoryId = action.categoryId,
                        filterSheetSubcategoryId = null,
                    )
                }
                performSearchWithCurrentFilters()
            }
            is CustomerHomeAction.OnSearchFilterSubcategorySelected -> {
                _homeState.update { it.copy(filterSheetSubcategoryId = action.subcategoryId) }
                performSearchWithCurrentFilters()
            }
            is CustomerHomeAction.OnClearSearchFilters -> {
                _homeState.update {
                    it.copy(
                        filterSheetCategoryId = null,
                        filterSheetSubcategoryId = null,
                    )
                }
                performSearchWithCurrentFilters()
            }
            is CustomerHomeAction.OnPullToRefresh -> onPullToRefresh()
        }
    }

    /**
     * Triggers a manual sync via SyncManager, which performs a one-shot Firestore fetch
     * and upserts results into Room.  Used by both pull-to-refresh and the EmptyNetworkState
     * retry button.
     * Per locked decisions: "Pull-to-refresh triggers SyncManager.manualSync()" and
     * "First launch empty state retry button triggers SyncManager.manualSync()".
     */
    private fun onPullToRefresh() {
        viewModelScope.launch {
            _homeState.update { it.copy(isRefreshing = true) }
            try {
                syncManager.manualSync()
            } finally {
                _homeState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun onSearchQueryChanged(query: String) {
        _homeState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        searchQueryFlow.value = query
    }

    private fun onClearSearch() {
        _homeState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                searchError = null,
            )
        }
        searchQueryFlow.value = ""
    }

    private fun onCategorySelected(categoryId: String) {
        _homeState.update { it.copy(selectedCategoryId = categoryId, selectedSubcategoryId = null) }
        observeProducts(categoryId, subcategoryId = null)
    }

    private fun onSubcategorySelected(subcategoryId: String?) {
        val categoryId = _homeState.value.selectedCategoryId ?: return
        _homeState.update { it.copy(selectedSubcategoryId = subcategoryId) }
        observeProducts(categoryId, subcategoryId)
    }
}
