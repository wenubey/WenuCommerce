package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of Firestore `sellerOrders/{id}`. JSON columns for `items` and
 * `statusHistory` follow the established `OrderEntity.itemsJson` pattern
 * (RESEARCH §2.5, W6 amendment) — no @Relation, no junction table.
 *
 * Indices on parentOrderId + sellerId back the customer-detail and seller-list
 * Flow queries respectively.
 */
@Entity(
    tableName = "seller_orders",
    indices = [
        Index("parentOrderId"),
        Index("sellerId")
    ]
)
data class SellerOrderEntity(
    @PrimaryKey val id: String,
    val parentOrderId: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerLogoUrl: String = "",
    val subtotal: Double = 0.0,
    val shippingShare: Double = 0.0,
    val discountShare: Double = 0.0,
    val status: String = "PENDING",
    val trackingNumber: String? = null,
    val refundId: String? = null,
    val refundedAmount: Double? = null,
    val itemsJson: String = "[]",
    val statusHistoryJson: String = "[]",
    val createdAt: String = "",
    val updatedAt: String = ""
)
