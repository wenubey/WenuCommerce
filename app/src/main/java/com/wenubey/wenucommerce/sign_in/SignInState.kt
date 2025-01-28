package com.wenubey.wenucommerce.sign_in

data class SignInState(
    val email: String = "",
    val isEmailValid: Boolean = false,
    val password: String = "",
    val isPasswordValid: Boolean = false,
    val isEmailVerified: Boolean = false,
    val errorMessage: String? = "",
    val isUserSignedIn: Boolean = false,
    val saveCredentials: Boolean = false,
)