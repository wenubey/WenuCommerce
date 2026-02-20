package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wenubey.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for offline write queue management.
 *
 * Key features:
 * - observePendingCount() provides reactive count for UI banner (PENDING + IN_PROGRESS)
 * - getNextPending() returns oldest PENDING operation for sequential processing
 * - updateStatus() atomically updates status + timestamp + error message
 * - incrementRetryCount() atomically increments retry counter
 */
@Dao
interface PendingOperationDao {

    /**
     * Reactive count of pending and in-progress operations.
     * Used by UI to show/hide offline sync banner.
     */
    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'PENDING' OR status = 'IN_PROGRESS'")
    fun observePendingCount(): Flow<Int>

    /**
     * Get the next pending operation to process (oldest first).
     * Returns null if queue is drained.
     */
    @Query("SELECT * FROM pending_operations WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPending(): PendingOperationEntity?

    /**
     * Observe all operations for queue management screen.
     * Newest first for debugging recent failures.
     */
    @Query("SELECT * FROM pending_operations ORDER BY createdAt DESC")
    fun observeAllOperations(): Flow<List<PendingOperationEntity>>

    /**
     * Insert a new operation into the queue.
     * Returns the auto-generated ID.
     */
    @Insert
    suspend fun insert(operation: PendingOperationEntity): Long

    /**
     * Update an existing operation (used for full entity updates).
     */
    @Update
    suspend fun update(operation: PendingOperationEntity)

    /**
     * Delete a successfully processed operation.
     */
    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Atomically update operation status, timestamp, and error message.
     * Used for marking operations as IN_PROGRESS or FAILED.
     */
    @Query("UPDATE pending_operations SET status = :status, lastAttemptAt = :timestamp, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, timestamp: String, errorMessage: String? = null)

    /**
     * Atomically increment retry count and update last attempt timestamp.
     * Called after each failed retry attempt.
     */
    @Query("UPDATE pending_operations SET retryCount = retryCount + 1, lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun incrementRetryCount(id: Long, timestamp: String)
}
