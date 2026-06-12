package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.WishlistItem
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.repository.WishlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeWishlistRepository : WishlistRepository {

    private val items = MutableStateFlow<Map<String, List<WishlistItem>>>(emptyMap())

    val syncAnonymousOnLoginCalls = mutableListOf<String>()
    val toggleCalls = mutableListOf<Pair<String?, Product>>()
    val removeCalls = mutableListOf<Pair<String, String>>()
    var syncAnonymousThrows: Throwable? = null

    override fun observeWishlistItems(userId: String): Flow<List<WishlistItem>> =
        items.asStateFlow().map { it[userId].orEmpty() }

    override fun isWishlisted(userId: String, productId: String): Flow<Boolean> =
        items.asStateFlow().map { snapshot ->
            snapshot[userId]?.any { it.productId == productId } == true
        }

    override suspend fun toggleWishlist(userId: String?, product: Product) {
        toggleCalls.add(userId to product)
    }

    override suspend fun removeFromWishlist(userId: String, productId: String) {
        removeCalls.add(userId to productId)
    }

    override suspend fun syncAnonymousOnLogin(userId: String) {
        syncAnonymousOnLoginCalls.add(userId)
        syncAnonymousThrows?.let { throw it }
    }
}
