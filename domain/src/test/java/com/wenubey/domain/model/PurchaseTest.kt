package com.wenubey.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PurchaseTest {

    @Test
    fun `default has empty ids and zero quantity and price`() {
        val purchase = Purchase()
        assertThat(purchase.purchaseId).isEmpty()
        assertThat(purchase.productId).isEmpty()
        assertThat(purchase.variantId).isEmpty()
        assertThat(purchase.quantity).isEqualTo(0)
        assertThat(purchase.price).isEqualTo(0.0)
        assertThat(purchase.purchaseDate).isEmpty()
        assertThat(purchase.stripePaymentIntentId).isEmpty()
        assertThat(purchase.stripeChargeId).isEmpty()
    }
}
