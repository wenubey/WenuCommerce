package com.wenubey.domain.model

data class AuthState(
    val isAuthenticated: Boolean = false,
    val isEmailVerified: Boolean = false,
    val userEmail: String? = null,
)