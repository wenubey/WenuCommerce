package com.wenubey.domain.model.order

import kotlinx.serialization.Serializable

@Serializable
data class OrderItem(
    val productId: String = "",
    val productTitle: String = "",
    val quantity: Int = 1,
    val snapshotPrice: Double = 0.0,
    val lineTotal: Double = 0.0
)
