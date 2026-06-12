package com.wenubey.wenucommerce.sign_up

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.auth.SignUpResult
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SignUpViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(auth: FakeAuthRepository = FakeAuthRepository()) =
        SignUpViewModel(auth, dispatcherProvider)

    @Test
    fun `initial state is empty and not signed in`() = runTest {
        val state = newViewModel().signUpState.value
        assertThat(state.email).isEmpty()
        assertThat(state.password).isEmpty()
        assertThat(state.isEmailValid).isFalse()
        assertThat(state.isPasswordValid).isFalse()
        assertThat(state.isUserSignedIn).isFalse()
        assertThat(state.saveCredentials).isFalse()
    }

    @Test
    fun `OnEmailChange validates well-formed email`() = runTest {
        val vm = newViewModel()
        vm.onAction(SignUpAction.OnEmailChange("alice@example.com"))
        advanceUntilIdle()

        assertThat(vm.signUpState.value.email).isEqualTo("alice@example.com")
        assertThat(vm.signUpState.value.isEmailValid).isTrue()
    }

    @Test
    fun `OnEmailChange rejects malformed email`() = runTest {
        val vm = newViewModel()
        vm.onAction(SignUpAction.OnEmailChange("nope"))
        advanceUntilIdle()
        assertThat(vm.signUpState.value.isEmailValid).isFalse()
    }

    @Test
    fun `OnPasswordChange validates strong password`() = runTest {
        val vm = newViewModel()
        vm.onAction(SignUpAction.OnPasswordChange("Abcdef1!"))
        advanceUntilIdle()
        assertThat(vm.signUpState.value.isPasswordValid).isTrue()
    }

    @Test
    fun `OnPasswordChange rejects password lacking symbol`() = runTest {
        val vm = newViewModel()
        vm.onAction(SignUpAction.OnPasswordChange("Abcdef12"))
        advanceUntilIdle()
        assertThat(vm.signUpState.value.isPasswordValid).isFalse()
    }

    @Test
    fun `OnToggleCredentials flips saveCredentials`() = runTest {
        val vm = newViewModel()
        vm.onAction(SignUpAction.OnToggleCredentials)
        advanceUntilIdle()
        assertThat(vm.signUpState.value.saveCredentials).isTrue()
        vm.onAction(SignUpAction.OnToggleCredentials)
        advanceUntilIdle()
        assertThat(vm.signUpState.value.saveCredentials).isFalse()
    }

    @Test
    fun `OnSignUpEmailPassword Success marks signed in and sets email-verified flag`() = runTest {
        val auth = FakeAuthRepository().apply {
            signUpResult = SignUpResult.Success
            isEmailVerifiedResult = Result.success(false)
        }
        val vm = newViewModel(auth)
        vm.onAction(SignUpAction.OnEmailChange("a@b.co"))
        vm.onAction(SignUpAction.OnPasswordChange("Aa1!aaaa"))
        vm.onAction(SignUpAction.OnToggleCredentials)
        vm.onAction(SignUpAction.OnSignUpEmailPassword)
        advanceUntilIdle()

        assertThat(vm.signUpState.value.isUserSignedIn).isTrue()
        assertThat(vm.signUpState.value.isEmailVerified).isFalse()
        assertThat(auth.signUpWithEmailCalls).containsExactly(
            Triple("a@b.co", "Aa1!aaaa", true),
        )
    }

    @Test
    fun `OnSignUpEmailPassword Failure surfaces error and stays signed out`() = runTest {
        val auth = FakeAuthRepository().apply { signUpResult = SignUpResult.Failure("email taken") }
        val vm = newViewModel(auth)

        vm.onAction(SignUpAction.OnSignUpEmailPassword)
        advanceUntilIdle()

        assertThat(vm.signUpState.value.errorMessage).isEqualTo("email taken")
        assertThat(vm.signUpState.value.isUserSignedIn).isFalse()
    }

    @Test
    fun `OnSignUpEmailPassword Cancelled surfaces cancelled message`() = runTest {
        val auth = FakeAuthRepository().apply { signUpResult = SignUpResult.Cancelled }
        val vm = newViewModel(auth)

        vm.onAction(SignUpAction.OnSignUpEmailPassword)
        advanceUntilIdle()

        assertThat(vm.signUpState.value.errorMessage).isEqualTo("Sign up cancelled.")
    }

    @Test
    fun `OnSignUpClicked credential success marks signed in`() = runTest {
        val auth = FakeAuthRepository().apply { credentialResult = Result.success(null) }
        val vm = newViewModel(auth)

        vm.onAction(SignUpAction.OnSignUpClicked)
        advanceUntilIdle()

        assertThat(vm.signUpState.value.isUserSignedIn).isTrue()
    }

    @Test
    fun `OnSignUpClicked credential failure surfaces error`() = runTest {
        val auth = FakeAuthRepository().apply {
            credentialResult = Result.failure(IllegalStateException("no provider"))
        }
        val vm = newViewModel(auth)

        vm.onAction(SignUpAction.OnSignUpClicked)
        advanceUntilIdle()

        assertThat(vm.signUpState.value.errorMessage).isEqualTo("no provider")
        assertThat(vm.signUpState.value.isUserSignedIn).isFalse()
    }
}
