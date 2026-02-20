package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent queue for offline write operations.
 *
 * Operations are stored in PENDING status, processed sequentially by SyncWorker,
 * and retried up to 3 times with exponential backoff. Failed operations remain
 * in the database for manual retry or debugging.
 */
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String, // OperationType.name
    val entityId: String, // ID of the entity being mutated
    val payloadJson: String, // JSON-serialized mutation data
    val status: String = OperationStatus.PENDING.name, // OperationStatus.name
    val retryCount: Int = 0,
    val createdAt: String, // ISO 8601 timestamp
    val lastAttemptAt: String? = null,
    val errorMessage: String? = null
)

/**
 * Types of offline write operations.
 * Stored as String name in PendingOperationEntity.operationType.
 */
enum class OperationType {
    ADD_TO_CART,
    UPDATE_CART_QUANTITY,
    REMOVE_FROM_CART,
    UPDATE_PROFILE,
    SUBMIT_REVIEW
}

/**
 * Operation status lifecycle: PENDING → IN_PROGRESS → (success = deleted) | (failure = FAILED)
 */
enum class OperationStatus {
    PENDING,
    IN_PROGRESS,
    FAILED
}
