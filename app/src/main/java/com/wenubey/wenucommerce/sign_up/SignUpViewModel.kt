package com.wenubey.wenucommerce.sign_up

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.auth.SignUpResult
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignUpViewModel(
    private val authRepository: AuthRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {
    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _signUpState = MutableStateFlow(SignUpState())
    val signUpState = _signUpState.asStateFlow()

    fun onAction(action: SignUpAction) {
        when (action) {
            is SignUpAction.OnEmailChange -> onEmailChange(action.email)
            is SignUpAction.OnPasswordChange -> onPasswordChange(action.password)
            is SignUpAction.OnSignUpClicked -> signUp()
            is SignUpAction.OnSignUpEmailPassword -> signUpWithEmailPassword()
            is SignUpAction.OnToggleCredentials -> toggleCredentials()
        }
    }

    private fun onEmailChange(email: String) {
        viewModelScope.launch(mainDispatcher) {
            _signUpState.value = _signUpState.value.copy(
                email = email,
                isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
            )
        }
    }

    private fun onPasswordChange(password: String) {
        viewModelScope.launch(mainDispatcher) {
            _signUpState.value = _signUpState.value.copy(
                password = password,
                isPasswordValid = isValidPassword(password)
            )
        }
    }

    private fun signUp() = viewModelScope.launch(ioDispatcher) {
        authRepository.getCredential()
            .onSuccess { credentialResponse ->
                if (credentialResponse != null) {
                    authRepository.signIn(credentialResponse)
                }
            }.onFailure { error ->
                viewModelScope.launch(mainDispatcher) {
                    _signUpState.update {
                        it.copy(errorMessage = error.message)
                    }
                }
            }
    }

    private fun signUpWithEmailPassword() = viewModelScope.launch(ioDispatcher) {
        val email = signUpState.value.email
        val password = signUpState.value.password
        val saveCredentials = signUpState.value.saveCredentials
        val result = authRepository.signUpWithEmailPassword(email, password, saveCredentials)

        when (result) {
            is SignUpResult.Cancelled -> {
                viewModelScope.launch(mainDispatcher) {
                    _signUpState.update {
                        it.copy(errorMessage = "Sign up cancelled.")
                    }
                }
            }

            is SignUpResult.Failure -> {
                viewModelScope.launch(mainDispatcher) {
                    _signUpState.update {
                        it.copy(errorMessage = result.errorMessage)
                    }
                }
            }

            is SignUpResult.Success -> {
                val isVerified = authRepository.isEmailVerified().getOrNull()
                _signUpState.update { currentState ->
                    currentState.copy(
                        isEmailVerified = isVerified ?: false,
                        isUserSignedIn = true
                    )
                }
            }
        }
    }

    private fun toggleCredentials() = viewModelScope.launch(mainDispatcher) {
        _signUpState.update {
            it.copy(
                saveCredentials = !signUpState.value.saveCredentials
            )
        }
    }

    private fun isValidPassword(password: String): Boolean {
        val passwordRegex = "^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9])(?=.*?[#?!@\$ %^&*-]).{8,}\$"
        return password.matches(passwordRegex.toRegex())
    }
}



