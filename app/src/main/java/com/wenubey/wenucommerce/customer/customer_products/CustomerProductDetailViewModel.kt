package com.wenubey.wenucommerce.customer.customer_products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.data.local.dao.SellerOrderDao
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.domain.model.product.ProductReview
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
import kotlinx.serialization.json.Json
import timber.log.Timber


class CustomerProductDetailViewModel(
    private val productRepository: ProductRepository,
    private val reviewRepository: ProductReviewRepository,
    private val cartRepository: CartRepository,
    private val authRepository: AuthRepository,
    private val wishlistRepository: WishlistRepository,
    private val sellerOrderDao: SellerOrderDao,
    private val savedStateHandle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val itemsJson = Json { ignoreUnknownKeys = true }

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
        checkDeliveredOrderStatus(productId)
        loadExistingReview(productId)
    }

    /**
     * D-07: sets [CustomerProductDetailState.hasDeliveredOrder] true iff the
     * signed-in customer has a DELIVERED seller order that contains this product.
     * This is a UX affordance only; the submitReview Cloud Function is the
     * security boundary (threat T-07-08).
     */
    private fun checkDeliveredOrderStatus(productId: String) {
        val userId = authRepository.currentUser.value?.uuid ?: return
        _state.update { it.copy(isCheckingEligibility = true) }
        viewModelScope.launch(ioDispatcher) {
            val hasDelivered = runCatching {
                val deliveredOrders = sellerOrderDao.getByUserAndStatus(userId, "DELIVERED")
                deliveredOrders.any { entity ->
                    runCatching {
                        itemsJson.decodeFromString<List<OrderItem>>(entity.itemsJson)
                            .any { it.productId == productId }
                    }.getOrDefault(false)
                }
            }.getOrDefault(false)
            _state.update {
                it.copy(hasDeliveredOrder = hasDelivered, isCheckingEligibility = false)
            }
        }
    }

    /** D-05: pre-fills the existing review (if any) so edit replaces in place. */
    private fun loadExistingReview(productId: String) {
        viewModelScope.launch(ioDispatcher) {
            val existing = reviewRepository.getMyReviewForProduct(productId).getOrNull()
            if (existing != null) {
                _state.update { it.copy(existingReview = existing) }
            }
        }
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
                                reviews = sortedReviews(reviews, it.reviewSortOrder),
                                isLoadingReviews = false,
                            )
                        }
                    }
            }
        }
    }

    /**
     * Pure sort helper (REVW-06). MOST_RECENT orders by createdAt descending;
     * HIGHEST_RATED orders by rating descending, ties broken by recency
     * (createdAt descending). createdAt is an epoch-millis string, sortable
     * lexicographically for equal-length values.
     */
    private fun sortedReviews(
        reviews: List<ProductReview>,
        order: ReviewSortOrder,
    ): List<ProductReview> = when (order) {
        ReviewSortOrder.MOST_RECENT -> reviews.sortedByDescending { it.createdAt }
        ReviewSortOrder.HIGHEST_RATED -> reviews.sortedWith(
            compareByDescending<ProductReview> { it.rating }
                .thenByDescending { it.createdAt }
        )
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
            is CustomerProductDetailAction.OpenReviewForm ->
                _state.update { it.copy(showReviewForm = true) }
            is CustomerProductDetailAction.DismissReviewForm ->
                _state.update { it.copy(showReviewForm = false, reviewSubmitError = null) }
            is CustomerProductDetailAction.SubmitReview ->
                submitReview(action.rating, action.title, action.body)
            is CustomerProductDetailAction.OnSortOrderChanged -> onSortOrderChanged(action.order)
        }
    }

    private fun onSortOrderChanged(order: ReviewSortOrder) {
        _state.update {
            it.copy(
                reviewSortOrder = order,
                reviews = sortedReviews(it.reviews, order),
            )
        }
    }

    private fun submitReview(rating: Int, title: String, body: String) {
        val product = _state.value.product ?: return
        val user = authRepository.currentUser.value ?: return
        val isEdit = _state.value.existingReview != null
        _state.update { it.copy(isSubmittingReview = true, reviewSubmitError = null) }
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                reviewRepository.submitReview(
                    ProductReview(
                        productId = product.id,
                        reviewerId = user.uuid ?: "",
                        reviewerName = "${user.name} ${user.surname}".trim(),
                        reviewerPhotoUrl = user.profilePhotoUri,
                        rating = rating,
                        title = title,
                        body = body,
                    )
                ).getOrThrow()
            }.onSuccess {
                _state.update {
                    it.copy(
                        isSubmittingReview = false,
                        showReviewForm = false,
                        cartMessage = if (isEdit) "Review updated" else "Review submitted",
                    )
                }
            }.onFailure { error ->
                Timber.e(error, "CustomerProductDetailViewModel: submitReview failed")
                _state.update {
                    it.copy(
                        isSubmittingReview = false,
                        reviewSubmitError = error.message
                            ?: "Couldn't submit review. Please try again.",
                    )
                }
            }
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
        // D-04: optimistic disable — record the vote immediately so the UI can
        // disable the Helpful action before the server round-trip completes.
        if (reviewId in _state.value.helpfulVotedIds) return
        _state.update { it.copy(helpfulVotedIds = it.helpfulVotedIds + reviewId) }
        viewModelScope.launch(ioDispatcher) {
            // Repeat votes are guarded server-side (already-exists); ignore failures.
            reviewRepository.markReviewHelpful(productId, reviewId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        reviewsJob?.cancel()
    }
}
