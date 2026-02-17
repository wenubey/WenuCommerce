package com.wenubey.wenucommerce.admin.admin_seller_approval

import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User

sealed interface AdminSellerApprovalAction {
    data class OnFilterChange(val status: VerificationStatus): AdminSellerApprovalAction
    data class OnSellerSelected(val seller: User): AdminSellerApprovalAction
    data class OnApprove(val sellerId: String, val notes: String): AdminSellerApprovalAction
    data class OnReject(val sellerId: String, val notes: String): AdminSellerApprovalAction
    data class OnRequestMoreInfo(val sellerId: String, val notes: String): AdminSellerApprovalAction
    data object OnDismissDialog: AdminSellerApprovalAction
}