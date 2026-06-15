package com.wenubey.wenucommerce.sign_in

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

class SignInScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun io(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun default(): CoroutineDispatcher = Dispatchers.Unconfined
    }

    private fun renderScreen(
        auth: TestAuthRepository = TestAuthRepository(),
        onTab: (User?) -> Unit = {},
        onVerifyEmail: (String) -> Unit = {},
        onSignUp: () -> Unit = {},
    ): SignInViewModel {
        val vm = SignInViewModel(auth, dispatcherProvider)
        composeTestRule.setContent {
            SignInScreen(
                viewModel = vm,
                navigateToTab = onTab,
                navigateToVerifyEmail = onVerifyEmail,
                navigateToSignUp = onSignUp,
            )
        }
        return vm
    }

    @Test
    fun renders_email_password_and_action_buttons() {
        renderScreen()

        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign in with saved credentials.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Don't have an account? Sign Up").assertIsDisplayed()
    }

    @Test
    fun typing_into_email_and_password_updates_viewmodel_state() {
        val vm = renderScreen()

        composeTestRule.onNodeWithText("Email").performTextInput("user@test.dev")
        composeTestRule.onNodeWithText("Password").performTextInput("Pass1234!")
        composeTestRule.waitForIdle()

        assertThat(vm.signInState.value.email).isEqualTo("user@test.dev")
        assertThat(vm.signInState.value.password).isEqualTo("Pass1234!")
        // Email validator passes for the canonical address shape.
        assertThat(vm.signInState.value.isEmailValid).isTrue()
    }

    @Test
    fun save_credentials_switch_toggles_state() {
        val vm = renderScreen()
        assertThat(vm.signInState.value.saveCredentials).isFalse()

        composeTestRule.onAllNodes(isToggleable()).onFirst().performClick()
        composeTestRule.waitForIdle()
        assertThat(vm.signInState.value.saveCredentials).isTrue()
    }

    @Test
    fun sign_in_button_routes_to_signInWithEmailPassword_on_repo() {
        val auth = TestAuthRepository().apply {
            signInResult = SignInResult.Failure("nope")
        }
        renderScreen(auth = auth)

        composeTestRule.onNodeWithText("Email").performTextInput("a@b.dev")
        composeTestRule.onNodeWithText("Password").performTextInput("Aa1!aaaaaaa")
        composeTestRule.onNodeWithText("Sign In").performClick()
        composeTestRule.waitForIdle()

        assertThat(auth.signInWithEmailCalls).hasSize(1)
        assertThat(auth.signInWithEmailCalls.first().first).isEqualTo("a@b.dev")
    }

    @Test
    fun displays_error_message_after_failed_sign_in() {
        val auth = TestAuthRepository().apply {
            signInResult = SignInResult.Failure("bad credentials")
        }
        renderScreen(auth = auth)

        composeTestRule.onNodeWithText("Email").performTextInput("a@b.dev")
        composeTestRule.onNodeWithText("Password").performTextInput("Aa1!aaaaaaa")
        composeTestRule.onNodeWithText("Sign In").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Error: bad credentials").assertIsDisplayed()
    }

    @Test
    fun navigateToVerifyEmail_fires_when_signed_in_but_unverified() {
        val auth = TestAuthRepository().apply {
            signInResult = SignInResult.Success(User(uuid = "u-1", email = "a@b.dev"))
            isEmailVerifiedResult = Result.success(false)
            currentUserOverride.value = User(uuid = "u-1", email = "a@b.dev")
        }
        var verifyEmailArg: String? = null
        renderScreen(auth = auth, onVerifyEmail = { verifyEmailArg = it })

        composeTestRule.onNodeWithText("Email").performTextInput("a@b.dev")
        composeTestRule.onNodeWithText("Password").performTextInput("Aa1!aaaaaaa")
        composeTestRule.onNodeWithText("Sign In").performClick()
        composeTestRule.waitForIdle()

        assertThat(verifyEmailArg).isEqualTo("a@b.dev")
    }

    @Test
    fun navigateToTab_fires_when_signed_in_and_verified() {
        val verified = User(uuid = "u-1", email = "a@b.dev", isEmailVerified = true)
        val auth = TestAuthRepository().apply {
            signInResult = SignInResult.Success(verified)
            isEmailVerifiedResult = Result.success(true)
            currentUserOverride.value = verified
        }
        var tabUser: User? = null
        renderScreen(auth = auth, onTab = { tabUser = it })

        composeTestRule.onNodeWithText("Email").performTextInput("a@b.dev")
        composeTestRule.onNodeWithText("Password").performTextInput("Aa1!aaaaaaa")
        composeTestRule.onNodeWithText("Sign In").performClick()
        composeTestRule.waitForIdle()

        assertThat(tabUser?.uuid).isEqualTo("u-1")
    }

    @Test
    fun sign_up_button_invokes_navigateToSignUp() {
        var clicked = false
        renderScreen(onSignUp = { clicked = true })

        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        composeTestRule.waitForIdle()

        assertThat(clicked).isTrue()
    }

    /**
     * Minimal inline fake. Captures sign-in calls and surfaces tunable results.
     * Only the methods SignInViewModel actually invokes are exercised; the
     * rest throw to make a future change-of-scope loud.
     */
    private class TestAuthRepository : AuthRepository {

        var signInResult: SignInResult = SignInResult.Failure("not stubbed")
        var isEmailVerifiedResult: Result<Boolean> = Result.success(false)
        val currentUserOverride = MutableStateFlow<User?>(null)
        val signInWithEmailCalls = mutableListOf<Triple<String, String, Boolean>>()

        override val isAuthenticated: Boolean = false
        override val currentAuthEmail: String? = null
        override val currentUser: StateFlow<User?> = currentUserOverride.asStateFlow()

        override suspend fun signIn(credentialResponse: GetCredentialResponse): Result<User> =
            Result.failure(IllegalStateException("not used"))

        override suspend fun getCredential(): Result<GetCredentialResponse?> =
            Result.failure(IllegalStateException("no saved credentials"))

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
        ): SignUpResult = SignUpResult.Failure("not used")

        override suspend fun logOut(): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun resendVerificationEmail(): Result<Unit> = Result.success(Unit)
        override suspend fun isUserAuthenticated(): Result<Boolean> = Result.success(false)
        override suspend fun isEmailVerified(): Result<Boolean> = isEmailVerifiedResult
        override suspend fun isPhoneNumberVerified(): Result<Boolean> = Result.success(false)
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
        override suspend fun setCurrentUserAfterOnboarding(user: User) {
            currentUserOverride.value = user
        }
    }
}
