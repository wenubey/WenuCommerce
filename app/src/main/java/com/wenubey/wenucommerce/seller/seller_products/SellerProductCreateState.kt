package com.wenubey.wenucommerce.seller.seller_products

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.ProductCondition
import com.wenubey.domain.model.product.ProductImage
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.Subcategory

data class SellerProductCreateState(
    val title: String = "",
    val description: String = "",
    val basePrice: String = "",
    val compareAtPrice: String = "",
    val condition: ProductCondition = ProductCondition.NEW,
    val selectedCategory: Category? = null,
    val selectedSubcategory: Subcategory? = null,
    val tags: List<String> = listOf(),
    val images: List<ProductImage> = listOf(),
    val localImageUris: List<String> = listOf(),
    val variants: List<ProductVariant> = listOf(
        ProductVariant(isDefault = true, label = "Default")
    ),
    val hasVariants: Boolean = false,
    val shipping: ProductShipping = ProductShipping(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isImageUploading: Boolean = false,
    val errorMessage: String? = null,
    val savedProductId: String? = null,
    val isSellerVerified: Boolean = false,
    val tagSuggestions: List<String> = listOf(),
    val isLoadingTagSuggestions: Boolean = false,
)
