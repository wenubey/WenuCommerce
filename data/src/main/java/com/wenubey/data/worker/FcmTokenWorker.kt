package com.wenubey.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessaging
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Duration

/**
 * One-time WorkManager job that refreshes the device FCM token in the
 * signed-in user's Firestore doc (NOTF-07 / D-05).
 *
 * Replaces the old fire-and-forget `onNewToken → updateFcmToken` path: the
 * job survives process death, waits for connectivity (CONNECTED constraint),
 * and retries with exponential backoff up to [MAX_RETRIES]. It fetches the
 * current token from [FirebaseMessaging] rather than trusting the (possibly
 * stale) token passed to `onNewToken`, then awaits the suspend
 * [FirestoreRepository.updateFcmToken] (made suspend in 08-01) so success /
 * retry can be decided on the actual write outcome.
 *
 * Uses a UNIQUE_WORK_NAME distinct from [SyncWorker] to avoid the two jobs
 * replacing each other (RESEARCH Pitfall 7).
 *
 * Dependencies injected via Koin WorkerFactory.
 */
class FcmTokenWorker(
    appContext: Context,
    params: WorkerParameters,
    private val firestoreRepository: FirestoreRepository,
    private val firebaseMessaging: FirebaseMessaging,
    private val dispatcherProvider: DispatcherProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = dispatcherProvider.io().run {
        return try {
            val token = firebaseMessaging.token.await()
            firestoreRepository.updateFcmToken(token).getOrThrow()
            Timber.d("FcmTokenWorker: token refreshed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "FcmTokenWorker: token update failed (attempt $runAttemptCount)")
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        const val UNIQUE_WORK_NAME = "fcm_token_refresh"

        /**
         * Enqueue a one-time token-refresh job (REPLACE so only one is active,
         * CONNECTED so it waits for network, 30s exponential backoff).
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<FcmTokenWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    Duration.ofSeconds(30),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )

            Timber.d("FcmTokenWorker: enqueued unique work '$UNIQUE_WORK_NAME'")
        }
    }
}
