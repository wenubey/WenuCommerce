package com.wenubey.wenucommerce.seller.seller_products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wenubey.domain.repository.ProductReviewRepository
import com.wenubey.wenucommerce.navigation.SellerProductReviews
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Read-only reviews list for ONE of the seller's own products (07-03).
 *
 * Subscribes to [ProductReviewRepository.observeReviewsForProduct] (Room-first,
 * product-scoped) and maps the emissions into [SellerProductReviewsState.reviews],
 * filtering out any review whose `isVisible == false` (defence-in-depth against
 * hidden-review disclosure — threat T-07-03-01). The screen this backs has NO
 * write/moderation controls; this ViewModel never calls submit/edit/setVisibility.
 *
 * Route args ([SellerProductReviews.productId], [productTitle]) arrive via
 * [SavedStateHandle] — the productId comes from the seller's OWN product row, so
 * there is no arbitrary-productId entry from outside the seller's products.
 */
class SellerProductReviewsViewModel(
    private val reviewRepository: ProductReviewRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: SellerProductReviews = run {
        val directId = savedStateHandle.get<String>("productId")
        val directTitle = savedStateHandle.get<String>("productTitle")
        if (directId != null) {
            SellerProductReviews(productId = directId, productTitle = directTitle.orEmpty())
        } else {
            savedStateHandle.toRoute<SellerProductReviews>()
        }
    }

    private val productId: String = route.productId

    private val _state = MutableStateFlow(
        SellerProductReviewsState(productTitle = route.productTitle),
    )
    val state: StateFlow<SellerProductReviewsState> = _state.asStateFlow()

    init {
        observeReviews()
    }

    private fun observeReviews() {
        viewModelScope.launch {
            reviewRepository.observeReviewsForProduct(productId)
                .catch { error ->
                    Timber.e(error, "SellerProductReviewsViewModel: observe failed for $productId")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Couldn't load reviews.",
                        )
                    }
                }
                .collect { reviews ->
                    _state.update {
                        it.copy(
                            reviews = reviews.filter { review -> review.isVisible },
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
        }
    }
}
