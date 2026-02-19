package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wenubey.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE status = 'ACTIVE' AND categoryId = :categoryId")
    fun observeActiveProductsByCategory(categoryId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE status = 'ACTIVE' AND categoryId = :categoryId AND subcategoryId = :subcategoryId")
    fun observeActiveProductsByCategoryAndSubcategory(
        categoryId: String,
        subcategoryId: String
    ): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE status = 'ACTIVE'")
    fun observeAllActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE sellerId = :sellerId")
    fun observeSellerProducts(sellerId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE status = :status")
    fun observeProductsByStatus(status: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE status = 'ACTIVE' AND (title LIKE '%' || :query || '%' OR categoryName LIKE '%' || :query || '%' OR searchKeywordsJson LIKE '%' || :query || '%')")
    suspend fun searchActiveProducts(query: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE title LIKE '%' || :query || '%' OR categoryName LIKE '%' || :query || '%' OR searchKeywordsJson LIKE '%' || :query || '%'")
    suspend fun searchAllProducts(query: String): List<ProductEntity>

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM products")
    suspend fun clearAll()
}
