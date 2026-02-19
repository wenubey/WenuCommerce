package com.wenubey.wenucommerce.admin.admin_products

import com.wenubey.domain.model.product.Product

data class AdminProductModerationState(
    val pendingProducts: List<Product> = listOf(),
    val selectedProduct: Product? = null,
    val isLoading: Boolean = false,
    val isActing: Boolean = false,
    val errorMessage: String? = null,
    val suspendReason: String = "",
    val showSuspendDialog: Boolean = false,
    val showApproveDialog: Boolean = false,
    val showDetailDialog: Boolean = false,
)
