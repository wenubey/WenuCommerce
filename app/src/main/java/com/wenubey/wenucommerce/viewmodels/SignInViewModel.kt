package com.wenubey.wenucommerce.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _signInUiState: MutableStateFlow<SignInUiState> =
        MutableStateFlow(SignInUiState.Error(""))
    val signInUiState: StateFlow<SignInUiState> =
        _signInUiState.asStateFlow()


    fun signInWithEmail(email: String, password: String) = viewModelScope.launch(ioDispatcher) {
        val result = authRepository.signInWithEmailPassword(email, password)

        result.onFailure { throwable ->
            viewModelScope.launch(mainDispatcher) {
                _signInUiState.update {
                    SignInUiState.Error(throwable.message ?: "Unknown Error")
                }
            }
        }
        result.onSuccess {
            val authState = authRepository.isUserAuthenticatedAndEmailVerified()
                .getOrNull()
            if (authState != null && !authState.isEmailVerified) {
                viewModelScope.launch(mainDispatcher) {
                    if (authState.userEmail != null) {
                        _signInUiState.update {
                            SignInUiState.EmailVerificationRequired(email)
                        }
                    } else {
                        _signInUiState.update {
                            SignInUiState.Error("Internal error occurred please try again.")
                        }
                    }
                }
            } else {
                viewModelScope.launch(mainDispatcher) {
                    _signInUiState.update { SignInUiState.Success(true) }
                }
            }
        }

    }
}

sealed interface SignInUiState {
    data class Error(val message: String) : SignInUiState
    data class Success(val isUserSigned: Boolean) : SignInUiState
    data class EmailVerificationRequired(val email: String) : SignInUiState

}