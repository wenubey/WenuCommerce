package com.wenubey.wenucommerce.screens.auth.sign_in

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wenubey.wenucommerce.viewmodels.SignInUiState

@Composable
fun SignInScreen(
    modifier: Modifier = Modifier,
    onSignInClicked: (email: String, password: String) -> Unit,
    signInUiState: SignInUiState,
    navigateToTab: () -> Unit,
    navigateToVerifyEmail: (String) -> Unit,
) {
    var errorMessage by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(signInUiState) {
        when(signInUiState) {
            is SignInUiState.Success -> navigateToTab.invoke()
            is SignInUiState.Error -> errorMessage = signInUiState.message
            is SignInUiState.EmailVerificationRequired -> navigateToVerifyEmail(email)
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
            TextField(value = email, onValueChange = { email = it })
            TextField(value = password, onValueChange = { password = it })
            Button(
                onClick = {
                    onSignInClicked(email, password)
                }
            ) {
                Text("Sign In")
            }

            Text("Error: $errorMessage")
        }
    }

}
