package com.wenubey.wenucommerce.customer.customer_products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductRepository
import com.wenubey.domain.repository.ProductReviewRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class CustomerProductDetailViewModel(
    private val productRepository: ProductRepository,
    private val reviewRepository: ProductReviewRepository,
    private val savedStateHandle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _state = MutableStateFlow(CustomerProductDetailState())
    val state: StateFlow<CustomerProductDetailState> = _state.asStateFlow()

    private var reviewsJob: Job? = null

    private val productId: String = checkNotNull(savedStateHandle["productId"]) {
        "CustomerProductDetailViewModel requires a productId in SavedStateHandle"
    }

    init {
        loadProduct(productId)
        observeReviews(productId)
        incrementViewCount(productId)
    }

    private fun loadProduct(id: String) {
        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isLoading = true) }
            withContext(ioDispatcher) {
                productRepository.getProductById(id).fold(
                    onSuccess = { product ->
                        val defaultVariant = product.variants.firstOrNull { it.isDefault }
                            ?: product.variants.firstOrNull()
                        _state.update {
                            it.copy(
                                product = product,
                                selectedVariant = defaultVariant,
                                isLoading = false,
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load product"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun observeReviews(productId: String) {
        reviewsJob?.cancel()
        reviewsJob = viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isLoadingReviews = true) }
            withContext(ioDispatcher) {
                reviewRepository.observeReviewsForProduct(productId)
                    .catch { error ->
                        _state.update {
                            it.copy(isLoadingReviews = false)
                        }
                        Timber.e(error, "Failed to load reviews")
                    }
                    .collect { reviews ->
                        _state.update {
                            it.copy(
                                reviews = reviews,
                                isLoadingReviews = false,
                            )
                        }
                    }
            }
        }
    }

    private fun incrementViewCount(productId: String) {
        viewModelScope.launch(ioDispatcher) {
            productRepository.incrementViewCount(productId)
        }
    }

    fun onAction(action: CustomerProductDetailAction) {
        when (action) {
            is CustomerProductDetailAction.OnVariantSelected ->
                _state.update { it.copy(selectedVariant = action.variant) }
            is CustomerProductDetailAction.OnMarkReviewHelpful -> markReviewHelpful(action.reviewId)
        }
    }

    private fun markReviewHelpful(reviewId: String) {
        val productId = _state.value.product?.id ?: return
        viewModelScope.launch(ioDispatcher) {
            reviewRepository.markReviewHelpful(productId, reviewId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        reviewsJob?.cancel()
    }
}
