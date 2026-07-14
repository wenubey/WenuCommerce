package com.wenubey.wenucommerce.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.wenucommerce.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

// TODO add Navigation Helper Functionality going to the Settings Screen when a notification is clicked
class MessagingService: FirebaseMessagingService() {

    private val firestoreRepository: FirestoreRepository by inject()
    private val context: Context by inject()
    private val syncBus: SyncBus by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureOrderStatusChannel(this)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        firestoreRepository.updateFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // Phase 6 Plan 04: route order_status payloads to SyncBus + system notification.
        if (data[FCM_DATA_KEY_TYPE] == FCM_TYPE_ORDER_STATUS) {
            // Emit on SyncBus FIRST so ViewModels can refresh Room even if the
            // user never taps the notification.
            serviceScope.launch {
                emitSyncIfOrderStatus(syncBus, data)
            }
            showOrderStatusNotification(message)
            return
        }
        // new_order payload — seller-side sync trigger from onNewSellerOrder.
        if (data[FCM_DATA_KEY_TYPE] == FCM_TYPE_NEW_ORDER) {
            serviceScope.launch {
                emitSyncIfNewOrder(syncBus, data)
            }
            showOrderStatusNotification(message)
            return
        }

        // Legacy device-login path (unchanged).
        message.notification?.let {
            val title = it.title ?: "Security Alert"
            val body = it.body ?: "Login detected from a new device"
            showNotification(title, body)
        }
    }

    private fun showOrderStatusNotification(message: RemoteMessage) {
        val data = message.data
        val orderId = data[FCM_DATA_KEY_ORDER_ID]
        if (orderId.isNullOrBlank()) {
            Timber.w("order_status FCM missing orderId — skip notification post")
            return
        }
        val newStatus = data[FCM_DATA_KEY_NEW_STATUS] ?: ""
        val title = message.notification?.title ?: "Order update"
        val body = message.notification?.body
            ?: if (newStatus.isNotBlank()) "Your order status changed to $newStatus"
            else "Your order has been updated"

        val launchIntent = buildOrderStatusNotificationIntent(this, data) ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            orderId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, ORDER_STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // orderId.hashCode() so subsequent updates for the same order replace
        // rather than stack (deliberate; aggregation polish is Phase 8).
        NotificationManagerCompat.from(this).notify(orderId.hashCode(), notification)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "device_login_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Timber.d("Notification Manager: ${notificationManager.activeNotifications}")
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Device Login Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for device login events."
            }

            notificationManager.createNotificationChannel(channel)
        }

        val packageManager = context.packageManager

        val launchIntent = packageManager.getLaunchIntentForPackage(context.packageName)
            ?: throw IllegalStateException("Could not find launch intent for package: ${context.packageName}")

        val notificationIntent = launchIntent.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NAVIGATE_TO_SETTINGS, true)
            putExtra(NOTIFICATION_CLICK_ACTION, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }


    companion object {
        const val NAVIGATE_TO_SETTINGS = "navigate_to_settings"
        const val NOTIFICATION_CLICK_ACTION = "notification_click"

        /**
         * Create the order-status notification channel (idempotent). Mirrors
         * the device_login_channel pattern. Safe on pre-O (no-op).
         */
        internal fun ensureOrderStatusChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            val channel = NotificationChannel(
                ORDER_STATUS_CHANNEL_ID,
                ORDER_STATUS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = ORDER_STATUS_CHANNEL_DESCRIPTION
            }
            nm.createNotificationChannel(channel)
        }

        /**
         * Builds the launch intent that the PendingIntent wraps when an
         * order_status FCM is posted as a system notification. Returns null
         * when the payload is not actionable (wrong type / missing orderId)
         * so callers can short-circuit posting.
         *
         * Pure function for unit testability — takes Context only to resolve
         * the package launch intent.
         */
        internal fun buildOrderStatusNotificationIntent(
            context: Context,
            data: Map<String, String>,
        ): Intent? {
            if (data[FCM_DATA_KEY_TYPE] != FCM_TYPE_ORDER_STATUS) return null
            val orderId = data[FCM_DATA_KEY_ORDER_ID]
            if (orderId.isNullOrBlank()) return null
            val sellerOrderId = data[FCM_DATA_KEY_SELLER_ORDER_ID].orEmpty()

            val launch = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?: return null
            return launch.apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_NAV_TARGET, NAV_TARGET_ORDER_DETAIL)
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_SELLER_ORDER_ID, sellerOrderId)
            }
        }

        /**
         * Emits [SyncEvent.OrderStatusChanged] on the given bus when the
         * payload is an order_status FCM with a non-blank orderId. Returns
         * true when an emit happened. Pure suspend helper for unit testing.
         */
        internal suspend fun emitSyncIfOrderStatus(
            syncBus: SyncBus,
            data: Map<String, String>,
        ): Boolean {
            if (data[FCM_DATA_KEY_TYPE] != FCM_TYPE_ORDER_STATUS) return false
            val orderId = data[FCM_DATA_KEY_ORDER_ID]
            if (orderId.isNullOrBlank()) return false
            val sellerOrderId = data[FCM_DATA_KEY_SELLER_ORDER_ID].orEmpty()
            syncBus.emit(
                SyncEvent.OrderStatusChanged(
                    orderId = orderId,
                    sellerOrderId = sellerOrderId,
                ),
            )
            return true
        }

        /**
         * Emits [SyncEvent.NewOrder] when the payload is a new_order FCM
         * with a non-blank sellerOrderId. Consumed by SellerOrdersViewModel
         * to trigger a fresh syncSellerOrders() call so the list reflects
         * the newly-arrived customer order without pull-to-refresh.
         */
        internal suspend fun emitSyncIfNewOrder(
            syncBus: SyncBus,
            data: Map<String, String>,
        ): Boolean {
            if (data[FCM_DATA_KEY_TYPE] != FCM_TYPE_NEW_ORDER) return false
            val sellerOrderId = data[FCM_DATA_KEY_SELLER_ORDER_ID]
            if (sellerOrderId.isNullOrBlank()) return false
            syncBus.emit(SyncEvent.NewOrder(sellerOrderId = sellerOrderId))
            return true
        }
    }
}
