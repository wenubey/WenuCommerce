package com.wenubey.wenucommerce.verify_email

data class VerifyEmailState(
    val isEmailVerified: Boolean = false,
    val isVerificationEmailSent: Boolean = false,
    val errorMessage: String? = null,
)
