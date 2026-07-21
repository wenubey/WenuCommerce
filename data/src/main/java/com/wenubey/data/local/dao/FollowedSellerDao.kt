package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wenubey.data.local.entity.FollowedSellerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowedSellerDao {

    @Query("SELECT * FROM followed_sellers WHERE userId = :userId ORDER BY followedAt DESC")
    fun observeFollowedSellers(userId: String): Flow<List<FollowedSellerEntity>>

    @Query("SELECT * FROM followed_sellers WHERE userId = :userId AND sellerId = :sellerId LIMIT 1")
    suspend fun getFollowedSeller(userId: String, sellerId: String): FollowedSellerEntity?

    @Upsert
    suspend fun upsert(item: FollowedSellerEntity)

    @Query("DELETE FROM followed_sellers WHERE userId = :userId AND sellerId = :sellerId")
    suspend fun deleteItem(userId: String, sellerId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM followed_sellers WHERE userId = :userId AND sellerId = :sellerId)")
    fun isFollowing(userId: String, sellerId: String): Flow<Boolean>
}
