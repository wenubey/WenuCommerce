package com.wenubey.wenucommerce.sign_in

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.auth.SignInResult
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignInViewModel(
    private val authRepository: AuthRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _signInState = MutableStateFlow(SignInState())
    val signInState: StateFlow<SignInState> = _signInState.asStateFlow()

    fun onAction(action: SignInAction) {
        when (action) {
            is SignInAction.OnEmailChange -> onEmailChange(action.email)
            is SignInAction.OnPasswordChange -> onPasswordChange(action.password)
            is SignInAction.OnSignInClicked -> signIn()
            is SignInAction.OnSignWithEmailPassword -> signInWithEmailPassword()
            is SignInAction.OnToggleCredentials -> toggleCredentials()
        }
    }

    private fun toggleCredentials() {
        viewModelScope.launch(mainDispatcher) {
            _signInState.update {
                it.copy(
                    saveCredentials = !signInState.value.saveCredentials
                )
            }
        }
    }

    private fun onEmailChange(email: String) {
        viewModelScope.launch(mainDispatcher) {
            _signInState.update {
                it.copy(
                    email = email,
                    isEmailValid = isValidEmail(email),
                    errorMessage = null
                )
            }
        }
    }

    private fun onPasswordChange(password: String) {
        viewModelScope.launch(mainDispatcher) {
            _signInState.update {
                it.copy(
                    password = password,
                    isPasswordValid = isValidPassword(password),
                    errorMessage = null
                )
            }
        }
    }

    private fun signIn() = viewModelScope.launch(ioDispatcher) {
        clearErrorMessage()
        authRepository.getCredential()
            .onSuccess { credentialResponse ->
                if (credentialResponse != null) {
                    authRepository.signIn(credentialResponse)
                }
            }
            .onFailure { error ->
                viewModelScope.launch(mainDispatcher) {
                    _signInState.update {
                        it.copy(errorMessage = error.message)
                    }
                }
            }


    }

    private fun signInWithEmailPassword() = viewModelScope.launch(ioDispatcher) {
        clearErrorMessage()
        val email = signInState.value.email
        val password = signInState.value.password
        val saveCredentials = signInState.value.saveCredentials
        when (val result =
            authRepository.signInWithEmailPassword(email, password, saveCredentials)) {
            is SignInResult.Success -> {
                val isVerified = authRepository.isEmailVerified().getOrNull()
                _signInState.update { currentState ->
                    currentState.copy(
                        isEmailVerified = isVerified ?: false,
                        isUserSignedIn = true
                    )
                }

            }

            SignInResult.Cancelled -> {
                viewModelScope.launch(mainDispatcher) {
                    _signInState.update {
                        it.copy(errorMessage = "Sign in cancelled.")
                    }
                }
            }

            is SignInResult.Failure -> {
                viewModelScope.launch(mainDispatcher) {
                    _signInState.update {
                        it.copy(errorMessage = result.errorMessage)
                    }
                }
            }

            SignInResult.NoCredentials -> {
                viewModelScope.launch(mainDispatcher) {
                    _signInState.update {
                        it.copy(errorMessage = "No credentials provided.")
                    }
                }
            }
        }
    }

    private fun clearErrorMessage() =
        viewModelScope.launch(mainDispatcher) { _signInState.update { it.copy(errorMessage = null) } }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidPassword(password: String): Boolean {
        val passwordRegex = "^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9])(?=.*?[#?!@\$ %^&*-]).{8,}\$"
        return password.matches(passwordRegex.toRegex())
    }
}