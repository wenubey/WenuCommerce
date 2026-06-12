package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShippingTypeTest {

    @Test
    fun `enum names are stable`() {
        assertThat(ShippingType.entries.map { it.name }).containsExactly(
            "FREE_SHIPPING",
            "PAID_SHIPPING",
            "LOCAL_PICKUP_ONLY",
            "DIGITAL_DELIVERY",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        ShippingType.entries.forEach { type ->
            assertThat(ShippingType.valueOf(type.name)).isEqualTo(type)
        }
    }
}
