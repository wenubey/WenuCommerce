package com.wenubey.wenucommerce.customer.customer_products

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductReview
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class CustomerProductDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val productId = "p-1"

    private fun renderScreen(
        state: CustomerProductDetailState = CustomerProductDetailState(isLoading = false),
    ): CustomerProductDetailViewModel {
        val vm: CustomerProductDetailViewModel = mockk(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        composeTestRule.setContent {
            CustomerProductDetailScreen(viewModel = vm)
        }
        return vm
    }

    private fun product(averageRating: Double = 0.0, reviewCount: Int = 0) =
        Product(
            id = productId,
            title = "Shirt",
            totalStockQuantity = 5,
            averageRating = averageRating,
            reviewCount = reviewCount,
        )

    private fun review(
        id: String,
        rating: Int = 5,
        isVerifiedPurchase: Boolean = true,
        createdAt: String = "1000",
    ) = ProductReview(
        id = id,
        productId = productId,
        reviewerName = "Alice",
        rating = rating,
        title = "Nice",
        body = "Great product",
        isVerifiedPurchase = isVerifiedPurchase,
        createdAt = createdAt,
    )

    @Test
    fun renders_not_found_when_product_is_null_and_no_error() {
        renderScreen(state = CustomerProductDetailState(isLoading = false, product = null))

        composeTestRule.onNodeWithText("Product not found").assertIsDisplayed()
    }

    @Test
    fun renders_error_message_when_load_fails() {
        renderScreen(
            state = CustomerProductDetailState(
                isLoading = false,
                product = null,
                errorMessage = "Fetch failed",
            ),
        )

        composeTestRule.onNodeWithText("Fetch failed").assertIsDisplayed()
    }

    @Test
    fun login_prompt_dialog_renders_when_flag_is_set() {
        renderScreen(state = CustomerProductDetailState(showLoginPrompt = true))

        composeTestRule.onNodeWithText("Sign in required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please sign in to add items to your cart.").assertIsDisplayed()
    }

    // --- REVW-05: Verified Purchase badge ---

    @Test
    fun verifiedReview_showsVerifiedPurchaseBadge() {
        renderScreen(
            CustomerProductDetailState(
                product = product(averageRating = 5.0, reviewCount = 1),
                reviews = listOf(review("r-1", isVerifiedPurchase = true)),
            )
        )
        composeTestRule.onNodeWithText("Verified Purchase").assertIsDisplayed()
    }

    @Test
    fun unverifiedReview_doesNotShowVerifiedPurchaseBadge() {
        renderScreen(
            CustomerProductDetailState(
                product = product(averageRating = 4.0, reviewCount = 1),
                reviews = listOf(review("r-1", isVerifiedPurchase = false)),
            )
        )
        composeTestRule.onAllNodes(hasText("Verified Purchase")).assertCountEquals(0)
    }

    // --- D-07: gated write/edit affordance ---

    @Test
    fun noDeliveredOrder_hidesWriteReviewAffordance() {
        renderScreen(
            CustomerProductDetailState(
                product = product(),
                hasDeliveredOrder = false,
            )
        )
        composeTestRule.onAllNodes(hasText("Write a Review")).assertCountEquals(0)
        composeTestRule.onAllNodes(hasText("Edit Your Review")).assertCountEquals(0)
    }

    @Test
    fun deliveredOrderNoReview_showsWriteAReview() {
        renderScreen(
            CustomerProductDetailState(
                product = product(),
                hasDeliveredOrder = true,
                existingReview = null,
            )
        )
        composeTestRule.onNodeWithText("Write a Review").assertIsDisplayed()
    }

    @Test
    fun deliveredOrderWithReview_showsEditYourReview() {
        renderScreen(
            CustomerProductDetailState(
                product = product(),
                hasDeliveredOrder = true,
                existingReview = review("r-existing"),
            )
        )
        composeTestRule.onNodeWithText("Edit Your Review").assertIsDisplayed()
    }

    // --- REVW-04: aggregate header ---

    @Test
    fun withReviews_showsAggregateAverageAndCount() {
        renderScreen(
            CustomerProductDetailState(
                product = product(averageRating = 4.3, reviewCount = 12),
                reviews = listOf(review("r-1")),
            )
        )
        composeTestRule.onNodeWithText("4.3").assertIsDisplayed()
        composeTestRule.onNodeWithText("12 reviews").assertIsDisplayed()
    }

    @Test
    fun noReviews_showsNoRatingsAndEmptyStateCopy() {
        renderScreen(
            CustomerProductDetailState(
                product = product(averageRating = 0.0, reviewCount = 0),
                reviews = emptyList(),
            )
        )
        composeTestRule.onNodeWithText("No ratings yet").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("No reviews yet. Be the first to share your experience.")
            .assertIsDisplayed()
    }

    // --- REVW-06: sort segments ---

    @Test
    fun withReviews_showsBothSortSegments() {
        renderScreen(
            CustomerProductDetailState(
                product = product(averageRating = 5.0, reviewCount = 2),
                reviews = listOf(review("r-1"), review("r-2")),
                reviewSortOrder = ReviewSortOrder.MOST_RECENT,
            )
        )
        composeTestRule.onNodeWithText("Most recent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Highest rated").assertIsDisplayed()
    }
}
