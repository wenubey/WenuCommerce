package com.wenubey.wenucommerce.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.wenubey.domain.repository.EmailAuthRepository
import com.wenubey.wenucommerce.navigation.SignIn
import com.wenubey.wenucommerce.navigation.Tab
import com.wenubey.wenucommerce.navigation.VerifyEmail

class AuthViewModel(
    private val repo: EmailAuthRepository,
): ViewModel() {
    private var authState by mutableStateOf(false)
    init {
        authState = repo.getAuthState()
    }
    fun getStartDestination(): Any {
        return if (authState) {
            SignIn
        } else{
            if (isEmailVerified) {
                Tab
            } else {
                VerifyEmail
            }
        }
    }

    private val isEmailVerified get() = repo.currentUser?.isEmailVerified ?: false


}