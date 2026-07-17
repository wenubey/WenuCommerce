package com.wenubey.domain.model

/**
 * Pure-Kotlin domain model for a notification-history entry (D-01). Mirrors the
 * Firestore `notifications/{uid}/items/{id}` doc written server-side by every
 * push-dispatching Cloud Function (plan 08-02) and cached in the Room
 * `notifications` table. All type-specific deep-link fields default to empty
 * strings so a single shape covers every notification type.
 *
 * No Android / Firebase dependency — the :domain module stays framework-free.
 */
data class Notification(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val orderId: String = "",
    val sellerOrderId: String = "",
    val productId: String = "",
    val productTitle: String = "",
    val isRead: Boolean = false,
    val createdAt: String = "",
)
