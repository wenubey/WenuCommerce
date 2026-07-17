package com.wenubey.wenucommerce.notification.notification_history

import com.wenubey.domain.model.Notification

/**
 * Single immutable UI state for the Notification History screen (NOTF-08, D-01).
 *
 * [notifications] is the full set observed from Room.
 * [hasUnread] is derived from [notifications] and drives the TopAppBar "Mark all read" action
 * and the bottom-nav BadgedBox (plan 08-04).
 */
data class NotificationHistoryState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val hasUnread: Boolean = false,
)
