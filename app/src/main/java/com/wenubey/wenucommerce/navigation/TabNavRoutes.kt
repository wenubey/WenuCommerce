package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.wenubey.wenucommerce.admin.AdminTabScreen
import com.wenubey.wenucommerce.customer.CustomerTabScreen
import com.wenubey.wenucommerce.seller.SellerTabScreen
import com.wenubey.wenucommerce.seller.seller_verification.SellerVerificationStatusScreen

fun NavGraphBuilder.tabNavRoutes(navController: NavController) {
    composable<CustomerTab> {
        val tabArgs = it.toRoute<CustomerTab>()
        CustomerTabScreen(tabIndex = tabArgs.tabIndex)
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
                // TODO create SellerVerificationStatusScreen Object for navigation
                navController.navigate(SellerVerificationStatusScreen)
            }
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
}



