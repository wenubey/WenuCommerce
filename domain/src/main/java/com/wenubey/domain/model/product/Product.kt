package com.wenubey.domain.model.product

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String = "",
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
    val tags: List<String> = listOf(),
    val tagNames: List<String> = listOf(),
    val searchKeywords: List<String> = listOf(),
    val condition: ProductCondition = ProductCondition.NEW,

    val basePrice: Double = 0.0,
    val compareAtPrice: Double? = null,
    val currency: String = "USD",

    val images: List<ProductImage> = listOf(),

    val variants: List<ProductVariant> = listOf(),
    val totalStockQuantity: Int = 0,
    val hasVariants: Boolean = false,

    val shipping: ProductShipping = ProductShipping(),

    val status: ProductStatus = ProductStatus.DRAFT,
    val moderationNotes: String = "",
    val suspendedBy: String = "",
    val suspendedAt: String = "",

    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,

    val stripeProductId: String = "",

    val viewCount: Int = 0,
    val purchaseCount: Int = 0,

    val createdAt: String = "",
    val updatedAt: String = "",
    val publishedAt: String = "",
    val archivedAt: String = "",
)

fun Product.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "description" to description,
    "slug" to slug,
    "sellerId" to sellerId,
    "sellerName" to sellerName,
    "sellerLogoUrl" to sellerLogoUrl,
    "categoryId" to categoryId,
    "categoryName" to categoryName,
    "subcategoryId" to subcategoryId,
    "subcategoryName" to subcategoryName,
    "tags" to tags,
    "tagNames" to tagNames,
    "searchKeywords" to searchKeywords,
    "condition" to condition.name,
    "basePrice" to basePrice,
    "compareAtPrice" to compareAtPrice,
    "currency" to currency,
    "images" to images.map { it.toMap() },
    "variants" to variants.map { it.toMap() },
    "totalStockQuantity" to totalStockQuantity,
    "hasVariants" to hasVariants,
    "shipping" to shipping.toMap(),
    "status" to status.name,
    "moderationNotes" to moderationNotes,
    "suspendedBy" to suspendedBy,
    "suspendedAt" to suspendedAt,
    "averageRating" to averageRating,
    "reviewCount" to reviewCount,
    "stripeProductId" to stripeProductId,
    "viewCount" to viewCount,
    "purchaseCount" to purchaseCount,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "publishedAt" to publishedAt,
    "archivedAt" to archivedAt,
)
