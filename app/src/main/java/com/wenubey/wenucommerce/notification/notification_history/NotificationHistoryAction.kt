package com.wenubey.wenucommerce.notification.notification_history

import com.wenubey.domain.model.Notification

/**
 * User-intent actions dispatched by the Notification History screen UI.
 *
 * Follows the UDF event-callback pattern used throughout the project.
 */
sealed interface NotificationHistoryAction {
    /** Tapping a notification row — marks it read and fires the deep-link nav effect. */
    data class OnItemClick(val item: Notification) : NotificationHistoryAction

    /** "Mark all read" TopAppBar action. */
    data object OnMarkAllRead : NotificationHistoryAction

    /** Pull-to-refresh gesture. */
    data object OnRefresh : NotificationHistoryAction

    /** Dismiss the error snackbar. */
    data object OnDismissError : NotificationHistoryAction
}
