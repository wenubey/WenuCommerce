package com.wenubey.domain

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.allowedNext
import org.junit.Test

/**
 * Mirrors the literal `allowedNext()` map in `firestore.rules`. If you change
 * one, change the other — and re-run the rules emulator test.
 *
 * Requirements: ORDR-05 (forward-only flow), ORDR-09 (cancel only pre-SHIPPED).
 */
class StatusTransitionTest {

    @Test
    fun `PENDING allows CONFIRMED and CANCELLED only`() {
        assertThat(OrderStatus.PENDING.allowedNext())
            .containsExactly(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)
    }

    @Test
    fun `CONFIRMED allows SHIPPED and CANCELLED only`() {
        assertThat(OrderStatus.CONFIRMED.allowedNext())
            .containsExactly(OrderStatus.SHIPPED, OrderStatus.CANCELLED)
    }

    @Test
    fun `SHIPPED allows DELIVERED only -- no cancel post-SHIPPED`() {
        assertThat(OrderStatus.SHIPPED.allowedNext())
            .containsExactly(OrderStatus.DELIVERED)
        // ORDR-05: cancel forbidden post-shipping
        assertThat(OrderStatus.SHIPPED.allowedNext()).doesNotContain(OrderStatus.CANCELLED)
    }

    @Test
    fun `DELIVERED is terminal`() {
        assertThat(OrderStatus.DELIVERED.allowedNext()).isEmpty()
    }

    @Test
    fun `CANCELLED is terminal`() {
        assertThat(OrderStatus.CANCELLED.allowedNext()).isEmpty()
    }

    @Test
    fun `no transition reverts backward`() {
        // forward-only: no `next` may be ordinal-less than `current` in our enum order.
        for (current in OrderStatus.values()) {
            for (next in current.allowedNext()) {
                if (next != OrderStatus.CANCELLED) {
                    assertThat(next.ordinal).isGreaterThan(current.ordinal)
                }
            }
        }
    }
}
