package com.wenubey.wenucommerce.admin.admin_products

import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus

sealed interface AdminProductSearchAction {
    data class OnSearchQueryChanged(val query: String) : AdminProductSearchAction
    data class OnStatusFilterChanged(val status: ProductStatus?) : AdminProductSearchAction
    data object OnClearSearch : AdminProductSearchAction
    data class OnProductSelected(val product: Product) : AdminProductSearchAction
    data object OnDismissDetailDialog : AdminProductSearchAction
    // Category filter actions
    data class OnFilterCategorySelected(val categoryId: String?) : AdminProductSearchAction
    data class OnFilterSubcategorySelected(val subcategoryId: String?) : AdminProductSearchAction
    data object OnClearCategoryFilters : AdminProductSearchAction
    data object OnRequestCategoryLoad : AdminProductSearchAction
}
