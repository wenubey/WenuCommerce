package com.wenubey.domain.model.product

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val subcategories: List<Subcategory> = listOf(),
    val isActive: Boolean = true,
    val createdBy: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

fun Category.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
    "description" to description,
    "imageUrl" to imageUrl,
    "subcategories" to subcategories.map { it.toMap() },
    "isActive" to isActive,
    "createdBy" to createdBy,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)
