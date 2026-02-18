package com.wenubey.wenucommerce.seller.seller_categories

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory

sealed interface SellerCategoryAction {
    data class OnCategorySelected(val category: Category) : SellerCategoryAction
    data class OnSubcategorySelected(val subcategory: Subcategory) : SellerCategoryAction
    data class OnCreateNewCategory(val name: String, val description: String) : SellerCategoryAction
    data class OnCreateNewSubcategory(val categoryId: String, val subcategory: Subcategory) : SellerCategoryAction
    data object OnShowCreateCategoryDialog : SellerCategoryAction
    data object OnShowCreateSubcategoryDialog : SellerCategoryAction
    data object OnDismissDialog : SellerCategoryAction
    data object OnRefresh : SellerCategoryAction
}
