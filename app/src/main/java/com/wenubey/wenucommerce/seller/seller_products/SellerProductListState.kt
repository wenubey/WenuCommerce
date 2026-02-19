package com.wenubey.wenucommerce.seller.seller_products

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus

data class SellerProductListState(
    val products: List<Product> = listOf(),
    val filteredProducts: List<Product> = listOf(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val selectedStatusFilter: ProductStatus? = null,
    val showDeleteDialog: Boolean = false,
    val productToDelete: Product? = null,
    // Category filter fields (lazy-loaded on first filter sheet open)
    val categories: List<Category> = listOf(),
    val isLoadingCategories: Boolean = false,
    val filterCategoryId: String? = null,
    val filterSubcategoryId: String? = null,
)
