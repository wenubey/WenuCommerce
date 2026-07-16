package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.wenubey.data.local.entity.SellerOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SellerOrderDao {

    @Insert(onConflict = REPLACE)
    suspend fun upsert(entity: SellerOrderEntity)

    @Insert(onConflict = REPLACE)
    suspend fun upsertAll(entities: List<SellerOrderEntity>)

    @Query("SELECT * FROM seller_orders WHERE id = :id")
    fun observeById(id: String): Flow<SellerOrderEntity?>

    @Query("SELECT * FROM seller_orders WHERE id = :id")
    suspend fun getById(id: String): SellerOrderEntity?

    @Query("SELECT * FROM seller_orders WHERE parentOrderId = :parentId")
    fun observeByParent(parentId: String): Flow<List<SellerOrderEntity>>

    @Query("SELECT * FROM seller_orders WHERE sellerId = :sellerId ORDER BY createdAt DESC")
    fun observeBySeller(sellerId: String): Flow<List<SellerOrderEntity>>

    /**
     * Backs the customer-side delivered-order gate (07-02): given a userId +
     * status (e.g. "DELIVERED"), returns the matching seller orders so the
     * caller can check whether any contains the target productId in itemsJson.
     */
    @Query("SELECT * FROM seller_orders WHERE userId = :userId AND status = :status")
    suspend fun getByUserAndStatus(userId: String, status: String): List<SellerOrderEntity>

    @Query(
        "UPDATE seller_orders SET status = :status, statusHistoryJson = :historyJson, " +
            "trackingNumber = :tracking, updatedAt = :now WHERE id = :id"
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        historyJson: String,
        tracking: String?,
        now: String
    )
}
