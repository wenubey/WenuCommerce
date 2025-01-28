package com.wenubey.domain.auth

sealed interface SignInResult {
    data object Success: SignInResult
    data object Cancelled: SignInResult
    data class Failure(val errorMessage: String?): SignInResult
    data object NoCredentials: SignInResult
}