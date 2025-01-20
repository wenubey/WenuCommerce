package com.wenubey.wenucommerce.screens.auth.sign_in

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SignInScreen(modifier: Modifier = Modifier, onSignInClicked: (email: String, password: String) -> Unit, signInUiState: SignInUiState,) {

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

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
        }
    }

    SignIn(signInUiState = signInUiState) {
        println(it)
    }
}

@Composable
fun SignIn(signInUiState: SignInUiState, showErrorMessage: (String) -> Unit,) {
    when(signInUiState) {
        is SignInUiState.Loading -> {
            CircularProgressIndicator()
        }
        is SignInUiState.Error -> {
            LaunchedEffect(signInUiState) {
                showErrorMessage(signInUiState.message)
            }
        }
        is SignInUiState.Success -> {
            Unit
        }
    }
}