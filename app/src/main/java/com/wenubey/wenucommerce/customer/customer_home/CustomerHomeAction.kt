package com.wenubey.wenucommerce.customer.customer_home

sealed interface CustomerHomeAction {
    data class OnCategorySelected(val categoryId: String) : CustomerHomeAction
}
