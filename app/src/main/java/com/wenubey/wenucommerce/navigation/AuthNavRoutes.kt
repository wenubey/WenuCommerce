package com.wenubey.wenucommerce.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.wenubey.wenucommerce.screens.auth.sign_in.SignInScreen
import com.wenubey.wenucommerce.screens.auth.sign_in.SignInViewModel
import com.wenubey.wenucommerce.screens.auth.verify_email.VerifyEmailScreen
import org.koin.androidx.compose.koinViewModel

fun NavGraphBuilder.authNavRoutes(navController: NavHostController) {
    composable<SignIn> {
        val signInViewModel: SignInViewModel = koinViewModel()
        val signInUiState by signInViewModel.signInUiState.collectAsStateWithLifecycle()
        SignInScreen(
            onSignInClicked = { email, password ->
                signInViewModel.signInWithEmail(email, password)
            },
            signInUiState = signInUiState
        )
    }
    composable<VerifyEmail> {
        VerifyEmailScreen()
    }
}

