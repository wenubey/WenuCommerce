package com.wenubey.wenucommerce.seller.seller_categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.domain.repository.CategoryRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SellerCategoryViewModel(
    private val categoryRepository: CategoryRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _categoryState = MutableStateFlow(SellerCategoryState())
    val categoryState: StateFlow<SellerCategoryState> = _categoryState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                val result = categoryRepository.getCategories()

                result.fold(
                    onSuccess = { categories ->
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                categories = categories,
                                errorMessage = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load categories"
                            )
                        }
                    }
                )
            }
        }
    }

    fun onAction(action: SellerCategoryAction) {
        when (action) {
            is SellerCategoryAction.OnCategorySelected -> onCategorySelected(action.category)
            is SellerCategoryAction.OnSubcategorySelected -> onSubcategorySelected(action.subcategory)
            is SellerCategoryAction.OnCreateNewCategory -> createCategory(action.name, action.description)
            is SellerCategoryAction.OnCreateNewSubcategory -> createSubcategory(action.categoryId, action.subcategory)
            is SellerCategoryAction.OnShowCreateCategoryDialog -> showCreateCategoryDialog()
            is SellerCategoryAction.OnShowCreateSubcategoryDialog -> showCreateSubcategoryDialog()
            is SellerCategoryAction.OnDismissDialog -> dismissDialog()
            is SellerCategoryAction.OnRefresh -> loadCategories()
        }
    }

    private fun onCategorySelected(category: Category) {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update {
                it.copy(
                    selectedCategory = category,
                    selectedSubcategory = null,
                )
            }
        }
    }

    private fun onSubcategorySelected(subcategory: Subcategory) {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(selectedSubcategory = subcategory) }
        }
    }

    private fun createCategory(name: String, description: String) {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                val category = Category(name = name, description = description)
                val result = categoryRepository.createCategory(category)

                result.fold(
                    onSuccess = {
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                showCreateCategoryDialog = false,
                                errorMessage = null,
                            )
                        }
                        loadCategories()
                    },
                    onFailure = { error ->
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to create category"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun createSubcategory(categoryId: String, subcategory: Subcategory) {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                val result = categoryRepository.addSubcategory(categoryId, subcategory)

                result.fold(
                    onSuccess = {
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                showCreateSubcategoryDialog = false,
                                errorMessage = null,
                            )
                        }
                        loadCategories()
                    },
                    onFailure = { error ->
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to add subcategory"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun showCreateCategoryDialog() {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(showCreateCategoryDialog = true) }
        }
    }

    private fun showCreateSubcategoryDialog() {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(showCreateSubcategoryDialog = true) }
        }
    }

    private fun dismissDialog() {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update {
                it.copy(
                    showCreateCategoryDialog = false,
                    showCreateSubcategoryDialog = false,
                )
            }
        }
    }
}
