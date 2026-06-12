package com.wenubey.wenucommerce.verify_email

import com.google.common.truth.Truth.assertThat
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * VerifyEmailViewModel runs a `while(true) { delay(5000) }` polling loop in
 * init {}. We do NOT exercise multi-tick polling under runTest because that
 * loop never terminates — runTest then either hangs or refuses to clean up.
 *
 * Strategy: every test stops the loop on its first line, then exercises the
 * actions directly. The "polling happens" guarantee is covered by asserting
 * that the initial check fired before we stopped, and that subsequent
 * Resend / Check actions update state correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEmailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(auth: FakeAuthRepository = FakeAuthRepository()) =
        VerifyEmailViewModel(auth, dispatcherProvider)

    @Test
    fun `initial state is unverified, email-not-sent, no error`() = runTest {
        val vm = newViewModel()
        vm.onAction(VerifyEmailAction.StopVerificationCheck)

        val state = vm.verifyEmailState.value
        assertThat(state.isEmailVerified).isFalse()
        assertThat(state.isVerificationEmailSent).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `init triggers an immediate verification check that propagates verified=true`() = runTest {
        val auth = FakeAuthRepository().apply { isEmailVerifiedResult = Result.success(true) }
        val vm = newViewModel(auth)
        // Let the first check run and propagate, then stop the loop before the
        // first 5s delay would queue more work.
        runCurrent()
        vm.onAction(VerifyEmailAction.StopVerificationCheck)
        runCurrent()

        assertThat(vm.verifyEmailState.value.isEmailVerified).isTrue()
    }

    @Test
    fun `check failure surfaces error message`() = runTest {
        val auth = FakeAuthRepository().apply {
            isEmailVerifiedResult = Result.failure(RuntimeException("offline"))
        }
        val vm = newViewModel(auth)
        runCurrent()
        vm.onAction(VerifyEmailAction.StopVerificationCheck)
        runCurrent()

        assertThat(vm.verifyEmailState.value.errorMessage).isEqualTo("offline")
    }

    @Test
    fun `ResendVerificationEmail success sets isVerificationEmailSent true`() = runTest {
        val auth = FakeAuthRepository().apply {
            isEmailVerifiedResult = Result.success(false)
            resendVerificationResult = Result.success(Unit)
        }
        val vm = newViewModel(auth)
        vm.onAction(VerifyEmailAction.StopVerificationCheck)
        runCurrent()

        vm.onAction(VerifyEmailAction.ResendVerificationEmail)
        runCurrent()

        assertThat(vm.verifyEmailState.value.isVerificationEmailSent).isTrue()
        assertThat(auth.resendVerificationCallCount).isEqualTo(1)
    }

    @Test
    fun `ResendVerificationEmail failure surfaces error and keeps sent flag false`() = runTest {
        val auth = FakeAuthRepository().apply {
            isEmailVerifiedResult = Result.success(false)
            resendVerificationResult = Result.failure(RuntimeException("rate limited"))
        }
        val vm = newViewModel(auth)
        vm.onAction(VerifyEmailAction.StopVerificationCheck)
        runCurrent()

        vm.onAction(VerifyEmailAction.ResendVerificationEmail)
        runCurrent()

        val state = vm.verifyEmailState.value
        assertThat(state.errorMessage).isEqualTo("rate limited")
        assertThat(state.isVerificationEmailSent).isFalse()
    }

    @Test
    fun `CheckEmailVerification re-invocation re-runs the verification check`() = runTest {
        // Stop the init loop, flip the verified result, then explicitly request
        // a fresh check. This pins that the action is idempotent and re-readable.
        val auth = FakeAuthRepository().apply { isEmailVerifiedResult = Result.success(false) }
        val vm = newViewModel(auth)
        runCurrent()
        vm.onAction(VerifyEmailAction.StopVerificationCheck)
        runCurrent()
        assertThat(vm.verifyEmailState.value.isEmailVerified).isFalse()

        auth.isEmailVerifiedResult = Result.success(true)
        vm.onAction(VerifyEmailAction.CheckEmailVerification)
        runCurrent()
        // Stop the new loop the action just started.
        vm.onAction(VerifyEmailAction.StopVerificationCheck)
        runCurrent()

        assertThat(vm.verifyEmailState.value.isEmailVerified).isTrue()
    }
}
