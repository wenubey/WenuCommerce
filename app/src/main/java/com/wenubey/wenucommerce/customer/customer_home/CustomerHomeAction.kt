package com.wenubey.wenucommerce.customer.customer_home

sealed interface CustomerHomeAction {
    data class OnCategorySelected(val categoryId: String) : CustomerHomeAction
    data class OnSubcategorySelected(val subcategoryId: String?) : CustomerHomeAction
    data class OnSearchQueryChanged(val query: String) : CustomerHomeAction
    data object OnClearSearch : CustomerHomeAction
    // Search filter sheet actions (separate from browse category selection)
    data class OnSearchFilterCategorySelected(val categoryId: String?) : CustomerHomeAction
    data class OnSearchFilterSubcategorySelected(val subcategoryId: String?) : CustomerHomeAction
    data object OnClearSearchFilters : CustomerHomeAction
    // Pull-to-refresh and empty-state retry action
    data object OnPullToRefresh : CustomerHomeAction
}
