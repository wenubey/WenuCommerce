package com.wenubey.domain.model

data class Purchase(
    val purchaseId: String,
    val productId: String,
    val quantity: Double,
    val price: Double,
    val purchaseDate: String,
)
