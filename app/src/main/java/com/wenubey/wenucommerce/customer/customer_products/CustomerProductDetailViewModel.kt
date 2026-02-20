package com.wenubey.wenucommerce.customer.customer_products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.CartRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductRepository
import com.wenubey.domain.repository.ProductReviewRepository
import com.wenubey.domain.repository.WishlistRepository
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
    private val cartRepository: CartRepository,
    private val authRepository: AuthRepository,
    private val wishlistRepository: WishlistRepository,
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
        observeWishlistState(productId)
    }

    private fun observeWishlistState(productId: String) {
        val userId = authRepository.currentUser.value?.uuid ?: ""
        viewModelScope.launch(ioDispatcher) {
            wishlistRepository.isWishlisted(userId, productId)
                .catch { error ->
                    Timber.e(error, "CustomerProductDetailViewModel: failed to observe wishlist state")
                }
                .collect { wishlisted ->
                    _state.update { it.copy(isWishlisted = wishlisted) }
                }
        }
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
                        // Check if product is already in cart
                        checkCartStatus(id)
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

    private suspend fun checkCartStatus(productId: String) {
        val userId = authRepository.currentUser.value?.uuid ?: return
        val cartItem = cartRepository.getCartItem(userId, productId)
        if (cartItem != null) {
            _state.update {
                it.copy(
                    isInCart = true,
                    cartQuantity = cartItem.quantity,
                    selectedQuantity = cartItem.quantity,
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
            is CustomerProductDetailAction.SetQuantity -> setQuantity(action.quantity)
            is CustomerProductDetailAction.AddToCart -> addToCart()
            is CustomerProductDetailAction.UpdateCartQuantity -> updateCartQuantity(action.newQuantity)
            is CustomerProductDetailAction.DismissLoginPrompt ->
                _state.update { it.copy(showLoginPrompt = false) }
            is CustomerProductDetailAction.DismissCartMessage ->
                _state.update { it.copy(cartMessage = null) }
            is CustomerProductDetailAction.ToggleWishlist -> toggleWishlist()
        }
    }

    private fun toggleWishlist() {
        val product = _state.value.product ?: return
        val userId = authRepository.currentUser.value?.uuid
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                wishlistRepository.toggleWishlist(userId, product)
            }.onFailure { error ->
                Timber.e(error, "CustomerProductDetailViewModel: failed to toggle wishlist for ${product.id}")
            }
        }
    }

    private fun setQuantity(quantity: Int) {
        val maxStock = _state.value.product?.totalStockQuantity ?: 1
        val clamped = quantity.coerceIn(1, maxStock.coerceAtLeast(1))
        _state.update { it.copy(selectedQuantity = clamped) }
    }

    private fun addToCart() {
        val userId = authRepository.currentUser.value?.uuid
        if (userId == null) {
            _state.update { it.copy(showLoginPrompt = true) }
            return
        }

        val product = _state.value.product ?: return
        if (product.totalStockQuantity <= 0) return

        _state.update { it.copy(isAddingToCart = true) }
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                cartRepository.addToCart(userId, product, _state.value.selectedQuantity)
            }.onSuccess {
                val newCartItem = cartRepository.getCartItem(userId, product.id)
                _state.update {
                    it.copy(
                        isAddingToCart = false,
                        isInCart = true,
                        cartQuantity = newCartItem?.quantity ?: it.selectedQuantity,
                        cartMessage = "Added to cart",
                    )
                }
            }.onFailure { error ->
                Timber.e(error, "CustomerProductDetailViewModel: failed to add to cart")
                _state.update {
                    it.copy(
                        isAddingToCart = false,
                        cartMessage = "Failed to add to cart",
                    )
                }
            }
        }
    }

    private fun updateCartQuantity(newQuantity: Int) {
        val user = authRepository.currentUser.value
        val userId = user?.uuid ?: return
        val productId = _state.value.product?.id ?: return

        viewModelScope.launch(ioDispatcher) {
            runCatching {
                cartRepository.updateQuantity(userId, productId, newQuantity)
            }.onSuccess {
                _state.update {
                    it.copy(
                        cartQuantity = newQuantity,
                        selectedQuantity = newQuantity,
                        isInCart = newQuantity > 0,
                    )
                }
            }.onFailure { error ->
                Timber.e(error, "CustomerProductDetailViewModel: failed to update cart quantity")
            }
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
