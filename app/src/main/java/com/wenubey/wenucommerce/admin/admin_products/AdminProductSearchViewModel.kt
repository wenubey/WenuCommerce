package com.wenubey.wenucommerce.admin.admin_products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.repository.CategoryRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(FlowPreview::class)
class AdminProductSearchViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminProductSearchState())
    val state: StateFlow<AdminProductSearchState> = _state.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    fun onAction(action: AdminProductSearchAction) {
        when (action) {
            is AdminProductSearchAction.OnSearchQueryChanged -> onSearchQueryChanged(action.query)
            is AdminProductSearchAction.OnStatusFilterChanged -> onStatusFilterChanged(action.status)
            is AdminProductSearchAction.OnClearSearch -> onClearSearch()
            is AdminProductSearchAction.OnProductSelected -> onProductSelected(action.product)
            is AdminProductSearchAction.OnDismissDetailDialog -> onDismissDetailDialog()
            is AdminProductSearchAction.OnFilterCategorySelected -> {
                _state.update {
                    it.copy(
                        filterCategoryId = action.categoryId,
                        filterSubcategoryId = null,
                    )
                }
                performSearchWithCurrentFilters()
            }
            is AdminProductSearchAction.OnFilterSubcategorySelected -> {
                _state.update { it.copy(filterSubcategoryId = action.subcategoryId) }
                performSearchWithCurrentFilters()
            }
            is AdminProductSearchAction.OnClearCategoryFilters -> {
                _state.update {
                    it.copy(
                        filterCategoryId = null,
                        filterSubcategoryId = null,
                    )
                }
                performSearchWithCurrentFilters()
            }
            is AdminProductSearchAction.OnRequestCategoryLoad -> loadCategoriesIfNeeded()
        }
    }

    private fun loadCategoriesIfNeeded() {
        val current = _state.value
        if (current.categories.isNotEmpty() || current.isLoadingCategories) return

        _state.update { it.copy(isLoadingCategories = true) }
        viewModelScope.launch {
            categoryRepository.getCategories().fold(
                onSuccess = { categories ->
                    _state.update {
                        it.copy(
                            categories = categories,
                            isLoadingCategories = false,
                        )
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to load categories for admin filter")
                    _state.update { it.copy(isLoadingCategories = false) }
                }
            )
        }
    }

    /**
     * Re-runs search with the current query and category filter state.
     * Used by filter action handlers to immediately apply filter changes.
     */
    private fun performSearchWithCurrentFilters() {
        val currentQuery = _state.value.searchQuery
        if (currentQuery.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(currentQuery)
        }
    }

    private fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        searchQueryFlow.value = query
    }

    private fun onStatusFilterChanged(status: ProductStatus?) {
        _state.update { it.copy(statusFilter = status) }
        applyStatusFilter()
    }

    private fun onClearSearch() {
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                filteredResults = emptyList(),
                isSearching = false,
                errorMessage = null,
            )
        }
        searchQueryFlow.value = ""
    }

    private fun onProductSelected(product: Product) {
        _state.update { it.copy(selectedProduct = product, showDetailDialog = true) }
    }

    private fun onDismissDetailDialog() {
        _state.update { it.copy(selectedProduct = null, showDetailDialog = false) }
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _state.update {
                it.copy(
                    searchResults = emptyList(),
                    filteredResults = emptyList(),
                    isSearching = false,
                    errorMessage = null,
                )
            }
            return
        }

        _state.update { it.copy(isSearching = true, errorMessage = null) }

        val currentState = _state.value
        productRepository.searchAllProducts(
            query = query,
            categoryId = currentState.filterCategoryId,
            subcategoryId = currentState.filterSubcategoryId,
        ).fold(
            onSuccess = { results ->
                _state.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false,
                        errorMessage = null,
                    )
                }
                applyStatusFilter()
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = error.message ?: "Search failed",
                    )
                }
            }
        )
    }

    private fun applyStatusFilter() {
        val current = _state.value
        val filtered = if (current.statusFilter == null) {
            current.searchResults
        } else {
            current.searchResults.filter { it.status == current.statusFilter }
        }
        _state.update { it.copy(filteredResults = filtered) }
    }
}
