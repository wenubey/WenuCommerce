package com.wenubey.domain.model.order

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins enum names (persisted in Firestore) and displayName values (rendered
 * in the order list/detail UI). A silent rename on either side breaks one
 * of: stored orders, the order list label, or the seller dashboard filter.
 */
class OrderStatusTest {

    @Test
    fun `enum names are stable`() {
        assertThat(OrderStatus.entries.map { it.name }).containsExactly(
            "PENDING",
            "CONFIRMED",
            "SHIPPED",
            "DELIVERED",
            "CANCELLED",
        ).inOrder()
    }

    @Test
    fun `display names are stable`() {
        assertThat(OrderStatus.PENDING.displayName).isEqualTo("Pending")
        assertThat(OrderStatus.CONFIRMED.displayName).isEqualTo("Confirmed")
        assertThat(OrderStatus.SHIPPED.displayName).isEqualTo("Shipped")
        assertThat(OrderStatus.DELIVERED.displayName).isEqualTo("Delivered")
        assertThat(OrderStatus.CANCELLED.displayName).isEqualTo("Cancelled")
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        OrderStatus.entries.forEach { status ->
            assertThat(OrderStatus.valueOf(status.name)).isEqualTo(status)
        }
    }
}
