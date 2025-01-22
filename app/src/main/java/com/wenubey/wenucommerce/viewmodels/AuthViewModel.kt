package com.wenubey.wenucommerce.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.wenucommerce.navigation.SignUp
import com.wenubey.wenucommerce.navigation.Tab
import com.wenubey.wenucommerce.navigation.VerifyEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _authState = MutableStateFlow(AuthState())


    private val _startDestination = MutableStateFlow<Any>(SignUp)
    val startDestination: StateFlow<Any> = _startDestination

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.isUserAuthenticatedAndEmailVerified()
                .onSuccess { authState ->
                    viewModelScope.launch(mainDispatcher) {
                        _authState.update {
                            it.copy(
                                isAuthenticated = authState.isAuthenticated,
                                isEmailVerified = authState.isEmailVerified,
                                userEmail = authState.userEmail,
                                errorMessage = null,
                            )
                        }
                        updateStartDestination(authState.isAuthenticated, authState.isEmailVerified)
                    }
                }
                .onFailure {
                    viewModelScope.launch(mainDispatcher) {
                        _authState.update {
                            it.copy(
                                errorMessage = it.errorMessage
                            )
                        }
                    }
                }
        }
    }

    private fun updateStartDestination(isAuthenticated: Boolean, isEmailVerified: Boolean) {
        viewModelScope.launch(mainDispatcher) {
            _startDestination.update {
                when {
                    isAuthenticated && isEmailVerified -> Tab(0)
                    isAuthenticated && !isEmailVerified -> VerifyEmail(_authState.value.userEmail ?: "")
                    else -> SignUp
                }
            }
        }
    }
}


data class AuthState(
    val isAuthenticated: Boolean = false,
    val isEmailVerified: Boolean = false,
    val userEmail: String? = null,
    val errorMessage: String? = null,
)

