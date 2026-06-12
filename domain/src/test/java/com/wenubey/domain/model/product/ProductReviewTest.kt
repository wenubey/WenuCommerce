package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductReviewTest {

    @Test
    fun `default is verified purchase, visible, rating zero`() {
        val review = ProductReview()
        assertThat(review.id).isEmpty()
        assertThat(review.productId).isEmpty()
        assertThat(review.rating).isEqualTo(0)
        assertThat(review.helpfulCount).isEqualTo(0)
        assertThat(review.isVerifiedPurchase).isTrue()
        assertThat(review.isVisible).isTrue()
    }

    @Test
    fun `toMap contains every persisted field`() {
        val review = ProductReview(
            id = "r-1",
            productId = "p-1",
            reviewerId = "u-1",
            reviewerName = "Alice",
            reviewerPhotoUrl = "https://cdn/u.jpg",
            purchaseId = "purch-1",
            rating = 5,
            title = "Great",
            body = "Loved it.",
            isVerifiedPurchase = true,
            helpfulCount = 3,
            isVisible = true,
            createdAt = "2026-01-01",
            updatedAt = "2026-01-02",
        )
        val map = review.toMap()
        assertThat(map.keys).containsExactly(
            "id", "productId", "reviewerId", "reviewerName", "reviewerPhotoUrl",
            "purchaseId", "rating", "title", "body",
            "isVerifiedPurchase", "helpfulCount", "isVisible",
            "createdAt", "updatedAt",
        )
        assertThat(map["rating"]).isEqualTo(5)
        assertThat(map["helpfulCount"]).isEqualTo(3)
        assertThat(map["isVerifiedPurchase"]).isEqualTo(true)
        assertThat(map["isVisible"]).isEqualTo(true)
    }
}
