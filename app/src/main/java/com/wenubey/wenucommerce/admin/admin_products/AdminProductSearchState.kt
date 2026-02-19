package com.wenubey.wenucommerce.admin.admin_products

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus

data class AdminProductSearchState(
    val searchQuery: String = "",
    val searchResults: List<Product> = listOf(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val statusFilter: ProductStatus? = null,
    val filteredResults: List<Product> = listOf(),
    val selectedProduct: Product? = null,
    val showDetailDialog: Boolean = false,
    // Category filter fields (lazy-loaded on first filter sheet open)
    val categories: List<Category> = listOf(),
    val isLoadingCategories: Boolean = false,
    val filterCategoryId: String? = null,
    val filterSubcategoryId: String? = null,
)
