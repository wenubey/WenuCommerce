package com.wenubey.wenucommerce.admin.admin_seller_approval

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeFirestoreRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminApprovalViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        firestore: FakeFirestoreRepository = FakeFirestoreRepository(),
    ): Pair<AdminApprovalViewModel, FakeFirestoreRepository> =
        AdminApprovalViewModel(firestore, dispatcherProvider) to firestore

    private fun seller(uuid: String) = User(uuid = uuid, email = "$uuid@x")

    // -------- observe --------

    @Test
    fun `observe pending sellers populates state and clears loading`() = runTest {
        val (vm, firestore) = newViewModel()

        firestore.emitSellers(VerificationStatus.PENDING, listOf(seller("s1"), seller("s2")))
        advanceUntilIdle()

        val state = vm.approvalState.value
        assertThat(state.sellers.map { it.uuid }).containsExactly("s1", "s2")
        assertThat(state.isLoading).isFalse()
        assertThat(state.selectedFilter).isEqualTo(VerificationStatus.PENDING)
    }

    @Test
    fun `OnFilterChange switches the observed status`() = runTest {
        val (vm, firestore) = newViewModel()
        firestore.emitSellers(VerificationStatus.PENDING, listOf(seller("p1")))
        firestore.emitSellers(VerificationStatus.REJECTED, listOf(seller("r1"), seller("r2")))
        advanceUntilIdle()
        assertThat(vm.approvalState.value.sellers.map { it.uuid }).containsExactly("p1")

        vm.onAction(AdminSellerApprovalAction.OnFilterChange(VerificationStatus.REJECTED))
        advanceUntilIdle()

        val state = vm.approvalState.value
        assertThat(state.selectedFilter).isEqualTo(VerificationStatus.REJECTED)
        assertThat(state.sellers.map { it.uuid }).containsExactly("r1", "r2")
    }

    // -------- selection / dialog --------

    @Test
    fun `OnSellerSelected opens APPROVE dialog with selection`() = runTest {
        val (vm, _) = newViewModel()
        val s = seller("s1")

        vm.onAction(AdminSellerApprovalAction.OnSellerSelected(s))
        advanceUntilIdle()

        val state = vm.approvalState.value
        assertThat(state.selectedSeller).isEqualTo(s)
        assertThat(state.showApprovalDialog).isTrue()
        assertThat(state.dialogType).isEqualTo(DialogType.APPROVE)
    }

    @Test
    fun `OnDismissDialog clears dialog state`() = runTest {
        val (vm, _) = newViewModel()
        vm.onAction(AdminSellerApprovalAction.OnSellerSelected(seller("s1")))
        advanceUntilIdle()

        vm.onAction(AdminSellerApprovalAction.OnDismissDialog)
        advanceUntilIdle()

        val state = vm.approvalState.value
        assertThat(state.showApprovalDialog).isFalse()
        assertThat(state.dialogType).isNull()
        assertThat(state.selectedSeller).isNull()
    }

    // -------- approve --------

    @Test
    fun `OnApprove routes to firestore with APPROVED status and clears dialog`() = runTest {
        val (vm, firestore) = newViewModel()
        vm.onAction(AdminSellerApprovalAction.OnSellerSelected(seller("s1")))

        vm.onAction(AdminSellerApprovalAction.OnApprove("s1", "looks good"))
        advanceUntilIdle()

        assertThat(firestore.updateSellerApprovalCalls)
            .containsExactly(Triple("s1", VerificationStatus.APPROVED, "looks good"))
        val state = vm.approvalState.value
        assertThat(state.showApprovalDialog).isFalse()
        assertThat(state.dialogType).isNull()
        assertThat(state.selectedSeller).isNull()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `OnApprove surfaces firestore error and keeps dialog open`() = runTest {
        val (vm, firestore) = newViewModel()
        firestore.updateSellerApprovalResult = Result.failure(RuntimeException("boom"))
        vm.onAction(AdminSellerApprovalAction.OnSellerSelected(seller("s1")))
        advanceUntilIdle()

        vm.onAction(AdminSellerApprovalAction.OnApprove("s1", ""))
        advanceUntilIdle()

        val state = vm.approvalState.value
        assertThat(state.errorMessage).isEqualTo("boom")
        assertThat(state.showApprovalDialog).isTrue()
    }

    // -------- reject --------

    @Test
    fun `OnReject routes with REJECTED status`() = runTest {
        val (vm, firestore) = newViewModel()

        vm.onAction(AdminSellerApprovalAction.OnReject("s2", "spam"))
        advanceUntilIdle()

        assertThat(firestore.updateSellerApprovalCalls)
            .containsExactly(Triple("s2", VerificationStatus.REJECTED, "spam"))
    }

    @Test
    fun `OnReject surfaces firestore error`() = runTest {
        val (vm, firestore) = newViewModel()
        firestore.updateSellerApprovalResult = Result.failure(RuntimeException("oops"))

        vm.onAction(AdminSellerApprovalAction.OnReject("s2", "spam"))
        advanceUntilIdle()

        assertThat(vm.approvalState.value.errorMessage).isEqualTo("oops")
    }

    // -------- requestMoreInfo --------

    @Test
    fun `OnRequestMoreInfo routes with REQUEST_MORE_INFO status`() = runTest {
        val (vm, firestore) = newViewModel()

        vm.onAction(AdminSellerApprovalAction.OnRequestMoreInfo("s3", "send tax doc"))
        advanceUntilIdle()

        assertThat(firestore.updateSellerApprovalCalls).containsExactly(
            Triple("s3", VerificationStatus.REQUEST_MORE_INFO, "send tax doc")
        )
    }

    @Test
    fun `OnRequestMoreInfo surfaces firestore error`() = runTest {
        val (vm, firestore) = newViewModel()
        firestore.updateSellerApprovalResult = Result.failure(RuntimeException("nope"))

        vm.onAction(AdminSellerApprovalAction.OnRequestMoreInfo("s3", ""))
        advanceUntilIdle()

        assertThat(vm.approvalState.value.errorMessage).isEqualTo("nope")
    }
}
