package com.wenubey.wenucommerce.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.wenubey.wenucommerce.screens.auth.sign_in.SignInScreen
import com.wenubey.wenucommerce.viewmodels.SignInViewModel
import com.wenubey.wenucommerce.screens.auth.sign_up.SignUpScreen
import com.wenubey.wenucommerce.screens.auth.verify_email.VerifyEmailScreen
import com.wenubey.wenucommerce.viewmodels.VerifyEmailViewModel
import org.koin.androidx.compose.koinViewModel

fun NavGraphBuilder.authNavRoutes(navController: NavHostController) {
    composable<SignIn> {
        val signInViewModel: SignInViewModel = koinViewModel()
        val signInUiState by signInViewModel.signInUiState.collectAsStateWithLifecycle()
        SignInScreen(
            onSignInClicked = { email, password ->
                signInViewModel.signInWithEmail(email, password)
            },
            signInUiState = signInUiState,
            navigateToTab = {
                navController.navigate(Tab(0))
            },
            navigateToVerifyEmail = { email ->
                navController.navigate(VerifyEmail(email))
            }
        )
    }
    composable<SignUp> {
        SignUpScreen()
    }
    composable<VerifyEmail> { navBackStack ->
        val email = navBackStack.toRoute<VerifyEmail>().email
        val viewModel: VerifyEmailViewModel = koinViewModel()
        val verifyEmailState = viewModel.verifyEmailState.collectAsStateWithLifecycle().value
        VerifyEmailScreen(
            emailArg = email,
            resendVerificationEmail = {
                viewModel.resendVerificationEmail()
            },
            verifyEmailState = verifyEmailState,
            navigateToTab = {
                navController.navigate(Tab(0))
            },
        )
    }
}

