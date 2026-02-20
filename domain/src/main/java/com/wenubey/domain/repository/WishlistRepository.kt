package com.wenubey.domain.repository

import com.wenubey.domain.model.WishlistItem
import com.wenubey.domain.model.product.Product
import kotlinx.coroutines.flow.Flow

interface WishlistRepository {
    fun observeWishlistItems(userId: String): Flow<List<WishlistItem>>
    fun isWishlisted(userId: String, productId: String): Flow<Boolean>
    suspend fun toggleWishlist(userId: String?, product: Product)
    suspend fun removeFromWishlist(userId: String, productId: String)
    suspend fun syncAnonymousOnLogin(userId: String)
}
