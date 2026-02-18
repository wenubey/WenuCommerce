package com.wenubey.wenucommerce.admin.admin_categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.domain.repository.CategoryRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AdminCategoryViewModel(
    private val categoryRepository: CategoryRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _categoryState = MutableStateFlow(AdminCategoryState())
    val categoryState: StateFlow<AdminCategoryState> = _categoryState.asStateFlow()

    private var categoryListenerJob: Job? = null

    init {
        observeCategories()
    }

    private fun observeCategories() {
        categoryListenerJob?.cancel()

        categoryListenerJob = viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                categoryRepository.observeCategories()
                    .catch { error ->
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load categories"
                            )
                        }
                    }
                    .collect { categories ->
                        _categoryState.update { current ->
                            val refreshedSelected = current.selectedCategory?.let { selected ->
                                categories.find { it.id == selected.id }
                            }
                            current.copy(
                                isLoading = false,
                                errorMessage = null,
                                categories = categories,
                                selectedCategory = refreshedSelected,
                            )
                        }
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        categoryListenerJob?.cancel()
    }

    fun onAction(action: AdminCategoryAction) {
        when (action) {
            is AdminCategoryAction.OnCreateCategory -> createCategory(action.name, action.description, action.imageUri)
            is AdminCategoryAction.OnEditCategory -> updateCategory(action.category, action.newImageUri)
            is AdminCategoryAction.OnDeleteCategory -> deleteCategory(action.categoryId)
            is AdminCategoryAction.OnAddSubcategory -> addSubcategory(action.categoryId, action.subcategory)
            is AdminCategoryAction.OnCategorySelected -> onCategorySelected(action.category)
            is AdminCategoryAction.OnShowCreateDialog -> showCreateDialog()
            is AdminCategoryAction.OnDismissDialog -> dismissDialog()
        }
    }

    private fun createCategory(name: String, description: String, imageUri: String) {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                val categoryId = UUID.randomUUID().toString()

                // Upload image first if provided
                val imageUrl = if (imageUri.isNotBlank()) {
                    val uploadResult = categoryRepository.uploadCategoryImage(imageUri, categoryId)
                    uploadResult.getOrElse { error ->
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Image upload failed: ${error.message}"
                            )
                        }
                        return@withContext
                    }
                } else ""

                val category = Category(
                    id = categoryId,
                    name = name,
                    description = description,
                    imageUrl = imageUrl,
                )
                val result = categoryRepository.createCategory(category)

                result.fold(
                    onSuccess = {
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                showCreateDialog = false,
                                errorMessage = null,
                            )
                        }
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

    private fun updateCategory(category: Category, newImageUri: String?) {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                // Upload new image if provided
                val imageUrl = if (!newImageUri.isNullOrBlank()) {
                    val uploadResult = categoryRepository.uploadCategoryImage(newImageUri, category.id)
                    uploadResult.getOrElse { error ->
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Image upload failed: ${error.message}"
                            )
                        }
                        return@withContext
                    }
                } else category.imageUrl

                val updatedCategory = category.copy(imageUrl = imageUrl)
                val result = categoryRepository.updateCategory(updatedCategory)

                result.fold(
                    onSuccess = {
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                showEditDialog = false,
                                selectedCategory = null,
                                errorMessage = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to update category"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun deleteCategory(categoryId: String) {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                val result = categoryRepository.deleteCategory(categoryId)

                result.fold(
                    onSuccess = {
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        _categoryState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to delete category"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun addSubcategory(categoryId: String, subcategory: Subcategory) {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(errorMessage = null) }

            withContext(ioDispatcher) {
                val result = categoryRepository.addSubcategory(categoryId, subcategory)

                result.fold(
                    onSuccess = {
                        _categoryState.update { current ->
                            val updatedSelected = current.selectedCategory?.let { selected ->
                                if (selected.id == categoryId) {
                                    selected.copy(subcategories = selected.subcategories + subcategory)
                                } else selected
                            }
                            current.copy(
                                errorMessage = null,
                                selectedCategory = updatedSelected,
                            )
                        }
                    },
                    onFailure = { error ->
                        _categoryState.update {
                            it.copy(
                                errorMessage = error.message ?: "Failed to add subcategory"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun onCategorySelected(category: Category) {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update {
                it.copy(
                    selectedCategory = category,
                    showEditDialog = true,
                )
            }
        }
    }

    private fun showCreateDialog() {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update { it.copy(showCreateDialog = true) }
        }
    }

    private fun dismissDialog() {
        viewModelScope.launch(mainDispatcher) {
            _categoryState.update {
                it.copy(
                    showCreateDialog = false,
                    showEditDialog = false,
                    selectedCategory = null,
                )
            }
        }
    }
}
