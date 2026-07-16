package com.wenubey.wenucommerce.navigation

import com.wenubey.domain.model.user.User
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
data object SellerVerificationStatusScreen

@Serializable
data object SellerProfile

@Serializable
data object SellerProductCreate

@Serializable
data class SellerProductEdit(val productId: String)

// Seller review visibility (Phase 7 — 07-03): read-only reviews for one own product
@Serializable
data class SellerProductReviews(
    val productId: String,
    val productTitle: String,
)

// Customer specific product screens
@Serializable
data class CustomerProductDetail(val productId: String)

@Serializable
data class SellerStorefront(val sellerId: String)

// Reviews (Phase 7 — 07-02)
@Serializable
data class WriteReview(
    val productId: String,
    val existingReviewId: String? = null,
)

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

@Serializable
data object QueueManagement

// Checkout screens
@Serializable
data object Checkout

@Serializable
data object AddressForm

@Serializable
data class OrderConfirmation(val orderId: String)

@Serializable
data class OrderDetail(val orderId: String)

// Discount screens
@Serializable
data class SellerDiscountCreateEdit(val code: String?, val isSeller: Boolean)

// Seller order screens (Phase 6)
@Serializable
data object SellerOrders

@Serializable
data class SellerOrderDetail(val sellerOrderId: String)

// Customer order screens (Phase 6)
@Serializable
data object CustomerOrderHistory

@Serializable
data class CustomerOrderDetail(val orderId: String)