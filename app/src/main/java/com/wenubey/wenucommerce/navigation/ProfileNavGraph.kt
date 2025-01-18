package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.wenubey.wenucommerce.screens.profile.ProfileScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.profileNavGraph(navController: NavController) {
    navigation<Graph.ProfileGraph>(startDestination = ProfileScreens.Profile) {
        composable<ProfileScreens.Profile> {
            ProfileScreen()
        }
    }
}

@Serializable
sealed class ProfileScreens {
    @Serializable
    data object Profile : ProfileScreens()
}