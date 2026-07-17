package com.wenubey.wenucommerce.notification

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wenubey.wenucommerce.testing.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 8 Plan 03 (NOTF-06 / D-03) — centralised notification channels.
 *
 * Verifies [NotificationChannels.createAll] registers exactly the three
 * channels (Order Updates HIGH / Account DEFAULT / Promotions LOW) with the
 * ids that 08-02's server channelId + the deep-link router depend on.
 *
 * Robolectric: needs a real [NotificationManager] to read back the channels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class NotificationChannelsTest {

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    private val nm: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun `createAll registers exactly the three expected channels`() {
        NotificationChannels.createAll(context)

        val ids = nm.notificationChannels.map { it.id }.toSet()
        assertEquals(
            setOf(
                ORDER_UPDATES_CHANNEL_ID,
                ACCOUNT_CHANNEL_ID,
                PROMOTIONS_CHANNEL_ID,
            ),
            ids,
        )
    }

    @Test
    fun `Order Updates channel has HIGH importance`() {
        NotificationChannels.createAll(context)

        val channel = nm.getNotificationChannel(ORDER_UPDATES_CHANNEL_ID)
        assertNotNull("order_updates_channel should exist", channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel!!.importance)
    }

    @Test
    fun `Account channel has DEFAULT importance`() {
        NotificationChannels.createAll(context)

        val channel = nm.getNotificationChannel(ACCOUNT_CHANNEL_ID)
        assertNotNull("account_channel should exist", channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel!!.importance)
    }

    @Test
    fun `Promotions channel has LOW importance`() {
        NotificationChannels.createAll(context)

        val channel = nm.getNotificationChannel(PROMOTIONS_CHANNEL_ID)
        assertNotNull("promotions_channel should exist", channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel!!.importance)
    }
}
