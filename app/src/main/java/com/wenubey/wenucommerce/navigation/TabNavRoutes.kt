package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.wenubey.wenucommerce.admin.AdminTabScreen
import com.wenubey.wenucommerce.customer.CustomerTabScreen
import com.wenubey.wenucommerce.customer.customer_products.CustomerProductDetailScreen
import com.wenubey.wenucommerce.seller.SellerTabScreen
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
        )
    }
    composable<AdminTab> {
        val tabArgs = it.toRoute<AdminTab>()
        AdminTabScreen(tabIndex = tabArgs.tabIndex)
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
        )
    }

    composable<SellerStorefront> {
        SellerStorefrontScreen(
            onProductClick = { productId ->
                navController.navigate(CustomerProductDetail(productId))
            },
        )
    }
}
