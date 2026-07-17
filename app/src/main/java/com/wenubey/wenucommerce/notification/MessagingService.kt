package com.wenubey.wenucommerce.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wenubey.data.worker.FcmTokenWorker
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

    private val context: Context by inject()
    private val syncBus: SyncBus by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Phase 8 (08-03 / NOTF-06): channels are created centrally in
    // WenuCommerce.onCreate via NotificationChannels.createAll — no per-service
    // channel creation here anymore.

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Phase 8 (08-03 / NOTF-07 / D-05): enqueue a unique WorkManager job that
        // survives process death and retries. The worker fetches the current
        // token from FirebaseMessaging and awaits the suspend Firestore write.
        FcmTokenWorker.enqueue(this)
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
            showNewOrderNotification(message)
            return
        }

        // new_review payload (08-03 / NOTF-04) — seller-side review alert from
        // onNewReview. Optional SyncBus emit lets a reviews screen refresh.
        if (data[FCM_DATA_KEY_TYPE] == FCM_TYPE_NEW_REVIEW) {
            serviceScope.launch {
                emitSyncIfNewReview(syncBus, data)
            }
            showNewReviewNotification(message)
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

        val notification = NotificationCompat.Builder(this, ORDER_UPDATES_CHANNEL_ID)
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

    private fun showNewOrderNotification(message: RemoteMessage) {
        val data = message.data
        val sellerOrderId = data[FCM_DATA_KEY_SELLER_ORDER_ID]
        if (sellerOrderId.isNullOrBlank()) {
            Timber.w("new_order FCM missing sellerOrderId — skip notification post")
            return
        }
        val title = message.notification?.title ?: "New order"
        val body = message.notification?.body ?: "You have a new order to fulfill."

        val launchIntent = buildNewOrderNotificationIntent(this, data) ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            sellerOrderId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, ORDER_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(sellerOrderId.hashCode(), notification)
    }

    private fun showNewReviewNotification(message: RemoteMessage) {
        val data = message.data
        val productId = data[FCM_DATA_KEY_PRODUCT_ID]
        if (productId.isNullOrBlank()) {
            Timber.w("new_review FCM missing productId — skip notification post")
            return
        }
        val title = message.notification?.title ?: "New review"
        val body = message.notification?.body ?: "One of your products received a new review."

        val launchIntent = buildNewReviewNotificationIntent(this, data) ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            productId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, ORDER_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(productId.hashCode(), notification)
    }

    private fun showNotification(title: String, body: String) {
        // Phase 8 (08-03 / D-03): device_login → Account channel. The channel is
        // created centrally (NotificationChannels.createAll); no inline creation.
        val channelId = ACCOUNT_CHANNEL_ID
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Timber.d("Notification Manager: ${notificationManager.activeNotifications}")

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
         * Builds the launch intent for a new_order (seller-side) FCM. Routes
         * the tap to the seller Orders tab. Returns null when the payload is
         * not actionable (wrong type / missing sellerOrderId).
         */
        internal fun buildNewOrderNotificationIntent(
            context: Context,
            data: Map<String, String>,
        ): Intent? {
            if (data[FCM_DATA_KEY_TYPE] != FCM_TYPE_NEW_ORDER) return null
            val sellerOrderId = data[FCM_DATA_KEY_SELLER_ORDER_ID]
            if (sellerOrderId.isNullOrBlank()) return null

            val launch = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?: return null
            return launch.apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_NAV_TARGET, NAV_TARGET_SELLER_ORDERS)
                putExtra(EXTRA_SELLER_ORDER_ID, sellerOrderId)
            }
        }

        /**
         * Builds the launch intent for a new_review (seller-side) FCM. Routes
         * the tap to the seller's product-reviews screen
         * (SellerProductReviews). Returns null when the payload is not
         * actionable (wrong type / missing productId) so the crafted-payload
         * DoS path (T-08-09) is dropped rather than deep-linked.
         */
        internal fun buildNewReviewNotificationIntent(
            context: Context,
            data: Map<String, String>,
        ): Intent? {
            if (data[FCM_DATA_KEY_TYPE] != FCM_TYPE_NEW_REVIEW) return null
            val productId = data[FCM_DATA_KEY_PRODUCT_ID]
            if (productId.isNullOrBlank()) return null
            val productTitle = data[FCM_DATA_KEY_PRODUCT_TITLE].orEmpty()

            val launch = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?: return null
            return launch.apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_NAV_TARGET, NAV_TARGET_NEW_REVIEW)
                putExtra(EXTRA_PRODUCT_ID, productId)
                putExtra(EXTRA_PRODUCT_TITLE, productTitle)
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

        /**
         * Emits [SyncEvent.NewReview] when the payload is a new_review FCM
         * with a non-blank productId. Optional refresh trigger for a seller's
         * product-reviews screen; mirrors [emitSyncIfNewOrder]. Returns false
         * (no emit) for the wrong type or a blank productId (T-08-09).
         */
        internal suspend fun emitSyncIfNewReview(
            syncBus: SyncBus,
            data: Map<String, String>,
        ): Boolean {
            if (data[FCM_DATA_KEY_TYPE] != FCM_TYPE_NEW_REVIEW) return false
            val productId = data[FCM_DATA_KEY_PRODUCT_ID]
            if (productId.isNullOrBlank()) return false
            syncBus.emit(SyncEvent.NewReview(productId = productId))
            return true
        }
    }
}
