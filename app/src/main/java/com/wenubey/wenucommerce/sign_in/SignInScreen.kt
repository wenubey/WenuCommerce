package com.wenubey.wenucommerce.sign_in

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.domain.model.user.UserRole
import org.koin.androidx.compose.koinViewModel

@Composable
fun SignInScreen(
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = koinViewModel(),
    navigateToTab: (userRole: UserRole) -> Unit,
    navigateToVerifyEmail: (String) -> Unit,
) {
    val signInState by viewModel.signInState.collectAsStateWithLifecycle()

    LaunchedEffect(signInState) {
        if (!signInState.isEmailVerified && signInState.isUserSignedIn) {
            navigateToVerifyEmail(signInState.email)
        }
        if (signInState.isUserSignedIn) {
            navigateToTab(signInState.userRole)
        }
    }
    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(value = signInState.email, onValueChange = {
                viewModel.onAction(SignInAction.OnEmailChange(it))
            })
            TextField(value = signInState.password, onValueChange = {
                viewModel.onAction(SignInAction.OnPasswordChange(it))
            })

            Row {
                Text("Save credentials for future logins")
                Switch(
                    checked = signInState.saveCredentials,
                    onCheckedChange = {
                        viewModel.onAction(SignInAction.OnToggleCredentials)
                    }
                )
            }
            Button(
                onClick = {
                    viewModel.onAction(SignInAction.OnSignWithEmailPassword)
                }
            ) {
                Text("Sign In")
            }
            if (signInState.errorMessage != null) {
                Text("Error: ${signInState.errorMessage}")

            }

            Button(
                onClick =  {
                    viewModel.onAction(SignInAction.OnSignInClicked)
                }
            ) {
                Text("Sign in with saved credentials.")
            }
        }
    }

}
