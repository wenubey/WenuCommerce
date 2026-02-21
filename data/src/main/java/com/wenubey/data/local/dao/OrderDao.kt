package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.wenubey.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Insert(onConflict = REPLACE)
    suspend fun upsert(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE id = :orderId")
    fun observeOrderById(orderId: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeOrdersByUser(userId: String): Flow<List<OrderEntity>>

    @Query("UPDATE orders SET status = :status, updatedAt = :updatedAt WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String, updatedAt: String)
}
