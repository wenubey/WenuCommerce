package com.wenubey.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.wenubey.data.local.dao.OrderDao
import com.wenubey.data.local.dao.SellerOrderDao
import com.wenubey.data.local.entity.OrderEntity
import com.wenubey.data.local.entity.SellerOrderEntity
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.local.mapper.toEntity
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.domain.model.order.StatusEntry
import com.wenubey.domain.model.order.allowedNext
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant

/**
 * Phase 6 repository:
 * - Reads = Room Flow (single source of truth, offline-first).
 * - Writes:
 *   - `updateSellerOrderStatus`: optimistic Room update, then Firestore mirror,
 *     rollback Room on Firestore failure. Pre-validated against allowedNext().
 *   - `cancelSellerOrder`: invokes the `cancelSellerOrder` Cloud Function callable;
 *     server performs atomic Stripe refund + Firestore write.
 * - Sync = one-shot Firestore read into Room (open-time + FCM-triggered).
 */
class OrderRepositoryImpl(
    private val orderDao: OrderDao,
    private val sellerOrderDao: SellerOrderDao,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val dispatcherProvider: DispatcherProvider,
) : OrderRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val ordersCollection get() = firestore.collection("orders")
    private val sellerOrdersCollection get() = firestore.collection("sellerOrders")

    // ── Observe ───────────────────────────────────────────────────────

    override fun observeCustomerOrders(userId: String): Flow<List<Order>> =
        orderDao.observeOrdersByUser(userId).map { list -> list.map { it.toDomain() } }

    override fun observeOrderWithSubOrders(
        orderId: String
    ): Flow<Pair<Order, List<SellerOrder>>?> =
        orderDao.observeOrderById(orderId)
            .combine(sellerOrderDao.observeByParent(orderId)) { parent, subs ->
                if (parent == null) null
                else parent.toDomain() to subs.map { it.toDomain() }
            }

    override fun observeSellerOrders(sellerId: String): Flow<List<SellerOrder>> =
        sellerOrderDao.observeBySeller(sellerId).map { list -> list.map { it.toDomain() } }

    override fun observeSellerOrderById(id: String): Flow<SellerOrder?> =
        sellerOrderDao.observeById(id).map { it?.toDomain() }

    // ── Writes ────────────────────────────────────────────────────────

    override suspend fun updateSellerOrderStatus(
        id: String,
        next: OrderStatus,
        trackingNumber: String?,
        note: String?
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        runCatching {
            val current = sellerOrderDao.getById(id)
                ?: error("SellerOrder $id not found in Room")
            val currentStatus = runCatching { OrderStatus.valueOf(current.status) }
                .getOrElse { OrderStatus.PENDING }
            if (next !in currentStatus.allowedNext()) {
                throw IllegalStateException(
                    "Forbidden transition: ${currentStatus.name} -> ${next.name}"
                )
            }

            val now = Instant.now().toString()
            val previousHistory = runCatching {
                json.decodeFromString<List<StatusEntry>>(current.statusHistoryJson)
            }.getOrElse { emptyList() }
            val newEntry = StatusEntry(
                status = next,
                timestamp = now,
                note = note,
                trackingNumber = trackingNumber
            )
            val newHistoryJson = json.encodeToString(previousHistory + newEntry)
            val resolvedTracking = trackingNumber ?: current.trackingNumber

            // Optimistic Room write
            sellerOrderDao.updateStatus(
                id = id,
                status = next.name,
                historyJson = newHistoryJson,
                tracking = resolvedTracking,
                now = now
            )

            // Firestore mirror (rules enforce forward-only + append-only)
            try {
                sellerOrdersCollection.document(id)
                    .update(
                        buildMap<String, Any?> {
                            put("status", next.name)
                            put(
                                "statusHistory",
                                (previousHistory + newEntry).map {
                                    mapOf(
                                        "status" to it.status.name,
                                        "timestamp" to it.timestamp,
                                        "note" to it.note,
                                        "trackingNumber" to it.trackingNumber
                                    )
                                }
                            )
                            put("trackingNumber", resolvedTracking)
                            put("updatedAt", FieldValue.serverTimestamp())
                        }
                    )
                    .await()
            } catch (e: Exception) {
                // Rollback Room on Firestore failure
                Timber.e(e, "OrderRepository: Firestore update failed, rolling back Room")
                sellerOrderDao.upsert(current)
                throw e
            }
            Unit
        }.onFailure { e ->
            Timber.e(e, "OrderRepository: updateSellerOrderStatus failed for $id")
        }
    }

    override suspend fun cancelSellerOrder(id: String): Result<Unit> =
        withContext(dispatcherProvider.io()) {
            runCatching {
                functions.getHttpsCallable("cancelSellerOrder")
                    .call(mapOf("sellerOrderId" to id))
                    .await()
                Unit
            }.recoverCatching { e ->
                val message = if (e is FirebaseFunctionsException) {
                    when (e.code) {
                        FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                            "Only the seller can cancel this order"
                        FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                            e.message ?: "Cannot cancel post-shipping"
                        else -> e.message ?: "Cancellation failed"
                    }
                } else {
                    e.message ?: "Cancellation failed"
                }
                Timber.e(e, "OrderRepository: cancelSellerOrder failed for $id")
                throw IllegalStateException(message, e)
            }
        }

    // ── Sync ──────────────────────────────────────────────────────────

    override suspend fun syncCustomerOrders(userId: String): Result<Unit> =
        withContext(dispatcherProvider.io()) {
            runCatching {
                val parents = ordersCollection.whereEqualTo("userId", userId).get().await()
                for (parentDoc in parents.documents) {
                    val parentEntity = parentDocToEntity(parentDoc.id, parentDoc.data)
                    orderDao.upsert(parentEntity)
                    // Filter by userId too: the tightened /sellerOrders read rule
                    // requires the query to constrain userId (rules are not
                    // filters), and every sub-order denormalises the customer's
                    // userId. Two equality filters need no composite index.
                    val subs = sellerOrdersCollection
                        .whereEqualTo("parentOrderId", parentDoc.id)
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    val subEntities = subs.documents.map { subDocToEntity(it.id, it.data) }
                    if (subEntities.isNotEmpty()) sellerOrderDao.upsertAll(subEntities)
                }
                Unit
            }.onFailure { Timber.e(it, "OrderRepository: syncCustomerOrders failed") }
        }

    override suspend fun syncSellerOrders(sellerId: String): Result<Unit> =
        withContext(dispatcherProvider.io()) {
            runCatching {
                val snap = sellerOrdersCollection
                    .whereEqualTo("sellerId", sellerId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()
                val entities = snap.documents.map { subDocToEntity(it.id, it.data) }
                if (entities.isNotEmpty()) sellerOrderDao.upsertAll(entities)
                Unit
            }.onFailure { Timber.e(it, "OrderRepository: syncSellerOrders failed") }
        }

    // ── Doc -> Entity helpers ─────────────────────────────────────────

    /**
     * Firestore stores createdAt/updatedAt and statusHistory timestamps as
     * [com.google.firebase.Timestamp]. A raw `.toString()` yields
     * "Timestamp(seconds=…, nanoseconds=…)", which leaked verbatim into the
     * order list/detail UI. Normalise to ISO-8601 so dates render and sort
     * correctly. Values already stored as a String (e.g. the client's
     * optimistic PENDING order) pass through unchanged.
     */
    private fun tsToIso(value: Any?): String = when (value) {
        is Timestamp -> value.toDate().toInstant().toString()
        is String -> value
        else -> ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun parentDocToEntity(id: String, data: Map<String, Any?>?): OrderEntity {
        val d = data ?: emptyMap()
        return OrderEntity(
            id = id,
            userId = (d["userId"] as? String).orEmpty(),
            status = (d["status"] as? String) ?: "PENDING",
            subtotal = (d["subtotal"] as? Number)?.toDouble() ?: 0.0,
            shippingTotal = (d["shippingTotal"] as? Number)?.toDouble() ?: 0.0,
            totalAmount = (d["totalAmount"] as? Number)?.toDouble() ?: 0.0,
            currency = (d["currency"] as? String) ?: "USD",
            stripePaymentIntentId = (d["stripePaymentIntentId"] as? String).orEmpty(),
            shippingAddressJson = (d["shippingAddress"] as? Map<*, *>)?.let {
                runCatching { json.encodeToString(it.toString()) }.getOrDefault("")
            } ?: "",
            itemsJson = (d["items"] as? List<*>)?.let { items ->
                runCatching {
                    val mapped = items.filterIsInstance<Map<String, Any?>>().map { m ->
                        OrderItem(
                            productId = (m["productId"] as? String).orEmpty(),
                            productTitle = (m["productTitle"] as? String).orEmpty(),
                            quantity = (m["quantity"] as? Number)?.toInt() ?: 1,
                            snapshotPrice = (m["snapshotPrice"] as? Number)?.toDouble() ?: 0.0,
                            lineTotal = (m["lineTotal"] as? Number)?.toDouble() ?: 0.0,
                        )
                    }
                    json.encodeToString(mapped)
                }.getOrDefault("[]")
            } ?: "[]",
            discountAmount = (d["discountAmount"] as? Number)?.toDouble() ?: 0.0,
            discountCode = (d["discountCode"] as? String).orEmpty(),
            createdAt = tsToIso(d["createdAt"]),
            updatedAt = tsToIso(d["updatedAt"]),
            sellerOrderIdsJson = (d["sellerOrderIds"] as? List<*>)?.let { ids ->
                runCatching {
                    json.encodeToString(ids.filterIsInstance<String>())
                }.getOrDefault("[]")
            } ?: "[]",
            aggregateStatus = (d["aggregateStatus"] as? String) ?: "PENDING"
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun subDocToEntity(id: String, data: Map<String, Any?>?): SellerOrderEntity {
        val d = data ?: emptyMap()
        val itemsJsonStr = (d["items"] as? List<*>)?.let { items ->
            runCatching {
                val mapped = items.filterIsInstance<Map<String, Any?>>().map { m ->
                    OrderItem(
                        productId = (m["productId"] as? String).orEmpty(),
                        productTitle = (m["productTitle"] as? String).orEmpty(),
                        quantity = (m["quantity"] as? Number)?.toInt() ?: 1,
                        snapshotPrice = (m["snapshotPrice"] as? Number)?.toDouble() ?: 0.0,
                        lineTotal = (m["lineTotal"] as? Number)?.toDouble() ?: 0.0,
                    )
                }
                json.encodeToString(mapped)
            }.getOrDefault("[]")
        } ?: "[]"
        val historyJsonStr = (d["statusHistory"] as? List<*>)?.let { entries ->
            runCatching {
                val mapped = entries.filterIsInstance<Map<String, Any?>>().map { m ->
                    StatusEntry(
                        status = runCatching {
                            OrderStatus.valueOf((m["status"] as? String) ?: "PENDING")
                        }.getOrElse { OrderStatus.PENDING },
                        timestamp = tsToIso(m["timestamp"]),
                        note = m["note"] as? String,
                        trackingNumber = m["trackingNumber"] as? String,
                    )
                }
                json.encodeToString(mapped)
            }.getOrDefault("[]")
        } ?: "[]"
        return SellerOrderEntity(
            id = id,
            parentOrderId = (d["parentOrderId"] as? String).orEmpty(),
            userId = (d["userId"] as? String).orEmpty(),
            sellerId = (d["sellerId"] as? String).orEmpty(),
            sellerName = (d["sellerName"] as? String).orEmpty(),
            sellerLogoUrl = (d["sellerLogoUrl"] as? String).orEmpty(),
            subtotal = (d["subtotal"] as? Number)?.toDouble() ?: 0.0,
            shippingShare = (d["shippingShare"] as? Number)?.toDouble() ?: 0.0,
            discountShare = (d["discountShare"] as? Number)?.toDouble() ?: 0.0,
            status = (d["status"] as? String) ?: "PENDING",
            trackingNumber = d["trackingNumber"] as? String,
            refundId = d["refundId"] as? String,
            refundedAmount = (d["refundedAmount"] as? Number)?.toDouble(),
            itemsJson = itemsJsonStr,
            statusHistoryJson = historyJsonStr,
            createdAt = tsToIso(d["createdAt"]),
            updatedAt = tsToIso(d["updatedAt"])
        )
    }
}
