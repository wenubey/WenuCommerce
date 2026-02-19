package com.wenubey.domain.model.product

import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val id: String = "",
    val name: String = "",
    val displayName: String = "",
    val createdBy: String = "",
    val createdAt: String = "",
)

fun Tag.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
    "displayName" to displayName,
    "createdBy" to createdBy,
    "createdAt" to createdAt,
)
