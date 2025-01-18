package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.wenubey.wenucommerce.screens.auth.LoginScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.authNavGraph(navController: NavHostController) {
    navigation<Graph.AuthGraph>(startDestination = AuthScreens.Login) {
        composable<AuthScreens.Login> {
            LoginScreen(
                navigateToTabScreen = {
                    navController.navigate(Graph.TabGraph) {
                        popUpTo(Graph.AuthGraph) {
                            inclusive = true
                        }
                    }
                },
            )
        }
    }
}

@Serializable
sealed class AuthScreens {
    @Serializable
    data object Login : AuthScreens()

    @Serializable
    data object Register : AuthScreens()

    @Serializable
    data object ForgotPassword : AuthScreens()
}