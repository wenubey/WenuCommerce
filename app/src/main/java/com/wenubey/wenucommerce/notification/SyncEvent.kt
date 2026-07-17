package com.wenubey.wenucommerce.notification

/**
 * Events emitted by [SyncBus] when an inbound FCM payload (or other side
 * channel) signals that local cache is stale and a sync should run.
 *
 * Distinct from [com.wenubey.data.local.SyncEvent] which models sync-outcome
 * UI events (banner / snackbar). This sealed class models *sync triggers*.
 */
sealed class SyncEvent {
    /**
     * Emitted by [MessagingService] when an `order_status` FCM payload is
     * received. Consumed by 06-02 customer ViewModels to trigger
     * `OrderRepository.syncCustomerOrders()` so Room reflects the new status
     * before the user opens the screen.
     */
    data class OrderStatusChanged(
        val orderId: String,
        val sellerOrderId: String,
    ) : SyncEvent()

    /**
     * Emitted by [MessagingService] when a `new_order` FCM payload is
     * received. Consumed by SellerOrdersViewModel to trigger
     * `OrderRepository.syncSellerOrders()` so a freshly-placed customer
     * order appears in the seller's list without a manual pull-to-refresh.
     */
    data class NewOrder(
        val sellerOrderId: String,
    ) : SyncEvent()

    /**
     * Emitted by [MessagingService] when a `new_review` FCM payload is
     * received (08-03 / NOTF-04). An optional refresh trigger so a seller's
     * product-reviews screen can update without pull-to-refresh; kept
     * consistent with [NewOrder]. Not required for the deep-link itself.
     */
    data class NewReview(
        val productId: String,
    ) : SyncEvent()
}
