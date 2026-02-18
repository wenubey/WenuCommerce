package com.wenubey.wenucommerce.customer.customer_home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class CustomerHomeViewModel(
    private val categoryRepository: CategoryRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _homeState = MutableStateFlow(CustomerHomeState())
    val homeState: StateFlow<CustomerHomeState> = _homeState.asStateFlow()

    private var categoryListenerJob: Job? = null

    init {
        observeCategories()
    }

    private fun observeCategories() {
        categoryListenerJob?.cancel()

        categoryListenerJob = viewModelScope.launch(mainDispatcher) {
            _homeState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
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
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        categoryListenerJob?.cancel()
    }

    fun onAction(action: CustomerHomeAction) {
        when (action) {
            is CustomerHomeAction.OnCategorySelected -> onCategorySelected(action.categoryId)
        }
    }

    private fun onCategorySelected(categoryId: String) {
        viewModelScope.launch(mainDispatcher) {
            _homeState.update { it.copy(selectedCategoryId = categoryId) }
        }
    }
}
