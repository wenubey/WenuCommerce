package com.wenubey.domain.model.discount

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins names + ordering of DiscountType. These are persisted in Firestore
 * (DiscountCode.type) and sent across the Cloud Functions boundary
 * (validateCoupon). A rename silently invalidates every saved coupon and
 * every in-flight checkout that already encoded the old name.
 *
 * NOTE: The actual discount-amount math lives server-side in Cloud Functions
 * (functions/src/index.ts :: validateCoupon). There is no domain-level
 * discount calculator to unit-test from Kotlin. The CouponValidationResult
 * carrier and CheckoutViewModel integration are tested in their own layers.
 */
class DiscountTypeTest {

    @Test
    fun `enum names are stable`() {
        assertThat(DiscountType.entries.map { it.name }).containsExactly(
            "PERCENTAGE",
            "FIXED_AMOUNT",
            "FREE_SHIPPING",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        DiscountType.entries.forEach { type ->
            assertThat(DiscountType.valueOf(type.name)).isEqualTo(type)
        }
    }
}
