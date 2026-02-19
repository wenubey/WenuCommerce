package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val isActive: Boolean = true,
    val createdBy: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",

    // JSON-serialized List<Subcategory>
    val subcategoriesJson: String = "[]",
)
