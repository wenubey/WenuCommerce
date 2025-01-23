package com.wenubey.wenucommerce.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.wenucommerce.navigation.SignUp
import com.wenubey.wenucommerce.navigation.Tab
import com.wenubey.wenucommerce.navigation.VerifyEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _startDestination = MutableStateFlow<Any>(SignUp)
    val startDestination = _startDestination.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.isUserAuthenticatedAndEmailVerified()
                .onSuccess { authState ->
                    viewModelScope.launch(mainDispatcher) {
                        updateStartDestination(authState.isAuthenticated, authState.isEmailVerified, authState.userEmail ?: "")
                    }
                }
        }
    }

    private fun updateStartDestination(isAuthenticated: Boolean, isEmailVerified: Boolean, userEmail: String) {
        viewModelScope.launch(mainDispatcher) {
            _startDestination.update {
                when {
                    isAuthenticated && isEmailVerified -> Tab(0)
                    isAuthenticated && !isEmailVerified -> VerifyEmail(userEmail)
                    else -> SignUp
                }
            }
        }
    }
}

