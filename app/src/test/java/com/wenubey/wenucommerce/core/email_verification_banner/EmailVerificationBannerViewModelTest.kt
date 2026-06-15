package com.wenubey.wenucommerce.core.email_verification_banner

import com.google.common.truth.Truth.assertThat
import com.wenubey.data.repository.NotificationPreferences
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationBannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        auth: FakeAuthRepository = FakeAuthRepository(),
        permanentlyHidden: Boolean = false,
    ): Pair<EmailVerificationBannerViewModel, NotificationPreferences> {
        val prefs: NotificationPreferences = mockk(relaxed = true)
        coEvery { prefs.isEmailVerificationPermanentlyHidden() } returns permanentlyHidden
        return EmailVerificationBannerViewModel(auth, prefs, dispatcherProvider) to prefs
    }

    // -------- init check --------

    @Test
    fun `banner hidden when user is not authenticated`() = runTest {
        val auth = FakeAuthRepository().apply {
            isAuthenticatedResult = Result.success(false)
        }
        val (vm, _) = newViewModel(auth = auth)

        advanceUntilIdle()

        val state = vm.emailVerificationBannerState.value
        assertThat(state.isEmailVerified).isTrue()  // anon → treated as verified
        assertThat(state.isVisible).isFalse()
    }

    @Test
    fun `banner visible when authed and email NOT verified and not permanently hidden`() = runTest {
        val auth = FakeAuthRepository().apply {
            isAuthenticatedResult = Result.success(true)
            isEmailVerifiedResult = Result.success(false)
        }
        val (vm, _) = newViewModel(auth = auth, permanentlyHidden = false)

        advanceUntilIdle()

        val state = vm.emailVerificationBannerState.value
        assertThat(state.isEmailVerified).isFalse()
        assertThat(state.isVisible).isTrue()
        assertThat(state.isPermanentlyHidden).isFalse()
    }

    @Test
    fun `banner hidden when authed but email already verified`() = runTest {
        val auth = FakeAuthRepository().apply {
            isAuthenticatedResult = Result.success(true)
            isEmailVerifiedResult = Result.success(true)
        }
        val (vm, _) = newViewModel(auth = auth)

        advanceUntilIdle()

        val state = vm.emailVerificationBannerState.value
        assertThat(state.isEmailVerified).isTrue()
        assertThat(state.isVisible).isFalse()
    }

    @Test
    fun `banner hidden when permanently hidden in preferences`() = runTest {
        val auth = FakeAuthRepository().apply {
            isAuthenticatedResult = Result.success(true)
            isEmailVerifiedResult = Result.success(false)
        }
        val (vm, _) = newViewModel(auth = auth, permanentlyHidden = true)

        advanceUntilIdle()

        val state = vm.emailVerificationBannerState.value
        assertThat(state.isVisible).isFalse()
        assertThat(state.isPermanentlyHidden).isTrue()
    }

    @Test
    fun `failed isAuthenticated treats user as verified (silent fail)`() = runTest {
        val auth = FakeAuthRepository().apply {
            isAuthenticatedResult = Result.failure(RuntimeException("network"))
        }
        val (vm, _) = newViewModel(auth = auth)

        advanceUntilIdle()

        assertThat(vm.emailVerificationBannerState.value.isEmailVerified).isTrue()
        assertThat(vm.emailVerificationBannerState.value.isVisible).isFalse()
    }

    @Test
    fun `failed isEmailVerified treats user as verified (silent fail)`() = runTest {
        val auth = FakeAuthRepository().apply {
            isAuthenticatedResult = Result.success(true)
            isEmailVerifiedResult = Result.failure(RuntimeException("reload failed"))
        }
        val (vm, _) = newViewModel(auth = auth)

        advanceUntilIdle()

        assertThat(vm.emailVerificationBannerState.value.isEmailVerified).isTrue()
        assertThat(vm.emailVerificationBannerState.value.isVisible).isFalse()
    }

    // -------- actions --------

    @Test
    fun `HideNotificationForSession hides banner without persisting`() = runTest {
        val auth = FakeAuthRepository().apply {
            isAuthenticatedResult = Result.success(true)
            isEmailVerifiedResult = Result.success(false)
        }
        val (vm, prefs) = newViewModel(auth = auth)
        advanceUntilIdle()
        assertThat(vm.emailVerificationBannerState.value.isVisible).isTrue()

        vm.onAction(EmailVerificationBannerAction.HideNotificationForSession)
        advanceUntilIdle()

        val state = vm.emailVerificationBannerState.value
        assertThat(state.isVisible).isFalse()
        assertThat(state.isHiddenForSession).isTrue()
        assertThat(state.isPermanentlyHidden).isFalse()
        coVerify(exactly = 0) { prefs.setEmailVerificationPermanentlyHidden(any()) }
    }

    @Test
    fun `DoNotShowAgain persists the preference and marks state permanently hidden`() = runTest {
        val auth = FakeAuthRepository().apply {
            isAuthenticatedResult = Result.success(true)
            isEmailVerifiedResult = Result.success(false)
        }
        val (vm, prefs) = newViewModel(auth = auth)
        advanceUntilIdle()

        vm.onAction(EmailVerificationBannerAction.DoNotShowAgain)
        advanceUntilIdle()

        val state = vm.emailVerificationBannerState.value
        assertThat(state.isVisible).isFalse()
        assertThat(state.isPermanentlyHidden).isTrue()
        coVerify(exactly = 1) { prefs.setEmailVerificationPermanentlyHidden(true) }
    }

    @Test
    fun `recheckEmailVerification refreshes state when email becomes verified`() = runTest {
        val auth = FakeAuthRepository().apply {
            isAuthenticatedResult = Result.success(true)
            isEmailVerifiedResult = Result.success(false)
        }
        val (vm, _) = newViewModel(auth = auth)
        advanceUntilIdle()
        assertThat(vm.emailVerificationBannerState.value.isVisible).isTrue()

        auth.isEmailVerifiedResult = Result.success(true)
        vm.recheckEmailVerification()
        advanceUntilIdle()

        val state = vm.emailVerificationBannerState.value
        assertThat(state.isEmailVerified).isTrue()
        assertThat(state.isVisible).isFalse()
    }
}
