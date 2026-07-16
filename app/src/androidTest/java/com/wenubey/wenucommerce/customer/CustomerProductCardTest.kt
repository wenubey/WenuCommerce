package com.wenubey.wenucommerce.customer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.wenubey.domain.model.product.Product
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the REVW-07 rating-count surface on [CustomerProductCard]
 * — the SAME card reused by browse/category listings, search results, and the
 * seller storefront (see CustomerHomeScreen call sites 302/361 + SellerStorefront).
 *
 * Contract (UI-SPEC C-09, no visual change): a product with reviewCount > 0
 * renders the amber-star aggregate row "<avg> (<count>)"; a product with
 * reviewCount == 0 renders NO rating row (no "0 reviews" clutter).
 */
class CustomerProductCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun product(averageRating: Double = 0.0, reviewCount: Int = 0) =
        Product(
            id = "p-1",
            title = "Shirt",
            description = "A nice shirt",
            basePrice = 19.99,
            sellerName = "Acme",
            averageRating = averageRating,
            reviewCount = reviewCount,
        )

    @Test
    fun reviewCountPositive_rendersAggregateAverageAndCount() {
        composeTestRule.setContent {
            CustomerProductCard(
                product = product(averageRating = 4.5, reviewCount = 12),
                onClick = {},
            )
        }

        // "%.1f (%d)".format(4.5, 12) == "4.5 (12)"
        composeTestRule.onNodeWithText("4.5 (12)").assertIsDisplayed()
    }

    @Test
    fun reviewCountZero_rendersNoRatingRow() {
        composeTestRule.setContent {
            CustomerProductCard(
                product = product(averageRating = 0.0, reviewCount = 0),
                onClick = {},
            )
        }

        // No aggregate row at all — the "%.1f (%d)" text node must be absent.
        composeTestRule.onAllNodesWithText("0.0 (0)").assertCountEquals(0)
        // And the product still renders (sanity: title present).
        composeTestRule.onNodeWithText("Shirt").assertIsDisplayed()
    }
}
