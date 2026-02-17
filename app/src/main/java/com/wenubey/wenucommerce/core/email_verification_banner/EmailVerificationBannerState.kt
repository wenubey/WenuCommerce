package com.wenubey.wenucommerce.core.email_verification_banner

data class EmailVerificationBannerState(
    val isEmailVerified: Boolean = false,
    val isVisible: Boolean = false,
    val isHiddenForSession: Boolean = false,
    val isPermanentlyHidden: Boolean = false
)