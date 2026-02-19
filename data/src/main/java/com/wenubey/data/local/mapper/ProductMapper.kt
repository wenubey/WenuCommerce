package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.ProductEntity
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductCondition
import com.wenubey.domain.model.product.ProductImage
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.model.product.ProductVariant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun ProductEntity.toDomain(): Product = Product(
    id = id,
    title = title,
    description = description,
    slug = slug,
    sellerId = sellerId,
    sellerName = sellerName,
    sellerLogoUrl = sellerLogoUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    subcategoryId = subcategoryId,
    subcategoryName = subcategoryName,
    basePrice = basePrice,
    compareAtPrice = compareAtPrice,
    currency = currency,
    status = runCatching { ProductStatus.valueOf(status) }.getOrElse { ProductStatus.DRAFT },
    condition = runCatching { ProductCondition.valueOf(condition) }.getOrElse { ProductCondition.NEW },
    averageRating = averageRating,
    reviewCount = reviewCount,
    totalStockQuantity = totalStockQuantity,
    hasVariants = hasVariants,
    viewCount = viewCount,
    purchaseCount = purchaseCount,
    moderationNotes = moderationNotes,
    suspendedBy = suspendedBy,
    suspendedAt = suspendedAt,
    stripeProductId = stripeProductId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    publishedAt = publishedAt,
    archivedAt = archivedAt,
    images = runCatching { json.decodeFromString<List<ProductImage>>(imagesJson) }.getOrElse { emptyList() },
    variants = runCatching { json.decodeFromString<List<ProductVariant>>(variantsJson) }.getOrElse { emptyList() },
    shipping = runCatching { json.decodeFromString<ProductShipping>(shippingJson) }.getOrElse { ProductShipping() },
    tags = runCatching { json.decodeFromString<List<String>>(tagsJson) }.getOrElse { emptyList() },
    tagNames = runCatching { json.decodeFromString<List<String>>(tagNamesJson) }.getOrElse { emptyList() },
    searchKeywords = runCatching { json.decodeFromString<List<String>>(searchKeywordsJson) }.getOrElse { emptyList() },
)

fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id,
    title = title,
    description = description,
    slug = slug,
    sellerId = sellerId,
    sellerName = sellerName,
    sellerLogoUrl = sellerLogoUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    subcategoryId = subcategoryId,
    subcategoryName = subcategoryName,
    basePrice = basePrice,
    compareAtPrice = compareAtPrice,
    currency = currency,
    status = status.name,
    condition = condition.name,
    averageRating = averageRating,
    reviewCount = reviewCount,
    totalStockQuantity = totalStockQuantity,
    hasVariants = hasVariants,
    viewCount = viewCount,
    purchaseCount = purchaseCount,
    moderationNotes = moderationNotes,
    suspendedBy = suspendedBy,
    suspendedAt = suspendedAt,
    stripeProductId = stripeProductId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    publishedAt = publishedAt,
    archivedAt = archivedAt,
    imagesJson = json.encodeToString(images),
    variantsJson = json.encodeToString(variants),
    shippingJson = json.encodeToString(shipping),
    tagsJson = json.encodeToString(tags),
    tagNamesJson = json.encodeToString(tagNames),
    searchKeywordsJson = json.encodeToString(searchKeywords),
)
