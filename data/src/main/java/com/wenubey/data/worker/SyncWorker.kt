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
import com.wenubey.data.local.dao.PendingOperationDao
import com.wenubey.data.local.entity.OperationStatus
import com.wenubey.data.local.entity.OperationType
import com.wenubey.data.repository.AddToCartPayload
import com.wenubey.data.repository.UpdateCartQuantityPayload
import com.wenubey.domain.repository.CartRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Duration
import java.time.Instant

/**
 * Background worker that drains the offline write queue sequentially.
 *
 * Behavior:
 * - Runs only when network is connected (CONNECTED constraint)
 * - Processes one operation at a time (oldest first)
 * - Retries up to 3 times with exponential backoff (30s initial)
 * - Marks operations as FAILED after max retries exceeded
 * - Re-enqueues itself after each successful operation to process next item
 *
 * Dependencies injected via Koin WorkerFactory:
 * - PendingOperationDao: Queue access
 * - CartRepository: Cart Firestore sync
 * - DispatcherProvider: IO dispatcher for database operations
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val pendingOperationDao: PendingOperationDao,
    private val cartRepository: CartRepository,
    private val dispatcherProvider: DispatcherProvider
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result = dispatcherProvider.io().run {
        // Handle CANCELLED state before doing any work
        if (isStopped) {
            Timber.d("SyncWorker: Work stopped before processing")
            return Result.failure()
        }

        // Check if max retry limit exceeded for this work request
        if (runAttemptCount > MAX_RETRIES) {
            Timber.e("SyncWorker: Max retries ($MAX_RETRIES) exceeded for work request")
            // Mark the current operation as FAILED if it exists
            val operation = pendingOperationDao.getNextPending()
            operation?.let {
                pendingOperationDao.updateStatus(
                    id = it.id,
                    status = OperationStatus.FAILED.name,
                    timestamp = Instant.now().toString(),
                    errorMessage = "Max retries exceeded"
                )
                Timber.e("SyncWorker: Marked operation ${it.id} (${it.operationType}) as FAILED")
            }
            return Result.failure()
        }

        // Get next pending operation
        val operation = pendingOperationDao.getNextPending()
        if (operation == null) {
            Timber.d("SyncWorker: Queue drained, no pending operations")
            return Result.success()
        }

        Timber.d("SyncWorker: Processing operation ${operation.id} (${operation.operationType}), attempt ${operation.retryCount + 1}")

        // Mark as IN_PROGRESS
        pendingOperationDao.updateStatus(
            id = operation.id,
            status = OperationStatus.IN_PROGRESS.name,
            timestamp = Instant.now().toString()
        )

        try {
            // Parse operation type with fallback for unknown types
            val operationType = runCatching {
                OperationType.valueOf(operation.operationType)
            }.getOrElse {
                Timber.e("SyncWorker: Unknown operation type: ${operation.operationType}")
                pendingOperationDao.updateStatus(
                    id = operation.id,
                    status = OperationStatus.FAILED.name,
                    timestamp = Instant.now().toString(),
                    errorMessage = "Unknown operation type: ${operation.operationType}"
                )
                return Result.failure()
            }

            // Execute operation based on type
            when (operationType) {
                OperationType.ADD_TO_CART -> {
                    val payload = json.decodeFromString<AddToCartPayload>(operation.payloadJson)
                    cartRepository.syncAddToCart(
                        userId = operation.entityId,
                        productId = payload.productId,
                        quantity = payload.quantity,
                        snapshotPrice = payload.snapshotPrice
                    )
                }
                OperationType.UPDATE_CART_QUANTITY -> {
                    val payload = json.decodeFromString<UpdateCartQuantityPayload>(operation.payloadJson)
                    cartRepository.syncUpdateQuantity(
                        userId = operation.entityId,
                        productId = payload.productId,
                        quantity = payload.quantity
                    )
                }
                OperationType.REMOVE_FROM_CART -> {
                    // payloadJson is the productId for REMOVE_FROM_CART
                    cartRepository.syncRemoveFromCart(
                        userId = operation.entityId,
                        productId = operation.payloadJson
                    )
                }
                OperationType.UPDATE_PROFILE -> TODO("Wire UPDATE_PROFILE to ProfileRepository in Phase 3+")
                OperationType.SUBMIT_REVIEW -> TODO("Wire SUBMIT_REVIEW to ReviewRepository in Phase 3+")
            }

            // On success: delete operation and enqueue next
            pendingOperationDao.deleteById(operation.id)
            Timber.d("SyncWorker: Successfully processed operation ${operation.id}")
            enqueue(applicationContext) // Process next item
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: Failed to process operation ${operation.id}")

            // Increment retry count
            pendingOperationDao.incrementRetryCount(
                id = operation.id,
                timestamp = Instant.now().toString()
            )

            // Check if we've exhausted retries for this operation
            if (operation.retryCount + 1 >= MAX_RETRIES) {
                pendingOperationDao.updateStatus(
                    id = operation.id,
                    status = OperationStatus.FAILED.name,
                    timestamp = Instant.now().toString(),
                    errorMessage = e.message
                )
                Timber.e("SyncWorker: Operation ${operation.id} exhausted retries, marked as FAILED")
                return Result.failure()
            }

            // Retry with backoff
            Timber.d("SyncWorker: Retrying operation ${operation.id}, retry ${operation.retryCount + 1}/$MAX_RETRIES")
            return Result.retry()
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        const val UNIQUE_WORK_NAME = "sync_pending_operations"

        /**
         * Enqueue a one-time sync work request with network constraints and exponential backoff.
         *
         * - REPLACE policy ensures only one work request is active at a time
         * - CONNECTED constraint prevents sync attempts without network
         * - 30s exponential backoff gives network time to stabilize after failures
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    Duration.ofSeconds(30)
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Timber.d("SyncWorker: Enqueued work request")
        }
    }
}
