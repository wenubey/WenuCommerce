package com.wenubey.wenucommerce.sign_up

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

class SignUpScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun io(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun default(): CoroutineDispatcher = Dispatchers.Unconfined
    }

    private fun renderScreen(
        auth: TestAuthRepository = TestAuthRepository(),
        onOnboarding: () -> Unit = {},
        onSignIn: () -> Unit = {},
    ): SignUpViewModel {
        val vm = SignUpViewModel(auth, dispatcherProvider)
        composeTestRule.setContent {
            SignUpScreen(
                viewModel = vm,
                navigateToOnboarding = onOnboarding,
                navigateToSignIn = onSignIn,
            )
        }
        return vm
    }

    @Test
    fun renders_core_form_widgets() {
        renderScreen()

        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign Up Account").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google").assertIsDisplayed()
        composeTestRule.onNodeWithText("Already user? Sign in").assertIsDisplayed()
    }

    @Test
    fun sign_up_button_is_disabled_until_email_is_valid() {
        val vm = renderScreen()

        composeTestRule.onNodeWithText("Sign Up Account").assertIsNotEnabled()
        assertThat(vm.signUpState.value.isEmailValid).isFalse()

        composeTestRule.onNodeWithText("Email").performTextInput("user@test.dev")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sign Up Account").assertIsEnabled()
        assertThat(vm.signUpState.value.isEmailValid).isTrue()
    }

    @Test
    fun typing_into_password_updates_viewmodel_state() {
        val vm = renderScreen()

        composeTestRule.onNodeWithText("Password").performTextInput("Pass1234!")
        composeTestRule.waitForIdle()

        assertThat(vm.signUpState.value.password).isEqualTo("Pass1234!")
    }

    @Test
    fun save_credentials_switch_toggles_state() {
        val vm = renderScreen()
        assertThat(vm.signUpState.value.saveCredentials).isFalse()

        // The password field also ships a visibility IconToggleButton, so
        // there are 2 toggleable nodes on screen. The Switch is the last one.
        composeTestRule.onAllNodes(isToggleable())[1].performClick()
        composeTestRule.waitForIdle()

        assertThat(vm.signUpState.value.saveCredentials).isTrue()
    }

    @Test
    fun sign_up_button_routes_to_signUpWithEmailPassword_on_repo() {
        val auth = TestAuthRepository().apply {
            signUpResult = SignUpResult.Success
        }
        renderScreen(auth = auth)

        composeTestRule.onNodeWithText("Email").performTextInput("a@b.dev")
        composeTestRule.onNodeWithText("Password").performTextInput("Aa1!aaaaaaa")
        composeTestRule.onNodeWithText("Sign Up Account").performClick()
        composeTestRule.waitForIdle()

        assertThat(auth.signUpWithEmailCalls).hasSize(1)
        assertThat(auth.signUpWithEmailCalls.first().first).isEqualTo("a@b.dev")
    }

    @Test
    fun sign_in_button_invokes_navigateToSignIn() {
        var clicked = false
        renderScreen(onSignIn = { clicked = true })

        composeTestRule.onNodeWithText("Already user? Sign in").performClick()
        composeTestRule.waitForIdle()

        assertThat(clicked).isTrue()
    }

    @Test
    fun google_button_routes_to_getCredential_path_on_repo() {
        val auth = TestAuthRepository()
        renderScreen(auth = auth)

        composeTestRule.onNodeWithText("Google").assertHasClickAction()
        composeTestRule.onNodeWithText("Google").performClick()
        composeTestRule.waitForIdle()

        // SignUpAction.OnSignUpClicked → VM calls authRepository.getCredential()
        assertThat(auth.getCredentialCallCount).isEqualTo(1)
    }

    /** Inline minimal AuthRepository fake. */
    private class TestAuthRepository : AuthRepository {

        var signUpResult: SignUpResult = SignUpResult.Failure("not stubbed")
        var signInResult: SignInResult = SignInResult.Failure("not used")
        val currentUserState = MutableStateFlow<User?>(null)
        val signUpWithEmailCalls = mutableListOf<Triple<String, String, Boolean>>()
        var getCredentialCallCount: Int = 0

        override val isAuthenticated: Boolean = false
        override val currentAuthEmail: String? = null
        override val currentUser: StateFlow<User?> = currentUserState.asStateFlow()

        override suspend fun signIn(credentialResponse: GetCredentialResponse): Result<User> =
            Result.failure(IllegalStateException("not used"))

        override suspend fun getCredential(): Result<GetCredentialResponse?> {
            getCredentialCallCount++
            return Result.success(null)
        }

        override suspend fun signInWithEmailPassword(
            email: String,
            password: String,
            saveCredentials: Boolean,
        ): SignInResult = signInResult

        override suspend fun signUpWithEmailPassword(
            email: String,
            password: String,
            saveCredentials: Boolean,
        ): SignUpResult {
            signUpWithEmailCalls.add(Triple(email, password, saveCredentials))
            return signUpResult
        }

        override suspend fun logOut(): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun resendVerificationEmail(): Result<Unit> = Result.success(Unit)
        override suspend fun isUserAuthenticated(): Result<Boolean> = Result.success(false)
        override suspend fun isEmailVerified(): Result<Boolean> = Result.success(false)
        override suspend fun isPhoneNumberVerified(): Result<Boolean> = Result.success(false)
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
        override suspend fun setCurrentUserAfterOnboarding(user: User) {
            currentUserState.value = user
        }
    }
}
