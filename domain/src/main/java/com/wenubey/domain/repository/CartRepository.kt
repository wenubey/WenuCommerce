package com.wenubey.domain.repository

import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.product.Product
import kotlinx.coroutines.flow.Flow

interface CartRepository {
    fun observeCartItems(userId: String): Flow<List<CartItem>>
    fun observeUniqueProductCount(userId: String): Flow<Int>
    suspend fun getCartItem(userId: String, productId: String): CartItem?
    suspend fun addToCart(userId: String, product: Product, quantity: Int)
    suspend fun restoreCartItem(userId: String, cartItem: CartItem)
    suspend fun updateQuantity(userId: String, productId: String, newQuantity: Int)
    suspend fun removeFromCart(userId: String, productId: String)
    suspend fun clearCart(userId: String)

    // Sync methods called by SyncWorker — write to Firestore
    suspend fun syncAddToCart(userId: String, productId: String, quantity: Int, snapshotPrice: Double)
    suspend fun syncUpdateQuantity(userId: String, productId: String, quantity: Int)
    suspend fun syncRemoveFromCart(userId: String, productId: String)
}
