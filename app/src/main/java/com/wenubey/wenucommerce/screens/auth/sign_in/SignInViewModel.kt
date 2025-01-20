package com.wenubey.wenucommerce.screens.auth.sign_in

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.EmailAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignInViewModel(
    private val emailAuthRepository: EmailAuthRepository,
) : ViewModel() {

    private val _signInUiState: MutableStateFlow<SignInUiState> =
        MutableStateFlow(SignInUiState.Loading)
    val signInUiState: StateFlow<SignInUiState> =
        _signInUiState.asStateFlow()


    fun signInWithEmail(email: String, password: String) = viewModelScope.launch {
        _signInUiState.update { SignInUiState.Loading }

        val result = emailAuthRepository.signInWithEmailAndPassword(email, password)

        result.onFailure { throwable ->
            _signInUiState.update {
                SignInUiState.Error(throwable.message ?: "Unknown Error") }
        }
        result.onSuccess { isUserSigned ->
            _signInUiState.update { SignInUiState.Success(isUserSigned) }
        }

    }
}

sealed interface SignInUiState {
    data object Loading : SignInUiState
    data class Error(val message: String) : SignInUiState
    data class Success(val isUserSigned: Boolean) : SignInUiState
}