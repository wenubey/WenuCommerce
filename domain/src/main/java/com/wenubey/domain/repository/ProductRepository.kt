package com.wenubey.domain.repository

import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import kotlinx.coroutines.flow.Flow

interface ProductRepository {

    // Seller Operations
    suspend fun createProduct(product: Product): Result<Product>
    suspend fun updateProduct(product: Product): Result<Unit>
    suspend fun submitForReview(productId: String): Result<Unit>
    suspend fun archiveProduct(productId: String): Result<Unit>
    suspend fun unarchiveProduct(productId: String): Result<Unit>

    // Seller Image Operations
    suspend fun uploadProductImage(
        localUri: String,
        productId: String,
        imageId: String,
    ): Result<String>
    suspend fun deleteProductImage(storagePath: String): Result<Unit>

    // Seller Queries
    fun observeSellerProducts(sellerId: String): Flow<List<Product>>
    suspend fun getSellerProducts(sellerId: String): Result<List<Product>>

    // Customer Queries
    fun observeActiveProductsByCategory(categoryId: String): Flow<List<Product>>
    fun observeActiveProductsByCategoryAndSubcategory(
        categoryId: String,
        subcategoryId: String?,
    ): Flow<List<Product>>
    suspend fun getProductById(productId: String): Result<Product>

    // Storefront
    suspend fun getStorefrontProducts(sellerId: String): Result<List<Product>>

    // Admin Operations
    fun observeProductsByStatus(status: ProductStatus): Flow<List<Product>>
    suspend fun approveProduct(productId: String): Result<Unit>
    suspend fun suspendProduct(productId: String, reason: String, adminId: String): Result<Unit>
    suspend fun adminUpdateProduct(product: Product): Result<Unit>

    // Search Operations

    /**
     * Searches active products for the customer role.
     * Uses searchKeywords arrayContains for the primary token, then filters client-side
     * for all additional tokens. Returns only ACTIVE products.
     *
     * @param query The raw user-entered search string. Returns empty list for blank query.
     * @param categoryId Optional category ID to filter results by. Applied client-side.
     * @param subcategoryId Optional subcategory ID to further narrow results. Applied client-side.
     */
    suspend fun searchActiveProducts(
        query: String,
        categoryId: String? = null,
        subcategoryId: String? = null,
    ): Result<List<Product>>

    /**
     * Searches all products regardless of status for the admin role.
     * Uses searchKeywords arrayContains for the primary token, then filters client-side
     * for all additional tokens. Returns products of any status.
     *
     * @param query The raw user-entered search string. Returns empty list for blank query.
     * @param categoryId Optional category ID to filter results by. Applied client-side.
     * @param subcategoryId Optional subcategory ID to further narrow results. Applied client-side.
     */
    suspend fun searchAllProducts(
        query: String,
        categoryId: String? = null,
        subcategoryId: String? = null,
    ): Result<List<Product>>

    // View Counter
    suspend fun incrementViewCount(productId: String): Result<Unit>

    // Stock
    suspend fun decrementStock(productId: String, variantId: String, quantity: Int): Result<Unit>

    // Seller Document
    suspend fun addProductToSellerDocument(sellerId: String, productId: String): Result<Unit>
}
