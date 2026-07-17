package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of Firestore `notifications/{uid}/items/{id}` (schema v10). Every
 * push-dispatching Cloud Function (onOrderStatusChange / onNewSellerOrder /
 * onNewReview — plan 08-02) also writes one of these docs so notification
 * history stays complete regardless of FCM delivery / foreground state / app
 * kill (D-01). The app mirrors them into this table and the history screen
 * reads Room (Room-first observe, synced from Firestore — same pattern as
 * orders/sellerOrders/reviews).
 *
 * Notifications are server-only writes (Firestore rules deny client create /
 * delete); the only client mutation is flipping the [isRead] flag. Indices on
 * userId + createdAt back the per-user, newest-first observe query.
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index("userId"),
        Index("createdAt")
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val orderId: String = "",
    val sellerOrderId: String = "",
    val productId: String = "",
    val productTitle: String = "",
    val isRead: Boolean = false,
    val createdAt: String = "",
)
