package com.wenubey.wenucommerce.notification

/**
 * Single-source-of-truth constants for order-status FCM notifications.
 *
 * Used by:
 *  - [MessagingService] when building the foreground notification + PendingIntent.
 *  - [com.wenubey.wenucommerce.MainActivity] when consuming the deep-link intent.
 *  - functions/src/index.ts `onOrderStatusChange` (Cloud Function) — keeps
 *    `ORDER_STATUS_CHANNEL_ID` in sync with the server-side
 *    `android.notification.channelId` field.
 *
 * Phase 6 ships a single channel; multi-channel split is Phase 8 territory.
 */
const val ORDER_STATUS_CHANNEL_ID = "order_status_channel"
const val ORDER_STATUS_CHANNEL_NAME = "Order updates"
const val ORDER_STATUS_CHANNEL_DESCRIPTION =
    "Notifications when your order status changes"

const val EXTRA_NAV_TARGET = "wenucommerce.nav_target"
const val EXTRA_ORDER_ID = "wenucommerce.order_id"
const val EXTRA_SELLER_ORDER_ID = "wenucommerce.seller_order_id"

const val NAV_TARGET_ORDER_DETAIL = "order_detail"

const val FCM_TYPE_ORDER_STATUS = "order_status"
const val FCM_TYPE_NEW_ORDER = "new_order"
const val FCM_DATA_KEY_TYPE = "type"
const val FCM_DATA_KEY_ORDER_ID = "orderId"
const val FCM_DATA_KEY_SELLER_ORDER_ID = "sellerOrderId"
const val FCM_DATA_KEY_NEW_STATUS = "newStatus"
