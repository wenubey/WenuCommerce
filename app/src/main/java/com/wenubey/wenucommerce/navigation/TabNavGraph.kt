package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.wenubey.wenucommerce.screens.tab.TabScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.tabNavGraph(navController: NavController) {
    navigation<Graph.TabGraph>(startDestination = TabScreens.Tab(tabIndex = 0)) {
        composable<TabScreens.Tab> {
            val tabIndex = it.toRoute<TabScreens.Tab>().tabIndex
            TabScreen(tabIndex)
        }
    }
}


@Serializable
sealed class TabScreens {
    @Serializable
    data class Tab(val tabIndex: Int)
}
