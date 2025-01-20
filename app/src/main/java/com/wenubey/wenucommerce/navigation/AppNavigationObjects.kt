package com.wenubey.wenucommerce.navigation

import kotlinx.serialization.Serializable

// Auth

@Serializable
data object SignIn

@Serializable
data object SignUp

@Serializable
data object ForgotPassword

@Serializable
data object VerifyEmail

@Serializable
data class Tab(val tabIndex: Int)

@Serializable
data object Home

@Serializable
data object Cart

@Serializable
data object Profile