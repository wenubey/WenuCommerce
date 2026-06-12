package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Bare-bones FakeProductRepository. Only the methods exercised by ViewModel
 * tests are wired with state; the rest return safe defaults. Add fields and
 * call lists incrementally as more ViewModel tests need them.
 */
class FakeProductRepository : ProductRepository {

    private val productsBySeller = MutableStateFlow<Map<String, List<Product>>>(emptyMap())

    var observeSellerProductsFlow: Flow<List<Product>>? = null

    val createProductCalls = mutableListOf<Product>()
    val updateProductCalls = mutableListOf<Product>()
    val submitForReviewCalls = mutableListOf<String>()
    val archiveCalls = mutableListOf<String>()
    val unarchiveCalls = mutableListOf<String>()
    val getProductByIdCalls = mutableListOf<String>()
    val incrementViewCountCalls = mutableListOf<String>()

    var createProductResult: (Product) -> Result<Product> = { Result.success(it) }
    var updateProductResult: Result<Unit> = Result.success(Unit)
    var getProductByIdResult: (String) -> Result<Product> = {
        Result.failure(NoSuchElementException("not stubbed"))
    }
    var searchActiveResult: Result<List<Product>> = Result.success(emptyList())
    var searchAllResult: Result<List<Product>> = Result.success(emptyList())

    fun emitSellerProducts(sellerId: String, products: List<Product>) {
        productsBySeller.value = productsBySeller.value.toMutableMap().apply {
            put(sellerId, products)
        }
    }

    override suspend fun createProduct(product: Product): Result<Product> {
        createProductCalls.add(product)
        return createProductResult(product)
    }

    override suspend fun updateProduct(product: Product): Result<Unit> {
        updateProductCalls.add(product)
        return updateProductResult
    }

    override suspend fun submitForReview(productId: String): Result<Unit> {
        submitForReviewCalls.add(productId)
        return Result.success(Unit)
    }

    override suspend fun archiveProduct(productId: String): Result<Unit> {
        archiveCalls.add(productId)
        return Result.success(Unit)
    }

    override suspend fun unarchiveProduct(productId: String): Result<Unit> {
        unarchiveCalls.add(productId)
        return Result.success(Unit)
    }

    override suspend fun uploadProductImage(
        localUri: String,
        productId: String,
        imageId: String,
    ): Result<String> = Result.success("https://fake/$imageId.jpg")

    override suspend fun deleteProductImage(storagePath: String): Result<Unit> = Result.success(Unit)

    override fun observeSellerProducts(sellerId: String): Flow<List<Product>> =
        observeSellerProductsFlow ?: productsBySeller.map { it[sellerId].orEmpty() }

    override suspend fun getSellerProducts(sellerId: String): Result<List<Product>> =
        Result.success(productsBySeller.value[sellerId].orEmpty())

    override fun observeActiveProductsByCategory(categoryId: String): Flow<List<Product>> =
        productsBySeller.map { snapshot ->
            snapshot.values.flatten().filter { it.categoryId == categoryId && it.status == ProductStatus.ACTIVE }
        }

    override fun observeActiveProductsByCategoryAndSubcategory(
        categoryId: String,
        subcategoryId: String?,
    ): Flow<List<Product>> = productsBySeller.map { snapshot ->
        snapshot.values.flatten().filter {
            it.categoryId == categoryId &&
                it.status == ProductStatus.ACTIVE &&
                (subcategoryId == null || it.subcategoryId == subcategoryId)
        }
    }

    override suspend fun getProductById(productId: String): Result<Product> {
        getProductByIdCalls.add(productId)
        return getProductByIdResult(productId)
    }

    override suspend fun getStorefrontProducts(sellerId: String): Result<List<Product>> =
        Result.success(productsBySeller.value[sellerId].orEmpty())

    override fun observeProductsByStatus(status: ProductStatus): Flow<List<Product>> =
        productsBySeller.map { snapshot ->
            snapshot.values.flatten().filter { it.status == status }
        }

    override suspend fun approveProduct(productId: String): Result<Unit> = Result.success(Unit)
    override suspend fun suspendProduct(productId: String, reason: String, adminId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun adminUpdateProduct(product: Product): Result<Unit> = Result.success(Unit)

    override suspend fun searchActiveProducts(
        query: String,
        categoryId: String?,
        subcategoryId: String?,
    ): Result<List<Product>> = searchActiveResult

    override suspend fun searchAllProducts(
        query: String,
        categoryId: String?,
        subcategoryId: String?,
    ): Result<List<Product>> = searchAllResult

    override suspend fun incrementViewCount(productId: String): Result<Unit> {
        incrementViewCountCalls.add(productId)
        return Result.success(Unit)
    }

    override suspend fun decrementStock(productId: String, variantId: String, quantity: Int): Result<Unit> =
        Result.success(Unit)

    override suspend fun addProductToSellerDocument(sellerId: String, productId: String): Result<Unit> =
        Result.success(Unit)
}
