package com.wenubey.domain.repository

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun getCategories(): Result<List<Category>>
    suspend fun createCategory(category: Category): Result<Category>
    suspend fun addSubcategory(categoryId: String, subcategory: Subcategory): Result<Unit>
    suspend fun updateCategory(category: Category): Result<Unit>
    suspend fun deleteCategory(categoryId: String): Result<Unit>
    suspend fun uploadCategoryImage(imageUri: String, categoryId: String): Result<String>
}
