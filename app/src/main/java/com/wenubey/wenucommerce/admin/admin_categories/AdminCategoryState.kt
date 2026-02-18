package com.wenubey.wenucommerce.admin.admin_categories

import com.wenubey.domain.model.product.Category

data class AdminCategoryState(
    val categories: List<Category> = listOf(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedCategory: Category? = null,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
)
