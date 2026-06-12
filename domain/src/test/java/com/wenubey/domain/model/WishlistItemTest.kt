package com.wenubey.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WishlistItemTest {

    @Test
    fun `default has empty fields and is not flagged as deleted`() {
        val item = WishlistItem()
        assertThat(item.productId).isEmpty()
        assertThat(item.productTitle).isEmpty()
        assertThat(item.productImageUrl).isEmpty()
        assertThat(item.productPrice).isEqualTo(0.0)
        assertThat(item.availableStock).isEqualTo(0)
        assertThat(item.isProductDeleted).isFalse()
        assertThat(item.addedAt).isEmpty()
    }
}
