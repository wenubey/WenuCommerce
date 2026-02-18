package com.wenubey.wenucommerce.customer.customer_home

import com.wenubey.domain.model.product.Category

data class CustomerHomeState(
    val categories: List<Category> = listOf(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedCategoryId: String? = null,
)
