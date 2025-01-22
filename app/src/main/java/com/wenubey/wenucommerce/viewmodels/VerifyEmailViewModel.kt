package com.wenubey.wenucommerce.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class VerifyEmailViewModel(
    private val authRepository: AuthRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _verifyEmailState = MutableStateFlow(VerifyEmailState())
    val verifyEmailState = _verifyEmailState.asStateFlow()

    private var emailCheckJob: Job? = null

    init {
        startEmailVerificationCheck()
    }

    private fun startEmailVerificationCheck() {
        emailCheckJob = viewModelScope.launch(ioDispatcher) {
            while (true) {
                checkEmailVerificationStatus()
                delay(5000L)
            }
        }
    }

    private suspend fun checkEmailVerificationStatus() {
        Timber.d("Checking email verification status: ${_verifyEmailState.value}")
        authRepository.isUserAuthenticatedAndEmailVerified()
            .onSuccess { authState ->
                viewModelScope.launch(mainDispatcher) {
                    _verifyEmailState.update {
                        it.copy(isEmailVerified = authState.isEmailVerified)
                    }
                }
            }.onFailure { error ->
                viewModelScope.launch(mainDispatcher) {
                    _verifyEmailState.update {
                        it.copy(errorMessage = error.message)
                    }
                }
            }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.resendVerificationEmail()
                .onSuccess {
                    viewModelScope.launch(mainDispatcher) {
                        _verifyEmailState.update {
                            it.copy(isVerificationEmailSent = true)
                        }
                    }
                }.onFailure { error ->
                    viewModelScope.launch(mainDispatcher) {
                        _verifyEmailState.update {
                            it.copy(
                                errorMessage = error.message,
                                isVerificationEmailSent = false,
                            )
                        }
                    }
                }
        }
    }

    private fun stopEmailVerificationCheck() {
        emailCheckJob?.cancel()
        emailCheckJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopEmailVerificationCheck()
    }

}

data class VerifyEmailState(
    val isEmailVerified: Boolean = false,
    val isVerificationEmailSent: Boolean = false,
    val errorMessage: String? = null
)

