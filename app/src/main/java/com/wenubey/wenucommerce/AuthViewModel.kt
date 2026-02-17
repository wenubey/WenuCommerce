package com.wenubey.wenucommerce

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.wenucommerce.navigation.AdminTab
import com.wenubey.wenucommerce.navigation.CustomerTab
import com.wenubey.wenucommerce.navigation.SellerTab
import com.wenubey.wenucommerce.navigation.SignUp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber


class AuthViewModel(
    private val authRepository: AuthRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<Any>(SignUp)
    val startDestination = _startDestination.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    val currentUser = authRepository.currentUser

    init {
        observeUserChanges()
    }

    private fun observeUserChanges() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                when {
                    user != null -> {
                        // User data is available, determine destination based on role
                        Timber.d("User Data Loaded: ${user.role}")
                        updateStartDestination(user.role)
                        _isInitialized.value = true
                    }
                    authRepository.currentFirebaseUser == null -> {
                        // Not authenticated
                        Timber.d("User Not Authenticated")
                        _startDestination.update { SignUp }
                        _isInitialized.value = true
                    }
                    else -> {
                        // User is authenticated but data hasn't loaded yet - don't update destination
                        Timber.d("Waiting for user data to load...")
                    }
                }
            }
        }
    }

    private fun updateStartDestination(userRole: UserRole) {
        viewModelScope.launch(dispatcherProvider.main()) {
            _startDestination.update {
                when (userRole) {
                    UserRole.CUSTOMER -> CustomerTab(0)
                    UserRole.SELLER -> SellerTab(0)
                    UserRole.ADMIN -> AdminTab(0)
                }
            }
        }
    }


}