package com.wenubey.wenucommerce

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.wenubey.wenucommerce.notification.EXTRA_NAV_TARGET
import com.wenubey.wenucommerce.notification.EXTRA_ORDER_ID
import com.wenubey.wenucommerce.notification.EXTRA_PRODUCT_ID
import com.wenubey.wenucommerce.notification.EXTRA_PRODUCT_TITLE
import com.wenubey.wenucommerce.notification.EXTRA_SELLER_ORDER_ID
import com.wenubey.wenucommerce.notification.FCM_DATA_KEY_NEW_STATUS
import com.wenubey.wenucommerce.notification.FCM_DATA_KEY_ORDER_ID
import com.wenubey.wenucommerce.notification.FCM_DATA_KEY_PRODUCT_ID
import com.wenubey.wenucommerce.notification.FCM_DATA_KEY_PRODUCT_TITLE
import com.wenubey.wenucommerce.notification.FCM_DATA_KEY_SELLER_ORDER_ID
import com.wenubey.wenucommerce.notification.FCM_DATA_KEY_TYPE
import com.wenubey.wenucommerce.notification.FCM_TYPE_NEW_ORDER
import com.wenubey.wenucommerce.notification.FCM_TYPE_NEW_REVIEW
import com.wenubey.wenucommerce.notification.FCM_TYPE_ORDER_STATUS
import com.wenubey.wenucommerce.notification.MessagingService
import com.wenubey.wenucommerce.notification.NAV_TARGET_NEW_REVIEW
import com.wenubey.wenucommerce.notification.NAV_TARGET_ORDER_DETAIL
import com.wenubey.wenucommerce.notification.NAV_TARGET_SELLER_ORDERS
import com.wenubey.wenucommerce.notification.SyncBus
import com.wenubey.wenucommerce.notification.SyncEvent
import com.wenubey.wenucommerce.testing.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 Plan 04 — MessagingService routing helpers.
 *
 * Covers the two pure helpers refactored out of [MessagingService] so the
 * SyncBus emit + Intent construction can be exercised without a running
 * service:
 *   - [MessagingService.emitSyncIfOrderStatus]
 *   - [MessagingService.buildOrderStatusNotificationIntent]
 *
 * Uses Robolectric only because `buildOrderStatusNotificationIntent` needs
 * a real Context to resolve the package launch intent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class MessagingServiceSyncBusTest {

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    private fun orderStatusPayload(
        orderId: String = "order-123",
        sellerOrderId: String = "sub-456",
        newStatus: String = "SHIPPED",
    ): Map<String, String> = mapOf(
        FCM_DATA_KEY_TYPE to FCM_TYPE_ORDER_STATUS,
        FCM_DATA_KEY_ORDER_ID to orderId,
        FCM_DATA_KEY_SELLER_ORDER_ID to sellerOrderId,
        FCM_DATA_KEY_NEW_STATUS to newStatus,
    )

    @Test
    fun `emitSyncIfOrderStatus emits OrderStatusChanged on order_status payload`() = runTest {
        val bus = SyncBus()
        bus.events.test {
            val emit = launch {
                val result = MessagingService.emitSyncIfOrderStatus(bus, orderStatusPayload())
                assertTrue("emit should return true for valid payload", result)
            }
            val event = awaitItem()
            assertEquals(
                SyncEvent.OrderStatusChanged(orderId = "order-123", sellerOrderId = "sub-456"),
                event,
            )
            emit.join()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitSyncIfOrderStatus returns false and does not emit when type missing`() = runTest {
        val bus = SyncBus()
        val payloadNoType = mapOf(
            FCM_DATA_KEY_ORDER_ID to "order-123",
        )
        val result = MessagingService.emitSyncIfOrderStatus(bus, payloadNoType)
        assertFalse(result)
    }

    @Test
    fun `emitSyncIfOrderStatus returns false when orderId is blank`() = runTest {
        val bus = SyncBus()
        val payloadBlankOrderId = mapOf(
            FCM_DATA_KEY_TYPE to FCM_TYPE_ORDER_STATUS,
            FCM_DATA_KEY_ORDER_ID to "",
        )
        val result = MessagingService.emitSyncIfOrderStatus(bus, payloadBlankOrderId)
        assertFalse(result)
    }

    @Test
    fun `buildOrderStatusNotificationIntent returns Intent with deep-link extras on valid payload`() {
        val intent = MessagingService.buildOrderStatusNotificationIntent(
            context,
            orderStatusPayload(),
        )
        assertNotNull("intent should be non-null for valid order_status payload", intent)
        assertEquals(NAV_TARGET_ORDER_DETAIL, intent!!.getStringExtra(EXTRA_NAV_TARGET))
        assertEquals("order-123", intent.getStringExtra(EXTRA_ORDER_ID))
        assertEquals("sub-456", intent.getStringExtra(EXTRA_SELLER_ORDER_ID))
    }

    @Test
    fun `buildOrderStatusNotificationIntent returns null when type missing`() {
        val intent = MessagingService.buildOrderStatusNotificationIntent(
            context,
            mapOf(FCM_DATA_KEY_ORDER_ID to "order-123"),
        )
        assertNull(intent)
    }

    @Test
    fun `buildOrderStatusNotificationIntent returns null when orderId is blank`() {
        val intent = MessagingService.buildOrderStatusNotificationIntent(
            context,
            mapOf(
                FCM_DATA_KEY_TYPE to FCM_TYPE_ORDER_STATUS,
                FCM_DATA_KEY_ORDER_ID to "",
            ),
        )
        assertNull(intent)
    }

    @Test
    fun `buildNewOrderNotificationIntent returns Intent routing to seller orders on valid payload`() {
        val intent = MessagingService.buildNewOrderNotificationIntent(
            context,
            mapOf(
                FCM_DATA_KEY_TYPE to FCM_TYPE_NEW_ORDER,
                FCM_DATA_KEY_SELLER_ORDER_ID to "sub-456",
            ),
        )
        assertNotNull("intent should be non-null for valid new_order payload", intent)
        assertEquals(NAV_TARGET_SELLER_ORDERS, intent!!.getStringExtra(EXTRA_NAV_TARGET))
        assertEquals("sub-456", intent.getStringExtra(EXTRA_SELLER_ORDER_ID))
    }

    @Test
    fun `buildNewOrderNotificationIntent returns null when type is not new_order`() {
        val intent = MessagingService.buildNewOrderNotificationIntent(
            context,
            mapOf(
                FCM_DATA_KEY_TYPE to FCM_TYPE_ORDER_STATUS,
                FCM_DATA_KEY_SELLER_ORDER_ID to "sub-456",
            ),
        )
        assertNull(intent)
    }

    @Test
    fun `buildNewOrderNotificationIntent returns null when sellerOrderId is blank`() {
        val intent = MessagingService.buildNewOrderNotificationIntent(
            context,
            mapOf(
                FCM_DATA_KEY_TYPE to FCM_TYPE_NEW_ORDER,
                FCM_DATA_KEY_SELLER_ORDER_ID to "",
            ),
        )
        assertNull(intent)
    }

    // --- new_review (08-03 / NOTF-04) ---

    private fun newReviewPayload(
        productId: String = "prod-789",
        productTitle: String = "Blue Mug",
    ): Map<String, String> = mapOf(
        FCM_DATA_KEY_TYPE to FCM_TYPE_NEW_REVIEW,
        FCM_DATA_KEY_PRODUCT_ID to productId,
        FCM_DATA_KEY_PRODUCT_TITLE to productTitle,
    )

    @Test
    fun `buildNewReviewNotificationIntent returns Intent routing to seller reviews on valid payload`() {
        val intent = MessagingService.buildNewReviewNotificationIntent(
            context,
            newReviewPayload(),
        )
        assertNotNull("intent should be non-null for valid new_review payload", intent)
        assertEquals(NAV_TARGET_NEW_REVIEW, intent!!.getStringExtra(EXTRA_NAV_TARGET))
        assertEquals("prod-789", intent.getStringExtra(EXTRA_PRODUCT_ID))
        assertEquals("Blue Mug", intent.getStringExtra(EXTRA_PRODUCT_TITLE))
    }

    @Test
    fun `buildNewReviewNotificationIntent defaults productTitle to empty when absent`() {
        val intent = MessagingService.buildNewReviewNotificationIntent(
            context,
            mapOf(
                FCM_DATA_KEY_TYPE to FCM_TYPE_NEW_REVIEW,
                FCM_DATA_KEY_PRODUCT_ID to "prod-789",
            ),
        )
        assertNotNull(intent)
        assertEquals("prod-789", intent!!.getStringExtra(EXTRA_PRODUCT_ID))
        assertEquals("", intent.getStringExtra(EXTRA_PRODUCT_TITLE))
    }

    @Test
    fun `buildNewReviewNotificationIntent returns null when type is not new_review`() {
        val intent = MessagingService.buildNewReviewNotificationIntent(
            context,
            mapOf(
                FCM_DATA_KEY_TYPE to FCM_TYPE_NEW_ORDER,
                FCM_DATA_KEY_PRODUCT_ID to "prod-789",
            ),
        )
        assertNull(intent)
    }

    @Test
    fun `buildNewReviewNotificationIntent returns null when productId is blank`() {
        val intent = MessagingService.buildNewReviewNotificationIntent(
            context,
            mapOf(
                FCM_DATA_KEY_TYPE to FCM_TYPE_NEW_REVIEW,
                FCM_DATA_KEY_PRODUCT_ID to "",
            ),
        )
        assertNull(intent)
    }

    @Test
    fun `emitSyncIfNewReview emits NewReview on new_review payload`() = runTest {
        val bus = SyncBus()
        bus.events.test {
            val emit = launch {
                val result = MessagingService.emitSyncIfNewReview(bus, newReviewPayload())
                assertTrue("emit should return true for valid new_review payload", result)
            }
            val event = awaitItem()
            assertEquals(SyncEvent.NewReview(productId = "prod-789"), event)
            emit.join()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitSyncIfNewReview returns false when type is not new_review`() = runTest {
        val bus = SyncBus()
        val result = MessagingService.emitSyncIfNewReview(
            bus,
            mapOf(FCM_DATA_KEY_PRODUCT_ID to "prod-789"),
        )
        assertFalse(result)
    }

    @Test
    fun `emitSyncIfNewReview returns false when productId is blank`() = runTest {
        val bus = SyncBus()
        val result = MessagingService.emitSyncIfNewReview(
            bus,
            mapOf(
                FCM_DATA_KEY_TYPE to FCM_TYPE_NEW_REVIEW,
                FCM_DATA_KEY_PRODUCT_ID to "",
            ),
        )
        assertFalse(result)
    }
}
