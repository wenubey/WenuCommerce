package com.wenubey.domain.model.order

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OrderItemTest {

    @Test
    fun `default quantity is 1 with zero prices`() {
        val item = OrderItem()
        assertThat(item.quantity).isEqualTo(1)
        assertThat(item.snapshotPrice).isEqualTo(0.0)
        assertThat(item.lineTotal).isEqualTo(0.0)
        assertThat(item.productId).isEmpty()
        assertThat(item.productTitle).isEmpty()
    }

    @Test
    fun `lineTotal is stored not computed`() {
        // Pin the current contract: OrderItem does NOT recompute lineTotal from
        // snapshotPrice * quantity. If a future change moves to a computed
        // property, this test will fail and the caller must be updated to
        // stop writing lineTotal explicitly.
        val item = OrderItem(quantity = 3, snapshotPrice = 10.0, lineTotal = 0.0)
        assertThat(item.lineTotal).isEqualTo(0.0)
    }
}
