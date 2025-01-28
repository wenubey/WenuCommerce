package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.wenubey.wenucommerce.verify_email.VerifyEmailScreen
import com.wenubey.wenucommerce.sign_in.SignInScreen
import com.wenubey.wenucommerce.sign_up.SignUpScreen

fun NavGraphBuilder.authNavRoutes(navController: NavHostController) {
    composable<SignIn> {
        SignInScreen(
            navigateToTab = {
                navController.navigate(Tab(tabIndex = 0))
            },
            navigateToVerifyEmail = { email ->
                navController.navigate(VerifyEmail(email))
            },
        )
    }
    composable<SignUp> {
        SignUpScreen(
            navigateToTab = {
                navController.navigate(Tab(0))
            },
            navigateToSignIn = {
                navController.navigate(SignIn)
            }
        )
    }
    composable<VerifyEmail> { navBackStack ->
        val email = navBackStack.toRoute<VerifyEmail>().email
        VerifyEmailScreen(
            emailArg = email,
            navigateToTab = {
                navController.navigate(Tab(0))
            },
        )
    }
}

