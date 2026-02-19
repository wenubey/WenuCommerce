package com.wenubey.domain.model.product

import kotlinx.serialization.Serializable

@Serializable
data class ProductImage(
    val id: String = "",
    val downloadUrl: String = "",
    val storagePath: String = "",
    val sortOrder: Int = 0,
    val uploadedAt: String = "",
)

fun ProductImage.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "downloadUrl" to downloadUrl,
    "storagePath" to storagePath,
    "sortOrder" to sortOrder,
    "uploadedAt" to uploadedAt,
)
