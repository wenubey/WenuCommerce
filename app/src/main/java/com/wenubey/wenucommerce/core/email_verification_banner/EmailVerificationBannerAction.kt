package com.wenubey.wenucommerce.core.email_verification_banner

sealed interface EmailVerificationBannerAction {
    data object HideNotificationForSession : EmailVerificationBannerAction
    data object DoNotShowAgain : EmailVerificationBannerAction
}

