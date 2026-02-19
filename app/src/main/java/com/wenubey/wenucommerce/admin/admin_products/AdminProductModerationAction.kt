package com.wenubey.wenucommerce.admin.admin_products

import com.wenubey.domain.model.product.Product

sealed interface AdminProductModerationAction {
    data class OnProductSelected(val product: Product) : AdminProductModerationAction
    data class OnSuspendReasonChanged(val reason: String) : AdminProductModerationAction
    data object OnShowSuspendDialog : AdminProductModerationAction
    data object OnShowApproveDialog : AdminProductModerationAction
    data object OnShowDetailDialog : AdminProductModerationAction
    data object OnDismissDialog : AdminProductModerationAction
    data object OnConfirmApprove : AdminProductModerationAction
    data object OnConfirmSuspend : AdminProductModerationAction
}
