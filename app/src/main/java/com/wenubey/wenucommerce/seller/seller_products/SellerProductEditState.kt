package com.wenubey.wenucommerce.seller.seller_products

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.Subcategory

data class SellerProductEditState(
    // --- existing fields (unchanged) ---
    val product: Product? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isEditable: Boolean = false,
    val savedSuccessfully: Boolean = false,

    // --- new: images ---
    // Tracks URIs of locally-selected images not yet uploaded.
    // Merged with product.images (existing remote images) in the UI.
    val localImageUris: List<String> = listOf(),

    // --- new: tags ---
    val tagSuggestions: List<String> = listOf(),
    val isLoadingTagSuggestions: Boolean = false,

    // --- new: category picker ---
    // Mirrors the category selected through the bottom sheet before it is
    // committed to product. Initialised from product.categoryId/categoryName
    // in loadProduct().
    val selectedCategory: Category? = null,
    val selectedSubcategory: Subcategory? = null,
)
