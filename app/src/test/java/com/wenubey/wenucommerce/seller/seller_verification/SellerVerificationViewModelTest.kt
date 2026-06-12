package com.wenubey.wenucommerce.seller.seller_verification

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.util.DocumentType
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestApplication
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

/**
 * Uses Robolectric because the VM passes android.net.Uri values through state
 * and the validators may also depend on framework regexes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SellerVerificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private val sellerId = "seller-1"

    private fun seller(businessInfo: BusinessInfo? = BusinessInfo(
        businessName = "Acme",
        businessType = BusinessType.LLC,
        businessAddress = "1 Main St",
        businessPhone = "1234567890",
        businessEmail = "biz@example.com",
        taxId = "12-3456789",
        businessLicense = "lic-1",
        bankAccountNumber = "12345678",
        routingNumber = "123456789",
        verificationStatus = VerificationStatus.REJECTED,
        taxDocumentUri = "https://old/tax.pdf",
    )) = User(uuid = sellerId, businessInfo = businessInfo)

    private fun newViewModel(
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = seller()),
        profile: FakeProfileRepository = FakeProfileRepository(),
    ) = SellerVerificationViewModel(dispatcherProvider, auth, profile)

    // --- init / observe ---

    @Test
    fun `init mirrors currentUser into state`() = runTest {
        val s = seller()
        val vm = newViewModel(auth = FakeAuthRepository(initialUser = s))
        advanceUntilIdle()
        assertThat(vm.sellerVerificationState.value.user).isEqualTo(s)
    }

    @Test
    fun `currentUser flow updates propagate live`() = runTest {
        val auth = FakeAuthRepository(initialUser = seller())
        val vm = newViewModel(auth = auth)
        advanceUntilIdle()

        val updated = seller().copy(name = "Renamed")
        auth.emitUser(updated)
        advanceUntilIdle()
        assertThat(vm.sellerVerificationState.value.user!!.name).isEqualTo("Renamed")
    }

    // --- dialogs ---

    @Test
    fun `ShowEditDialog populates editable fields from the user's businessInfo`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowEditDialog)
        advanceUntilIdle()

        val s = vm.sellerVerificationState.value
        assertThat(s.showEditDialog).isTrue()
        assertThat(s.businessName).isEqualTo("Acme")
        assertThat(s.businessType).isEqualTo(BusinessType.LLC)
        assertThat(s.businessAddress).isEqualTo("1 Main St")
        assertThat(s.businessEmail).isEqualTo("biz@example.com")
        assertThat(s.taxId).isEqualTo("12-3456789")
        assertThat(s.existingTaxDocumentUrl).isEqualTo("https://old/tax.pdf")
        assertThat(s.newTaxDocumentUri).isNull()
        assertThat(s.newBusinessLicenseUri).isNull()
        assertThat(s.newIdentityDocumentUri).isNull()
    }

    @Test
    fun `ShowEditDialog with null businessInfo uses safe defaults`() = runTest {
        val vm = newViewModel(auth = FakeAuthRepository(initialUser = seller(businessInfo = null)))
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowEditDialog)
        advanceUntilIdle()

        val s = vm.sellerVerificationState.value
        assertThat(s.businessName).isEmpty()
        assertThat(s.businessType).isEqualTo(BusinessType.INDIVIDUAL)
    }

    @Test
    fun `ShowCancelDialog flips the cancel flag`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowCancelDialog)
        advanceUntilIdle()
        assertThat(vm.sellerVerificationState.value.showCancelDialog).isTrue()
    }

    @Test
    fun `DismissDialog clears both dialog flags and pending document uris`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowEditDialog)
        vm.onAction(SellerVerificationAction.OnTaxDocumentSelected(Uri.parse("content://tax")))
        vm.onAction(SellerVerificationAction.OnBusinessLicenseDocumentSelected(Uri.parse("content://lic")))
        vm.onAction(SellerVerificationAction.OnIdentityDocumentSelected(Uri.parse("content://id")))
        advanceUntilIdle()
        assertThat(vm.sellerVerificationState.value.newTaxDocumentUri).isNotNull()

        vm.onAction(SellerVerificationAction.DismissDialog)
        advanceUntilIdle()

        val s = vm.sellerVerificationState.value
        assertThat(s.showEditDialog).isFalse()
        assertThat(s.showCancelDialog).isFalse()
        assertThat(s.newTaxDocumentUri).isNull()
        assertThat(s.newBusinessLicenseUri).isNull()
        assertThat(s.newIdentityDocumentUri).isNull()
    }

    // --- field updates ---

    @Test
    fun `OnBusinessNameChange marks error true when blank`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerVerificationAction.OnBusinessNameChange("Acme"))
        advanceUntilIdle()
        assertThat(vm.sellerVerificationState.value.businessNameError).isFalse()

        vm.onAction(SellerVerificationAction.OnBusinessNameChange(""))
        advanceUntilIdle()
        assertThat(vm.sellerVerificationState.value.businessNameError).isTrue()
    }

    @Test
    fun `OnBusinessTypeChange stores the value`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.OnBusinessTypeChange(BusinessType.CORPORATION))
        advanceUntilIdle()
        assertThat(vm.sellerVerificationState.value.businessType).isEqualTo(BusinessType.CORPORATION)
    }

    @Test
    fun `simple field updates flow through unchanged`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.OnBusinessDescriptionChange("desc"))
        vm.onAction(SellerVerificationAction.OnBusinessAddressChange("addr"))
        vm.onAction(SellerVerificationAction.OnBusinessPhoneChange("555-0100"))
        vm.onAction(SellerVerificationAction.OnBusinessLicenseChange("lic-2"))
        advanceUntilIdle()

        val s = vm.sellerVerificationState.value
        assertThat(s.businessDescription).isEqualTo("desc")
        assertThat(s.businessAddress).isEqualTo("addr")
        assertThat(s.businessPhone).isEqualTo("555-0100")
        assertThat(s.businessLicense).isEqualTo("lic-2")
    }

    @Test
    fun `document selection actions stash the new uri without clearing the existing url`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowEditDialog)
        advanceUntilIdle()
        val taxUri = Uri.parse("content://test/tax.pdf")
        val licUri = Uri.parse("content://test/lic.pdf")
        val idUri = Uri.parse("content://test/id.pdf")

        vm.onAction(SellerVerificationAction.OnTaxDocumentSelected(taxUri))
        vm.onAction(SellerVerificationAction.OnBusinessLicenseDocumentSelected(licUri))
        vm.onAction(SellerVerificationAction.OnIdentityDocumentSelected(idUri))
        advanceUntilIdle()

        val s = vm.sellerVerificationState.value
        assertThat(s.newTaxDocumentUri).isEqualTo(taxUri)
        assertThat(s.newBusinessLicenseUri).isEqualTo(licUri)
        assertThat(s.newIdentityDocumentUri).isEqualTo(idUri)
        // Existing URLs untouched.
        assertThat(s.existingTaxDocumentUrl).isEqualTo("https://old/tax.pdf")
    }

    // --- cancelApplication ---

    @Test
    fun `ConfirmCancelApplication success closes cancel dialog and clears submitting flag`() = runTest {
        val profile = FakeProfileRepository()
        val vm = newViewModel(profile = profile)
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowCancelDialog)
        vm.onAction(SellerVerificationAction.ConfirmCancelApplication)
        advanceUntilIdle()

        assertThat(profile.cancelSellerApplicationCalls).containsExactly(sellerId)
        assertThat(vm.sellerVerificationState.value.showCancelDialog).isFalse()
        assertThat(vm.sellerVerificationState.value.isSubmitting).isFalse()
        assertThat(vm.sellerVerificationState.value.errorMessage).isNull()
    }

    @Test
    fun `ConfirmCancelApplication failure surfaces error and clears submitting flag`() = runTest {
        val profile = FakeProfileRepository().apply {
            cancelSellerApplicationResult = Result.failure(RuntimeException("perm denied"))
        }
        val vm = newViewModel(profile = profile)
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ConfirmCancelApplication)
        advanceUntilIdle()

        assertThat(vm.sellerVerificationState.value.errorMessage).isEqualTo("perm denied")
        assertThat(vm.sellerVerificationState.value.isSubmitting).isFalse()
    }

    @Test
    fun `ConfirmCancelApplication does nothing when user uuid is null`() = runTest {
        val auth = FakeAuthRepository(initialUser = User(uuid = null))
        val profile = FakeProfileRepository()
        val vm = newViewModel(auth = auth, profile = profile)
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ConfirmCancelApplication)
        advanceUntilIdle()

        assertThat(profile.cancelSellerApplicationCalls).isEmpty()
    }

    // --- submitUpdatedInfo ---

    @Test
    fun `SubmitUpdatedInfo uploads only the new documents that were selected`() = runTest {
        val profile = FakeProfileRepository()
        val vm = newViewModel(profile = profile)
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowEditDialog)
        advanceUntilIdle()

        // Only tax document gets a new URI; the other two remain as existing URLs.
        vm.onAction(SellerVerificationAction.OnTaxDocumentSelected(Uri.parse("content://tax")))
        vm.onAction(SellerVerificationAction.SubmitUpdatedInfo)
        advanceUntilIdle()

        // Only one updateSellerDocument call.
        assertThat(profile.updateSellerDocumentCalls).hasSize(1)
        assertThat(profile.updateSellerDocumentCalls[0].second).isEqualTo(DocumentType.TAX_DOCUMENTS)

        // BusinessInfo persisted with RESUBMITTED status + previous status set.
        assertThat(profile.updateSellerBusinessInfoCalls).hasSize(1)
        val (uid, info) = profile.updateSellerBusinessInfoCalls[0]
        assertThat(uid).isEqualTo(sellerId)
        assertThat(info.verificationStatus).isEqualTo(VerificationStatus.RESUBMITTED)
        assertThat(info.previousStatus).isEqualTo(VerificationStatus.REJECTED)
    }

    @Test
    fun `SubmitUpdatedInfo success state — submitting cleared, dialog dismissed, success flag set, new uris cleared`() = runTest {
        val profile = FakeProfileRepository()
        val vm = newViewModel(profile = profile)
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowEditDialog)
        vm.onAction(SellerVerificationAction.OnTaxDocumentSelected(Uri.parse("content://tax")))
        vm.onAction(SellerVerificationAction.SubmitUpdatedInfo)
        advanceUntilIdle()

        val s = vm.sellerVerificationState.value
        assertThat(s.submissionSuccess).isTrue()
        assertThat(s.isSubmitting).isFalse()
        assertThat(s.showEditDialog).isFalse()
        assertThat(s.newTaxDocumentUri).isNull()
        assertThat(s.newBusinessLicenseUri).isNull()
        assertThat(s.newIdentityDocumentUri).isNull()
    }

    @Test
    fun `SubmitUpdatedInfo failure on updateSellerBusinessInfo surfaces errorMessage`() = runTest {
        val profile = FakeProfileRepository().apply {
            updateSellerBusinessInfoResult = Result.failure(RuntimeException("validation"))
        }
        val vm = newViewModel(profile = profile)
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowEditDialog)
        vm.onAction(SellerVerificationAction.SubmitUpdatedInfo)
        advanceUntilIdle()

        assertThat(vm.sellerVerificationState.value.errorMessage).isEqualTo("validation")
        assertThat(vm.sellerVerificationState.value.isSubmitting).isFalse()
        assertThat(vm.sellerVerificationState.value.submissionSuccess).isFalse()
    }

    @Test
    fun `SubmitUpdatedInfo with no new documents keeps existing URLs in the saved BusinessInfo`() = runTest {
        val profile = FakeProfileRepository()
        val vm = newViewModel(profile = profile)
        advanceUntilIdle()
        vm.onAction(SellerVerificationAction.ShowEditDialog)
        // No document selections at all.
        vm.onAction(SellerVerificationAction.SubmitUpdatedInfo)
        advanceUntilIdle()

        assertThat(profile.updateSellerDocumentCalls).isEmpty()
        val saved = profile.updateSellerBusinessInfoCalls.single().second
        assertThat(saved.taxDocumentUri).isEqualTo("https://old/tax.pdf")
    }
}
