package com.wenubey.data

import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.entity.SellerOrderEntity
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.local.mapper.toEntity
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.domain.model.order.StatusEntry
import org.junit.Test

class SellerOrderMapperTest {

    @Test
    fun `domain - entity - domain round-trip preserves all fields`() {
        val original = SellerOrder(
            id = "so-1",
            parentOrderId = "ord-99",
            sellerId = "seller-a",
            sellerName = "Acme",
            sellerLogoUrl = "https://example.com/a.png",
            items = listOf(
                OrderItem(
                    productId = "p1",
                    productTitle = "Widget",
                    quantity = 2,
                    snapshotPrice = 9.99,
                    lineTotal = 19.98
                )
            ),
            subtotal = 19.98,
            shippingShare = 4.50,
            discountShare = 1.00,
            status = OrderStatus.CONFIRMED,
            statusHistory = listOf(
                StatusEntry(OrderStatus.PENDING, "2026-06-15T10:00:00Z"),
                StatusEntry(OrderStatus.CONFIRMED, "2026-06-15T11:00:00Z", note = "ack")
            ),
            trackingNumber = null,
            refundId = null,
            refundedAmount = null,
            createdAt = "2026-06-15T10:00:00Z",
            updatedAt = "2026-06-15T11:00:00Z"
        )

        val roundTripped = original.toEntity().toDomain()

        assertThat(roundTripped).isEqualTo(original)
    }

    @Test
    fun `corrupt statusHistoryJson yields empty list - no throw`() {
        val entity = SellerOrderEntity(
            id = "so-2",
            parentOrderId = "ord-1",
            sellerId = "seller-a",
            statusHistoryJson = "{ this is not JSON",
            itemsJson = "[]"
        )

        val domain = entity.toDomain()

        assertThat(domain.statusHistory).isEmpty()
        assertThat(domain.items).isEmpty()
        // status falls back to PENDING via runCatching
        assertThat(domain.status).isEqualTo(OrderStatus.PENDING)
    }

    @Test
    fun `corrupt itemsJson yields empty list - no throw`() {
        val entity = SellerOrderEntity(
            id = "so-3",
            parentOrderId = "ord-1",
            sellerId = "seller-a",
            itemsJson = "not-valid",
            statusHistoryJson = "[]"
        )

        val domain = entity.toDomain()
        assertThat(domain.items).isEmpty()
    }

    @Test
    fun `unknown status name falls back to PENDING`() {
        val entity = SellerOrderEntity(
            id = "so-4",
            parentOrderId = "ord-1",
            sellerId = "seller-a",
            status = "UNKNOWN_FUTURE_STATUS",
            itemsJson = "[]",
            statusHistoryJson = "[]"
        )
        assertThat(entity.toDomain().status).isEqualTo(OrderStatus.PENDING)
    }
}
