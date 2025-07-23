package com.wenubey.wenucommerce.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.wenucommerce.R
import org.koin.android.ext.android.inject
import timber.log.Timber

// TODO add Navigation Helper Functionality going to the Settings Screen when a notification is clicked
class MessagingService: FirebaseMessagingService() {

    private val firestoreRepository: FirestoreRepository by inject()
    private val context: Context by inject()


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        firestoreRepository.updateFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        message.notification?.let {
            val title = it.title ?: "Security Alert"
            val body = it.body ?: "Login detected from a new device"
            showNotification(title, body)
        }
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
    }
}