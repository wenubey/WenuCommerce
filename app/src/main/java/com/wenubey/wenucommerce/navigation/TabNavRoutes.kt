package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.wenubey.wenucommerce.screens.tab.TabScreen

fun NavGraphBuilder.tabNavRoutes(navController: NavController) {
    composable<Tab> {
        val tabArgs = it.toRoute<Tab>()
        TabScreen(tabIndex = tabArgs.tabIndex)
    }
}



