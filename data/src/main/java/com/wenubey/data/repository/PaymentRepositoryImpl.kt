package com.wenubey.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.wenubey.data.local.dao.OrderDao
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.local.mapper.toEntity
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.ShippingAddress
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.PaymentIntentResult
import com.wenubey.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant

class PaymentRepositoryImpl(
    private val orderDao: OrderDao,
    private val firestore: FirebaseFirestore,
    private val dispatcherProvider: DispatcherProvider,
) : PaymentRepository {

    override suspend fun createPaymentIntent(
        userId: String,
        cartItems: List<CartItem>,
        shippingAddress: ShippingAddress,
    ): Result<PaymentIntentResult> = withContext(dispatcherProvider.io()) {
        runCatching {
            val data = mapOf(
                "userId" to userId,
                "items" to cartItems.map { item ->
                    mapOf(
                        "productId" to item.productId,
                        "quantity" to item.quantity,
                        "snapshotPrice" to item.snapshotPrice
                    )
                },
                "shippingAddress" to shippingAddress.toMap()
            )

            val result = Firebase.functions
                .getHttpsCallable("createPaymentIntent")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val responseData = result.getData() as? Map<String, Any>
                ?: error("Invalid response from createPaymentIntent")

            val clientSecret = responseData["clientSecret"] as? String
                ?: error("Missing clientSecret in response")
            val amountCents = (responseData["amountCents"] as? Number)?.toInt()
                ?: error("Missing amountCents in response")
            val orderId = responseData["orderId"] as? String
                ?: error("Missing orderId in response")

            PaymentIntentResult(
                clientSecret = clientSecret,
                amountCents = amountCents,
                orderId = orderId
            )
        }.onFailure { e ->
            Timber.e(e, "PaymentRepository: createPaymentIntent failed")
        }
    }

    override suspend fun createOrderInRoom(order: Order) =
        withContext(dispatcherProvider.io()) {
            orderDao.upsert(order.toEntity())
        }

    override suspend fun getOrderById(orderId: String): Order? =
        withContext(dispatcherProvider.io()) {
            orderDao.getOrderById(orderId)?.toDomain()
        }

    override fun observeOrderById(orderId: String): Flow<Order?> =
        orderDao.observeOrderById(orderId).map { it?.toDomain() }

    override suspend fun updateOrderStatus(
        orderId: String,
        status: OrderStatus
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        runCatching {
            val now = Instant.now().toString()
            // Update Room first (optimistic local update)
            orderDao.updateOrderStatus(orderId, status.name, now)
            // Then update Firestore
            firestore.collection("orders")
                .document(orderId)
                .update(
                    mapOf(
                        "status" to status.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Unit
        }.onFailure { e ->
            Timber.e(e, "PaymentRepository: updateOrderStatus failed for $orderId")
        }
    }
}
