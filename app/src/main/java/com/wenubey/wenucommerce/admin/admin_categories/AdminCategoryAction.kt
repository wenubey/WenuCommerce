package com.wenubey.wenucommerce.admin.admin_categories

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory

sealed interface AdminCategoryAction {
    data class OnCreateCategory(val name: String, val description: String, val imageUri: String) : AdminCategoryAction
    data class OnEditCategory(val category: Category, val newImageUri: String?) : AdminCategoryAction
    data class OnDeleteCategory(val categoryId: String) : AdminCategoryAction
    data class OnAddSubcategory(val categoryId: String, val subcategory: Subcategory) : AdminCategoryAction
    data class OnCategorySelected(val category: Category) : AdminCategoryAction
    data object OnShowCreateDialog : AdminCategoryAction
    data object OnDismissDialog : AdminCategoryAction
}
