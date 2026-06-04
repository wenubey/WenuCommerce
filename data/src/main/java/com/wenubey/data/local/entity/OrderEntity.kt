package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val status: String = "PENDING",
    val subtotal: Double = 0.0,
    val shippingTotal: Double = 0.0,
    val totalAmount: Double = 0.0,
    val currency: String = "USD",
    val stripePaymentIntentId: String = "",
    val shippingAddressJson: String = "",
    val itemsJson: String = "[]",
    val discountAmount: Double = 0.0,
    val discountCode: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)
