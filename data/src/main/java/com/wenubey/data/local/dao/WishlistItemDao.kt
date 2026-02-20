package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wenubey.data.local.entity.WishlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistItemDao {

    @Query("SELECT * FROM wishlist_items WHERE userId = :userId ORDER BY addedAt DESC")
    fun observeWishlistItems(userId: String): Flow<List<WishlistItemEntity>>

    @Query("SELECT * FROM wishlist_items WHERE userId = :userId AND productId = :productId LIMIT 1")
    suspend fun getWishlistItem(userId: String, productId: String): WishlistItemEntity?

    @Query("SELECT * FROM wishlist_items WHERE userId = :userId")
    suspend fun getItemsForUser(userId: String): List<WishlistItemEntity>

    @Upsert
    suspend fun upsert(item: WishlistItemEntity)

    @Query("DELETE FROM wishlist_items WHERE userId = :userId AND productId = :productId")
    suspend fun deleteItem(userId: String, productId: String)

    @Query("DELETE FROM wishlist_items WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM wishlist_items WHERE userId = :userId AND productId = :productId)")
    fun isWishlisted(userId: String, productId: String): Flow<Boolean>
}
