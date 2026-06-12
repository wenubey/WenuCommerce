package com.wenubey.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CartItemTest {

    @Test
    fun `default quantity is 1 and item is not flagged as deleted`() {
        val item = CartItem()
        assertThat(item.quantity).isEqualTo(1)
        assertThat(item.snapshotPrice).isEqualTo(0.0)
        assertThat(item.availableStock).isEqualTo(0)
        assertThat(item.isProductDeleted).isFalse()
        assertThat(item.productId).isEmpty()
        assertThat(item.productTitle).isEmpty()
        assertThat(item.productImageUrl).isEmpty()
    }
}
