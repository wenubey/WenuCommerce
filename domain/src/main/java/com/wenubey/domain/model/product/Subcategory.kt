package com.wenubey.domain.model.product

import kotlinx.serialization.Serializable

@Serializable
data class Subcategory(
    val id: String = "",
    val name: String = "",
)

fun Subcategory.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
)
