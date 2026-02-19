package com.wenubey.wenucommerce.customer.customer_home

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product

data class CustomerHomeState(
    val categories: List<Category> = listOf(),
    val products: List<Product> = listOf(),
    val isLoading: Boolean = false,
    val isLoadingProducts: Boolean = false,
    val errorMessage: String? = null,
    // Browse category selection (drives the horizontal category carousel)
    val selectedCategoryId: String? = null,
    val selectedSubcategoryId: String? = null,
    val searchQuery: String = "",
    val searchResults: List<Product> = listOf(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    // Search filter sheet state (separate from browse category selection)
    val filterSheetCategoryId: String? = null,
    val filterSheetSubcategoryId: String? = null,
    // Pull-to-refresh state — drives the PullToRefreshBox indicator and shimmer overlay
    val isRefreshing: Boolean = false,
)
