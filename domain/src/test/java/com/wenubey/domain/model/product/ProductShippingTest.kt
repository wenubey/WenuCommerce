package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductShippingTest {

    @Test
    fun `default uses PAID_SHIPPING with zero cost`() {
        val shipping = ProductShipping()
        assertThat(shipping.shippingType).isEqualTo(ShippingType.PAID_SHIPPING)
        assertThat(shipping.shippingCost).isEqualTo(0.0)
        assertThat(shipping.estimatedDaysMin).isEqualTo(0)
        assertThat(shipping.estimatedDaysMax).isEqualTo(0)
        assertThat(shipping.shipsFrom).isEmpty()
        assertThat(shipping.isInternationalShipping).isFalse()
    }

    @Test
    fun `toMap serializes shippingType by name not ordinal`() {
        val shipping = ProductShipping(shippingType = ShippingType.FREE_SHIPPING)
        assertThat(shipping.toMap()["shippingType"]).isEqualTo("FREE_SHIPPING")
    }

    @Test
    fun `toMap contains every persisted field`() {
        val shipping = ProductShipping(
            shippingType = ShippingType.LOCAL_PICKUP_ONLY,
            shippingCost = 4.99,
            estimatedDaysMin = 2,
            estimatedDaysMax = 5,
            shipsFrom = "Istanbul",
            isInternationalShipping = true,
        )
        val map = shipping.toMap()
        assertThat(map.keys).containsExactly(
            "shippingType",
            "shippingCost",
            "estimatedDaysMin",
            "estimatedDaysMax",
            "shipsFrom",
            "isInternationalShipping",
        )
        assertThat(map["shippingType"]).isEqualTo("LOCAL_PICKUP_ONLY")
        assertThat(map["shippingCost"]).isEqualTo(4.99)
        assertThat(map["estimatedDaysMin"]).isEqualTo(2)
        assertThat(map["estimatedDaysMax"]).isEqualTo(5)
        assertThat(map["shipsFrom"]).isEqualTo("Istanbul")
        assertThat(map["isInternationalShipping"]).isEqualTo(true)
    }
}
