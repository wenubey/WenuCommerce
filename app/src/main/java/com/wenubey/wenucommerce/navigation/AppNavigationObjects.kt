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
data class VerifyEmail(val email: String)

@Serializable
data object Onboarding

// Tab
@Serializable
data class CustomerTab(val tabIndex: Int)

@Serializable
data class SellerTab(val tabIndex: Int)

@Serializable
data class AdminTab(val tabIndex: Int)

// Customer specific screens
@Serializable
data object CustomerHome

@Serializable
data object CustomerCart

@Serializable
data object CustomerProfile

// Seller specific screens
@Serializable
data object SellerDashboard

@Serializable
data object SellerProducts

@Serializable
data object SellerOrders

@Serializable
data object SellerProfile

// Admin specific screens
@Serializable
data object AdminDashboard

@Serializable
data object AdminUsers

@Serializable
data object AdminAnalytics

@Serializable
data object AdminSettings

@Serializable
data object Home

@Serializable
data object Cart

@Serializable
data object Profile