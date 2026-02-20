package com.wenubey.wenucommerce.queue_management

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.data.local.dao.PendingOperationDao
import com.wenubey.data.local.entity.OperationStatus
import com.wenubey.data.local.entity.OperationType
import com.wenubey.data.local.entity.PendingOperationEntity
import com.wenubey.data.worker.SyncWorker
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ViewModel for queue management screen.
 *
 * Observes all pending operations from the database and provides
 * retry/discard actions for failed operations.
 */
class QueueManagementViewModel(
    private val pendingOperationDao: PendingOperationDao,
    private val dispatcherProvider: DispatcherProvider,
    private val application: Application
) : ViewModel() {

    val operations: StateFlow<List<PendingOperationUiModel>> =
        pendingOperationDao.observeAllOperations()
            .map { entities ->
                entities.map { it.toUiModel() }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun retryOperation(id: Long) {
        viewModelScope.launch(dispatcherProvider.io()) {
            // Reset to PENDING status to allow SyncWorker to retry
            pendingOperationDao.updateStatus(
                id = id,
                status = OperationStatus.PENDING.name,
                timestamp = Instant.now().toString()
            )
            // Trigger SyncWorker to process the queue
            SyncWorker.enqueue(application)
        }
    }

    fun discardOperation(id: Long) {
        viewModelScope.launch(dispatcherProvider.io()) {
            pendingOperationDao.deleteById(id)
        }
    }

    private fun PendingOperationEntity.toUiModel(): PendingOperationUiModel {
        val operationTypeEnum = runCatching {
            OperationType.valueOf(operationType)
        }.getOrNull()

        val displayName = when (operationTypeEnum) {
            OperationType.ADD_TO_CART -> "Cart update"
            OperationType.UPDATE_CART_QUANTITY -> "Cart update"
            OperationType.REMOVE_FROM_CART -> "Cart update"
            OperationType.UPDATE_PROFILE -> "Profile update"
            OperationType.SUBMIT_REVIEW -> "Review submission"
            null -> "Unknown operation"
        }

        val statusEnum = runCatching {
            OperationStatus.valueOf(status)
        }.getOrDefault(OperationStatus.PENDING)

        val statusText = when (statusEnum) {
            OperationStatus.PENDING -> "Pending"
            OperationStatus.IN_PROGRESS -> "Syncing…"
            OperationStatus.FAILED -> "Failed"
        }

        val createdAtFormatted = runCatching {
            val instant = Instant.parse(createdAt)
            val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        }.getOrDefault(createdAt)

        return PendingOperationUiModel(
            id = id,
            displayName = displayName,
            statusText = statusText,
            status = statusEnum,
            createdAt = createdAtFormatted
        )
    }
}

/**
 * UI model for displaying pending operations.
 */
data class PendingOperationUiModel(
    val id: Long,
    val displayName: String,
    val statusText: String,
    val status: OperationStatus,
    val createdAt: String
)
