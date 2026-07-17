package com.wenubey.wenucommerce.notification.permission

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * One-time contextual rationale dialog shown before the OS POST_NOTIFICATIONS system dialog
 * on Android 13+ (API 33 / TIRAMISU). Per NOTF-05 / D-02 / T-08-14:
 * - Shown at most once per install (gated by NotificationPreferences.isNotificationPermissionRequested).
 * - "Enable" → triggers the system permission launcher, then sets the gate.
 * - "Not now" → sets the gate without requesting permission; Profile affordance still available.
 *
 * Call site: CustomerTabScreen + SellerTabScreen (post-login authenticated home).
 */
@Composable
fun NotificationPermissionRationaleDialog(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(text = "Stay in the loop")
        },
        text = {
            Text(
                text = "Get notified when your orders ship, when customers place new orders, and when reviews come in — right on your device.",
            )
        },
        confirmButton = {
            Button(onClick = onEnable) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
    )
}
