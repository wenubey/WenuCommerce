package com.wenubey.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 8 Plan 03 (NOTF-07 / D-05) — FcmTokenWorker.
 *
 * Verifies the one-time WorkManager token-refresh job:
 *  - success when the token resolves and updateFcmToken succeeds
 *  - retry (below MAX_RETRIES) when updateFcmToken fails
 *  - a UNIQUE_WORK_NAME distinct from SyncWorker's
 *
 * Uses [TestListenableWorkerBuilder] with an inline [WorkerFactory] to inject
 * the mocked dependencies (no Koin in the unit test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FcmTokenWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val testDispatcherProvider = object : DispatcherProvider {
        override fun io() = Dispatchers.Unconfined
        override fun main() = Dispatchers.Unconfined
        override fun default() = Dispatchers.Unconfined
    }

    private fun buildWorker(
        firestoreRepository: FirestoreRepository,
        firebaseMessaging: FirebaseMessaging,
        runAttemptCount: Int = 0,
    ): FcmTokenWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = FcmTokenWorker(
                appContext,
                workerParameters,
                firestoreRepository,
                firebaseMessaging,
                testDispatcherProvider,
            )
        }
        return TestListenableWorkerBuilder<FcmTokenWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun `doWork returns success when token resolves and updateFcmToken succeeds`() = runTest {
        val firebaseMessaging = mockk<FirebaseMessaging> {
            every { token } returns Tasks.forResult("fresh-token")
        }
        val firestoreRepository = mockk<FirestoreRepository> {
            coEvery { updateFcmToken("fresh-token") } returns Result.success(Unit)
        }

        val worker = buildWorker(firestoreRepository, firebaseMessaging)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns retry when updateFcmToken fails below max retries`() = runTest {
        val firebaseMessaging = mockk<FirebaseMessaging> {
            every { token } returns Tasks.forResult("fresh-token")
        }
        val firestoreRepository = mockk<FirestoreRepository> {
            coEvery { updateFcmToken(any()) } returns Result.failure(RuntimeException("boom"))
        }

        val worker = buildWorker(firestoreRepository, firebaseMessaging, runAttemptCount = 0)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork returns failure when updateFcmToken fails at max retries`() = runTest {
        val firebaseMessaging = mockk<FirebaseMessaging> {
            every { token } returns Tasks.forResult("fresh-token")
        }
        val firestoreRepository = mockk<FirestoreRepository> {
            coEvery { updateFcmToken(any()) } returns Result.failure(RuntimeException("boom"))
        }

        // runAttemptCount == MAX_RETRIES (3) → no more retries, fail.
        val worker = buildWorker(firestoreRepository, firebaseMessaging, runAttemptCount = 3)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `unique work name is fcm_token_refresh and distinct from SyncWorker`() {
        assertEquals("fcm_token_refresh", FcmTokenWorker.UNIQUE_WORK_NAME)
        // Guard against the DoS collision described in RESEARCH Pitfall 7.
        assert(FcmTokenWorker.UNIQUE_WORK_NAME != "sync_pending_operations")
    }
}
