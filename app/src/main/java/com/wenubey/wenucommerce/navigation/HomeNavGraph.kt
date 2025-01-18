package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.wenubey.wenucommerce.screens.home.HomeScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.homeNavGraph(navController: NavController) {
    navigation<Graph.HomeGraph>(startDestination = HomeScreens.Home) {
        composable<HomeScreens.Home> {
            HomeScreen()
        }
    }
}

@Serializable
sealed class HomeScreens {
    @Serializable
    data object Home : HomeScreens()
}