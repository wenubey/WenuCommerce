package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.CategoryEntity
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    isActive = isActive,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    subcategories = runCatching { json.decodeFromString<List<Subcategory>>(subcategoriesJson) }.getOrElse { emptyList() },
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    isActive = isActive,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    subcategoriesJson = json.encodeToString(subcategories),
)
