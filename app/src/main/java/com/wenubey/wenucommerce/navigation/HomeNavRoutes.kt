package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.wenubey.wenucommerce.screens.home.HomeScreen

fun NavGraphBuilder.homeNavRoutes(navController: NavController) {
    composable<Home> {
        HomeScreen()
    }

}



