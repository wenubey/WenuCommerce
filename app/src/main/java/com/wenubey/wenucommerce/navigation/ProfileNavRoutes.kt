package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.wenubey.wenucommerce.screens.profile.ProfileScreen

fun NavGraphBuilder.profileNavRoutes(navController: NavController) {
    composable<Profile> {
        ProfileScreen()
    }
}

