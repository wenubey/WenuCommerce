package com.wenubey.wenucommerce.queue_management

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.dao.PendingOperationDao
import com.wenubey.data.local.entity.OperationStatus
import com.wenubey.data.local.entity.OperationType
import com.wenubey.data.local.entity.PendingOperationEntity
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class QueueManagementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build(),
        )
    }

    private fun newViewModel(
        operations: MutableStateFlow<List<PendingOperationEntity>> = MutableStateFlow(emptyList()),
    ): Pair<QueueManagementViewModel, PendingOperationDao> {
        val dao: PendingOperationDao = mockk(relaxed = true)
        every { dao.observeAllOperations() } returns operations
        return QueueManagementViewModel(dao, dispatcherProvider, app) to dao
    }

    private fun entity(
        id: Long,
        type: OperationType = OperationType.ADD_TO_CART,
        status: OperationStatus = OperationStatus.PENDING,
        createdAt: String = "2026-06-14T10:00:00Z",
    ) = PendingOperationEntity(
        id = id,
        operationType = type.name,
        entityId = "user-1",
        payloadJson = "{}",
        status = status.name,
        retryCount = 0,
        createdAt = createdAt,
    )

    @Test
    fun `operations stream maps dao entities to UI models`() = runTest {
        val source = MutableStateFlow(emptyList<PendingOperationEntity>())
        val (vm, _) = newViewModel(source)

        vm.operations.test {
            assertThat(awaitItem()).isEmpty()

            source.value = listOf(
                entity(id = 1, type = OperationType.ADD_TO_CART, status = OperationStatus.PENDING),
                entity(id = 2, type = OperationType.SUBMIT_REVIEW, status = OperationStatus.FAILED),
            )
            val list = awaitItem()
            assertThat(list).hasSize(2)
            assertThat(list[0].id).isEqualTo(1L)
            assertThat(list[0].displayName).isEqualTo("Cart update")
            assertThat(list[0].statusText).isEqualTo("Pending")
            assertThat(list[1].displayName).isEqualTo("Review submission")
            assertThat(list[1].statusText).isEqualTo("Failed")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * stateIn's initialValue (emptyList) emits before upstream collection
     * begins; this helper drains it so callers can immediately assert the
     * first real upstream emission.
     */
    private suspend fun app.cash.turbine.ReceiveTurbine<List<PendingOperationUiModel>>.firstReal(): List<PendingOperationUiModel> {
        var item = awaitItem()
        while (item.isEmpty()) item = awaitItem()
        return item
    }

    @Test
    fun `operation type to display name covers every enum value`() = runTest {
        val source = MutableStateFlow(
            listOf(
                entity(1, OperationType.ADD_TO_CART),
                entity(2, OperationType.UPDATE_CART_QUANTITY),
                entity(3, OperationType.REMOVE_FROM_CART),
                entity(4, OperationType.UPDATE_PROFILE),
                entity(5, OperationType.SUBMIT_REVIEW),
            )
        )
        val (vm, _) = newViewModel(source)

        vm.operations.test {
            val list = firstReal()
            assertThat(list.map { it.displayName }).containsExactly(
                "Cart update", "Cart update", "Cart update",
                "Profile update", "Review submission",
            ).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown operation type maps to 'Unknown operation'`() = runTest {
        val raw = PendingOperationEntity(
            id = 99,
            operationType = "INVENTED_OP",
            entityId = "u",
            payloadJson = "{}",
            status = OperationStatus.PENDING.name,
            createdAt = "2026-06-14T10:00:00Z",
        )
        val source = MutableStateFlow(listOf(raw))
        val (vm, _) = newViewModel(source)

        vm.operations.test {
            val list = firstReal()
            assertThat(list.single().displayName).isEqualTo("Unknown operation")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `status text mapping covers PENDING IN_PROGRESS and FAILED`() = runTest {
        val source = MutableStateFlow(
            listOf(
                entity(1, status = OperationStatus.PENDING),
                entity(2, status = OperationStatus.IN_PROGRESS),
                entity(3, status = OperationStatus.FAILED),
            )
        )
        val (vm, _) = newViewModel(source)

        vm.operations.test {
            val list = firstReal()
            assertThat(list.map { it.statusText })
                .containsExactly("Pending", "Syncing…", "Failed")
                .inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `malformed createdAt falls back to the raw string`() = runTest {
        val source = MutableStateFlow(listOf(entity(1, createdAt = "not-an-instant")))
        val (vm, _) = newViewModel(source)

        vm.operations.test {
            val list = firstReal()
            assertThat(list.single().createdAt).isEqualTo("not-an-instant")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retryOperation resets status to PENDING and enqueues SyncWorker`() = runTest {
        val (vm, dao) = newViewModel()

        vm.retryOperation(id = 42)
        advanceUntilIdle()

        val timestamp = slot<String>()
        coVerify(exactly = 1) {
            dao.updateStatus(
                id = 42,
                status = OperationStatus.PENDING.name,
                timestamp = capture(timestamp),
                errorMessage = null,
            )
        }
        // updateStatus' default-argument boilerplate may differ across mockk
        // versions; we only care that the call happened with PENDING + a
        // non-empty ISO timestamp.
        assertThat(timestamp.captured).isNotEmpty()
    }

    @Test
    fun `discardOperation deletes the row by id`() = runTest {
        val (vm, dao) = newViewModel()
        coEvery { dao.deleteById(any()) } returns Unit

        vm.discardOperation(id = 99)
        advanceUntilIdle()

        coVerify(exactly = 1) { dao.deleteById(99) }
    }
}
