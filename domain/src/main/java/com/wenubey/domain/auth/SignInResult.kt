package com.wenubey.domain.auth

import com.wenubey.domain.model.user.User

sealed interface SignInResult {
    data class Success(val user: User): SignInResult
    data object Cancelled: SignInResult
    data class Failure(val errorMessage: String?): SignInResult
    data object NoCredentials: SignInResult
}