package com.wenubey.wenucommerce.seller.seller_products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.wenubey.domain.repository.CategoryRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SellerProductListViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val auth: FirebaseAuth,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _state = MutableStateFlow(SellerProductListState())
    val state: StateFlow<SellerProductListState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeProducts()
    }

    private fun observeProducts() {
        val sellerId = auth.currentUser?.uid ?: return
        observeJob?.cancel()
        observeJob = viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isLoading = true) }
            productRepository.observeSellerProducts(sellerId).collect { products ->
                _state.update {
                    it.copy(
                        products = products,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                applyFilters()
            }
        }
    }

    fun onAction(action: SellerProductListAction) {
        when (action) {
            is SellerProductListAction.OnSearchQueryChanged -> {
                _state.update { it.copy(searchQuery = action.query) }
                applyFilters()
            }
            is SellerProductListAction.OnStatusFilterSelected -> {
                _state.update { it.copy(selectedStatusFilter = action.status) }
                applyFilters()
            }
            is SellerProductListAction.OnSubmitForReview -> submitForReview(action.productId)
            is SellerProductListAction.OnUnarchiveProduct -> unarchiveProduct(action.productId)
            is SellerProductListAction.OnShowDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = true, productToDelete = action.product) }
            }
            is SellerProductListAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, productToDelete = null) }
            }
            is SellerProductListAction.OnConfirmDelete -> confirmDelete()
            is SellerProductListAction.OnFilterCategorySelected -> {
                _state.update {
                    it.copy(
                        filterCategoryId = action.categoryId,
                        filterSubcategoryId = null,
                    )
                }
                applyFilters()
            }
            is SellerProductListAction.OnFilterSubcategorySelected -> {
                _state.update { it.copy(filterSubcategoryId = action.subcategoryId) }
                applyFilters()
            }
            is SellerProductListAction.OnClearCategoryFilters -> {
                _state.update {
                    it.copy(
                        filterCategoryId = null,
                        filterSubcategoryId = null,
                    )
                }
                applyFilters()
            }
            is SellerProductListAction.OnRequestCategoryLoad -> loadCategoriesIfNeeded()
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
                    Timber.e(error, "Failed to load categories for seller filter")
                    _state.update { it.copy(isLoadingCategories = false) }
                }
            )
        }
    }

    private fun applyFilters() {
        val current = _state.value
        val filtered = current.products.filter { product ->
            val matchesSearch = current.searchQuery.isBlank() || run {
                val query = current.searchQuery.trim().lowercase()
                product.title.contains(query, ignoreCase = true) ||
                    product.categoryName.contains(query, ignoreCase = true) ||
                    product.subcategoryName.contains(query, ignoreCase = true) ||
                    product.tagNames.any { it.contains(query, ignoreCase = true) }
            }
            val matchesStatus = current.selectedStatusFilter == null ||
                    product.status == current.selectedStatusFilter
            val matchesFilterCategory = current.filterCategoryId == null ||
                    product.categoryId == current.filterCategoryId
            val matchesFilterSubcategory = current.filterSubcategoryId == null ||
                    product.subcategoryId == current.filterSubcategoryId
            matchesSearch && matchesStatus && matchesFilterCategory && matchesFilterSubcategory
        }
        _state.update { it.copy(filteredProducts = filtered) }
    }

    private fun submitForReview(productId: String) {
        viewModelScope.launch(mainDispatcher) {
            withContext(ioDispatcher) {
                productRepository.submitForReview(productId).fold(
                    onSuccess = { Timber.d("Product submitted for review: $productId") },
                    onFailure = { error ->
                        _state.update {
                            it.copy(errorMessage = error.message ?: "Failed to submit for review")
                        }
                    }
                )
            }
        }
    }

    private fun archiveProduct(productId: String) {
        viewModelScope.launch(mainDispatcher) {
            withContext(ioDispatcher) {
                productRepository.archiveProduct(productId).fold(
                    onSuccess = { Timber.d("Product archived: $productId") },
                    onFailure = { error ->
                        _state.update {
                            it.copy(errorMessage = error.message ?: "Failed to archive product")
                        }
                    }
                )
            }
        }
    }

    private fun unarchiveProduct(productId: String) {
        viewModelScope.launch(mainDispatcher) {
            withContext(ioDispatcher) {
                productRepository.unarchiveProduct(productId).fold(
                    onSuccess = { Timber.d("Product unarchived: $productId") },
                    onFailure = { error ->
                        _state.update {
                            it.copy(errorMessage = error.message ?: "Failed to unarchive product")
                        }
                    }
                )
            }
        }
    }

    private fun confirmDelete() {
        val product = _state.value.productToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, productToDelete = null) }
        archiveProduct(product.id)
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
    }
}
