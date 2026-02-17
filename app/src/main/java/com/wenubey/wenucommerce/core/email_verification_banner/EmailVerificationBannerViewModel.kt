package com.wenubey.wenucommerce.core.email_verification_banner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.data.repository.NotificationPreferences
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmailVerificationBannerViewModel(
    private val authRepository: AuthRepository,
    private val notificationPreferences: NotificationPreferences,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val ioDispatcher = dispatcherProvider.io()
    private val mainDispatcher = dispatcherProvider.main()


    private val _emailVerificationBannerState = MutableStateFlow(EmailVerificationBannerState())
    val emailVerificationBannerState: StateFlow<EmailVerificationBannerState> =
        _emailVerificationBannerState.asStateFlow()


    fun onAction(action: EmailVerificationBannerAction) {
        when (action) {
            is EmailVerificationBannerAction.DoNotShowAgain -> doNotShowAgain()
            is EmailVerificationBannerAction.HideNotificationForSession -> hideNotificationForSession()
        }
    }

    init {
        checkEmailVerificationStatus()
    }

    private fun checkEmailVerificationStatus() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.isUserAuthenticated().fold(
                onSuccess = { isAuthenticated ->
                    if (isAuthenticated) {
                        authRepository.isEmailVerified().fold(
                            onSuccess = { isVerified ->
                                _emailVerificationBannerState.update { it.copy(isEmailVerified = isVerified) }
                                updateNotificationBarState(isVerified)
                            },
                            onFailure = {
                                _emailVerificationBannerState.update { it.copy(isEmailVerified = true) }
                                updateNotificationBarState(true)
                            }
                        )
                    } else {
                        _emailVerificationBannerState.update { it.copy(isEmailVerified = true) }
                        updateNotificationBarState(true)
                    }
                },
                onFailure = {
                    _emailVerificationBannerState.update { it.copy(isEmailVerified = true) }
                    updateNotificationBarState(true)
                }
            )
        }
    }

    private fun updateNotificationBarState(isEmailVerified: Boolean) {
        viewModelScope.launch(mainDispatcher) {
            val isPermanentlyHidden =
                notificationPreferences.isEmailVerificationPermanentlyHidden()
            val currentState = _emailVerificationBannerState.value

            _emailVerificationBannerState.update {
                it.copy(
                    isVisible = !isEmailVerified && !isPermanentlyHidden && !currentState.isHiddenForSession,
                    isPermanentlyHidden = isPermanentlyHidden
                )
            }
        }
    }

    private fun hideNotificationForSession() {
        viewModelScope.launch(mainDispatcher) {
            _emailVerificationBannerState.update {
                it.copy(
                    isVisible = false,
                    isHiddenForSession = true
                )
            }
        }
    }

    private fun doNotShowAgain() {
        viewModelScope.launch(ioDispatcher) {
            notificationPreferences.setEmailVerificationPermanentlyHidden(true)
            withContext(mainDispatcher) {
                _emailVerificationBannerState.update {
                    it.copy(
                        isVisible = false,
                        isPermanentlyHidden = true
                    )
                }
            }
        }
    }
}