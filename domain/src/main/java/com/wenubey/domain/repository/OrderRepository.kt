package com.wenubey.domain.repository

import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import kotlinx.coroutines.flow.Flow

/**
 * Phase 6 contract — Room-first observe + Firestore sync + callable wrappers.
 * Consumed by Plans 06-02 (customer UI), 06-03 (seller UI), 06-04 (FCM trigger).
 */
interface OrderRepository {

    /** Customer view of all their parent orders, newest first. */
    fun observeCustomerOrders(userId: String): Flow<List<Order>>

    /**
     * Customer order detail — emits the parent + all its sub-orders as a pair.
     * Null when the parent order isn't in Room (yet); sub-orders may be empty
     * for legacy Phase 4 single-seller docs.
     */
    fun observeOrderWithSubOrders(orderId: String): Flow<Pair<Order, List<SellerOrder>>?>

    /** Seller view of all their sub-orders, newest first. */
    fun observeSellerOrders(sellerId: String): Flow<List<SellerOrder>>

    /** Single sub-order observer for the seller detail screen. */
    fun observeSellerOrderById(id: String): Flow<SellerOrder?>

    /**
     * Forward-only status update. Pre-validated client-side via [OrderStatus.allowedNext]
     * (defense-in-depth); the Firestore rule is the authority. Optimistic Room
     * write, then Firestore mirror, then rollback on Firestore failure.
     */
    suspend fun updateSellerOrderStatus(
        id: String,
        next: OrderStatus,
        trackingNumber: String? = null,
        note: String? = null
    ): Result<Unit>

    /**
     * Seller-initiated cancellation. Routes through the `cancelSellerOrder`
     * callable (atomic Stripe partial refund + status flip + statusHistory append).
     */
    suspend fun cancelSellerOrder(id: String): Result<Unit>

    /** One-shot Firestore -> Room sync for customer screen open. */
    suspend fun syncCustomerOrders(userId: String): Result<Unit>

    /** One-shot Firestore -> Room sync for seller orders list. */
    suspend fun syncSellerOrders(sellerId: String): Result<Unit>
}
