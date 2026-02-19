package com.wenubey.domain.model.product

import kotlinx.serialization.Serializable

@Serializable
data class ProductShipping(
    val shippingType: ShippingType = ShippingType.PAID_SHIPPING,
    val shippingCost: Double = 0.0,
    val estimatedDaysMin: Int = 0,
    val estimatedDaysMax: Int = 0,
    val shipsFrom: String = "",
    val isInternationalShipping: Boolean = false,
)

fun ProductShipping.toMap(): Map<String, Any> = mapOf(
    "shippingType" to shippingType.name,
    "shippingCost" to shippingCost,
    "estimatedDaysMin" to estimatedDaysMin,
    "estimatedDaysMax" to estimatedDaysMax,
    "shipsFrom" to shipsFrom,
    "isInternationalShipping" to isInternationalShipping,
)
