package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.wenubey.domain.model.user.UserRole
import com.wenubey.wenucommerce.onboard.OnboardingScreen
import com.wenubey.wenucommerce.sign_in.SignInScreen
import com.wenubey.wenucommerce.sign_up.SignUpScreen
import com.wenubey.wenucommerce.verify_email.VerifyEmailScreen

fun NavGraphBuilder.authNavRoutes(navController: NavHostController) {
    composable<SignIn> {
        SignInScreen(
            navigateToTab = { user ->
                val userRole = user?.role
                when (userRole) {
                    UserRole.ADMIN -> navController.navigate(AdminTab(tabIndex = 0))
                    UserRole.CUSTOMER -> navController.navigate(CustomerTab(tabIndex = 0))
                    UserRole.SELLER -> navController.navigate(SellerTab(tabIndex = 0))
                    else -> navController.navigate(SignIn)
                }
            },
            navigateToVerifyEmail = { email ->
                navController.navigate(VerifyEmail(email))
            },
        )
    }
    composable<SignUp> {
        SignUpScreen(
            navigateToOnboarding = {
                navController.navigate(Onboarding)
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
                //navController.navigate(Tab(0))
            },
        )
    }

    composable<Onboarding> { navBackStackEntry ->
        OnboardingScreen(
            onNavigateToTabScreen = { userRole ->
                when (userRole) {
                    UserRole.ADMIN -> navController.navigate(AdminTab(tabIndex = 0))
                    UserRole.CUSTOMER -> navController.navigate(CustomerTab(tabIndex = 0))
                    UserRole.SELLER -> navController.navigate(SellerTab(tabIndex = 0))
                }
            }
        )
    }
}

