package com.wenubey.wenucommerce.verify_email

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.GetCredentialResponse
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.auth.SignInResult
import com.wenubey.domain.auth.SignUpResult
import com.wenubey.domain.model.user.User
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test

class VerifyEmailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun io(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun default(): CoroutineDispatcher = Dispatchers.Unconfined
    }

    private fun renderScreen(
        auth: TestAuthRepository = TestAuthRepository(),
        emailArg: String = "user@test.dev",
        onTab: () -> Unit = {},
    ): VerifyEmailViewModel {
        val vm = VerifyEmailViewModel(auth, dispatcherProvider)
        // Stop the polling loop spawned in init so the test doesn't hang.
        vm.onAction(VerifyEmailAction.StopVerificationCheck)
        composeTestRule.setContent {
            VerifyEmailScreen(
                viewModel = vm,
                emailArg = emailArg,
                navigateToTab = onTab,
            )
        }
        return vm
    }

    @Test
    fun renders_prompt_text_and_resend_button_with_email_argument() {
        renderScreen(emailArg = "buyer@test.dev")

        composeTestRule.onNodeWithText("You need to verify your email address to use app.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Resend verification email to buyer@test.dev")
            .assertIsDisplayed()
    }

    @Test
    fun resend_button_routes_through_repo() {
        val auth = TestAuthRepository()
        renderScreen(auth = auth)

        composeTestRule.onNodeWithText("Resend verification email to user@test.dev").performClick()
        composeTestRule.waitForIdle()

        assertThat(auth.resendCallCount).isEqualTo(1)
    }

    @Test
    fun navigateToTab_fires_when_email_becomes_verified() {
        val auth = TestAuthRepository()
        var tabCount = 0
        val vm = renderScreen(auth = auth, onTab = { tabCount++ })

        // Simulate the polling loop discovering the email is now verified.
        auth.isEmailVerifiedResult = Result.success(true)
        vm.onAction(VerifyEmailAction.CheckEmailVerification)
        composeTestRule.waitForIdle()
        vm.onAction(VerifyEmailAction.StopVerificationCheck)

        assertThat(tabCount).isAtLeast(1)
    }

    private class TestAuthRepository : AuthRepository {
        var isEmailVerifiedResult: Result<Boolean> = Result.success(false)
        var resendCallCount: Int = 0
        val currentUserState = MutableStateFlow<User?>(null)

        override val isAuthenticated: Boolean = false
        override val currentAuthEmail: String? = null
        override val currentUser: StateFlow<User?> = currentUserState.asStateFlow()

        override suspend fun signIn(credentialResponse: GetCredentialResponse): Result<User> =
            Result.failure(IllegalStateException("not used"))
        override suspend fun getCredential(): Result<GetCredentialResponse?> = Result.success(null)
        override suspend fun signInWithEmailPassword(
            email: String,
            password: String,
            saveCredentials: Boolean,
        ): SignInResult = SignInResult.Failure("not used")
        override suspend fun signUpWithEmailPassword(
            email: String,
            password: String,
            saveCredentials: Boolean,
        ): SignUpResult = SignUpResult.Failure("not used")
        override suspend fun logOut(): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun resendVerificationEmail(): Result<Unit> {
            resendCallCount++
            return Result.success(Unit)
        }
        override suspend fun isUserAuthenticated(): Result<Boolean> = Result.success(true)
        override suspend fun isEmailVerified(): Result<Boolean> = isEmailVerifiedResult
        override suspend fun isPhoneNumberVerified(): Result<Boolean> = Result.success(false)
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
        override suspend fun setCurrentUserAfterOnboarding(user: User) {
            currentUserState.value = user
        }
    }
}
