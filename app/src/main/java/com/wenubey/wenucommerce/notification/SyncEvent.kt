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
}
