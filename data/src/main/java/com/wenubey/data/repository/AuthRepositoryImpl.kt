package com.wenubey.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.AuthState
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class AuthRepositoryImpl(
    private val credentialManager: CredentialManager,
    private val firebaseAuth: FirebaseAuth,
    private val context: Context,
    private val googleIdOption: GetGoogleIdOption,
    dispatcherProvider: DispatcherProvider,
) : AuthRepository {

    private val ioDispatcher = dispatcherProvider.io()

    override suspend fun signIn(credentialResponse: GetCredentialResponse): Result<Unit> =
        safeApiCall(dispatcher = ioDispatcher) {
            when (val credential = credentialResponse.credential) {
                is PasswordCredential -> {
                    val email = credential.id
                    val password = credential.password
                    val user = firebaseAuth.signInWithEmailAndPassword(email, password)
                        .await().user
                    sendEmailVerificationIfNeeded(user)
                }

                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        val firebaseGoogleCredential =
                            GoogleAuthProvider.getCredential(idToken, null)
                        val user =
                            firebaseAuth.signInWithCredential(firebaseGoogleCredential).await().user
                        sendEmailVerificationIfNeeded(user)
                    } else {
                        throw IllegalArgumentException("Unrecognized credential type")
                    }
                }

                else -> throw IllegalArgumentException("Unsupported credential type")
            }
        }

    override suspend fun getCredential(): Result<GetCredentialResponse?> =
        safeApiCall(dispatcher = ioDispatcher) {
            val passwordOption = GetPasswordOption()
            val googleIdOption = googleIdOption
            val credentialRequest = GetCredentialRequest(
                credentialOptions = listOf(
                    passwordOption,
                    googleIdOption
                )
            )
            credentialManager.getCredential(
                context = context,
                request = credentialRequest
            )
        }


    override suspend fun signInWithEmailPassword(
        email: String,
        password: String
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        val passwordRequest = CreatePasswordRequest(
            id = email,
            password = password
        )

        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        result.user?.let { user ->
            credentialManager.createCredential(
                context = context,
                request = passwordRequest
            )
            sendEmailVerificationIfNeeded(user)
        }
    }

    override suspend fun isUserAuthenticatedAndEmailVerified(): Result<AuthState> =
        safeApiCall(ioDispatcher) {
            val user = firebaseAuth.currentUser
            user?.reload()?.await()
            val isAuthenticated = user != null
            val isEmailVerified = user?.isEmailVerified ?: false
            AuthState(
                isAuthenticated = isAuthenticated,
                isEmailVerified = isEmailVerified,
                userEmail = user?.email
            )
        }



    override suspend fun resendVerificationEmail(): Result<Unit> = safeApiCall(ioDispatcher) {
        val user =
            firebaseAuth.currentUser ?: throw IllegalStateException("No authenticated user found")
        sendEmailVerificationIfNeeded(user)
    }

    override suspend fun logOut(): Result<Unit> = safeApiCall(ioDispatcher) {
        firebaseAuth.signOut()
        val clearCredentialRequest = ClearCredentialStateRequest()
        credentialManager.clearCredentialState(
            clearCredentialRequest
        )
    }

    override suspend fun deleteAccount(): Result<Unit> = safeApiCall(ioDispatcher) {
        firebaseAuth.currentUser?.delete()
        val clearCredentialRequest = ClearCredentialStateRequest()
        credentialManager.clearCredentialState(
            clearCredentialRequest
        )
    }

    private suspend fun sendEmailVerificationIfNeeded(user: FirebaseUser?) {
        if (user != null && !user.isEmailVerified) {
            user.sendEmailVerification().await()
            Timber.d("Verification email sent to ${user.email}")
        }
    }
}