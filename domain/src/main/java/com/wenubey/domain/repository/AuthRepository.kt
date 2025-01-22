package com.wenubey.domain.repository

import androidx.credentials.GetCredentialResponse
import com.wenubey.domain.model.AuthState

interface AuthRepository {
    suspend fun signIn(credentialResponse: GetCredentialResponse): Result<Unit>
    suspend fun getCredential(): Result<GetCredentialResponse?>
    suspend fun signInWithEmailPassword(email: String, password: String): Result<Unit>
    suspend fun logOut(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun resendVerificationEmail(): Result<Unit>
    suspend fun isUserAuthenticatedAndEmailVerified(): Result<AuthState>
}