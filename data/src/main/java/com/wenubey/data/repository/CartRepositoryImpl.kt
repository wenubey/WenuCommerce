package com.wenubey.data.repository

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.local.SyncManager
import com.wenubey.data.local.dao.CartItemDao
import com.wenubey.data.local.dao.PendingOperationDao
import com.wenubey.data.local.entity.CartItemEntity
import com.wenubey.data.local.entity.OperationType
import com.wenubey.data.local.entity.PendingOperationEntity
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.worker.SyncWorker
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.repository.CartRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant

@Serializable
data class AddToCartPayload(
    val productId: String,
    val quantity: Int,
    val snapshotPrice: Double
)

@Serializable
data class UpdateCartQuantityPayload(
    val productId: String,
    val quantity: Int
)

class CartRepositoryImpl(
    private val cartItemDao: CartItemDao,
    private val pendingOperationDao: PendingOperationDao,
    private val syncManager: SyncManager,
    private val firestore: FirebaseFirestore,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
) : CartRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeCartItems(userId: String): Flow<List<CartItem>> =
        cartItemDao.observeCartItems(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeUniqueProductCount(userId: String): Flow<Int> =
        cartItemDao.observeUniqueProductCount(userId)

    override suspend fun getCartItem(userId: String, productId: String): CartItem? =
        withContext(dispatcherProvider.io()) {
            cartItemDao.getCartItem(userId, productId)?.toDomain()
        }

    override suspend fun restoreCartItem(userId: String, cartItem: CartItem) =
        withContext(dispatcherProvider.io()) {
            val now = Instant.now().toString()
            val entity = CartItemEntity(
                userId = userId,
                productId = cartItem.productId,
                productTitle = cartItem.productTitle,
                productImageUrl = cartItem.productImageUrl,
                quantity = cartItem.quantity,
                snapshotPrice = cartItem.snapshotPrice,
                availableStock = cartItem.availableStock,
                isProductDeleted = cartItem.isProductDeleted,
                addedAt = cartItem.addedAt.ifEmpty { now },
                updatedAt = now
            )
            cartItemDao.upsert(entity)
            queueCartOperation(
                entityId = userId,
                operationType = OperationType.ADD_TO_CART,
                payloadJson = json.encodeToString(
                    AddToCartPayload(
                        productId = cartItem.productId,
                        quantity = cartItem.quantity,
                        snapshotPrice = cartItem.snapshotPrice
                    )
                )
            )
            SyncWorker.enqueue(context)
            syncManager.emitOfflineWriteQueued()
        }

    override suspend fun addToCart(userId: String, product: Product, quantity: Int) =
        withContext(dispatcherProvider.io()) {
            val now = Instant.now().toString()
            val existing = cartItemDao.getCartItem(userId, product.id)

            if (existing != null) {
                val newQuantity = existing.quantity + quantity
                cartItemDao.updateQuantity(userId, product.id, newQuantity, now)
                queueCartOperation(
                    entityId = userId,
                    operationType = OperationType.UPDATE_CART_QUANTITY,
                    payloadJson = json.encodeToString(
                        UpdateCartQuantityPayload(
                            productId = product.id,
                            quantity = newQuantity
                        )
                    )
                )
            } else {
                val entity = CartItemEntity(
                    userId = userId,
                    productId = product.id,
                    productTitle = product.title,
                    productImageUrl = product.images.firstOrNull()?.downloadUrl ?: "",
                    quantity = quantity,
                    snapshotPrice = product.basePrice,
                    availableStock = product.totalStockQuantity,
                    isProductDeleted = false,
                    addedAt = now,
                    updatedAt = now
                )
                cartItemDao.upsert(entity)
                queueCartOperation(
                    entityId = userId,
                    operationType = OperationType.ADD_TO_CART,
                    payloadJson = json.encodeToString(
                        AddToCartPayload(
                            productId = product.id,
                            quantity = quantity,
                            snapshotPrice = product.basePrice
                        )
                    )
                )
            }

            SyncWorker.enqueue(context)
            syncManager.emitOfflineWriteQueued()
            Unit
        }

    override suspend fun updateQuantity(
        userId: String,
        productId: String,
        newQuantity: Int
    ) = withContext(dispatcherProvider.io()) {
        if (newQuantity <= 0) {
            removeFromCart(userId, productId)
            return@withContext
        }

        val now = Instant.now().toString()
        cartItemDao.updateQuantity(userId, productId, newQuantity, now)
        queueCartOperation(
            entityId = userId,
            operationType = OperationType.UPDATE_CART_QUANTITY,
            payloadJson = json.encodeToString(
                UpdateCartQuantityPayload(
                    productId = productId,
                    quantity = newQuantity
                )
            )
        )
        SyncWorker.enqueue(context)
    }

    override suspend fun removeFromCart(userId: String, productId: String) =
        withContext(dispatcherProvider.io()) {
            cartItemDao.deleteItem(userId, productId)
            queueCartOperation(
                entityId = userId,
                operationType = OperationType.REMOVE_FROM_CART,
                payloadJson = productId
            )
            SyncWorker.enqueue(context)
        }

    override suspend fun clearCart(userId: String) =
        withContext(dispatcherProvider.io()) {
            // No queue — used on checkout success (Phase 4)
            cartItemDao.clearCart(userId)
        }

    override suspend fun syncAddToCart(
        userId: String,
        productId: String,
        quantity: Int,
        snapshotPrice: Double
    ) = withContext(dispatcherProvider.io()) {
        val now = Instant.now().toString()
        val data = mapOf(
            "productId" to productId,
            "quantity" to quantity,
            "snapshotPrice" to snapshotPrice,
            "updatedAt" to now
        )
        firestore
            .collection("users").document(userId)
            .collection("cart").document(productId)
            .set(data)
            .await()
        Timber.d("CartRepository: synced ADD_TO_CART for $productId")
    }

    override suspend fun syncUpdateQuantity(
        userId: String,
        productId: String,
        quantity: Int
    ) = withContext(dispatcherProvider.io()) {
        val now = Instant.now().toString()
        val data = mapOf(
            "quantity" to quantity,
            "updatedAt" to now
        )
        firestore
            .collection("users").document(userId)
            .collection("cart").document(productId)
            .update(data)
            .await()
        Timber.d("CartRepository: synced UPDATE_CART_QUANTITY for $productId")
    }

    override suspend fun syncRemoveFromCart(userId: String, productId: String) =
        withContext(dispatcherProvider.io()) {
            firestore
                .collection("users").document(userId)
                .collection("cart").document(productId)
                .delete()
                .await()
            Timber.d("CartRepository: synced REMOVE_FROM_CART for $productId")
        }

    private suspend fun queueCartOperation(
        entityId: String,
        operationType: OperationType,
        payloadJson: String
    ) {
        pendingOperationDao.insert(
            PendingOperationEntity(
                operationType = operationType.name,
                entityId = entityId,
                payloadJson = payloadJson,
                createdAt = Instant.now().toString()
            )
        )
    }
}
