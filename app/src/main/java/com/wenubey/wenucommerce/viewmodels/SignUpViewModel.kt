package com.wenubey.wenucommerce.viewmodels

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SignUpViewModel(
    private val authRepository: AuthRepository,
): ViewModel() {

    private val _signUpState = MutableStateFlow(SignUpState())
    val signUpState = _signUpState.asStateFlow()

    fun signIn(onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            val credentials = authRepository.getCredential().getOrNull()
            authRepository.signIn(credentials ?: return@launch).onSuccess {
                onSuccess.invoke()
            }.onFailure {
                onFailure.invoke()
            }
        }
    }


    fun updateEmail(email: String) {
        _signUpState.value = _signUpState.value.copy(
            email = email,
            isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
        )
    }

    fun updatePassword(password: String) {
        _signUpState.value = _signUpState.value.copy(
            password = password
        )
    }

    fun createPasswordCredential() {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.signInWithEmailPassword(
                email = _signUpState.value.email,
                password = _signUpState.value.password
            )
        }
    }


}

data class SignUpState(
    val email: String = "",
    val isEmailValid: Boolean = false,
    val password: String = "",
    val isEmailVerified: Boolean = false,
)


