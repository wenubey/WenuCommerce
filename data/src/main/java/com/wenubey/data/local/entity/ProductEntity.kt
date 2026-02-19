package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val description: String = "",
    val slug: String = "",

    val sellerId: String = "",
    val sellerName: String = "",
    val sellerLogoUrl: String = "",

    val categoryId: String = "",
    val categoryName: String = "",
    val subcategoryId: String = "",
    val subcategoryName: String = "",

    val basePrice: Double = 0.0,
    val compareAtPrice: Double? = null,
    val currency: String = "USD",

    // Enum stored as name string
    val status: String = "DRAFT",
    val condition: String = "NEW",

    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,
    val totalStockQuantity: Int = 0,
    val hasVariants: Boolean = false,
    val viewCount: Int = 0,
    val purchaseCount: Int = 0,

    val moderationNotes: String = "",
    val suspendedBy: String = "",
    val suspendedAt: String = "",
    val stripeProductId: String = "",

    val createdAt: String = "",
    val updatedAt: String = "",
    val publishedAt: String = "",
    val archivedAt: String = "",

    // JSON-serialized nested types — all have safe default values
    val imagesJson: String = "[]",
    val variantsJson: String = "[]",
    val shippingJson: String = "{}",
    val tagsJson: String = "[]",
    val tagNamesJson: String = "[]",
    val searchKeywordsJson: String = "[]",
)
