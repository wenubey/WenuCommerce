package com.wenubey.wenucommerce.onboard

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.model.Gender
import com.wenubey.wenucommerce.onboard.util.GenderUiModel
import com.wenubey.wenucommerce.onboard.util.UserRoleUiModel
import com.wenubey.wenucommerce.onboard.util.toUiModel
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeProfileRepository
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
@Config(application = android.app.Application::class, sdk = [33])
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        profile: FakeProfileRepository = FakeProfileRepository(),
        auth: FakeAuthRepository = FakeAuthRepository().apply {
            currentAuthEmailOverride = "registered@test.dev"
        },
    ): Triple<OnboardingViewModel, FakeProfileRepository, FakeAuthRepository> =
        Triple(OnboardingViewModel(profile, auth, dispatcherProvider), profile, auth)

    private fun seller(): UserRoleUiModel = UserRole.SELLER.toUiModel()
    private fun customer(): UserRoleUiModel = UserRole.CUSTOMER.toUiModel()

    private val maleGender: GenderUiModel = Gender.MALE.toUiModel()

    /**
     * Drives every shared field to a valid customer-onboarding state. The
     * trailing duplicate OnNameChange flushes a final validateForm() pass
     * after all earlier launches have applied (TB-10: each field update is
     * dispatched async but validateForm runs synchronously, leaving
     * isNextButtonEnabled one action behind).
     */
    private fun OnboardingViewModel.fillCustomerHappyPath() {
        onAction(OnboardingAction.OnNameChange("Ada"))
        onAction(OnboardingAction.OnSurnameChange("Lovelace"))
        onAction(OnboardingAction.OnPhoneNumberChange("5551234567"))
    }

    private fun OnboardingViewModel.fillSellerHappyPath() {
        fillCustomerHappyPath()
        onAction(OnboardingAction.OnRoleSelected(seller()))
        onAction(OnboardingAction.OnBusinessNameChange("Lovelace Looms"))
        onAction(OnboardingAction.OnTaxIdChange("123456789"))
        onAction(OnboardingAction.OnBusinessAddressChange("1 Compiler Lane"))
        onAction(OnboardingAction.OnBusinessPhoneChange("5550000000"))
        onAction(OnboardingAction.OnBusinessEmailChange("biz@lovelace.dev"))
        onAction(OnboardingAction.OnBankAccountNumberChange("12345678"))
        onAction(OnboardingAction.OnRoutingNumberChange("123456789"))
        onAction(OnboardingAction.OnTaxDocumentUpload("file:///tax.pdf"))
        onAction(OnboardingAction.OnIdentityDocumentUpload("file:///id.jpg"))
    }

    /**
     * Flushes a final validateForm() pass after all previously queued field
     * updates have applied. See TB-10 in the backfill tracker.
     */
    private fun OnboardingViewModel.flushValidation() {
        onAction(OnboardingAction.OnNameChange(state.value.name))
    }

    // -------- init --------

    @Test
    fun `initial state populates registrationEmail from auth`() = runTest {
        val (vm, _, _) = newViewModel()

        assertThat(vm.state.value.registrationEmail).isEqualTo("registered@test.dev")
        assertThat(vm.state.value.isNextButtonEnabled).isFalse()
    }

    @Test
    fun `initial state uses empty registrationEmail when auth has no email`() = runTest {
        val auth = FakeAuthRepository()
        val (vm, _, _) = newViewModel(auth = auth)

        assertThat(vm.state.value.registrationEmail).isEmpty()
    }

    // -------- basic field validation --------

    @Test
    fun `blank name triggers nameError and unblanks it on update`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnNameChange(""))
        advanceUntilIdle()
        assertThat(vm.state.value.nameError).isTrue()

        vm.onAction(OnboardingAction.OnNameChange("Ada"))
        advanceUntilIdle()
        assertThat(vm.state.value.nameError).isFalse()
    }

    @Test
    fun `surname and phoneNumber follow the same blank-error rule`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnSurnameChange(""))
        vm.onAction(OnboardingAction.OnPhoneNumberChange(""))
        advanceUntilIdle()
        assertThat(vm.state.value.surnameError).isTrue()
        assertThat(vm.state.value.phoneNumberError).isTrue()

        vm.onAction(OnboardingAction.OnSurnameChange("L"))
        vm.onAction(OnboardingAction.OnPhoneNumberChange("5"))
        advanceUntilIdle()
        assertThat(vm.state.value.surnameError).isFalse()
        assertThat(vm.state.value.phoneNumberError).isFalse()
    }

    // -------- date of birth + seller age gate --------

    @Test
    fun `seller under 18 trips dateOfBirthError`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.onAction(OnboardingAction.OnRoleSelected(seller()))
        // 10 years ago in millis
        val tenYearsAgo = System.currentTimeMillis() - 10L * 365 * 24 * 3600 * 1000

        vm.onAction(OnboardingAction.OnDateOfBirthSelected(tenYearsAgo))
        advanceUntilIdle()

        assertThat(vm.state.value.dateOfBirthError).isTrue()
    }

    @Test
    fun `seller 18-plus clears dateOfBirthError`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.onAction(OnboardingAction.OnRoleSelected(seller()))
        val thirtyYearsAgo = System.currentTimeMillis() - 30L * 365 * 24 * 3600 * 1000

        vm.onAction(OnboardingAction.OnDateOfBirthSelected(thirtyYearsAgo))
        advanceUntilIdle()

        assertThat(vm.state.value.dateOfBirthError).isFalse()
    }

    @Test
    fun `customer under 18 does NOT trip dateOfBirthError`() = runTest {
        val (vm, _, _) = newViewModel()
        val tenYearsAgo = System.currentTimeMillis() - 10L * 365 * 24 * 3600 * 1000

        vm.onAction(OnboardingAction.OnDateOfBirthSelected(tenYearsAgo))
        advanceUntilIdle()

        assertThat(vm.state.value.dateOfBirthError).isFalse()
    }

    @Test
    fun `OnRoleSelected re-runs the age gate with current dateOfBirthMillis`() = runTest {
        val (vm, _, _) = newViewModel()
        val tenYearsAgo = System.currentTimeMillis() - 10L * 365 * 24 * 3600 * 1000
        vm.onAction(OnboardingAction.OnDateOfBirthSelected(tenYearsAgo))
        advanceUntilIdle()
        assertThat(vm.state.value.dateOfBirthError).isFalse()

        vm.onAction(OnboardingAction.OnRoleSelected(seller()))
        advanceUntilIdle()

        assertThat(vm.state.value.dateOfBirthError).isTrue()
    }

    // -------- seller field validators --------

    @Test
    fun `taxId requires nine digits`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnTaxIdChange("12345"))
        advanceUntilIdle()
        assertThat(vm.state.value.taxIdError).isTrue()

        vm.onAction(OnboardingAction.OnTaxIdChange("12-3456789"))
        advanceUntilIdle()
        assertThat(vm.state.value.taxIdError).isFalse()
    }

    @Test
    fun `businessEmail uses email format validator`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnBusinessEmailChange("not-an-email"))
        advanceUntilIdle()
        assertThat(vm.state.value.businessEmailError).isTrue()

        vm.onAction(OnboardingAction.OnBusinessEmailChange("biz@x.dev"))
        advanceUntilIdle()
        assertThat(vm.state.value.businessEmailError).isFalse()
    }

    @Test
    fun `routingNumber requires nine digits`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnRoutingNumberChange("12345"))
        advanceUntilIdle()
        assertThat(vm.state.value.routingNumberError).isTrue()

        vm.onAction(OnboardingAction.OnRoutingNumberChange("123456789"))
        advanceUntilIdle()
        assertThat(vm.state.value.routingNumberError).isFalse()
    }

    @Test
    fun `bankAccountNumber requires 8 to 17 digits`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnBankAccountNumberChange("1234567"))
        advanceUntilIdle()
        assertThat(vm.state.value.bankAccountNumberError).isTrue()

        vm.onAction(OnboardingAction.OnBankAccountNumberChange("12345678"))
        advanceUntilIdle()
        assertThat(vm.state.value.bankAccountNumberError).isFalse()
    }

    // -------- use registration email toggle --------

    @Test
    fun `useRegistrationEmail toggle copies registrationEmail into businessEmail`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnUseRegistrationEmailToggle(true))
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.useRegistrationEmail).isTrue()
        assertThat(state.businessEmail).isEqualTo("registered@test.dev")
        assertThat(state.businessEmailError).isFalse()
    }

    @Test
    fun `useRegistrationEmail toggle off clears businessEmail`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.onAction(OnboardingAction.OnUseRegistrationEmailToggle(true))
        advanceUntilIdle()

        vm.onAction(OnboardingAction.OnUseRegistrationEmailToggle(false))
        advanceUntilIdle()

        assertThat(vm.state.value.businessEmail).isEmpty()
        assertThat(vm.state.value.useRegistrationEmail).isFalse()
    }

    // -------- document upload --------

    @Test
    fun `document upload actions parse uri string into state`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnTaxDocumentUpload("file:///tax.pdf"))
        vm.onAction(OnboardingAction.OnBusinessLicenseDocumentUpload("file:///bl.pdf"))
        vm.onAction(OnboardingAction.OnIdentityDocumentUpload("file:///id.jpg"))
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.taxDocumentUri).isEqualTo(Uri.parse("file:///tax.pdf"))
        assertThat(state.businessLicenseDocumentUri).isEqualTo(Uri.parse("file:///bl.pdf"))
        assertThat(state.identityDocumentUri).isEqualTo(Uri.parse("file:///id.jpg"))
    }

    // -------- form validation toggle --------

    @Test
    fun `customer next button enables once name surname and phone are filled`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.fillCustomerHappyPath()
        advanceUntilIdle()
        vm.flushValidation() // TB-10 workaround
        advanceUntilIdle()

        assertThat(vm.state.value.isNextButtonEnabled).isTrue()
    }

    @Test
    fun `seller next button stays disabled when any required field missing`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.fillSellerHappyPath()
        advanceUntilIdle()
        vm.flushValidation()
        advanceUntilIdle()
        assertThat(vm.state.value.isNextButtonEnabled).isTrue()

        // Remove a single required field — the button should re-disable.
        vm.onAction(OnboardingAction.OnTaxIdChange("12"))
        advanceUntilIdle()
        vm.flushValidation()
        advanceUntilIdle()

        assertThat(vm.state.value.isNextButtonEnabled).isFalse()
    }

    @Test
    fun `seller next button stays disabled without required documents`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.fillCustomerHappyPath()
        vm.onAction(OnboardingAction.OnRoleSelected(seller()))
        vm.onAction(OnboardingAction.OnBusinessNameChange("X"))
        vm.onAction(OnboardingAction.OnTaxIdChange("123456789"))
        vm.onAction(OnboardingAction.OnBusinessAddressChange("addr"))
        vm.onAction(OnboardingAction.OnBusinessPhoneChange("5550000000"))
        vm.onAction(OnboardingAction.OnBusinessEmailChange("biz@x.dev"))
        vm.onAction(OnboardingAction.OnBankAccountNumberChange("12345678"))
        vm.onAction(OnboardingAction.OnRoutingNumberChange("123456789"))
        // Skip both required document uploads.
        advanceUntilIdle()

        assertThat(vm.state.value.isNextButtonEnabled).isFalse()
    }

    // -------- onboarding complete --------

    @Test
    fun `OnOnboardingComplete success populates completedUser`() = runTest {
        val profile = FakeProfileRepository()
        profile.onboardingResult = Result.success(User(uuid = "uid-1", name = "Ada"))
        val (vm, _, _) = newViewModel(profile = profile)
        vm.fillCustomerHappyPath()
        advanceUntilIdle()

        vm.onAction(OnboardingAction.OnOnboardingComplete)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.completedUser?.uuid).isEqualTo("uid-1")
        assertThat(state.errorMessage).isNull()
        assertThat(profile.onboardingCalls).hasSize(1)
        assertThat(profile.onboardingCalls.first().name).isEqualTo("Ada")
        assertThat(profile.onboardingCalls.first().role).isEqualTo(UserRole.CUSTOMER)
    }

    @Test
    fun `OnOnboardingComplete failure surfaces errorMessage`() = runTest {
        val profile = FakeProfileRepository()
        profile.onboardingResult = Result.failure(RuntimeException("boom"))
        val (vm, _, _) = newViewModel(profile = profile)
        vm.fillCustomerHappyPath()
        advanceUntilIdle()

        vm.onAction(OnboardingAction.OnOnboardingComplete)
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isEqualTo("boom")
        assertThat(vm.state.value.completedUser).isNull()
    }

    @Test
    fun `OnOnboardingComplete routes seller role and businessName to repo`() = runTest {
        val profile = FakeProfileRepository()
        val (vm, _, _) = newViewModel(profile = profile)
        vm.fillSellerHappyPath()
        advanceUntilIdle()

        vm.onAction(OnboardingAction.OnOnboardingComplete)
        advanceUntilIdle()

        val call = profile.onboardingCalls.single()
        assertThat(call.role).isEqualTo(UserRole.SELLER)
        assertThat(call.businessName).isEqualTo("Lovelace Looms")
    }

    // -------- misc actions --------

    @Test
    fun `OnGenderSelected updates state`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnGenderSelected(maleGender))
        advanceUntilIdle()

        assertThat(vm.state.value.gender.name).isEqualTo("Male")
    }

    @Test
    fun `OnBusinessTypeChange updates state`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnBusinessTypeChange(BusinessType.LLC))
        advanceUntilIdle()

        assertThat(vm.state.value.businessType).isEqualTo(BusinessType.LLC)
    }

    @Test
    fun `OnAddressChange updates state`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnAddressChange("Home"))
        advanceUntilIdle()

        assertThat(vm.state.value.address).isEqualTo("Home")
    }

    @Test
    fun `OnPhotoUrlChange updates state`() = runTest {
        val (vm, _, _) = newViewModel()

        vm.onAction(OnboardingAction.OnPhotoUrlChange("https://x/y.jpg"))
        advanceUntilIdle()

        assertThat(vm.state.value.photoUrl).isEqualTo("https://x/y.jpg")
    }
}
