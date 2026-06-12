package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCategoryRepository : CategoryRepository {

    private val categories = MutableStateFlow<List<Category>>(emptyList())

    val createCalls = mutableListOf<Category>()
    val updateCalls = mutableListOf<Category>()
    val deleteCalls = mutableListOf<String>()
    val addSubcategoryCalls = mutableListOf<Pair<String, Subcategory>>()
    val uploadImageCalls = mutableListOf<Pair<String, String>>()

    var getCategoriesResult: Result<List<Category>> = Result.success(emptyList())
    var createCategoryResult: (Category) -> Result<Category> = { Result.success(it) }
    var updateCategoryResult: Result<Unit> = Result.success(Unit)
    var deleteCategoryResult: Result<Unit> = Result.success(Unit)
    var addSubcategoryResult: Result<Unit> = Result.success(Unit)
    var uploadImageResult: Result<String> = Result.success("https://fake/img.jpg")

    fun setCategories(list: List<Category>) {
        categories.value = list
        getCategoriesResult = Result.success(list)
    }

    override fun observeCategories(): Flow<List<Category>> = categories
    override suspend fun getCategories(): Result<List<Category>> = getCategoriesResult
    override suspend fun createCategory(category: Category): Result<Category> {
        createCalls.add(category)
        return createCategoryResult(category)
    }
    override suspend fun addSubcategory(categoryId: String, subcategory: Subcategory): Result<Unit> {
        addSubcategoryCalls.add(categoryId to subcategory)
        return addSubcategoryResult
    }
    override suspend fun updateCategory(category: Category): Result<Unit> {
        updateCalls.add(category)
        return updateCategoryResult
    }
    override suspend fun deleteCategory(categoryId: String): Result<Unit> {
        deleteCalls.add(categoryId)
        return deleteCategoryResult
    }
    override suspend fun uploadCategoryImage(imageUri: String, categoryId: String): Result<String> {
        uploadImageCalls.add(imageUri to categoryId)
        return uploadImageResult
    }
}
