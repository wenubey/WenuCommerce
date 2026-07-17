# Phase 8: Notifications — Pattern Map

**Mapped:** 2026-07-17
**Files analyzed:** 28 new/modified files
**Analogs found:** 28 / 28

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `functions/src/index.ts` (onNewReview + notifications-doc writes) | service | event-driven | `functions/src/index.ts` onNewSellerOrder (lines 1319–1370) | exact |
| `firestore.rules` (notifications/{uid}/items block) | config | request-response | `firestore.rules` /orders block (lines 61–65) | exact |
| `data/.../entity/NotificationEntity.kt` | model | CRUD | `data/.../entity/ReviewEntity.kt` | exact |
| `data/.../dao/NotificationDao.kt` | model | CRUD | `data/.../dao/ReviewDao.kt` | exact |
| `data/.../local/WenuCommerceDatabase.kt` (MIGRATION_9_10 + entity) | config | CRUD | same file MIGRATION_8_9 (lines 305–336) | exact |
| `domain/.../model/Notification.kt` | model | CRUD | `domain/.../model/product/ProductReview.kt` | role-match |
| `domain/.../repository/NotificationRepository.kt` | service | CRUD | `domain/.../repository/ProductReviewRepository.kt` | role-match |
| `data/.../repository/NotificationRepositoryImpl.kt` | service | event-driven | `data/.../repository/ProductReviewRepositoryImpl.kt` (callbackFlow) | exact |
| `data/.../worker/FcmTokenWorker.kt` | utility | request-response | `data/.../worker/SyncWorker.kt` | role-match |
| `app/.../notification/NotificationChannels.kt` | utility | request-response | `app/.../notification/MessagingService.kt` ensureOrderStatusChannel (lines 197–209) | role-match |
| `app/.../notification/OrderNotificationConstants.kt` (extend) | config | — | same file (lines 1–33) | exact |
| `app/.../notification/MessagingService.kt` (extend) | service | event-driven | same file (full file) | exact |
| `app/.../WenuCommerce.kt` (extend) | config | — | same file (lines 14–35) | exact |
| `app/.../di/DataModule.kt` (extend: FcmTokenWorker + NotificationRepositoryImpl) | config | — | same file workerModule (line 173) + repositoryModule (lines 73–90) | exact |
| `app/.../notification/notification_history/NotificationHistoryScreen.kt` | component | request-response | `app/.../customer/orders/CustomerOrderHistoryScreen.kt` | exact |
| `app/.../notification/notification_history/NotificationHistoryViewModel.kt` | component | CRUD | `app/.../customer/orders/CustomerOrderHistoryViewModel.kt` | exact |
| `app/.../notification/notification_history/NotificationHistoryState.kt` | model | — | `app/.../customer/orders/CustomerOrderHistoryState.kt` | exact |
| `app/.../notification/notification_history/NotificationHistoryAction.kt` | model | — | `app/.../customer/orders/CustomerOrderHistoryAction.kt` | role-match |
| `app/.../navigation/AppNavigationObjects.kt` (add NotificationHistory route) | config | — | same file data object CustomerOrderHistory (line 127) | exact |
| `app/.../customer/CustomerTabs.kt` (add Notifications entry) | config | — | same file (lines 16–41) | exact |
| `app/.../seller/SellerTabs.kt` (add Notifications entry) | config | — | same file (lines 18–47) | exact |
| `app/.../customer/CustomerTabScreen.kt` (BadgedBox for notifications) | component | request-response | same file CartBadge BadgedBox pattern (lines 126–138) | exact |
| `app/.../customer/CustomerProfileScreen.kt` (Notifications row) | component | request-response | same file ProfileMenuItem (lines 86–125 + 129–172) | exact |
| `app/.../seller/SellerProfileScreen.kt` (Notifications SettingsItem) | component | request-response | `app/.../seller/SellerProfileScreen.kt` SettingsItem rows | role-match |
| `app/.../notification/permission/NotificationPermissionRationaleDialog.kt` | component | request-response | `app/.../customer/CustomerTabScreen.kt` AlertDialog patterns | role-match |
| `app/.../MainActivity.kt` (extend deep-link LaunchedEffect) | component | event-driven | same file lines 113–147 | exact |
| `data/.../repository/NotificationPreferences.kt` (extend: permission gate key) | utility | request-response | same file (lines 1–24) | exact |
| `app/.../notification/SyncEvent.kt` (optionally add NewReview) | utility | event-driven | same file existing SyncEvent sealed class | exact |

---

## Pattern Assignments

### `functions/src/index.ts` — onNewReview Cloud Function (NEW)

**Analog:** `functions/src/index.ts` lines 1319–1370 (`onNewSellerOrder`)

**Imports pattern** (lines 1–10 — already present in file):
```typescript
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { getMessaging } from "firebase-admin/messaging";
```

**Core trigger pattern** (lines 1319–1370 — copy shape exactly):
```typescript
export const onNewSellerOrder = onDocumentCreated(
  "sellerOrders/{sellerOrderId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) { console.log("[new_order] no data on created doc — skipping"); return; }
    const sellerUid = data.sellerId as string | undefined;
    if (!sellerUid) { console.log("[new_order] no sellerId on doc — skipping"); return; }

    const db = admin.firestore();
    const userSnap = await db.collection("USERS").doc(sellerUid).get();
    const fcmToken = userSnap.data()?.fcmToken as string | undefined;
    if (!fcmToken) { console.log("[new_order] no fcmToken on seller USERS doc — skipping"); return; }

    try {
      const messageId = await getMessaging().send({
        token: fcmToken,
        notification: { title: "New order", body: "You have a new order to fulfill." },
        data: { type: "new_order", sellerOrderId },
        android: { priority: "high", notification: { channelId: "order_status_channel" } },
      });
      console.log("[new_order] send SUCCESS", { messageId });
    } catch (err) {
      console.error("[new_order] dispatch FAILED for sellerOrderId", sellerOrderId, err);
    }
  },
);
```

**For `onNewReview`:** Change trigger path to `"PRODUCTS/{productId}/REVIEWS/{reviewId}"`, look up `productSnap.data()?.sellerId`, look up `productSnap.data()?.title` for `productTitle`, change FCM data keys to `type: "new_review"`, `productId`, `productTitle`, change channelId to `"order_updates_channel"`. Write `notifications/{sellerId}/items` doc BEFORE the FCM send (always persist history even if FCM fails).

**Notifications-doc write to add to onOrderStatusChange** (after FCM send, lines 1300–1306 area):
```typescript
// Persist to customer notification history
if (customerUid) {
  const notifRef = db.collection("notifications").doc(customerUid).collection("items").doc();
  await notifRef.set({
    id: notifRef.id,
    type: "order_status",
    title: titleFor(after.status),
    body: `Your order is ${String(after.status).toLowerCase()}.`,
    orderId: parentId,
    sellerOrderId: event.params.sellerOrderId,
    productId: "",
    productTitle: "",
    read: false,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}
```

**Notifications-doc write to add to onNewSellerOrder** (after FCM send, inside try block lines 1347–1368):
```typescript
const notifRef = db.collection("notifications").doc(sellerUid).collection("items").doc();
await notifRef.set({
  id: notifRef.id,
  type: "new_order",
  title: "New order",
  body: "You have a new order to fulfill.",
  orderId: "",
  sellerOrderId,
  productId: "",
  productTitle: "",
  read: false,
  createdAt: admin.firestore.FieldValue.serverTimestamp(),
});
```

---

### `firestore.rules` — notifications/{uid}/items block (EXTEND)

**Analog:** `firestore.rules` lines 61–65 (`/orders` owner-read, server-only write)

**Existing pattern to copy:**
```javascript
// firestore.rules lines 61–65
match /orders/{orderId} {
  allow read: if request.auth != null
              && resource.data.userId == request.auth.uid;
  allow create, update, delete: if false;
}
```

**New block to add (after the /orders block):**
```javascript
match /notifications/{userId}/items/{notifId} {
  // Owner can read their own notifications
  allow read: if request.auth != null && request.auth.uid == userId;
  // Owner can mark as read (update read field only)
  allow update: if request.auth != null
                && request.auth.uid == userId
                && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['read']);
  // All creates/deletes are server-only (Admin SDK bypasses rules)
  allow create, delete: if false;
}
```

Key difference from `/orders`: the `update` rule is permitted for mark-as-read (the client writes `read = true`). The `diff(...).hasOnly(['read'])` pattern is not yet used elsewhere — it is the standard Firestore field-restriction idiom.

---

### `data/.../entity/NotificationEntity.kt` (NEW)

**Analog:** `data/.../entity/ReviewEntity.kt` (full file, lines 1–39)

**Imports + annotation pattern** (lines 1–8):
```kotlin
package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reviews",
    indices = [
        Index("productId"),
        Index("reviewerId")
    ]
)
data class ReviewEntity(
    @PrimaryKey val id: String,
    val productId: String = "",
    ...
    val createdAt: String = "",
    val updatedAt: String = "",
)
```

**For NotificationEntity:** Change `tableName = "notifications"`, indices to `Index("userId")` + `Index("createdAt")`. Columns: `id`, `userId`, `type`, `title`, `body`, `orderId`, `sellerOrderId`, `productId`, `productTitle`, `isRead: Boolean = false`, `createdAt: String = ""`. No `updatedAt` (notifications are immutable except for `isRead`). Mirror all defaults (`= ""`, `= false`) exactly as ReviewEntity uses `= ""`, `= 0`, `= true`.

---

### `data/.../dao/NotificationDao.kt` (NEW)

**Analog:** `data/.../dao/ReviewDao.kt` (full file, lines 1–27)

**Exact pattern to copy:**
```kotlin
package com.wenubey.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wenubey.data.local.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Upsert
    suspend fun upsert(review: ReviewEntity)

    @Upsert
    suspend fun upsertAll(reviews: List<ReviewEntity>)

    @Query("SELECT * FROM reviews WHERE productId = :productId AND isVisible = 1")
    fun observeByProduct(productId: String): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE reviewerId = :reviewerId AND productId = :productId LIMIT 1")
    suspend fun getByReviewerAndProduct(reviewerId: String, productId: String): ReviewEntity?

    @Query("DELETE FROM reviews WHERE productId = :productId")
    suspend fun deleteByProduct(productId: String)
}
```

**For NotificationDao:** Replace entity type with `NotificationEntity`, replace query in `observeBy*` to `WHERE userId = :userId ORDER BY createdAt DESC`. Replace per-reviewer query with `observeUnreadCount(userId: String): Flow<Int>` using `SELECT COUNT(*) WHERE userId = :userId AND isRead = 0`. Replace `deleteByProduct` with `markAsRead(id: String)` using `UPDATE notifications SET isRead = 1 WHERE id = :id`. Keep `upsertAll` identical.

---

### `data/.../local/WenuCommerceDatabase.kt` — MIGRATION_9_10 (EXTEND)

**Analog:** Same file, lines 305–336 (`MIGRATION_8_9`)

**Exact template to clone:**
```kotlin
// WenuCommerceDatabase.kt lines 305–336
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reviews` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `productId` TEXT NOT NULL DEFAULT '',
                ...
                `createdAt` TEXT NOT NULL DEFAULT '',
                `updatedAt` TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reviews_productId` " +
                "ON `reviews` (`productId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reviews_reviewerId` " +
                "ON `reviews` (`reviewerId`)"
        )
    }
}
```

**For MIGRATION_9_10:** Change `Migration(8, 9)` to `Migration(9, 10)`. Replace table body with the `notifications` columns from RESEARCH.md §2. Replace indices to `index_notifications_userId` and `index_notifications_createdAt`.

**Also update in same file:**
1. `@Database(version = 9` → `version = 10`
2. Add `NotificationEntity::class` to the entities array (line 32 area)
3. Add `abstract fun notificationDao(): NotificationDao` (line 66 area)

**Also update `DataModule.kt`** (line 137 area):
```kotlin
.addMigrations(
    WenuCommerceDatabase.MIGRATION_1_2,
    // ... all existing ...
    WenuCommerceDatabase.MIGRATION_8_9,
    WenuCommerceDatabase.MIGRATION_9_10,  // add
)
// Also add:
single { get<WenuCommerceDatabase>().notificationDao() }
```

---

### `data/.../repository/NotificationRepositoryImpl.kt` (NEW)

**Analog:** `data/.../repository/ProductReviewRepositoryImpl.kt` lines 37–92 (callbackFlow + channelFlow Room-first pattern)

**Imports pattern** (lines 1–26):
```kotlin
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.local.dao.ReviewDao
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.local.mapper.toEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
```

**Core Firestore callbackFlow → Room upsert pattern** (lines 54–92):
```kotlin
override fun observeReviewsForProduct(productId: String): Flow<List<ProductReview>> =
    channelFlow {
        launch(ioDispatcher) {
            productReviewSnapshots(productId).collect { reviews ->
                reviewDao.upsertAll(reviews.map { it.toEntity() })
            }
        }
        reviewDao.observeByProduct(productId)
            .map { list -> list.map { it.toDomain() } }
            .collect { send(it) }
    }

private fun productReviewSnapshots(productId: String): Flow<List<ProductReview>> =
    callbackFlow {
        val listener = reviewsCollection(productId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error observing reviews for product: $productId")
                    return@addSnapshotListener
                }
                val reviews = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(ProductReview::class.java) }
                    catch (e: Exception) { Timber.e(e, "Failed to deserialize"); null }
                } ?: emptyList()
                trySend(reviews)
            }
        awaitClose { listener.remove() }
    }
```

**For NotificationRepositoryImpl:** The collection path is `firestore.collection("notifications").document(userId).collection("items").orderBy("createdAt", Query.Direction.DESCENDING)`. The listener is per-user (userId-scoped), not per-product. Start/stop pattern follows `AuthRepositoryImpl.startUserListener` (lines 134–164): store `listenerJob: Job?`, cancel in `stopListener()`. Call `startListener(userId)` from `AuthRepositoryImpl.authStateListener` non-null branch, alongside `startUserListener`.

**Listener lifecycle pattern from AuthRepositoryImpl** (lines 81–107, 134–164):
```kotlin
// AuthRepositoryImpl.kt lines 81–91 — auth-state-gated start
private val authStateListener: AuthStateListener = AuthStateListener { auth ->
    if (auth.currentUser == null) {
        stopUserListener()
        _currentUser.value = null
        ...
    } else {
        startUserListener(auth.currentUser!!.uid)
        // FCM token refresh ...
    }
}

// lines 134–159 — Firestore snapshot listener with ListenerRegistration
private fun startUserListener(uid: String) {
    userListener?.remove()
    userListener = firestore.collection(USER_COLLECTION)
        .document(uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { Timber.e(error, "Failed to load user data"); return@addSnapshotListener }
            ...
        }
}

private fun stopUserListener() {
    userListener?.remove()
    userListener = null
}
```

For NotificationRepositoryImpl: use a `Job?` (`listenerJob`) rather than `ListenerRegistration?` because the notification listener uses a `callbackFlow` + `collect` pattern (coroutine-based). Cancel the `listenerJob` in `stopListener()`.

---

### `data/.../worker/FcmTokenWorker.kt` (NEW)

**Analog:** `data/.../worker/SyncWorker.kt` (full file, lines 1–205)

**Imports + class header pattern** (lines 1–48):
```kotlin
package com.wenubey.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wenubey.domain.repository.DispatcherProvider
import timber.log.Timber
import java.time.Duration

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val pendingOperationDao: PendingOperationDao,
    ...
    private val dispatcherProvider: DispatcherProvider
) : CoroutineWorker(appContext, params) {
```

**doWork pattern** (lines 52–57):
```kotlin
override suspend fun doWork(): Result = dispatcherProvider.io().run {
    if (isStopped) { return Result.failure() }
    if (runAttemptCount > MAX_RETRIES) { ... return Result.failure() }
    ...
    try {
        ...
        Result.success()
    } catch (e: Exception) {
        Timber.e(e, "SyncWorker: Failed ...")
        ...
        return Result.retry()
    }
}
```

**Companion enqueue pattern** (lines 172–203):
```kotlin
companion object {
    private const val MAX_RETRIES = 3
    const val UNIQUE_WORK_NAME = "sync_pending_operations"  // → "fcm_token_refresh" for FcmTokenWorker

    fun enqueue(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
```

**For FcmTokenWorker:** Constructor takes `(appContext, params, firestoreRepository: FirestoreRepository, firebaseMessaging: FirebaseMessaging, dispatcherProvider: DispatcherProvider)`. `doWork()` fetches `firebaseMessaging.token.await()` then calls `firestoreRepository.updateFcmToken(token).getOrThrow()`. `UNIQUE_WORK_NAME = "fcm_token_refresh"` (distinct from `"sync_pending_operations"`). No re-enqueue-self (not a queue drain loop). `FirestoreRepository.updateFcmToken` must be `suspend` before this worker will compile — see pitfall in RESEARCH.md §Pitfall 3.

**Koin registration** in `DataModule.kt` (line 173, mirrors existing `workerModule`):
```kotlin
val workerModule = module {
    worker { SyncWorker(get(), get(), get(), get(), get(), get()) }
    worker { FcmTokenWorker(get(), get(), get(), get(), get()) }  // add
}
```

---

### `app/.../notification/NotificationChannels.kt` (NEW)

**Analog:** `app/.../notification/MessagingService.kt` lines 197–209 (`ensureOrderStatusChannel`)

**Exact pattern to extend:**
```kotlin
// MessagingService.kt lines 197–209
internal fun ensureOrderStatusChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        ORDER_STATUS_CHANNEL_ID,
        ORDER_STATUS_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = ORDER_STATUS_CHANNEL_DESCRIPTION
    }
    nm.createNotificationChannel(channel)
}
```

**For NotificationChannels.kt:** Extract to `object NotificationChannels`, call `nm.createNotificationChannels(listOf(...))` with all three channels in one call. Call `NotificationChannels.createAll(this)` in `WenuCommerce.onCreate()` BEFORE `startKoin` (line 22 area). Remove `ensureOrderStatusChannel()` call from `MessagingService.onCreate()` (line 34). Remove inline `device_login_channel` creation from `showNotification()` (lines 143–156).

**WenuCommerce.kt insertion point** (before `startKoin` call, line 22):
```kotlin
// WenuCommerce.kt lines 14–35 — existing onCreate
override fun onCreate() {
    super.onCreate()
    FirebaseApp.initializeApp(this@WenuCommerce)
    PaymentConfiguration.init(applicationContext, BuildConfig.STRIPE_PUBLISHABLE_KEY)
    // ADD HERE:
    NotificationChannels.createAll(this)
    startKoin { ... }
    ...
}
```

---

### `app/.../notification/OrderNotificationConstants.kt` (EXTEND)

**Analog:** Same file, lines 1–33 (full file — all constants follow the same flat `const val` pattern)

**Existing pattern** (lines 15–33):
```kotlin
const val ORDER_STATUS_CHANNEL_ID = "order_status_channel"
const val ORDER_STATUS_CHANNEL_NAME = "Order updates"
const val ORDER_STATUS_CHANNEL_DESCRIPTION = "Notifications when your order status changes"

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
```

**Add to this file:**
```kotlin
// New review type
const val FCM_TYPE_NEW_REVIEW = "new_review"
const val FCM_DATA_KEY_PRODUCT_ID = "productId"
const val FCM_DATA_KEY_PRODUCT_TITLE = "productTitle"
const val NAV_TARGET_NEW_REVIEW = "new_review"
const val EXTRA_PRODUCT_ID = "wenucommerce.product_id"
const val EXTRA_PRODUCT_TITLE = "wenucommerce.product_title"

// Three channel IDs (D-03) — used by NotificationChannels + MessagingService
const val ORDER_UPDATES_CHANNEL_ID = "order_updates_channel"
const val ACCOUNT_CHANNEL_ID = "account_channel"
const val PROMOTIONS_CHANNEL_ID = "promotions_channel"
```

Keep `ORDER_STATUS_CHANNEL_ID` in place for backward compat and existing tests.

---

### `app/.../notification/MessagingService.kt` (EXTEND)

**Analog:** Same file (full file, lines 1–304)

**onMessageReceived router to extend** (lines 48–75 — add new_review branch):
```kotlin
// MessagingService.kt lines 48–75 — existing router
override fun onMessageReceived(message: RemoteMessage) {
    val data = message.data
    if (data[FCM_DATA_KEY_TYPE] == FCM_TYPE_ORDER_STATUS) {
        serviceScope.launch { emitSyncIfOrderStatus(syncBus, data) }
        showOrderStatusNotification(message)
        return
    }
    if (data[FCM_DATA_KEY_TYPE] == FCM_TYPE_NEW_ORDER) {
        serviceScope.launch { emitSyncIfNewOrder(syncBus, data) }
        showNewOrderNotification(message)
        return
    }
    // Legacy device-login path
    message.notification?.let { ... showNotification(title, body) }
}
```

**Add before the legacy device-login branch:**
```kotlin
if (data[FCM_DATA_KEY_TYPE] == FCM_TYPE_NEW_REVIEW) {
    showNewReviewNotification(message)
    return
}
```

**showNewReviewNotification (copy shape from showNewOrderNotification lines 112–139):**
```kotlin
// MessagingService.kt lines 112–139 — template for showNewReviewNotification
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
        this, sellerOrderId.hashCode(), launchIntent,
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
    NotificationManagerCompat.from(this).notify(sellerOrderId.hashCode(), notification)
}
```

For `showNewReviewNotification`: read `FCM_DATA_KEY_PRODUCT_ID`, use `buildNewReviewNotificationIntent`, channelId = `ORDER_UPDATES_CHANNEL_ID`.

**buildNewReviewNotificationIntent (copy shape from buildNewOrderNotificationIntent lines 246–263):**
```kotlin
// MessagingService.kt lines 246–263 — template for buildNewReviewNotificationIntent
internal fun buildNewOrderNotificationIntent(
    context: Context,
    data: Map<String, String>,
): Intent? {
    if (data[FCM_DATA_KEY_TYPE] != FCM_TYPE_NEW_ORDER) return null
    val sellerOrderId = data[FCM_DATA_KEY_SELLER_ORDER_ID]
    if (sellerOrderId.isNullOrBlank()) return null
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    return launch.apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_NAV_TARGET, NAV_TARGET_SELLER_ORDERS)
        putExtra(EXTRA_SELLER_ORDER_ID, sellerOrderId)
    }
}
```

**onNewToken replacement** (line 43–46):
```kotlin
// Current (lines 43–46):
override fun onNewToken(token: String) {
    super.onNewToken(token)
    firestoreRepository.updateFcmToken(token)  // fire-and-forget
}
// Replace with:
override fun onNewToken(token: String) {
    super.onNewToken(token)
    FcmTokenWorker.enqueue(this)  // token fetched inside worker
}
```

Also update `NotificationCompat.Builder(this, ORDER_STATUS_CHANNEL_ID)` → `ORDER_UPDATES_CHANNEL_ID` in `showOrderStatusNotification` and `showNewOrderNotification`.

---

### `app/.../notification/notification_history/NotificationHistoryScreen.kt` (NEW)

**Analog:** `app/.../customer/orders/CustomerOrderHistoryScreen.kt` (lines 1–100 read; full file is the analog)

**Scaffold + TopAppBar + SnackbarHost pattern** (lines 64–79):
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("My Orders") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
) { innerPadding ->
    ...
}
```

**PullToRefreshBox + LazyColumn state machine pattern** (lines 91–100):
```kotlin
PullToRefreshBox(
    isRefreshing = state.isRefreshing,
    onRefresh = { viewModel.onAction(CustomerOrderHistoryAction.OnRefresh) },
    modifier = Modifier.fillMaxSize(),
) {
    when {
        state.isLoading && state.orders.isEmpty() -> { LoadingPlaceholder() }
        visible.isEmpty() -> { /* EmptyState */ }
        else -> { /* LazyColumn */ }
    }
}
```

**Error snackbar pattern** (lines 57–62):
```kotlin
LaunchedEffect(state.errorMessage) {
    state.errorMessage?.let { msg ->
        snackbarHostState.showSnackbar(msg)
        viewModel.onAction(CustomerOrderHistoryAction.OnDismissError)
    }
}
```

**For NotificationHistoryScreen:** Title = "Notifications". Add `TextButton("Mark all read")` in TopAppBar `actions` slot (shown only when `state.hasUnread`). Replace `LoadingPlaceholder()` with `CircularProgressIndicator()`. LazyColumn items = `NotificationRow` cards (see UI-SPEC §1). Key items by `item.id`.

---

### `app/.../notification/notification_history/NotificationHistoryViewModel.kt` (NEW)

**Analog:** `app/.../customer/orders/CustomerOrderHistoryViewModel.kt` (full file, lines 1–112)

**StateFlow + init pattern** (lines 34–44):
```kotlin
class CustomerOrderHistoryViewModel(
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val syncBus: SyncBus,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerOrderHistoryState(isLoading = true))
    val state: StateFlow<CustomerOrderHistoryState> = _state.asStateFlow()

    private val userId: String?
        get() = authRepository.currentUser.value?.uuid

    init {
        observeOrders()
        triggerInitialSync()
        observeSyncBus()
    }
```

**Flow observe → state update pattern** (lines 46–59):
```kotlin
private fun observeOrders() {
    val uid = userId ?: run { _state.update { it.copy(isLoading = false) }; return }
    orderRepository.observeCustomerOrders(uid)
        .catch { error ->
            _state.update { it.copy(isLoading = false, errorMessage = error.message) }
        }
        .onEach { orders ->
            _state.update { it.copy(orders = orders, isLoading = false) }
        }
        .launchIn(viewModelScope)
}
```

**For NotificationHistoryViewModel:** Replace `observeCustomerOrders` with `notificationRepository.observeNotifications(uid)`, map to `notifications` in state. Add `markAsRead(id)` suspend action. Add `observeUnreadCount(uid)` for badge. The `syncBus` dependency is optional (no FCM-triggered refresh for notification history — the Firestore listener handles it).

---

### `app/.../notification/notification_history/NotificationHistoryState.kt` (NEW)

**Analog:** `app/.../customer/orders/CustomerOrderHistoryState.kt` (full file, lines 1–53)

**Pattern to copy** (lines 19–25):
```kotlin
data class CustomerOrderHistoryState(
    val orders: List<Order> = emptyList(),
    val filter: OrderFilter = OrderFilter.ALL,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)
```

**For NotificationHistoryState:**
```kotlin
data class NotificationHistoryState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val hasUnread: Boolean = false,  // drives "Mark all read" TopAppBar action visibility
)
```

No filter enum needed (notifications are not filterable this phase).

---

### `app/.../navigation/AppNavigationObjects.kt` (EXTEND)

**Analog:** Same file, lines 126–130 (`data object CustomerOrderHistory`)

**Exact pattern to copy:**
```kotlin
// AppNavigationObjects.kt lines 126–130
@Serializable
data object CustomerOrderHistory

@Serializable
data class CustomerOrderDetail(val orderId: String)
```

**Add at end of file:**
```kotlin
@Serializable
data object NotificationHistory
```

No payload needed (screen loads for the current auth user).

---

### `app/.../customer/CustomerTabs.kt` (EXTEND)

**Analog:** Same file (full file, lines 1–41) + `SellerTabs.kt` (lines 1–47)

**Exact pattern to copy** (lines 21–40):
```kotlin
enum class CustomerTabs(
    @StringRes val text: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(...),
    Cart(...),
    Wishlist(...),
    Profile(
        text = R.string.profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}
```

**Add before `Profile`:**
```kotlin
Notifications(
    text = R.string.notifications,
    selectedIcon = Icons.Filled.Notifications,
    unselectedIcon = Icons.Outlined.Notifications,
),
```

Same addition pattern for `SellerTabs.kt` before the `Profile` entry.

---

### `app/.../customer/CustomerTabScreen.kt` (EXTEND — BadgedBox for notifications)

**Analog:** Same file lines 126–138 (cart BadgedBox pattern)

**Exact pattern to copy:**
```kotlin
// CustomerTabScreen.kt lines 126–138
if (tab == CustomerTabs.Cart && cartCount > 0) {
    BadgedBox(badge = { Badge { Text("$cartCount") } }) {
        Icon(
            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = stringResource(id = tab.text)
        )
    }
} else {
    Icon(
        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
        contentDescription = stringResource(id = tab.text)
    )
}
```

**For Notifications BadgedBox:** Same structure but condition = `tab == CustomerTabs.Notifications && unreadCount > 0`. Display text = `if (unreadCount > 9) "9+" else "$unreadCount"`. Source `unreadCount` from `notificationRepository.observeUnreadCount(uid)` collected via `collectAsStateWithLifecycle` in the tab screen, same as `cartCountFlow` (lines 53–56).

---

### `app/.../customer/CustomerProfileScreen.kt` + `SellerProfileScreen.kt` (EXTEND)

**Analog:** `CustomerProfileScreen.kt` lines 86–125 (`ProfileMenuItem` calls) and lines 129–172 (`ProfileMenuItem` composable definition)

**Exact ProfileMenuItem composable** (lines 129–172):
```kotlin
@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(24.dp), tint = textColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = textColor)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Navigate", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

**Usage pattern to copy** (lines 86–91):
```kotlin
item {
    ProfileMenuItem(
        icon = Icons.Default.ShoppingBag,
        title = "My Orders",
        subtitle = "View your past orders"
    ) { onNavigateToOrderHistory() }
}
```

**For Notifications row:** `icon = Icons.Default.Notifications`, `title = "Notifications"`, `subtitle = if (notificationsEnabled) "Notifications are on" else "Tap to enable notifications"`, `textColor = if (!notificationsEnabled) colorScheme.error else colorScheme.onSurface`. On tap: if denied → launch `Settings.ACTION_APP_NOTIFICATION_SETTINGS` intent; if granted → `onNavigateToNotificationHistory()`.

---

### `app/.../notification/permission/NotificationPermissionRationaleDialog.kt` (NEW)

**Analog:** `app/.../customer/CustomerProfileScreen.kt` `ProfileMenuItem` Card structure + standard M3 AlertDialog

**AlertDialog pattern (M3 — used throughout the project):**
From `CustomerProfileScreen.kt` lines 76–79 (Badge) and M3 AlertDialog standard signature:
```kotlin
// Standard M3 AlertDialog used in the project (pattern from existing dialogs):
AlertDialog(
    onDismissRequest = { onDismiss() },
    icon = { Icon(...) },
    title = { Text("Stay in the loop") },
    text = { Text("Get notified when your orders ship...") },
    confirmButton = { Button(onClick = { onEnable() }) { Text("Enable") } },
    dismissButton = { TextButton(onClick = { onDismiss() }) { Text("Not now") } }
)
```

**Permission launcher pattern** (use `rememberLauncherForActivityResult` from activity-compose 1.10.0, consistent with existing `LifecycleResumeEffect` usage in `CustomerTabScreen.kt` lines 46–49):
```kotlin
// CustomerTabScreen.kt lines 46–49 — lifecycle pattern analog
LifecycleResumeEffect(Unit) {
    emailBannerVm.recheckEmailVerification()
    onPauseOrDispose { }
}
```

For the permission: use `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`. Gate with `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)`. Store gate in `NotificationPreferences` DataStore (`booleanPreferencesKey("notification_permission_requested")`).

---

### `app/.../MainActivity.kt` (EXTEND — new_review deep-link branch)

**Analog:** Same file lines 113–147 (existing deep-link `LaunchedEffect`)

**Exact existing pattern to extend** (lines 113–147):
```kotlin
// MainActivity.kt lines 113–147
LaunchedEffect(intentVersion, currentBackStackEntry) {
    if (currentBackStackEntry == null) return@LaunchedEffect

    val namespacedOrderId = intent.getStringExtra(EXTRA_ORDER_ID)
    val fcmRawOrderId = intent.getStringExtra("orderId")
    val fcmRawType = intent.getStringExtra("type")
    val target = intent.getStringExtra(EXTRA_NAV_TARGET)

    val isSellerNewOrder = target == NAV_TARGET_SELLER_ORDERS || fcmRawType == FCM_TYPE_NEW_ORDER
    val orderId = namespacedOrderId ?: fcmRawOrderId?.takeIf { fcmRawType == FCM_TYPE_ORDER_STATUS }

    when {
        isSellerNewOrder -> {
            navController.navigate(SellerTab(tabIndex = SellerTabs.Orders.ordinal)) {
                popUpTo<SellerTab> { inclusive = true }
            }
            clearNotificationExtras()
        }
        !orderId.isNullOrBlank() && ... -> {
            navController.navigate(CustomerOrderHistory)
            navController.navigate(CustomerOrderDetail(orderId))
            clearNotificationExtras()
        }
    }
}
```

**Add before the `when` block (read new extras):**
```kotlin
val namespacedProductId = intent.getStringExtra(EXTRA_PRODUCT_ID)
val fcmRawProductId = intent.getStringExtra("productId")
val productId = namespacedProductId ?: fcmRawProductId
val namespacedProductTitle = intent.getStringExtra(EXTRA_PRODUCT_TITLE)
val productTitle = namespacedProductTitle ?: intent.getStringExtra("productTitle") ?: ""

val isNewReview = target == NAV_TARGET_NEW_REVIEW || fcmRawType == FCM_TYPE_NEW_REVIEW
```

**Add as first branch in `when`:**
```kotlin
isNewReview && !productId.isNullOrBlank() -> {
    Timber.d("FCM deep-link → seller product reviews %s", productId)
    navController.navigate(SellerProductReviews(productId, productTitle))
    clearNotificationExtras()
}
```

**Extend clearNotificationExtras()** (lines 232–237):
```kotlin
private fun clearNotificationExtras() {
    intent.removeExtra(EXTRA_NAV_TARGET)
    intent.removeExtra(EXTRA_ORDER_ID)
    intent.removeExtra("orderId")
    intent.removeExtra("type")
    // ADD:
    intent.removeExtra(EXTRA_PRODUCT_ID)
    intent.removeExtra(EXTRA_PRODUCT_TITLE)
    intent.removeExtra("productId")
    intent.removeExtra("productTitle")
}
```

---

### `data/.../repository/NotificationPreferences.kt` (EXTEND)

**Analog:** Same file (full file, lines 1–24)

**Existing DataStore key pattern** (lines 12–13):
```kotlin
companion object {
    private val KEY_EMAIL_VERIFICATION_HIDDEN = booleanPreferencesKey("email_verification_hidden")
}
```

**Add new key (no collision with existing key):**
```kotlin
private val KEY_NOTIFICATION_PERMISSION_REQUESTED = booleanPreferencesKey("notification_permission_requested")
```

Add `suspend fun isNotificationPermissionRequested(): Boolean` and `suspend fun setNotificationPermissionRequested(requested: Boolean)` following the exact same `dataStore.data.first()` + `dataStore.edit { }` pattern (lines 16–24).

---

## Shared Patterns

### Authentication / User-scoped listener lifecycle
**Source:** `data/.../repository/AuthRepositoryImpl.kt` lines 81–107, 134–164
**Apply to:** `NotificationRepositoryImpl` (start/stop listener in auth-state listener)

```kotlin
// AuthRepositoryImpl.kt lines 90–91 — auth-state branch: call startNotificationListener alongside startUserListener
} else {
    startUserListener(auth.currentUser!!.uid)
    notificationRepository.startListener(auth.currentUser!!.uid)  // add Phase 8
    ...
}
```

### Room-first + Firestore callbackFlow sync
**Source:** `data/.../repository/ProductReviewRepositoryImpl.kt` lines 54–92
**Apply to:** `NotificationRepositoryImpl.observeNotifications`

Pattern: `channelFlow { launch { firestoreFlow.collect { dao.upsertAll(it) } }; dao.observeByX().collect { send(it) } }`

### WorkManager one-time job with CONNECTED constraint + EXPONENTIAL backoff + REPLACE policy
**Source:** `data/.../worker/SyncWorker.kt` lines 183–203
**Apply to:** `FcmTokenWorker.enqueue()`

### BadgedBox with count display
**Source:** `app/.../customer/CustomerTabScreen.kt` lines 126–138
**Apply to:** `CustomerTabScreen` Notifications tab item, `SellerTabScreen` Notifications tab item

Cap: `if (count > 9) "9+" else "$count"` (UI-SPEC §2)

### ProfileMenuItem clickable row
**Source:** `app/.../customer/CustomerProfileScreen.kt` lines 129–172
**Apply to:** Customer "Notifications" profile row (permission-denied affordance)

### SellerTabScreen NavigationBar item (for Notifications entry)
**Source:** `app/.../seller/SellerTabs.kt` lines 18–47 — enum entry pattern
**Apply to:** `SellerTabs.kt` new `Notifications` entry

### Koin `worker { }` registration
**Source:** `app/.../di/DataModule.kt` line 173
**Apply to:** `FcmTokenWorker` registration in `workerModule`

### Koin `singleOf(::XxxRepositoryImpl).bind<XxxRepository>()` registration
**Source:** `app/.../di/DataModule.kt` line 74 (`singleOf(::FirestoreRepositoryImpl).bind<FirestoreRepository>()`)
**Apply to:** `NotificationRepositoryImpl` in `repositoryModule`

### `@Serializable data object` route
**Source:** `app/.../navigation/AppNavigationObjects.kt` line 127 (`data object CustomerOrderHistory`)
**Apply to:** `data object NotificationHistory`

---

## No Analog Found

No files in this phase lack a close analog. All patterns are drawn from existing codebase code.

---

## Metadata

**Analog search scope:** `app/src/`, `data/src/`, `domain/src/`, `functions/src/`, root `firestore.rules`
**Files scanned:** ~18 source files read directly
**Phase directory:** `.planning/phases/08-notifications/`
**Pattern extraction date:** 2026-07-17
