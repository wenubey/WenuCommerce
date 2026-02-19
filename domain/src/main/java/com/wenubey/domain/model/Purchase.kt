package com.wenubey.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Purchase(
    val purchaseId: String = "",
    val productId: String = "",
    val variantId: String = "",
    val quantity: Double = 0.0,
    val price: Double = 0.0,
    val purchaseDate: String = "",
    val stripePaymentIntentId: String = "",
    val stripeChargeId: String = "",
)
