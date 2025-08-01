package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.wenubey.wenucommerce.screens.admin.AdminTabScreen
import com.wenubey.wenucommerce.screens.customer.CustomerTabScreen
import com.wenubey.wenucommerce.screens.seller.SellerTabScreen

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
        SellerTabScreen(tabIndex = tabArgs.tabIndex)
    }
}



