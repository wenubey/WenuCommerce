package com.wenubey.wenucommerce.seller.seller_products

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.product.ProductReview
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.fakes.FakeProductReviewRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerProductReviewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val productId = "p-1"
    private val productTitle = "Blue Shirt"

    private fun savedState() = SavedStateHandle(
        mapOf("productId" to productId, "productTitle" to productTitle),
    )

    private fun newViewModel(
        repo: FakeProductReviewRepository = FakeProductReviewRepository(),
    ) = SellerProductReviewsViewModel(repo, savedState()) to repo

    private fun review(
        id: String,
        rating: Int = 5,
        isVisible: Boolean = true,
        createdAt: String = "1000",
    ) = ProductReview(
        id = id,
        productId = productId,
        reviewerName = "Alice",
        rating = rating,
        title = "Nice",
        body = "Great product",
        isVerifiedPurchase = true,
        isVisible = isVisible,
        createdAt = createdAt,
    )

    @Test
    fun `init subscribes to observeReviewsForProduct and maps emissions into state`() = runTest {
        val (vm, repo) = newViewModel()
        advanceUntilIdle()

        repo.emit(productId, listOf(review("r-1"), review("r-2")))
        advanceUntilIdle()

        assertThat(vm.state.value.reviews.map { it.id }).containsExactly("r-1", "r-2")
        // Title threaded from the route args.
        assertThat(vm.state.value.productTitle).isEqualTo(productTitle)
    }

    @Test
    fun `reviews with isVisible false are filtered out of state`() = runTest {
        val (vm, repo) = newViewModel()
        advanceUntilIdle()

        repo.emit(
            productId,
            listOf(
                review("visible-1", isVisible = true),
                review("hidden-1", isVisible = false),
                review("visible-2", isVisible = true),
            ),
        )
        advanceUntilIdle()

        assertThat(vm.state.value.reviews.map { it.id })
            .containsExactly("visible-1", "visible-2")
    }

    @Test
    fun `state transitions from loading to loaded on first emission`() = runTest {
        val (vm, repo) = newViewModel()

        vm.state.test {
            // Initial state: loading, before any emission.
            assertThat(awaitItem().isLoading).isTrue()

            repo.emit(productId, listOf(review("r-1")))
            advanceUntilIdle()

            val loaded = awaitItem()
            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.reviews).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty emission yields empty reviews and not loading`() = runTest {
        val (vm, repo) = newViewModel()
        advanceUntilIdle()

        repo.emit(productId, emptyList())
        advanceUntilIdle()

        assertThat(vm.state.value.reviews).isEmpty()
        assertThat(vm.state.value.isLoading).isFalse()
    }
}
