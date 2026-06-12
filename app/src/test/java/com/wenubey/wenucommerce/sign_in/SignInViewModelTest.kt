package com.wenubey.wenucommerce.sign_in

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.auth.SignInResult
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestApplication
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uses Robolectric because SignInViewModel calls android.util.Patterns.EMAIL_ADDRESS.
 * That regex is a static Pattern object initialized by the Android framework.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SignInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(auth: FakeAuthRepository = FakeAuthRepository()) =
        SignInViewModel(auth, dispatcherProvider)

    @Test
    fun `initial state has empty fields and invalid email and password`() = runTest {
        val vm = newViewModel()
        val state = vm.signInState.value
        assertThat(state.email).isEmpty()
        assertThat(state.password).isEmpty()
        assertThat(state.isEmailValid).isFalse()
        assertThat(state.isPasswordValid).isFalse()
        assertThat(state.isUserSignedIn).isFalse()
        assertThat(state.saveCredentials).isFalse()
        assertThat(state.user).isNull()
    }

    @Test
    fun `OnEmailChange marks isEmailValid true for a well-formed email`() = runTest {
        val vm = newViewModel()

        vm.onAction(SignInAction.OnEmailChange("alice@example.com"))
        advanceUntilIdle()

        assertThat(vm.signInState.value.email).isEqualTo("alice@example.com")
        assertThat(vm.signInState.value.isEmailValid).isTrue()
    }

    @Test
    fun `OnEmailChange marks isEmailValid false for malformed email`() = runTest {
        val vm = newViewModel()

        vm.onAction(SignInAction.OnEmailChange("not-an-email"))
        advanceUntilIdle()

        assertThat(vm.signInState.value.isEmailValid).isFalse()
    }

    @Test
    fun `OnEmailChange clears any error message`() = runTest {
        // Cause an error first via failed sign-in.
        val auth = FakeAuthRepository().apply { signInResult = SignInResult.Failure("boom") }
        val vm = newViewModel(auth)
        vm.onAction(SignInAction.OnEmailChange("a@b.co"))
        vm.onAction(SignInAction.OnPasswordChange("Aa1!aaaa"))
        vm.onAction(SignInAction.OnSignWithEmailPassword)
        advanceUntilIdle()
        assertThat(vm.signInState.value.errorMessage).isEqualTo("boom")

        vm.onAction(SignInAction.OnEmailChange("c@d.co"))
        advanceUntilIdle()
        assertThat(vm.signInState.value.errorMessage).isNull()
    }

    @Test
    fun `OnPasswordChange marks valid password (upper lower digit symbol 8 plus chars)`() = runTest {
        val vm = newViewModel()

        vm.onAction(SignInAction.OnPasswordChange("Abcdef1!"))
        advanceUntilIdle()

        assertThat(vm.signInState.value.isPasswordValid).isTrue()
    }

    @Test
    fun `OnPasswordChange marks short password invalid`() = runTest {
        val vm = newViewModel()
        vm.onAction(SignInAction.OnPasswordChange("Aa1!"))
        advanceUntilIdle()
        assertThat(vm.signInState.value.isPasswordValid).isFalse()
    }

    @Test
    fun `OnPasswordChange marks password missing symbol invalid`() = runTest {
        val vm = newViewModel()
        vm.onAction(SignInAction.OnPasswordChange("Abcdef12"))
        advanceUntilIdle()
        assertThat(vm.signInState.value.isPasswordValid).isFalse()
    }

    @Test
    fun `OnToggleCredentials flips saveCredentials`() = runTest {
        val vm = newViewModel()
        vm.onAction(SignInAction.OnToggleCredentials)
        advanceUntilIdle()
        assertThat(vm.signInState.value.saveCredentials).isTrue()

        vm.onAction(SignInAction.OnToggleCredentials)
        advanceUntilIdle()
        assertThat(vm.signInState.value.saveCredentials).isFalse()
    }

    @Test
    fun `OnSignWithEmailPassword success sets isUserSignedIn true and stores user`() = runTest {
        val user = User(uuid = "u-1", role = UserRole.CUSTOMER, email = "a@b.co")
        val auth = FakeAuthRepository(initialUser = user).apply {
            signInResult = SignInResult.Success(user)
            isEmailVerifiedResult = Result.success(true)
        }
        val vm = newViewModel(auth)

        vm.onAction(SignInAction.OnEmailChange("a@b.co"))
        vm.onAction(SignInAction.OnPasswordChange("Aa1!aaaa"))
        vm.onAction(SignInAction.OnToggleCredentials)
        vm.onAction(SignInAction.OnSignWithEmailPassword)
        advanceUntilIdle()

        val state = vm.signInState.value
        assertThat(state.isUserSignedIn).isTrue()
        assertThat(state.isEmailVerified).isTrue()
        assertThat(state.user).isEqualTo(user)
        assertThat(auth.signInWithEmailCalls).containsExactly(
            Triple("a@b.co", "Aa1!aaaa", true),
        )
    }

    @Test
    fun `OnSignWithEmailPassword Failure surfaces errorMessage`() = runTest {
        val auth = FakeAuthRepository().apply { signInResult = SignInResult.Failure("wrong password") }
        val vm = newViewModel(auth)

        vm.onAction(SignInAction.OnEmailChange("a@b.co"))
        vm.onAction(SignInAction.OnPasswordChange("Aa1!aaaa"))
        vm.onAction(SignInAction.OnSignWithEmailPassword)
        advanceUntilIdle()

        assertThat(vm.signInState.value.errorMessage).isEqualTo("wrong password")
        assertThat(vm.signInState.value.isUserSignedIn).isFalse()
    }

    @Test
    fun `OnSignWithEmailPassword Cancelled surfaces cancelled message`() = runTest {
        val auth = FakeAuthRepository().apply { signInResult = SignInResult.Cancelled }
        val vm = newViewModel(auth)

        vm.onAction(SignInAction.OnSignWithEmailPassword)
        advanceUntilIdle()

        assertThat(vm.signInState.value.errorMessage).isEqualTo("Sign in cancelled.")
    }

    @Test
    fun `OnSignWithEmailPassword NoCredentials surfaces no-credentials message`() = runTest {
        val auth = FakeAuthRepository().apply { signInResult = SignInResult.NoCredentials }
        val vm = newViewModel(auth)

        vm.onAction(SignInAction.OnSignWithEmailPassword)
        advanceUntilIdle()

        assertThat(vm.signInState.value.errorMessage).isEqualTo("No credentials provided.")
    }

    @Test
    fun `OnSignInClicked credential failure surfaces error from result`() = runTest {
        val auth = FakeAuthRepository().apply {
            credentialResult = Result.failure(IllegalStateException("no provider"))
        }
        val vm = newViewModel(auth)

        vm.onAction(SignInAction.OnSignInClicked)
        advanceUntilIdle()

        assertThat(vm.signInState.value.errorMessage).isEqualTo("no provider")
    }

    @Test
    fun `OnSignInClicked credential success with null response does not crash and stays signed out`() = runTest {
        val auth = FakeAuthRepository().apply { credentialResult = Result.success(null) }
        val vm = newViewModel(auth)

        vm.onAction(SignInAction.OnSignInClicked)
        advanceUntilIdle()

        assertThat(vm.signInState.value.isUserSignedIn).isFalse()
        assertThat(vm.signInState.value.errorMessage).isNull()
    }
}
