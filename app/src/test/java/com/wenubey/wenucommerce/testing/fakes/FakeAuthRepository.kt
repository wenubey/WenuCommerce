package com.wenubey.wenucommerce.testing.fakes

import androidx.credentials.GetCredentialResponse
import com.google.firebase.auth.FirebaseUser
import com.wenubey.domain.auth.SignInResult
import com.wenubey.domain.auth.SignUpResult
import com.wenubey.domain.model.user.User
import com.wenubey.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(
    initialUser: User? = null,
) : AuthRepository {

    private val _currentUser = MutableStateFlow(initialUser)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    var hasFirebaseUser: Boolean = initialUser != null
    override val currentFirebaseUser: FirebaseUser? get() = null // never touched by tests

    var signInResult: SignInResult = SignInResult.Failure("not stubbed")
    var signUpResult: SignUpResult = SignUpResult.Failure("not stubbed")
    var logOutResult: Result<Unit> = Result.success(Unit)
    var deleteAccountResult: Result<Unit> = Result.success(Unit)
    var resendVerificationResult: Result<Unit> = Result.success(Unit)
    var isAuthenticatedResult: Result<Boolean> = Result.success(false)
    var isEmailVerifiedResult: Result<Boolean> = Result.success(false)
    var isPhoneVerifiedResult: Result<Boolean> = Result.success(false)
    var refreshResult: Result<User?> = Result.success(null)
    var credentialResult: Result<GetCredentialResponse?> = Result.success(null)
    var signInCredentialResult: Result<User> = Result.failure(IllegalStateException("not stubbed"))

    val signInWithEmailCalls = mutableListOf<Triple<String, String, Boolean>>()
    val signUpWithEmailCalls = mutableListOf<Triple<String, String, Boolean>>()
    var logOutCallCount = 0
    var deleteAccountCallCount = 0
    var resendVerificationCallCount = 0

    fun emitUser(user: User?) {
        _currentUser.value = user
        hasFirebaseUser = user != null
    }

    override suspend fun signIn(credentialResponse: GetCredentialResponse): Result<User> =
        signInCredentialResult

    override suspend fun getCredential(): Result<GetCredentialResponse?> = credentialResult

    override suspend fun signInWithEmailPassword(
        email: String,
        password: String,
        saveCredentials: Boolean,
    ): SignInResult {
        signInWithEmailCalls.add(Triple(email, password, saveCredentials))
        return signInResult
    }

    override suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        saveCredentials: Boolean,
    ): SignUpResult {
        signUpWithEmailCalls.add(Triple(email, password, saveCredentials))
        return signUpResult
    }

    override suspend fun logOut(): Result<Unit> {
        logOutCallCount++
        return logOutResult
    }

    override suspend fun deleteAccount(): Result<Unit> {
        deleteAccountCallCount++
        return deleteAccountResult
    }

    override suspend fun resendVerificationEmail(): Result<Unit> {
        resendVerificationCallCount++
        return resendVerificationResult
    }

    override suspend fun isUserAuthenticated(): Result<Boolean> = isAuthenticatedResult
    override suspend fun isEmailVerified(): Result<Boolean> = isEmailVerifiedResult
    override suspend fun isPhoneNumberVerified(): Result<Boolean> = isPhoneVerifiedResult
    override suspend fun refreshCurrentUser(): Result<User?> = refreshResult
    override suspend fun setCurrentUserAfterOnboarding(user: User) {
        _currentUser.value = user
    }
}
