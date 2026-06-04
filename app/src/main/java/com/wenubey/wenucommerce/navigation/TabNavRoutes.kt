package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.wenubey.wenucommerce.admin.AdminTabScreen
import com.wenubey.wenucommerce.customer.CustomerTabScreen
import com.wenubey.wenucommerce.customer.checkout.CheckoutScreen
import com.wenubey.wenucommerce.customer.checkout.components.AddressFormScreen
import com.wenubey.wenucommerce.customer.customer_products.CustomerProductDetailScreen
import com.wenubey.wenucommerce.customer.order_confirmation.MinimalOrderScreen
import com.wenubey.wenucommerce.customer.order_confirmation.OrderConfirmationScreen
import com.wenubey.wenucommerce.seller.SellerTabScreen
import com.wenubey.wenucommerce.queue_management.QueueManagementScreen
import com.wenubey.wenucommerce.seller.seller_discounts.SellerDiscountCreateEditScreen
import com.wenubey.wenucommerce.seller.seller_products.SellerProductCreateScreen
import com.wenubey.wenucommerce.seller.seller_products.SellerProductEditScreen
import com.wenubey.wenucommerce.seller.seller_storefront.SellerStorefrontScreen
import com.wenubey.wenucommerce.seller.seller_verification.SellerVerificationStatusScreen

fun NavGraphBuilder.tabNavRoutes(navController: NavController) {
    composable<CustomerTab> {
        val tabArgs = it.toRoute<CustomerTab>()
        CustomerTabScreen(
            tabIndex = tabArgs.tabIndex,
            onProductClick = { productId ->
                navController.navigate(CustomerProductDetail(productId))
            },
            onNavigateToCheckout = {
                navController.navigate(Checkout)
            },
        )
    }
    composable<AdminTab> {
        val tabArgs = it.toRoute<AdminTab>()
        AdminTabScreen(
            tabIndex = tabArgs.tabIndex,
            onNavigateToCreateDiscount = {
                navController.navigate(SellerDiscountCreateEdit(code = null, isSeller = false))
            },
            onNavigateToEditDiscount = { code ->
                navController.navigate(SellerDiscountCreateEdit(code = code, isSeller = false))
            },
        )
    }
    composable<SellerTab> {
        val tabArgs = it.toRoute<SellerTab>()
        val tabIndex = tabArgs.tabIndex
        SellerTabScreen(
            tabIndex = tabIndex,
            onNavigateToSellerVerification = { user ->
                navController.navigate(SellerVerificationStatusScreen)
            },
            onNavigateToCreateProduct = {
                navController.navigate(SellerProductCreate)
            },
            onNavigateToEditProduct = { productId ->
                navController.navigate(SellerProductEdit(productId))
            },
            onNavigateToCreateDiscount = {
                navController.navigate(SellerDiscountCreateEdit(code = null, isSeller = true))
            },
            onNavigateToEditDiscount = { code ->
                navController.navigate(SellerDiscountCreateEdit(code = code, isSeller = true))
            },
        )
    }

    composable<SellerVerificationStatusScreen> {
        SellerVerificationStatusScreen(
            onViewDashboardClick = { navController.navigate(SellerTab(tabIndex = 0)) },
            onNavigateBack = {
                navController.navigateUp()
            }
        )
    }

    // Product screens
    composable<SellerProductCreate> {
        SellerProductCreateScreen(
            onProductSaved = { productId ->
                navController.navigateUp()
            },
            onNavigateBack = {
                navController.navigateUp()
            },
        )
    }

    composable<SellerProductEdit> {
        SellerProductEditScreen(
            onNavigateBack = {
                navController.navigateUp()
            },
        )
    }

    composable<CustomerProductDetail> {
        CustomerProductDetailScreen(
            onNavigateBack = {
                navController.navigateUp()
            },
            onNavigateToCart = {
                navController.navigate(CustomerTab(tabIndex = 1)) {
                    popUpTo<CustomerTab> { inclusive = true }
                }
            },
        )
    }

    composable<SellerStorefront> {
        SellerStorefrontScreen(
            onProductClick = { productId ->
                navController.navigate(CustomerProductDetail(productId))
            },
        )
    }

    composable<QueueManagement> {
        QueueManagementScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }

    // Checkout screens
    composable<Checkout> {
        CheckoutScreen(
            onNavigateToAddAddress = { navController.navigate(AddressForm) },
            onNavigateToConfirmation = { orderId ->
                navController.navigate(OrderConfirmation(orderId)) {
                    popUpTo<CustomerTab> { inclusive = false }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<AddressForm> {
        AddressFormScreen(
            onAddressSaved = { navController.popBackStack() },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<OrderConfirmation> {
        val args = it.toRoute<OrderConfirmation>()
        OrderConfirmationScreen(
            orderId = args.orderId,
            onContinueShopping = {
                navController.navigate(CustomerTab(tabIndex = 0)) {
                    popUpTo<CustomerTab> { inclusive = true }
                }
            },
            onViewOrder = { orderId ->
                navController.navigate(OrderDetail(orderId))
            },
        )
    }

    composable<OrderDetail> {
        val args = it.toRoute<OrderDetail>()
        MinimalOrderScreen(
            orderId = args.orderId,
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<SellerDiscountCreateEdit> { backStackEntry ->
        val args = backStackEntry.toRoute<SellerDiscountCreateEdit>()
        SellerDiscountCreateEditScreen(
            code = args.code,
            isSeller = args.isSeller,
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
