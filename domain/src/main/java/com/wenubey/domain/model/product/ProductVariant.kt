package com.wenubey.domain.model.product

import kotlinx.serialization.Serializable

@Serializable
data class ProductVariant(
    val id: String = "",
    val label: String = "",
    val attributes: Map<String, String> = mapOf(),
    val sku: String = "",
    val priceOverride: Double? = null,
    val stockQuantity: Int = 0,
    val inStock: Boolean = true,
    val isDefault: Boolean = false,
    val stripePriceId: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

fun ProductVariant.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "label" to label,
    "attributes" to attributes,
    "sku" to sku,
    "priceOverride" to priceOverride,
    "stockQuantity" to stockQuantity,
    "inStock" to inStock,
    "isDefault" to isDefault,
    "stripePriceId" to stripePriceId,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)
