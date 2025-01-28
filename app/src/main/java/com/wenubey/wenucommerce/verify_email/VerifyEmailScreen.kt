package com.wenubey.wenucommerce.verify_email

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun VerifyEmailScreen(
    modifier: Modifier = Modifier,
    viewModel: VerifyEmailViewModel = koinViewModel(),
    emailArg: String,
    navigateToTab: () -> Unit,
) {
    val state by viewModel.verifyEmailState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state.isEmailVerified) {
            navigateToTab()
            viewModel.onAction(VerifyEmailAction.StopVerificationCheck)
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Text("You need to verify your email address to use app.")
            Button(onClick = { viewModel.onAction(VerifyEmailAction.ResendVerificationEmail) }) {
                Text("Resend verification email to $emailArg")
            }

        }

    }
}