package com.wenubey.wenucommerce.sign_up

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun SignUpScreen(
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = koinViewModel(),
    navigateToTab: () -> Unit,
    navigateToSignIn: () -> Unit,
) {
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmailField(
                    email = signUpState.email,
                    isEmailValid = signUpState.isEmailValid,
                    updateEmail = { newValue ->
                        viewModel.onAction(SignUpAction.OnEmailChange(newValue))
                    }
                )

                PasswordField(
                    password = signUpState.password,
                    updatePassword = { newValue ->
                        viewModel.onAction(SignUpAction.OnPasswordChange(newValue))
                    }
                )

                Button(onClick = navigateToSignIn) {
                    Text("Already user? Sign in")
                }

                Row {
                    Text("Save credentials for future logins")
                    Switch(
                        checked = signUpState.saveCredentials,
                        onCheckedChange = {
                            viewModel.onAction(SignUpAction.OnToggleCredentials)
                        }
                    )
                }

                Button(
                    modifier = Modifier
                        .padding(
                            top = 16.dp
                        ),
                    onClick = {
                        keyboardController?.hide()
                        viewModel.onAction(SignUpAction.OnSignUpEmailPassword)
                        navigateToTab()

                    },
                    shape = ButtonDefaults.filledTonalShape,
                    enabled = signUpState.isEmailValid
                ) {
                    Text(
                        text = "Sign Up Account"
                    )
                }


                Button(
                    modifier = Modifier
                        .padding(
                            top = 16.dp
                        ),
                    onClick = {
                        viewModel.onAction(SignUpAction.OnSignUpClicked)
                        navigateToTab()
                    },
                    shape = ButtonDefaults.filledTonalShape,
                ) {
                    Text(
                        text = "Google"
                    )
                }
            }
        }
    }
}

@Composable
private fun EmailField(
    email: String,
    isEmailValid: Boolean,
    updateEmail: (String) -> Unit,
) {
    TextField(
        modifier = Modifier
            .width(300.dp)
            .padding(
                bottom = 6.dp
            )
            .clip(
                shape = ShapeDefaults.Small
            ),
        value = email,
        onValueChange = { newValue: String ->
            updateEmail(newValue)
        },
        maxLines = 1,
        label = {
            Text(
                text = "Email"
            )
        },
        placeholder = {
            Text(
                text = "MyEmail"
            )
        },
        trailingIcon = {
            if (email.isNotBlank()) {
                Icon(
                    imageVector = if (isEmailValid) Icons.Default.Check else Icons.Default.Error,
                    contentDescription = if (isEmailValid) Icons.Default.Check.name else Icons.Default.Error.name
                )
            }
        },
        isError = !isEmailValid,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = Icons.Default.Email.name
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done
        )
    )
}

@Composable
private fun PasswordField(
    password: String,
    updatePassword: (String) -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }

    TextField(
        modifier = Modifier
            .width(300.dp)
            .clip(
                shape = ShapeDefaults.Small
            ),
        value = password,
        onValueChange = { newValue: String ->
            updatePassword(newValue)
        },
        maxLines = 1,
        label = {
            Text(
                text = "Password"
            )
        },
        placeholder = {
            Text(
                text = "MyPassword"
            )
        },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconToggleButton(
                checked = showPassword,
                onCheckedChange = {
                    showPassword = !showPassword
                }
            ) {
                Icon(
                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (showPassword) Icons.Default.Visibility.name else Icons.Default.VisibilityOff.name
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Password,
                contentDescription = Icons.Default.Password.name
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        )
    )
}