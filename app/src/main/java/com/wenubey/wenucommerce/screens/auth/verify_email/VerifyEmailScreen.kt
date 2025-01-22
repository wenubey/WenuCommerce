package com.wenubey.wenucommerce.screens.auth.verify_email

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wenubey.wenucommerce.viewmodels.VerifyEmailState

@Composable
fun VerifyEmailScreen(
    modifier: Modifier = Modifier,
    emailArg: String,
    verifyEmailState: VerifyEmailState,
    resendVerificationEmail: () -> Unit,
    navigateToTab: () -> Unit
) {
    var errorMessage by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(verifyEmailState) {
        if (verifyEmailState.isEmailVerified) {
            navigateToTab()
        } else if (verifyEmailState.errorMessage != null) {
            errorMessage = verifyEmailState.errorMessage
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Text("You need to verify your email address to use app.")
            Button(onClick = { resendVerificationEmail() }) {
                Text("Resend verification email to $emailArg")
            }
            Text("Error: $errorMessage")
        }

    }
}