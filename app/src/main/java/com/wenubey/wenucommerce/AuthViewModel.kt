package com.wenubey.wenucommerce

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.wenucommerce.navigation.SignUp
import com.wenubey.wenucommerce.navigation.Tab
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
            authRepository.isUserAuthenticated()
                .onSuccess { isUserAuthenticated ->
                    viewModelScope.launch(mainDispatcher) {
                        updateStartDestination(isUserAuthenticated)
                    }
                }
        }
    }

    private fun updateStartDestination(isAuthenticated: Boolean) {
        viewModelScope.launch(mainDispatcher) {
            _startDestination.update {
                if (isAuthenticated) Tab(0) else SignUp
            }
        }
    }
}

