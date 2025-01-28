package com.wenubey.domain.auth

sealed interface SignUpResult {
    data object Success: SignUpResult
    data object Cancelled: SignUpResult
    data class Failure(val errorMessage: String?): SignUpResult
}