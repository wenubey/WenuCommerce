package com.wenubey.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.entity.OperationStatus
import com.wenubey.data.local.entity.OperationType
import com.wenubey.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PendingOperationDaoTest {

    private lateinit var db: WenuCommerceDatabase
    private lateinit var dao: PendingOperationDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pendingOperationDao()
    }

    @After
    fun tearDown() = db.close()

    private fun op(
        type: OperationType = OperationType.ADD_TO_CART,
        entityId: String = "e",
        status: OperationStatus = OperationStatus.PENDING,
        createdAt: String = "2026-01-01T00:00:00Z",
    ) = PendingOperationEntity(
        operationType = type.name,
        entityId = entityId,
        payloadJson = "{}",
        status = status.name,
        createdAt = createdAt,
    )

    @Test
    fun `OperationType and OperationStatus enum names are stable`() {
        assertThat(OperationType.entries.map { it.name }).containsExactly(
            "ADD_TO_CART",
            "UPDATE_CART_QUANTITY",
            "REMOVE_FROM_CART",
            "UPDATE_PROFILE",
            "SUBMIT_REVIEW",
        ).inOrder()
        assertThat(OperationStatus.entries.map { it.name }).containsExactly(
            "PENDING", "IN_PROGRESS", "FAILED",
        ).inOrder()
    }

    @Test
    fun `insert returns the auto-generated id`() = runTest {
        val id = dao.insert(op())
        assertThat(id).isAtLeast(1L)
    }

    @Test
    fun `observePendingCount counts PENDING and IN_PROGRESS but not FAILED`() = runTest {
        dao.insert(op(status = OperationStatus.PENDING))
        dao.insert(op(status = OperationStatus.IN_PROGRESS))
        dao.insert(op(status = OperationStatus.FAILED))

        dao.observePendingCount().test {
            assertThat(awaitItem()).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getNextPending returns oldest PENDING and ignores IN_PROGRESS or FAILED`() = runTest {
        dao.insert(op(entityId = "newer", createdAt = "2026-02-01"))
        dao.insert(op(entityId = "older", createdAt = "2026-01-01"))
        dao.insert(op(entityId = "in-progress", status = OperationStatus.IN_PROGRESS, createdAt = "2025-01-01"))
        dao.insert(op(entityId = "failed", status = OperationStatus.FAILED, createdAt = "2024-01-01"))

        val next = dao.getNextPending()
        assertThat(next).isNotNull()
        assertThat(next!!.entityId).isEqualTo("older")
    }

    @Test
    fun `getNextPending returns null when queue is drained`() = runTest {
        dao.insert(op(status = OperationStatus.FAILED))
        assertThat(dao.getNextPending()).isNull()
    }

    @Test
    fun `observeAllOperations orders newest createdAt first`() = runTest {
        dao.insert(op(entityId = "older", createdAt = "2026-01-01"))
        dao.insert(op(entityId = "newer", createdAt = "2026-02-01"))

        dao.observeAllOperations().test {
            val list = awaitItem()
            assertThat(list.map { it.entityId }).containsExactly("newer", "older").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteById removes the row`() = runTest {
        val id = dao.insert(op())
        dao.deleteById(id)
        dao.observeAllOperations().test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateStatus updates status timestamp and errorMessage atomically`() = runTest {
        val id = dao.insert(op())

        dao.updateStatus(id, OperationStatus.FAILED.name, "2026-03-01T00:00:00Z", "network down")

        val row = dao.observeAllOperations().first().first()
        assertThat(row.status).isEqualTo("FAILED")
        assertThat(row.lastAttemptAt).isEqualTo("2026-03-01T00:00:00Z")
        assertThat(row.errorMessage).isEqualTo("network down")
    }

    @Test
    fun `updateStatus accepts null errorMessage and clears any prior message`() = runTest {
        val id = dao.insert(op())
        dao.updateStatus(id, OperationStatus.FAILED.name, "2026-03-01", "old error")
        dao.updateStatus(id, OperationStatus.IN_PROGRESS.name, "2026-03-02", null)

        val row = dao.observeAllOperations().first().first()
        assertThat(row.status).isEqualTo("IN_PROGRESS")
        assertThat(row.errorMessage).isNull()
    }

    @Test
    fun `incrementRetryCount bumps counter and timestamp without touching status`() = runTest {
        val id = dao.insert(op())

        dao.incrementRetryCount(id, "2026-03-01")
        dao.incrementRetryCount(id, "2026-03-02")

        val row = dao.observeAllOperations().first().first()
        assertThat(row.retryCount).isEqualTo(2)
        assertThat(row.lastAttemptAt).isEqualTo("2026-03-02")
        assertThat(row.status).isEqualTo(OperationStatus.PENDING.name)
    }

    @Test
    fun `update overwrites the full entity`() = runTest {
        val id = dao.insert(op(entityId = "before"))
        val updated = op(entityId = "after", status = OperationStatus.FAILED).copy(id = id)

        dao.update(updated)

        val row = dao.observeAllOperations().first().first()
        assertThat(row.entityId).isEqualTo("after")
        assertThat(row.status).isEqualTo(OperationStatus.FAILED.name)
    }
}
