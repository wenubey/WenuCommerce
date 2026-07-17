package com.wenubey.wenucommerce.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Centralised creation of the three notification channels (NOTF-06 / D-03).
 *
 * Created ONCE in [com.wenubey.wenucommerce.WenuCommerce.onCreate] before
 * `startKoin`, replacing the scattered per-service channel creation that used
 * to live in [MessagingService.onCreate] and `showNotification`.
 *
 *  - **Order Updates** (HIGH)  ← order_status + new_order + new_review
 *  - **Account**       (DEFAULT) ← device_login / security alerts
 *  - **Promotions**    (LOW)     ← reserved, empty this phase
 *
 * No-op below API 26 (channels don't exist pre-O).
 */
object NotificationChannels {

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        val orderUpdates = NotificationChannel(
            ORDER_UPDATES_CHANNEL_ID,
            "Order Updates",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Order status changes, new orders, and new product reviews"
        }

        val account = NotificationChannel(
            ACCOUNT_CHANNEL_ID,
            "Account",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Device login and security alerts"
        }

        val promotions = NotificationChannel(
            PROMOTIONS_CHANNEL_ID,
            "Promotions",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Promotional offers and discounts"
        }

        nm.createNotificationChannels(listOf(orderUpdates, account, promotions))
    }
}
