package com.wenubey.domain.repository

import com.wenubey.domain.model.Notification
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the notification-history feature (NOTF-08, D-01).
 *
 * The implementation is Room-first: [observeNotifications] and
 * [observeUnreadCount] read the local `notifications` cache, kept live by a
 * per-user Firestore listener over `notifications/{uid}/items`. The listener is
 * started/stopped from the auth-state listener ([startListener] on login,
 * [stopListener] on sign-out) so it never runs with a blank uid (RESEARCH
 * §Pitfall 4). [markAsRead] flips the local flag optimistically and mirrors the
 * change to Firestore best-effort.
 */
interface NotificationRepository {
    fun observeNotifications(userId: String): Flow<List<Notification>>
    fun observeUnreadCount(userId: String): Flow<Int>
    suspend fun markAsRead(notificationId: String): Result<Unit>
    fun startListener(userId: String)
    fun stopListener()
}
