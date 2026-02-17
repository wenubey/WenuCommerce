package com.wenubey.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Purchase(
    val purchaseId: String,
    val productId: String,
    val quantity: Double,
    val price: Double,
    val purchaseDate: String,
)
