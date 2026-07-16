package com.wenubey.data.local.mapper

import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.entity.ReviewEntity
import com.wenubey.domain.model.product.ProductReview
import org.junit.Test

class ReviewMapperTest {

    @Test
    fun `domain - entity - domain round-trip preserves all 14 fields`() {
        val original = ProductReview(
            id = "rev-1",
            productId = "p-1",
            reviewerId = "cust-1",
            reviewerName = "Ada Lovelace",
            reviewerPhotoUrl = "https://example.com/ada.png",
            purchaseId = "so-1",
            rating = 4,
            title = "Solid",
            body = "Works exactly as described.",
            isVerifiedPurchase = true,
            helpfulCount = 7,
            isVisible = true,
            createdAt = "1700000000000",
            updatedAt = "1700000000500",
        )

        val roundTripped = original.toEntity().toDomain()

        assertThat(roundTripped).isEqualTo(original)
    }

    @Test
    fun `entity - domain - entity round-trip preserves all 14 fields`() {
        val entity = ReviewEntity(
            id = "rev-2",
            productId = "p-2",
            reviewerId = "cust-2",
            reviewerName = "Grace",
            reviewerPhotoUrl = "",
            purchaseId = "so-2",
            rating = 5,
            title = "",
            body = "Great",
            isVerifiedPurchase = true,
            helpfulCount = 0,
            isVisible = true,
            createdAt = "1700000001000",
            updatedAt = "1700000001000",
        )

        assertThat(entity.toDomain().toEntity()).isEqualTo(entity)
    }

    @Test
    fun `boolean flags survive the round-trip when false`() {
        val original = ProductReview(
            id = "rev-3",
            productId = "p-3",
            reviewerId = "cust-3",
            rating = 1,
            isVerifiedPurchase = false,
            isVisible = false,
        )

        val roundTripped = original.toEntity().toDomain()

        assertThat(roundTripped.isVerifiedPurchase).isFalse()
        assertThat(roundTripped.isVisible).isFalse()
        assertThat(roundTripped).isEqualTo(original)
    }
}
