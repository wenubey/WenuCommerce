package com.wenubey.domain.repository

import androidx.credentials.GetCredentialResponse
import com.wenubey.domain.auth.SignInResult
import com.wenubey.domain.auth.SignUpResult

interface AuthRepository {
    suspend fun signIn(credentialResponse: GetCredentialResponse): Result<Unit>
    suspend fun getCredential(): Result<GetCredentialResponse?>
    suspend fun signInWithEmailPassword(email: String, password: String, saveCredentials: Boolean): SignInResult
    suspend fun signUpWithEmailPassword(email: String, password: String, saveCredentials: Boolean): SignUpResult
    suspend fun logOut(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun resendVerificationEmail(): Result<Unit>
    suspend fun isUserAuthenticated(): Result<Boolean>
    suspend fun isEmailVerified(): Result<Boolean>
}