package com.wenubey.wenucommerce.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import kotlinx.serialization.Serializable

@Composable
fun RootNavigationGraph(navController: NavHostController, startDestination: Any) {
    NavHost(navController = navController, startDestination = startDestination) {
        authNavGraph(navController)
        tabNavGraph(navController)
        homeNavGraph(navController)
        cartNavGraph(navController)
        profileNavGraph(navController)
    }
}


@Serializable
sealed class Graph {
    @Serializable
    data object TabGraph: Graph()

    @Serializable
    data object AuthGraph: Graph()

    @Serializable
    data object HomeGraph: Graph()

    @Serializable
    data object CartGraph: Graph()

    @Serializable
    data object ProfileGraph: Graph()
}


