package com.wenubey.wenucommerce.admin

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeFirestoreRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminBadgeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        firestore: FakeFirestoreRepository = FakeFirestoreRepository(),
    ): Pair<AdminBadgeViewModel, FakeFirestoreRepository> =
        AdminBadgeViewModel(dispatcherProvider, firestore) to firestore

    @Test
    fun `initial state has zero pending approvals`() = runTest {
        val (vm, _) = newViewModel()

        assertThat(vm.badgeState.value.pendingApprovals).isEqualTo(0)
    }

    @Test
    fun `badge reflects latest pending count from firestore observer`() = runTest {
        val (vm, firestore) = newViewModel()

        firestore.emitPendingResubmittedCount(3)
        advanceUntilIdle()

        assertThat(vm.badgeState.value.pendingApprovals).isEqualTo(3)
    }

    @Test
    fun `badge updates when count changes over time`() = runTest {
        val (vm, firestore) = newViewModel()

        vm.badgeState.test {
            assertThat(awaitItem().pendingApprovals).isEqualTo(0)

            firestore.emitPendingResubmittedCount(5)
            assertThat(awaitItem().pendingApprovals).isEqualTo(5)

            firestore.emitPendingResubmittedCount(2)
            assertThat(awaitItem().pendingApprovals).isEqualTo(2)

            firestore.emitPendingResubmittedCount(0)
            assertThat(awaitItem().pendingApprovals).isEqualTo(0)
        }
    }
}
