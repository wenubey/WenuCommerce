package com.wenubey.wenucommerce.seller.seller_categories

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory

data class SellerCategoryState(
    val categories: List<Category> = listOf(),
    val selectedCategory: Category? = null,
    val selectedSubcategory: Subcategory? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showCreateCategoryDialog: Boolean = false,
    val showCreateSubcategoryDialog: Boolean = false,
)
