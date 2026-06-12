package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.repository.CartRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

class FakeCartRepository : CartRepository {

    private val itemsByUser = MutableStateFlow<Map<String, List<CartItem>>>(emptyMap())
    private val cartItemsFlow = MutableSharedFlow<List<CartItem>>(replay = 1)

    val updateQuantityCalls = mutableListOf<Triple<String, String, Int>>()
    val removeFromCartCalls = mutableListOf<Pair<String, String>>()
    val restoreCartItemCalls = mutableListOf<Pair<String, CartItem>>()
    val addToCartCalls = mutableListOf<Triple<String, Product, Int>>()
    val clearCartCalls = mutableListOf<String>()

    /** Override behavior */
    var updateQuantityThrows: Throwable? = null
    var removeFromCartThrows: Throwable? = null
    var restoreCartItemThrows: Throwable? = null
    var addToCartThrows: Throwable? = null
    var observeCartItemsFlow: Flow<List<CartItem>>? = null

    fun emitCartItems(userId: String, items: List<CartItem>) {
        itemsByUser.value = itemsByUser.value.toMutableMap().apply { put(userId, items) }
        cartItemsFlow.tryEmit(items)
    }

    override fun observeCartItems(userId: String): Flow<List<CartItem>> =
        observeCartItemsFlow ?: itemsByUser.map { it[userId].orEmpty() }

    override fun observeUniqueProductCount(userId: String): Flow<Int> =
        itemsByUser.map { it[userId].orEmpty().distinctBy { i -> i.productId }.size }

    override suspend fun getCartItem(userId: String, productId: String): CartItem? =
        itemsByUser.value[userId]?.firstOrNull { it.productId == productId }

    override suspend fun addToCart(userId: String, product: Product, quantity: Int) {
        addToCartCalls.add(Triple(userId, product, quantity))
        addToCartThrows?.let { throw it }
    }

    override suspend fun restoreCartItem(userId: String, cartItem: CartItem) {
        restoreCartItemCalls.add(userId to cartItem)
        restoreCartItemThrows?.let { throw it }
    }

    override suspend fun updateQuantity(userId: String, productId: String, newQuantity: Int) {
        updateQuantityCalls.add(Triple(userId, productId, newQuantity))
        updateQuantityThrows?.let { throw it }
    }

    override suspend fun removeFromCart(userId: String, productId: String) {
        removeFromCartCalls.add(userId to productId)
        removeFromCartThrows?.let { throw it }
    }

    override suspend fun clearCart(userId: String) {
        clearCartCalls.add(userId)
    }

    override suspend fun syncAddToCart(userId: String, productId: String, quantity: Int, snapshotPrice: Double) {}
    override suspend fun syncUpdateQuantity(userId: String, productId: String, quantity: Int) {}
    override suspend fun syncRemoveFromCart(userId: String, productId: String) {}

    @Suppress("unused")
    private val unused = cartItemsFlow.asSharedFlow()
}
