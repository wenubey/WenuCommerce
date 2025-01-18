package com.wenubey.wenucommerce.screens.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LoginScreen(modifier: Modifier = Modifier, navigateToTabScreen: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Login Screen")
        Button(onClick = navigateToTabScreen) {
            Text("Navigate to Tab Screen")
        }
    }
}