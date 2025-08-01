package com.wenubey.wenucommerce

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.wenucommerce.navigation.AdminTab
import com.wenubey.wenucommerce.navigation.CustomerTab
import com.wenubey.wenucommerce.navigation.Onboarding
import com.wenubey.wenucommerce.navigation.SellerTab
import com.wenubey.wenucommerce.navigation.SignUp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber


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
        observeUserChanges()
    }

    private fun checkAuthState() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.isUserAuthenticated()
                .onSuccess { isUserAuthenticated ->
                    if (isUserAuthenticated) {
                        // Check if we have user data in currentUser StateFlow
                        val currentUser = authRepository.currentUser.value
                        viewModelScope.launch(mainDispatcher) {
                            if (currentUser != null) {
                                Timber.d("1.User Data: ${currentUser.role}")
                                updateStartDestination(true, currentUser.role)
                            } else {
                                // User is authenticated but no user data - might need onboarding
                                Timber.d("2.User Data Not Found")
                                updateStartDestination(true, null)
                            }
                        }
                    } else {
                        viewModelScope.launch(mainDispatcher) {
                            Timber.d("3.User Not Authenticated")
                            updateStartDestination(false, null)
                        }
                    }
                }
                .onFailure {
                    viewModelScope.launch(mainDispatcher) {
                        Timber.e(it, "Error checking authentication state")
                        updateStartDestination(false, null)
                    }
                }
        }
    }

    private fun observeUserChanges() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                when {
                    user == null -> {
                        // User signed out or not authenticated
                        _startDestination.update { SignUp }
                    }
                    else -> {
                        // User data is available, determine destination based on role
                        updateStartDestination(true, user.role)
                    }
                }
            }
        }
    }

    private fun updateStartDestination(isAuthenticated: Boolean, userRole: UserRole?) {
        viewModelScope.launch(mainDispatcher) {
            _startDestination.update {
                when {
                    !isAuthenticated -> SignUp
                    userRole == null -> Onboarding // User needs to complete onboarding
                    userRole == UserRole.CUSTOMER -> CustomerTab(0)
                    userRole == UserRole.SELLER -> SellerTab(0)
                    userRole == UserRole.ADMIN -> AdminTab(0)
                    else -> CustomerTab(0) // Default fallback
                }
            }
        }
    }


}