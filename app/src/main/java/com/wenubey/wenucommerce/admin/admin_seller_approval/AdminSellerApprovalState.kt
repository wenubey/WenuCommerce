package com.wenubey.wenucommerce.admin.admin_seller_approval

import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User

data class AdminSellerApprovalState(
    val sellers: List<User> = listOf(),
    val selectedFilter: VerificationStatus = VerificationStatus.PENDING,
    val selectedSeller: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showApprovalDialog: Boolean = false,
    val dialogType: DialogType? = null,
    val statusCounts: Map<VerificationStatus, Int> = emptyMap()
    )


enum class DialogType {
    APPROVE,
    REJECT,
    REQUEST_INFO
}