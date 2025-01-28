package com.wenubey.wenucommerce.verify_email

sealed interface VerifyEmailAction {
    data object CheckEmailVerification: VerifyEmailAction
    data object ResendVerificationEmail: VerifyEmailAction
    data object StopVerificationCheck: VerifyEmailAction
}