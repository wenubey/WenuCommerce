package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wenubey.data.local.entity.CartItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CartItemDao {

    @Query("SELECT * FROM cart_items WHERE userId = :userId ORDER BY addedAt ASC")
    fun observeCartItems(userId: String): Flow<List<CartItemEntity>>

    @Query("SELECT COUNT(DISTINCT productId) FROM cart_items WHERE userId = :userId")
    fun observeUniqueProductCount(userId: String): Flow<Int>

    @Query("SELECT * FROM cart_items WHERE userId = :userId AND productId = :productId LIMIT 1")
    suspend fun getCartItem(userId: String, productId: String): CartItemEntity?

    @Upsert
    suspend fun upsert(item: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE userId = :userId AND productId = :productId")
    suspend fun deleteItem(userId: String, productId: String)

    @Query("DELETE FROM cart_items WHERE userId = :userId")
    suspend fun clearCart(userId: String)

    @Query("UPDATE cart_items SET quantity = :quantity, updatedAt = :updatedAt WHERE userId = :userId AND productId = :productId")
    suspend fun updateQuantity(userId: String, productId: String, quantity: Int, updatedAt: String)

    @Query("UPDATE cart_items SET isProductDeleted = :deleted, availableStock = :stock WHERE userId = :userId AND productId = :productId")
    suspend fun updateProductStatus(userId: String, productId: String, deleted: Boolean, stock: Int)
}
