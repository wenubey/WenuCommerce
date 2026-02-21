package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.wenubey.data.local.entity.AddressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AddressDao {

    @Insert(onConflict = REPLACE)
    suspend fun upsert(address: AddressEntity)

    @Insert(onConflict = REPLACE)
    suspend fun upsertAll(addresses: List<AddressEntity>)

    @Query("SELECT * FROM addresses WHERE userId = :userId ORDER BY fullName ASC")
    fun observeByUser(userId: String): Flow<List<AddressEntity>>

    @Query("DELETE FROM addresses WHERE userId = :userId AND addressId = :addressId")
    suspend fun delete(userId: String, addressId: String)

    @Query("DELETE FROM addresses WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
