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
const val NAV_TARGET_SELLER_ORDERS = "seller_orders"

const val FCM_TYPE_ORDER_STATUS = "order_status"
const val FCM_TYPE_NEW_ORDER = "new_order"
const val FCM_DATA_KEY_TYPE = "type"
const val FCM_DATA_KEY_ORDER_ID = "orderId"
const val FCM_DATA_KEY_SELLER_ORDER_ID = "sellerOrderId"
const val FCM_DATA_KEY_NEW_STATUS = "newStatus"

// Phase 8 (08-03) — new_review type (NOTF-04) routed to SellerProductReviews.
const val FCM_TYPE_NEW_REVIEW = "new_review"
const val FCM_DATA_KEY_PRODUCT_ID = "productId"
const val FCM_DATA_KEY_PRODUCT_TITLE = "productTitle"
// notifId embedded by every server FCM data payload (08-02) for Room dedup.
const val FCM_DATA_KEY_NOTIF_ID = "notifId"
const val NAV_TARGET_NEW_REVIEW = "new_review"
const val EXTRA_PRODUCT_ID = "wenucommerce.product_id"
const val EXTRA_PRODUCT_TITLE = "wenucommerce.product_title"

// Phase 8 (08-03) — the three centralised notification channels (NOTF-06 / D-03).
// ORDER_UPDATES_CHANNEL_ID MUST equal the server-side channelId from 08-02.
const val ORDER_UPDATES_CHANNEL_ID = "order_updates_channel"
const val ACCOUNT_CHANNEL_ID = "account_channel"
const val PROMOTIONS_CHANNEL_ID = "promotions_channel"
