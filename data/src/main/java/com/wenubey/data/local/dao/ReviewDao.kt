package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wenubey.data.local.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Upsert
    suspend fun upsert(review: ReviewEntity)

    @Upsert
    suspend fun upsertAll(reviews: List<ReviewEntity>)

    /** Product-scoped Room-first observe; only visible reviews surface to the UI. */
    @Query("SELECT * FROM reviews WHERE productId = :productId AND isVisible = 1")
    fun observeByProduct(productId: String): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE reviewerId = :reviewerId AND productId = :productId LIMIT 1")
    suspend fun getByReviewerAndProduct(reviewerId: String, productId: String): ReviewEntity?

    @Query("DELETE FROM reviews WHERE productId = :productId")
    suspend fun deleteByProduct(productId: String)
}
