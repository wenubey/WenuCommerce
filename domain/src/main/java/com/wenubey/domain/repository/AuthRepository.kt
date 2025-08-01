package com.wenubey.domain.repository

import androidx.credentials.GetCredentialResponse
import com.google.firebase.auth.FirebaseUser
import com.wenubey.domain.auth.SignInResult
import com.wenubey.domain.auth.SignUpResult
import com.wenubey.domain.model.user.User
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentFirebaseUser: FirebaseUser?
    val currentUser: StateFlow<User?>

    suspend fun signIn(credentialResponse: GetCredentialResponse): Result<User>
    suspend fun getCredential(): Result<GetCredentialResponse?>
    suspend fun signInWithEmailPassword(email: String, password: String, saveCredentials: Boolean): SignInResult
    suspend fun signUpWithEmailPassword(email: String, password: String, saveCredentials: Boolean): SignUpResult
    suspend fun logOut(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun resendVerificationEmail(): Result<Unit>
    suspend fun isUserAuthenticated(): Result<Boolean>
    suspend fun isEmailVerified(): Result<Boolean>
    suspend fun isPhoneNumberVerified(): Result<Boolean>

    // New methods for user state management
    suspend fun refreshCurrentUser(): Result<User?>
    suspend fun setCurrentUserAfterOnboarding(user: User)
}