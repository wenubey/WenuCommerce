package com.wenubey.wenucommerce.screens.tab

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.data.repository.NotificationPreferences
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TabViewModel(
    private val authRepository: AuthRepository,
    private val notificationPreferences: NotificationPreferences,
    dispatcherProvider: DispatcherProvider,
    private val savedStateHandle: SavedStateHandle,
): ViewModel() {

    companion object {
        private const val KEY_CURRENT_TAB_INDEX = "current_tab_index"
    }

    private val ioDispatcher = dispatcherProvider.io()
    // TODO Move to Customer, Seller viewModels and test is it working
    private val _notificationBarState = MutableStateFlow(NotificationBarState())
    val notificationBarState: StateFlow<NotificationBarState> = _notificationBarState.asStateFlow()

    private val _isEmailVerified = MutableStateFlow(true)

    // Use SavedStateHandle to persist tab state
    val currentTabIndex: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_CURRENT_TAB_INDEX, 0)

    init {
        checkEmailVerificationStatus()
    }

    // Initialize with navigation argument if needed
    fun initializeTabIfNeeded(navigationTabIndex: Int) {
        // Only set if no saved state exists (first time or fresh navigation)
        val currentSavedIndex = savedStateHandle.get<Int>(KEY_CURRENT_TAB_INDEX)
        if (currentSavedIndex == null || currentSavedIndex == 0) {
            savedStateHandle[KEY_CURRENT_TAB_INDEX] = navigationTabIndex
        }
    }

    private fun checkEmailVerificationStatus() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.isUserAuthenticated().fold(
                onSuccess = { isAuthenticated ->
                    if (isAuthenticated) {
                        authRepository.isEmailVerified().fold(
                            onSuccess = { isVerified ->
                                _isEmailVerified.value = isVerified
                                updateNotificationBarState(isVerified)
                            },
                            onFailure = {
                                _isEmailVerified.value = true
                                updateNotificationBarState(true)
                            }
                        )
                    } else {
                        _isEmailVerified.value = true
                        updateNotificationBarState(true)
                    }
                },
                onFailure = {
                    _isEmailVerified.value = true
                    updateNotificationBarState(true)
                }
            )
        }
    }

    private fun updateNotificationBarState(isEmailVerified: Boolean) {
        viewModelScope.launch {
            val isPermanentlyHidden = notificationPreferences.isEmailVerificationPermanentlyHiddenSync()
            val currentState = _notificationBarState.value

            _notificationBarState.value = currentState.copy(
                isVisible = !isEmailVerified && !isPermanentlyHidden && !currentState.isHiddenForSession,
                isPermanentlyHidden = isPermanentlyHidden
            )
        }
    }

    fun hideNotificationForSession() {
        _notificationBarState.value = _notificationBarState.value.copy(
            isVisible = false,
            isHiddenForSession = true
        )
    }

    fun doNotShowAgain() {
        viewModelScope.launch {
            notificationPreferences.setEmailVerificationPermanentlyHidden(true)
            _notificationBarState.value = _notificationBarState.value.copy(
                isVisible = false,
                isPermanentlyHidden = true
            )
        }
    }

    // Tab navigation functions
    fun updateCurrentTabIndex(tabIndex: Int) {
        savedStateHandle[KEY_CURRENT_TAB_INDEX] = tabIndex
    }
}