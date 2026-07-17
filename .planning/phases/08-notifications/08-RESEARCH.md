# Phase 8: Notifications — Research

**Researched:** 2026-07-17
**Domain:** Android FCM, Room, WorkManager, Compose permission API, Firebase Cloud Functions
**Confidence:** HIGH (all findings drawn from direct codebase inspection + verified Android/Firebase official docs)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01** — Firestore `notifications/{uid}/items/{id}` docs written by every push-dispatching Cloud
  Function. Room `notifications` table mirrors them. History screen reads Room (Room-first).
  Track `read` flag; mark-read on open. Unread badge on history entry point is Claude's
  discretion (D-01b).
- **D-02** — `POST_NOTIFICATIONS` requested contextually after login, first landing on the
  authenticated home, preceded by an in-app rationale dialog. On denial: no nag; expose
  "Enable notifications" affordance in Profile/Settings that opens system app-notification
  settings. Android 13+ (API 33) only.
- **D-03** — Three channels created once in a central place (Application `onCreate` or a helper):
  **"Order Updates"** (IMPORTANCE_HIGH) ← order_status + new_order + new_review;
  **"Account"** (IMPORTANCE_DEFAULT) ← device_login;
  **"Promotions"** (IMPORTANCE_LOW) ← reserved, empty.
  Migrate existing `order_status_channel` usage onto Order Updates.
- **D-04** — New `onNewReview` Cloud Function (`onDocumentCreated` on
  `PRODUCTS/{productId}/REVIEWS/{reviewId}`) → FCM to the product's seller + notifications
  doc for the seller. Deep-links to `SellerProductReviews(productId, productTitle)`.
  Extend `MainActivity` + `OrderNotificationConstants`.
- **D-05** — `onNewToken` enqueues a **unique one-time WorkManager job** (retry + backoff) to
  update `USERS/{uid}.fcmToken`. Also ensure/refresh token on login.
  Reuse existing WorkManager infra (SyncWorker pattern).
- **D-06** — `MessagingService` unified type router: order_status, new_order, new_review,
  device_login → each maps to its channel (D-03) and deep-link intent.
  Server writes the `notifications` doc for history completeness even when
  `onMessageReceived` is not called (background notification messages).

### Claude's Discretion

- Exact `notifications` Firestore doc path/shape, Room `NotificationEntity` columns +
  MIGRATION_9_10.
- Permission mechanism (Accompanist vs manual `ActivityResultContracts.RequestPermission`).
- Unread badge on history entry point.
- Notification de-dup/coalescing.

### Deferred Ideas (OUT OF SCOPE)

- Promotional/marketing notification content (Promotions channel is created but unused).
- Notification grouping/summary, quiet hours, per-type mute toggles.
- Rich notifications (images/actions/inline reply).
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| NOTF-01 | FCM push notification sent to customer on order status change | Already live (Phase 6); extend with notifications-doc write + channel migration (D-03/D-06) |
| NOTF-02 | FCM push notification sent to seller on new order placed | Already live (Phase 6); extend with notifications-doc write + channel migration |
| NOTF-03 | FCM push notification sent to seller on new product review | D-04 `onNewReview` Cloud Function; §Cloud Function section below |
| NOTF-04 | Tapping notification deep-links to relevant screen | Already live for order/seller_orders; extend with `new_review` → `SellerProductReviews`; §Deep-link section |
| NOTF-05 | Android 13+ POST_NOTIFICATIONS runtime permission with rationale | D-02; §Permission Flow section |
| NOTF-06 | Separate notification channels: Order Updates, Account, Promotions | D-03; §Channel section |
| NOTF-07 | FCM token refresh handled (update Firestore user document) | D-05; §WorkManager Token Job section |
| NOTF-08 | In-app notification history screen | D-01; §History Screen section |
</phase_requirements>

---

## Summary

Phase 8 extends a substantial Phase 6 notification foundation rather than building from scratch.
`MessagingService`, `OrderNotificationConstants`, the MainActivity deep-link consumer, the
`onOrderStatusChange` / `onNewSellerOrder` Cloud Functions, and the WorkManager + Koin
integration all exist and are production-tested. The four principal additions are:
(1) a third notification type `new_review` threaded through the Cloud Function, MessagingService,
constants, and MainActivity;
(2) notifications-doc writes added to all three push-dispatching Cloud Functions so a persistent
Firestore-backed history is maintained independent of FCM delivery;
(3) Room `notifications` table (MIGRATION_9_10, schema v10) + DAO + ViewModel + history screen;
(4) the Android 13 POST_NOTIFICATIONS permission rationale flow.

**Primary recommendation:** Thread the `new_review` type through the existing type-router pattern
first, then add the notifications-doc writes to all three Cloud Functions, then add the Room
table + history UI, then add the permission flow at app login. Each step is independently
verifiable and no step blocks another.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| FCM push dispatch | Firebase Cloud Functions | — | Server-authoritative; triggers from Firestore writes |
| Notifications Firestore doc write | Firebase Cloud Functions | — | Server-only write (same security pattern as /orders) |
| Notifications Firestore → Room sync | `:data` (NotificationRepositoryImpl) | — | Per-user callbackFlow listener on `notifications/{uid}/items` |
| Notification history read | `:data` Room DAO | Firestore snapshot | Room-first; Firestore is source of truth for history completeness |
| Type routing (onMessageReceived) | `:app` MessagingService | — | Android FCM service layer |
| Deep-link consumption | `:app` MainActivity LaunchedEffect | — | Existing currentBackStackEntry-gated pattern |
| FCM token refresh | `:data` WorkManager (FcmTokenWorker) | `:data` AuthRepositoryImpl | WorkManager for onNewToken; AuthRepositoryImpl already refreshes on login |
| Permission flow | `:app` composable (post-login home) | — | Android runtime permission UI lives in UI layer |
| Notification channels | `:app` WenuCommerce Application | `NotificationChannels` helper | Created once in onCreate, idempotent |
| History screen | `:app` NotificationHistoryScreen + ViewModel | `:data` NotificationDao | StateFlow UDF; Room Flow observed |

---

## Existing Infrastructure Inventory

### What Phase 6 Already Built (EXTEND ONLY)

| Component | File | Current State | Phase 8 Change |
|-----------|------|---------------|----------------|
| `MessagingService` | `app/.../notification/MessagingService.kt` | Routes `order_status` / `new_order` / `device_login`; creates `order_status_channel` in `onCreate`; `onNewToken` calls `firestoreRepository.updateFcmToken` fire-and-forget | Add `new_review` branch to router; move channel creation to central helper; replace `onNewToken` body with `FcmTokenWorker.enqueue()`; add Room persistence per received notification (D-06) |
| `OrderNotificationConstants` | `app/.../notification/OrderNotificationConstants.kt` | Defines `ORDER_STATUS_CHANNEL_ID`, `EXTRA_*`, `FCM_TYPE_*`, `NAV_TARGET_*` for order_status + new_order | Add `FCM_TYPE_NEW_REVIEW`, `NAV_TARGET_NEW_REVIEW`, `EXTRA_PRODUCT_ID`, `EXTRA_PRODUCT_TITLE`; add three channel-id constants (ORDER_UPDATES / ACCOUNT / PROMOTIONS) |
| `MainActivity` deep-link consumer | `app/.../MainActivity.kt` lines 113-147 | `LaunchedEffect(intentVersion, currentBackStackEntry)` handles `NAV_TARGET_ORDER_DETAIL` and `NAV_TARGET_SELLER_ORDERS`; `clearNotificationExtras()` strips intent | Add `new_review` branch: navigate to `SellerProductReviews(productId, productTitle)`; extend `clearNotificationExtras()` to strip `EXTRA_PRODUCT_ID` / `EXTRA_PRODUCT_TITLE` |
| `onOrderStatusChange` CF | `functions/src/index.ts` | Sends FCM to customer on status change | Add `notifications/{customerUid}/items/{id}` write after FCM send |
| `onNewSellerOrder` CF | `functions/src/index.ts` | Sends FCM to seller on new paid order | Add `notifications/{sellerUid}/items/{id}` write after FCM send |
| `SyncWorker` | `data/.../worker/SyncWorker.kt` | `CoroutineWorker`, CONNECTED constraint, `ExistingWorkPolicy.REPLACE`, EXPONENTIAL backoff | Template for new `FcmTokenWorker`; do NOT modify SyncWorker itself |
| `WorkManager` Koin setup | `app/.../di/DataModule.kt` | `workerModule` uses `worker { SyncWorker(...) }`; `WenuCommerce.kt` calls `workManagerFactory()` | Add `worker { FcmTokenWorker(...) }` to `workerModule` |
| `WenuCommerceDatabase` | `data/.../local/WenuCommerceDatabase.kt` | Schema v9; 10 entities; `MIGRATION_8_9` is most recent | Add `NotificationEntity`, `NotificationDao`; write `MIGRATION_9_10`; register entity and migration |
| `NotificationPreferences` | `data/.../repository/NotificationPreferences.kt` | Currently holds only `email_verification_hidden` DataStore key — NOT related to push notification settings | No conflict; will remain untouched this phase |
| `SyncBus` / `SyncEvent` (app package) | `app/.../notification/SyncBus.kt`, `SyncEvent.kt` | SharedFlow bus for order-status / new-order sync triggers | Optionally extend `SyncEvent` with `NewReview` if seller product-reviews screen should refresh automatically (nice-to-have; not required for NOTF-03) |
| `SellerProductReviews` route | `navigation/AppNavigationObjects.kt` | `data class SellerProductReviews(val productId: String, val productTitle: String)` | Already exists as deep-link destination (Phase 7); no change needed to the route object |

### FirestoreRepository Interface Gap

`FirestoreRepository.updateFcmToken(token: String): Result<Unit>` exists in the domain
interface and is implemented in `FirestoreRepositoryImpl` (fire-and-forget via Task listener).
Phase 8 stops calling it from `onNewToken` and instead enqueues `FcmTokenWorker`. The interface
method should remain for the "refresh on login" path already in `AuthRepositoryImpl`.

---

## Standard Stack

### Core (all already in `libs.versions.toml` — zero new entries needed)

| Library | Version in catalog | Purpose |
|---------|-------------------|---------|
| `work-runtime-ktx` | 2.11.1 | `FcmTokenWorker` one-time job |
| `koin-workmanager` | BOM-managed (koin-bom 4.0.1) | `WorkerFactory` for `FcmTokenWorker` Koin injection |
| `room-runtime` + `room-ktx` + `room-compiler` | 2.6.1 | `NotificationEntity` / `NotificationDao` / MIGRATION_9_10 |
| `firebase-messaging-ktx` | Firebase BOM 33.8.0 | `FirebaseMessagingService`, `FirebaseMessaging.getInstance().token` |
| `androidx-activity-compose` | 1.10.0 | `rememberLauncherForActivityResult` for permission request |

**No new dependencies are required for Phase 8.** All needed libraries are already declared in
`gradle/libs.versions.toml`. [VERIFIED: codebase grep of libs.versions.toml]

### Permission API — Manual `ActivityResultContracts` (no Accompanist)

Accompanist Permissions is NOT present in `libs.versions.toml` and is in maintenance mode.
[ASSUMED: based on training knowledge and absence from catalog]
Use the standard Jetpack `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`.
This is the approach consistent with `activityCompose 1.10.0` already on the classpath.

---

## Package Legitimacy Audit

No new packages are installed for Phase 8. All required libraries are already declared in
`gradle/libs.versions.toml` and are production-tested in prior phases.

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
[Firestore write: PRODUCTS/{id}/REVIEWS/{id} created]
         |
         v
[Cloud Function: onNewReview]
   - Look up product.sellerId → USERS doc → fcmToken
   - getMessaging().send({ type:"new_review", productId, productTitle })
   - db.collection("notifications").doc(sellerId)
          .collection("items").add({ type, title, body, productId, productTitle, read:false, createdAt })
         |
         v
[FCM → Android device]
         |
   ┌─────┴────────────────────────┐
   │ App foreground?               │
   │ YES → onMessageReceived       │  NO → FCM tray notification (data payload)
   │   → type router               │          → user taps → MainActivity cold start
   │   → showNewReviewNotification │                          with raw extras
   │   → Room persist              │
   └───────────────────────────────┘
         |
         v
[MainActivity LaunchedEffect]
   - reads EXTRA_NAV_TARGET / fcmRawType
   - "new_review" branch → navController.navigate(SellerProductReviews(productId, productTitle))

[Firestore callbackFlow: notifications/{uid}/items]
   - NotificationRepositoryImpl listens per-user
   - upserts NotificationEntity rows to Room
         |
         v
[NotificationHistoryScreen]
   - observes NotificationDao.observeByUser(uid) Flow
   - tapping a row → markAsRead → navigate to deep-link destination
```

### Recommended Project Structure for New Files

```
app/src/main/java/com/wenubey/wenucommerce/
├── notification/
│   ├── MessagingService.kt          (extend: new_review + channel refactor + WorkManager token)
│   ├── NotificationChannels.kt      (NEW: centralised channel creation helper)
│   ├── OrderNotificationConstants.kt (extend: new FCM_TYPE / NAV_TARGET / EXTRA constants)
│   └── SyncEvent.kt                 (optionally extend with NewReview)
├── notification_history/            (NEW feature package)
│   ├── NotificationHistoryScreen.kt
│   ├── NotificationHistoryViewModel.kt
│   ├── NotificationHistoryState.kt
│   └── NotificationHistoryAction.kt
├── navigation/
│   └── AppNavigationObjects.kt      (add NotificationHistory route)
└── di/
    └── DataModule.kt                (add FcmTokenWorker to workerModule, NotificationHistoryViewModel to viewModelModule)

data/src/main/java/com/wenubey/data/
├── local/
│   ├── WenuCommerceDatabase.kt      (extend: NotificationEntity + MIGRATION_9_10 + v10)
│   ├── entity/
│   │   └── NotificationEntity.kt   (NEW)
│   └── dao/
│       └── NotificationDao.kt      (NEW)
├── repository/
│   └── NotificationRepositoryImpl.kt (NEW: Firestore listener → Room; markAsRead)
└── worker/
    └── FcmTokenWorker.kt            (NEW: one-time WorkManager job for token update)

domain/src/main/java/com/wenubey/domain/
├── model/
│   └── Notification.kt             (NEW domain model — pure Kotlin)
└── repository/
    └── NotificationRepository.kt   (NEW interface)

functions/src/index.ts               (extend: onNewReview + notifications-doc writes to all 3 triggers)
firestore.rules                      (extend: notifications/{uid}/items/{id} owner-read, server-only write)
```

---

## Detailed Implementation Specifications

### 1. Firestore Notifications Doc Shape (D-01)

**Collection path:** `notifications/{uid}/items/{autoId}`

```typescript
// Written by Cloud Functions (onOrderStatusChange, onNewSellerOrder, onNewReview)
{
  id: string,               // same as the Firestore doc id (for Room primary key)
  type: string,             // "order_status" | "new_order" | "new_review" | "device_login"
  title: string,            // notification title (mirrors push title)
  body: string,             // notification body (mirrors push body)
  orderId: string,          // populated for order_status
  sellerOrderId: string,    // populated for order_status + new_order
  productId: string,        // populated for new_review
  productTitle: string,     // populated for new_review
  read: boolean,            // false on create; updated to true by client (Firestore client SDK)
  createdAt: Timestamp,     // FieldValue.serverTimestamp()
}
```

All fields not relevant to a given type can be empty strings. This avoids nullable
nullability complexity in both TypeScript and the Room entity.

### 2. Room NotificationEntity + MIGRATION_9_10

```kotlin
// data/local/entity/NotificationEntity.kt
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,             // owner (for per-user query)
    val type: String,               // "order_status" | "new_order" | "new_review" | "device_login"
    val title: String,
    val body: String,
    val orderId: String = "",
    val sellerOrderId: String = "",
    val productId: String = "",
    val productTitle: String = "",
    val isRead: Boolean = false,
    val createdAt: String = "",     // epoch-millis string, consistent with other entities
)
```

**MIGRATION_9_10 SQL:**

```sql
CREATE TABLE IF NOT EXISTS `notifications` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `userId` TEXT NOT NULL DEFAULT '',
    `type` TEXT NOT NULL DEFAULT '',
    `title` TEXT NOT NULL DEFAULT '',
    `body` TEXT NOT NULL DEFAULT '',
    `orderId` TEXT NOT NULL DEFAULT '',
    `sellerOrderId` TEXT NOT NULL DEFAULT '',
    `productId` TEXT NOT NULL DEFAULT '',
    `productTitle` TEXT NOT NULL DEFAULT '',
    `isRead` INTEGER NOT NULL DEFAULT 0,
    `createdAt` TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS `index_notifications_userId` ON `notifications` (`userId`);
CREATE INDEX IF NOT EXISTS `index_notifications_createdAt` ON `notifications` (`createdAt`);
```

After writing MIGRATION_9_10 and registering `NotificationEntity`, run
`./gradlew :data:generateReleaseRoomSchemas` to produce `data/schemas/.../10.json`.

### 3. NotificationDao

```kotlin
// data/local/dao/NotificationDao.kt
@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: String): Flow<List<NotificationEntity>>

    @Upsert
    suspend fun upsertAll(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun observeUnreadCount(userId: String): Flow<Int>
}
```

### 4. Domain Notification Model

```kotlin
// domain/model/Notification.kt  (pure Kotlin — no Android/Firebase deps)
data class Notification(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val orderId: String = "",
    val sellerOrderId: String = "",
    val productId: String = "",
    val productTitle: String = "",
    val isRead: Boolean = false,
    val createdAt: String = "",
)
```

### 5. NotificationRepository Interface

```kotlin
// domain/repository/NotificationRepository.kt
interface NotificationRepository {
    fun observeNotifications(userId: String): Flow<List<Notification>>
    fun observeUnreadCount(userId: String): Flow<Int>
    suspend fun markAsRead(notificationId: String): Result<Unit>
    suspend fun syncNotifications(userId: String): Result<Unit>
}
```

`syncNotifications` is a one-shot pull for the initial load. The ongoing listener is started
by `SyncManager` or a dedicated `NotificationSyncManager` on login.

### 6. Firestore Sync Listener Pattern (mirrors `OrderRepositoryImpl`)

```kotlin
// NotificationRepositoryImpl — per-user callbackFlow listener
private fun startNotificationListener(userId: String) {
    listenerJob?.cancel()
    listenerJob = scope.launch {
        callbackFlow {
            val listener = firestore
                .collection("notifications")
                .document(userId)
                .collection("items")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) { Timber.e(error, "Notification sync error"); return@addSnapshotListener }
                    val entities = snapshot?.documents?.mapNotNull { it.toNotificationEntity(userId) } ?: emptyList()
                    trySend(entities)
                }
            awaitClose { listener.remove() }
        }.collect { notificationDao.upsertAll(it) }
    }
}
```

**Lifecycle:** Start listener in `AuthRepositoryImpl.authStateListener` when `currentUser != null`
(same pattern as `startUserListener`). Cancel on sign-out. Alternatively, route through
`SyncManager` with `SupervisorJob` sibling coroutine.

### 7. Cloud Function: `onNewReview` (D-04)

```typescript
// functions/src/index.ts — add after onNewSellerOrder
export const onNewReview = onDocumentCreated(
  "PRODUCTS/{productId}/REVIEWS/{reviewId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) return;
    const productId = event.params.productId;
    const db = admin.firestore();

    // Look up the product to get sellerId
    const productSnap = await db.collection("PRODUCTS").doc(productId).get();
    const sellerId = productSnap.data()?.sellerId as string | undefined;
    const productTitle = productSnap.data()?.title as string | undefined ?? productId;
    if (!sellerId) { console.log("[new_review] no sellerId on product — skipping"); return; }

    // Look up seller FCM token
    const userSnap = await db.collection("USERS").doc(sellerId).get();
    const fcmToken = userSnap.data()?.fcmToken as string | undefined;

    const title = "New review";
    const body = `Your product "${productTitle}" received a new review.`;

    // Persist to notification history (always — even if FCM fails)
    const notifRef = db.collection("notifications").doc(sellerId).collection("items").doc();
    await notifRef.set({
      id: notifRef.id,
      type: "new_review",
      title,
      body,
      orderId: "",
      sellerOrderId: "",
      productId,
      productTitle,
      read: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    if (!fcmToken) { console.log("[new_review] no fcmToken — history written, push skipped"); return; }

    try {
      const messageId = await getMessaging().send({
        token: fcmToken,
        notification: { title, body },
        data: {
          type: "new_review",
          productId,
          productTitle,
        },
        android: {
          priority: "high",
          notification: { channelId: "order_updates_channel" },
        },
      });
      console.log("[new_review] send SUCCESS", { messageId });
    } catch (err) {
      console.error("[new_review] FCM dispatch FAILED", err);
    }
  },
);
```

### 8. Notifications-Doc Writes Added to Existing Functions

In `onOrderStatusChange` — after the existing FCM `getMessaging().send(...)` succeeds,
add inside the try-catch (swallowed on failure same as FCM):

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

In `onNewSellerOrder` — similarly, after FCM send:

```typescript
// Persist to seller notification history
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

### 9. Firestore Rules Extension

```javascript
// firestore.rules — add inside the top-level match block
match /notifications/{userId}/items/{notifId} {
  // Owner can read their own notifications
  allow read: if request.auth != null && request.auth.uid == userId;
  // Owner can mark as read (update read field only)
  allow update: if request.auth != null
                && request.auth.uid == userId
                && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['read']);
  // All writes (create/delete) are server-only (Admin SDK bypasses rules)
  allow create, delete: if false;
}
```

**Pattern:** Matches the `/orders/{orderId}` owner-read + server-only-write pattern.
The `update` allow is needed so the client can mark-as-read from the Android app.

### 10. Centralised Notification Channels (D-03)

Create `app/.../notification/NotificationChannels.kt`:

```kotlin
object NotificationChannels {
    const val ORDER_UPDATES_CHANNEL_ID = "order_updates_channel"
    const val ACCOUNT_CHANNEL_ID = "account_channel"
    const val PROMOTIONS_CHANNEL_ID = "promotions_channel"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(listOf(
            NotificationChannel(ORDER_UPDATES_CHANNEL_ID, "Order Updates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Order status changes, new orders, and new product reviews"
            },
            NotificationChannel(ACCOUNT_CHANNEL_ID, "Account", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Device login and security alerts"
            },
            NotificationChannel(PROMOTIONS_CHANNEL_ID, "Promotions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Promotional offers and discounts"
            },
        ))
    }
}
```

Call `NotificationChannels.createAll(this)` in `WenuCommerce.onCreate()` BEFORE
`startKoin` (channels must exist before first notification can be posted).

Remove `ensureOrderStatusChannel()` from `MessagingService.onCreate()` and the inline
`device_login_channel` creation from `showNotification()`.

**Channel migration:** The existing `order_status_channel` used by Phase 6 on-device
installations will persist in system settings forever, but new notifications that reference
`order_updates_channel` will route to the new channel. No migration of existing channel is
possible — Android system settings accumulate channels across installs. This is fine and
matches the requirement (D-03 says create three channels; the old one becomes orphaned).

Update `onOrderStatusChange` Cloud Function and `onNewSellerOrder` to use
`channelId: "order_updates_channel"` instead of `"order_status_channel"`. Update
`MessagingService.showOrderStatusNotification` and `showNewOrderNotification` to use
`NotificationChannels.ORDER_UPDATES_CHANNEL_ID`.

### 11. MessagingService Type Router Refactor (D-06)

```kotlin
override fun onMessageReceived(message: RemoteMessage) {
    val data = message.data
    when (data[FCM_DATA_KEY_TYPE]) {
        FCM_TYPE_ORDER_STATUS -> {
            serviceScope.launch { emitSyncIfOrderStatus(syncBus, data) }
            showOrderStatusNotification(message)
            serviceScope.launch { persistNotification(data, recipientUid = data[FCM_DATA_KEY_ORDER_ID]) }
        }
        FCM_TYPE_NEW_ORDER -> {
            serviceScope.launch { emitSyncIfNewOrder(syncBus, data) }
            showNewOrderNotification(message)
            // history written server-side; local persist optional
        }
        FCM_TYPE_NEW_REVIEW -> {
            showNewReviewNotification(message)
            // history written server-side
        }
        else -> {
            // device_login path (legacy notification message, not data message)
            message.notification?.let { showAccountNotification(it.title ?: "Security Alert", it.body ?: "Login detected") }
        }
    }
}
```

The `persistNotification` helper writes a `NotificationEntity` to Room via the injected
`NotificationDao` so foreground arrivals are immediately visible in history without waiting
for the Firestore listener snapshot. This is a best-effort path (identical to the server
write); the Firestore listener upsert will overwrite it with the authoritative server doc.

### 12. OrderNotificationConstants Extensions

```kotlin
// Add to OrderNotificationConstants.kt
const val FCM_TYPE_NEW_REVIEW = "new_review"
const val FCM_DATA_KEY_PRODUCT_ID = "productId"
const val FCM_DATA_KEY_PRODUCT_TITLE = "productTitle"
const val NAV_TARGET_NEW_REVIEW = "new_review"
const val EXTRA_PRODUCT_ID = "wenucommerce.product_id"
const val EXTRA_PRODUCT_TITLE = "wenucommerce.product_title"

// Replace ORDER_STATUS_CHANNEL_ID references with NotificationChannels constants
// (keep ORDER_STATUS_CHANNEL_ID for backward compat / existing tests — alias it)
```

### 13. MainActivity Deep-Link Extension (D-04)

Add to the `when` block in the deep-link `LaunchedEffect`:

```kotlin
val navTarget = intent.getStringExtra(EXTRA_NAV_TARGET)
val fcmRawType = intent.getStringExtra("type")
val productId = intent.getStringExtra(EXTRA_PRODUCT_ID)
    ?: intent.getStringExtra("productId")
val productTitle = intent.getStringExtra(EXTRA_PRODUCT_TITLE)
    ?: intent.getStringExtra("productTitle")

val isNewReview = navTarget == NAV_TARGET_NEW_REVIEW || fcmRawType == FCM_TYPE_NEW_REVIEW

when {
    isNewReview && !productId.isNullOrBlank() -> {
        Timber.d("FCM deep-link → seller product reviews %s", productId)
        navController.navigate(SellerProductReviews(productId, productTitle ?: ""))
        clearNotificationExtras()
    }
    // ... existing isSellerNewOrder and orderId branches ...
}
```

Extend `clearNotificationExtras()` to remove `EXTRA_PRODUCT_ID`, `EXTRA_PRODUCT_TITLE`,
`"productId"`, `"productTitle"`.

### 14. FCM Token WorkManager Job (D-05)

```kotlin
// data/worker/FcmTokenWorker.kt
class FcmTokenWorker(
    appContext: Context,
    params: WorkerParameters,
    private val firestoreRepository: FirestoreRepository,
    private val firebaseMessaging: FirebaseMessaging,
    private val dispatcherProvider: DispatcherProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = dispatcherProvider.io().run {
        return try {
            val token = firebaseMessaging.token.await()
            firestoreRepository.updateFcmToken(token).getOrThrow()
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "FcmTokenWorker: token update failed attempt $runAttemptCount")
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        const val UNIQUE_WORK_NAME = "fcm_token_refresh"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<FcmTokenWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
```

**`FirestoreRepository.updateFcmToken` must become `suspend` (or return `Deferred<Result<Unit>>`)**
for `getOrThrow()` to be callable from a coroutine. Currently it is fire-and-forget. Change
signature to `suspend fun updateFcmToken(token: String): Result<Unit>` in both the domain
interface and `FirestoreRepositoryImpl`. The existing `AuthRepositoryImpl` call site also becomes
`firestoreRepository.updateFcmToken(token)` (no change except now `suspend` — already in a coroutine).

Replace `MessagingService.onNewToken`:
```kotlin
override fun onNewToken(token: String) {
    super.onNewToken(token)
    FcmTokenWorker.enqueue(this)  // token is not passed; worker fetches from FirebaseMessaging
}
```

Add to `workerModule` in `DataModule.kt`:
```kotlin
worker { FcmTokenWorker(get(), get(), get(), get(), get()) }
```

### 15. POST_NOTIFICATIONS Permission Flow (D-02)

```kotlin
// In the post-login home composable (CustomerHomeScreen or the authenticated root)
val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { isGranted ->
    // No-op on denial — do NOT re-prompt; profile settings has the affordance
}

var showRationaleDialog by rememberSaveable { mutableStateOf(false) }
val hasRequestedPermission = remember { mutableStateOf(false) }  // or DataStore key

LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val status = ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS)
        if (status != PERMISSION_GRANTED && !hasRequestedPermission.value) {
            showRationaleDialog = true
        }
    }
}

if (showRationaleDialog) {
    AlertDialog(
        onDismissRequest = { showRationaleDialog = false },
        title = { Text("Stay in the loop") },
        text = { Text("Enable notifications to get order updates and seller alerts.") },
        confirmButton = {
            TextButton(onClick = {
                showRationaleDialog = false
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("Enable") }
        },
        dismissButton = {
            TextButton(onClick = { showRationaleDialog = false }) { Text("Not now") }
        }
    )
}
```

**"Enable notifications" affordance in Profile/Settings:**
```kotlin
// Opens system app-notification settings for this app
val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
context.startActivity(intent)
```

No new composable needed — add as a row in the existing Profile/Settings screen.

**Manifest:** Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
to `AndroidManifest.xml`. This permission is a no-op below API 33 but the declaration is
required for the runtime request to work on API 33+. [VERIFIED: developer.android.com/develop/ui/views/notifications/notification-permission]

**DataStore gate:** To prevent re-prompting on every cold start after denial, store a
`booleanPreferencesKey("notification_permission_requested")` in DataStore (reuse the existing
`NotificationPreferences` DataStore or the app's preferences DataStore). Set to `true` after
the launcher fires (regardless of outcome). Check this key before setting `showRationaleDialog`.

### 16. Notification History Screen (NOTF-08)

**Route:**
```kotlin
// AppNavigationObjects.kt
@Serializable
data object NotificationHistory
```

**ViewModel:**
```kotlin
class NotificationHistoryViewModel(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationHistoryState())
    val uiState: StateFlow<NotificationHistoryState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.filterNotNull().collect { user ->
                notificationRepository.observeNotifications(user.uuid ?: return@collect)
                    .collect { notifications ->
                        _uiState.update { it.copy(notifications = notifications, isLoading = false) }
                    }
            }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }
}
```

**UiState:**
```kotlin
data class NotificationHistoryState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
```

The history screen entry point (button/icon on the authenticated home or bottom bar)
can show an unread badge count — this is Claude's discretion (D-01b). Use
`notificationRepository.observeUnreadCount(uid)` + `BadgedBox` if implemented.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| FCM token delivery retry | Custom retry loop in `onNewToken` | `WorkManager` with `ExistingWorkPolicy.REPLACE` + exponential backoff | WorkManager survives process death; handles connectivity wait |
| Notification channel creation | Per-notification channel creation | `NotificationChannels.createAll()` in `Application.onCreate()` | Android deduplicates by channel ID; creating in each notification path risks skipping due to early return paths |
| Runtime permission check | Manual `PackageManager.checkPermission` | `ContextCompat.checkSelfPermission` | API-level normalization is built in |
| Notification history source-of-truth | FCM delivery tracking | Firestore `notifications/{uid}/items` written by Cloud Functions | FCM delivery is not guaranteed; Firestore persists history even on app kill, device off, token change |
| Firestore ordered paging | Custom offset pagination | Firestore `orderBy("createdAt", DESCENDING)` + `.limit(50)` | Cursor-based; no separate count query needed |

---

## Common Pitfalls

### Pitfall 1: `new_review` deep-link while app is backgrounded (raw FCM data extras)

**What goes wrong:** When the app is killed and a `new_review` notification arrives, FCM
delivers it as a data-only message. The tray notification is built by MessagingService
(foreground received before kill) or by FCM system tray (if system-notification path). On
tap, MainActivity receives raw intent extras keyed by the FCM `data` field names (`"type"`,
`"productId"`, `"productTitle"`) — NOT the namespaced `EXTRA_*` constants.

**How to avoid:** The deep-link `LaunchedEffect` already handles both paths for order_status
(see `fcmRawOrderId` + `fcmRawType` fallback). Replicate the same dual-read for `productId`
/ `productTitle`: first check `EXTRA_PRODUCT_ID`, then fall back to raw `"productId"` key.

### Pitfall 2: `order_status_channel` vs `order_updates_channel` mismatch

**What goes wrong:** The Cloud Function `onOrderStatusChange` currently sends
`android.notification.channelId: "order_status_channel"`. If the Android client declares
only `order_updates_channel`, background notification messages will route to the old
channel (which still exists on-device from Phase 6). They will display but not in the
"Order Updates" channel.

**How to avoid:** Update `onOrderStatusChange` and `onNewSellerOrder` to send
`channelId: "order_updates_channel"` at the same time as creating the new channels on the
client. The two changes should ship atomically (deploy Functions + release app together).

### Pitfall 3: `updateFcmToken` must become `suspend` before `FcmTokenWorker`

**What goes wrong:** `FirestoreRepository.updateFcmToken` is currently a non-suspend function
that fires a Firestore Task listener and returns immediately. `FcmTokenWorker` needs to `await()`
the Firestore write to know whether to return `Result.success()` or `Result.retry()`.

**How to avoid:** Change domain interface + impl + all call sites to `suspend` in plan 08-02.
The existing call in `AuthRepositoryImpl` is already inside a coroutine scope — no structural
change needed there.

### Pitfall 4: Firestore listener started before login

**What goes wrong:** If `NotificationRepositoryImpl.startListener()` is called with a null/empty
uid, the listener queries the entire `notifications` collection without a `userId` filter,
which Firestore rules deny (server returns PERMISSION_DENIED → listener logs error repeatedly).

**How to avoid:** Gate listener start on `auth.currentUser != null`. Start in the
`authStateListener` non-null branch alongside `startUserListener`. Cancel on sign-out.

### Pitfall 5: Duplicate `notifications` Firestore doc on foreground FCM

**What goes wrong:** `onMessageReceived` (foreground) writes a Room row directly. The
Firestore listener then fires with the server doc (written by the Cloud Function), attempting
an upsert on the same `id`. Since the Room `id` is the Firestore doc's auto-id and the
foreground path receives the same data payload, the room write uses whatever id is embedded
in the FCM data. If the FCM data payload does NOT include the Firestore notification doc id,
the direct Room write and the listener upsert create two rows.

**How to avoid:** The Cloud Function should include the Firestore notification doc id in the
FCM `data` payload (`notifId: notifRef.id`). The foreground `persistNotification` helper in
`MessagingService` uses this `notifId` as the Room entity primary key. The Firestore listener
upsert then hits the same PK and overwrites cleanly. If the `notifId` is absent (e.g. legacy
background path), the direct Room write is skipped and the listener handles it.

### Pitfall 6: `POST_NOTIFICATIONS` declared but no `maxSdkVersion`

**What goes wrong:** On API < 33 the runtime permission check `ContextCompat.checkSelfPermission`
returns `PERMISSION_DENIED` for `POST_NOTIFICATIONS` even though no runtime permission is
actually required below API 33. Without an API-level guard, the rationale dialog pops on
every API 31/32 device, which is wrong per spec.

**How to avoid:** Wrap all permission-request logic in
`if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)`. This is required by D-02.

### Pitfall 7: `FcmTokenWorker` unique name collision with `SyncWorker`

**What goes wrong:** If `FcmTokenWorker` accidentally reuses `SyncWorker.UNIQUE_WORK_NAME`
(`"sync_pending_operations"`), the two workers interfere: enqueueing one replaces the other.

**How to avoid:** Use a distinct `UNIQUE_WORK_NAME = "fcm_token_refresh"` as specified above.

---

## Code Examples

### Extending `emitSyncIfNewOrder` pattern to `emitSyncIfNewReview`

```kotlin
// MessagingService.kt companion object
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
```

### Room schema version bump pattern (from MIGRATION_8_9 → MIGRATION_9_10)

```kotlin
// WenuCommerceDatabase.kt
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `notifications` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `userId` TEXT NOT NULL DEFAULT '',
                `type` TEXT NOT NULL DEFAULT '',
                `title` TEXT NOT NULL DEFAULT '',
                `body` TEXT NOT NULL DEFAULT '',
                `orderId` TEXT NOT NULL DEFAULT '',
                `sellerOrderId` TEXT NOT NULL DEFAULT '',
                `productId` TEXT NOT NULL DEFAULT '',
                `productTitle` TEXT NOT NULL DEFAULT '',
                `isRead` INTEGER NOT NULL DEFAULT 0,
                `createdAt` TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_userId` ON `notifications` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_createdAt` ON `notifications` (`createdAt`)")
    }
}
```

Then in `databaseModule`:
```kotlin
.addMigrations(
    WenuCommerceDatabase.MIGRATION_1_2,
    // ...
    WenuCommerceDatabase.MIGRATION_8_9,
    WenuCommerceDatabase.MIGRATION_9_10,   // add
)
```

And bump `@Database(version = 10, ...)` and add `NotificationEntity::class` and
`notificationDao()`.

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `Manifest.permission.POST_NOTIFICATIONS` declared — no rationale | Contextual rationale dialog before system dialog | Required on API 33+ for good UX |
| Channel created inline in notification-posting method | Channels created centrally in `Application.onCreate()` | Guaranteed to exist before first notification |
| `onNewToken` fire-and-forget Firestore update | WorkManager unique one-time job with retry | Survives process death, app kill, offline |
| Push only (no persistent history) | Firestore `notifications/{uid}/items` written by CF + Room mirror | History complete regardless of FCM delivery |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Accompanist Permissions library is NOT present and NOT needed; standard `ActivityResultContracts.RequestPermission()` is sufficient | Permission Flow | Low — accompanist is in maintenance mode and `activityCompose 1.10.0` already provides the contracts API |
| A2 | `onNewReview` fires reliably for both new reviews AND edits (since `submitReview` uses `tx.set()`, which triggers `onDocumentCreated` only on first create, not on edit) | Cloud Function | The requirement is "new review notification" — edits should not re-notify; `onDocumentCreated` is the correct trigger |
| A3 | No Firestore composite index is needed for `notifications/{uid}/items` ordered by `createdAt` because `orderBy` on a subcollection with a single field is covered by the default index | Firestore sync | Low risk — Firestore auto-indexes all single fields; composite index only needed for multi-field orderBy+where combinations |
| A4 | `NotificationPreferences` DataStore is a safe place to store the `notification_permission_requested` gate key (no collision with existing `email_verification_hidden` key) | Permission Flow | No risk — keys are strings, no collision |

---

## Open Questions

1. **Notification sync listener lifecycle owner**
   - What we know: `AuthRepositoryImpl` already hosts `startUserListener()` / `stopUserListener()` keyed to auth state.
   - What's unclear: Should `NotificationRepositoryImpl` manage its own listener internally (simpler, but another class with auth-state awareness), or should `SyncManager` host it as a third sibling coroutine (consistent with Product/Category pattern)?
   - Recommendation: Host in `NotificationRepositoryImpl` with `startListener(userId)` / `stopListener()` called from `AuthRepositoryImpl.authStateListener` — same pattern as `startUserListener`. Keep `SyncManager` focused on Product/Category.

2. **History screen navigation entry point**
   - What we know: There is no dedicated "Notifications" tab. The roadmap plan outline mentions entry in the history screen.
   - What's unclear: Where does the user navigate to `NotificationHistory` from? (A bell icon on the top bar of home screens, or a row in the profile screen?)
   - Recommendation: Add a bell `IconButton` in the top app bar of `CustomerHomeScreen` and/or `SellerTab`. This is standard e-commerce UX.

3. **`markAsRead` — client Firestore write vs Room-only**
   - What we know: Firestore rules allow the owner to update the `read` field. Room upsert will overwrite on next sync.
   - What's unclear: Should `markAsRead` write to Firestore (so read state persists across devices/reinstalls) or only to Room?
   - Recommendation: Write to both — Room immediately (optimistic), Firestore in background (durable). If the Firestore write fails it's low-stakes (next app open re-fetches from Firestore and shows as unread again, which is acceptable).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| WorkManager | FcmTokenWorker | Yes | 2.11.1 (catalog) | — |
| Firebase Messaging SDK | MessagingService, FirebaseMessaging.token | Yes | Firebase BOM 33.8.0 | — |
| Room | NotificationEntity/DAO | Yes | 2.6.1 (catalog) | — |
| Koin WorkManager | FcmTokenWorker DI | Yes | koin-bom 4.0.1 | — |
| Activity Compose | Permission launcher | Yes | 1.10.0 (catalog) | — |
| Firebase Admin SDK (CF) | `notifications` doc writes | Yes | functions runtime | — |

All environment dependencies are already satisfied. No new installs required.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit4 + Robolectric 4.14.1 + Turbine 1.1.0 + MockK 1.12.4 |
| Config file | `app/src/test/` (standard Android unit test source set) |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*Notification*"` |
| Full suite command | `./gradlew testDebugUnitTest` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| NOTF-01 | notifications-doc written by `onOrderStatusChange` | Jest unit (CF) | `cd functions && npm test -- --testNamePattern "onOrderStatusChange"` | ❌ Wave 0 |
| NOTF-02 | notifications-doc written by `onNewSellerOrder` | Jest unit (CF) | `cd functions && npm test -- --testNamePattern "onNewSellerOrder"` | ❌ Wave 0 |
| NOTF-03 | `onNewReview` sends FCM + writes notifications-doc | Jest unit (CF) | `cd functions && npm test -- --testNamePattern "onNewReview"` | ❌ Wave 0 |
| NOTF-04 | `buildNewReviewNotificationIntent` returns Intent with correct extras | Robolectric unit | `./gradlew :app:testDebugUnitTest --tests "*MessagingService*"` | Extend existing `MessagingServiceSyncBusTest.kt` |
| NOTF-04 | MainActivity deep-link `new_review` branch navigates to `SellerProductReviews` | Unit (pure logic) | `./gradlew :app:testDebugUnitTest --tests "*DeepLink*"` | ❌ Wave 0 |
| NOTF-05 | Permission gate skips on API < 33 | Unit | `./gradlew :app:testDebugUnitTest --tests "*Permission*"` | ❌ Wave 0 |
| NOTF-06 | `NotificationChannels.createAll` creates 3 channels | Robolectric unit | `./gradlew :app:testDebugUnitTest --tests "*NotificationChannels*"` | ❌ Wave 0 |
| NOTF-07 | `FcmTokenWorker` updates token on success, retries on failure | Unit (WorkManager TestDriver) | `./gradlew :data:testDebugUnitTest --tests "*FcmTokenWorker*"` | ❌ Wave 0 |
| NOTF-08 | `NotificationHistoryViewModel` emits notifications list from Room | Unit (Turbine) | `./gradlew :app:testDebugUnitTest --tests "*NotificationHistoryViewModel*"` | ❌ Wave 0 |

Existing `MessagingServiceSyncBusTest.kt` covers `emitSyncIfOrderStatus` / `buildOrderStatusNotificationIntent` / `buildNewOrderNotificationIntent` / `emitSyncIfNewOrder`. Extend it with `new_review` equivalents.

### Sampling Rate

- **Per task commit:** `./gradlew :app:testDebugUnitTest --tests "*Notification*"`
- **Per wave merge:** `./gradlew testDebugUnitTest` (all 3 modules)
- **Phase gate:** Full suite green + `:app:assembleDebug` green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `app/src/test/.../MessagingServiceSyncBusTest.kt` — extend with `new_review` intent + SyncBus tests
- [ ] `app/src/test/.../NotificationHistoryViewModelTest.kt` — fake `NotificationRepository`, Turbine
- [ ] `app/src/test/.../NotificationChannelsTest.kt` — Robolectric channel creation
- [ ] `data/src/test/.../FcmTokenWorkerTest.kt` — WorkManager `TestListenableWorkerBuilder` pattern
- [ ] `functions/src/__tests__/index.test.ts` — Jest tests for `onNewReview` + notifications-doc writes in existing triggers

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | N/A (notifications are per-authenticated user) |
| V3 Session Management | No | N/A |
| V4 Access Control | Yes | Firestore rules: `notifications/{uid}` owner-read only; Cloud Function (Admin SDK) for write |
| V5 Input Validation | Yes | Cloud Functions validate `productId` / `sellerId` presence before FCM send |
| V6 Cryptography | No | FCM tokens treated as opaque strings; no crypto needed |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Client writes fake notification history | Tampering | Firestore rule: `allow create: if false` — server-only |
| Client marks other user's notifications as read | Elevation of Privilege | Rule checks `request.auth.uid == userId` on `notifications/{userId}` |
| Seller spoofs `new_review` FCM to another seller | Spoofing | Cloud Function is the only writer; clients cannot call FCM directly |
| Flood `notifications` subcollection | DoS | Cloud Function fires only on real Firestore document create (submitReview); not a callable endpoint |

---

## Sources

### Primary (HIGH confidence — direct codebase inspection)

- `app/.../notification/MessagingService.kt` — full type router, channel creation, intent builders, SyncBus emit helpers
- `app/.../notification/OrderNotificationConstants.kt` — existing FCM keys and channel ID
- `app/.../MainActivity.kt` — deep-link LaunchedEffect pattern, `clearNotificationExtras()`
- `functions/src/index.ts` — `onOrderStatusChange`, `onNewSellerOrder`, `submitReview` implementations
- `firestore.rules` — `/orders` / `/sellerOrders` security rule pattern to replicate
- `data/.../local/WenuCommerceDatabase.kt` — schema v9, all 10 entities, MIGRATION_8_9 template
- `data/.../worker/SyncWorker.kt` — WorkManager + Koin pattern for `FcmTokenWorker`
- `data/.../repository/AuthRepositoryImpl.kt` lines 94-107 — FCM token refresh on login (fire-and-forget)
- `data/.../repository/NotificationPreferences.kt` — DataStore usage, no conflict
- `app/.../di/DataModule.kt` — `workerModule`, `notificationModule`, `databaseModule`
- `app/.../WenuCommerce.kt` — `workManagerFactory()` call, Application lifecycle
- `gradle/libs.versions.toml` — all existing library versions verified
- `app/src/main/AndroidManifest.xml` — FCM service declaration, WorkManager disabling

### Secondary (MEDIUM confidence — Android/Firebase official docs)

- [CITED: developer.android.com/develop/ui/views/notifications/notification-permission] — `POST_NOTIFICATIONS`, API 33 guard, `Settings.ACTION_APP_NOTIFICATION_SETTINGS`
- [CITED: developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration] — `WorkerFactory` + Koin integration
- [CITED: firebase.google.com/docs/cloud-messaging/android/receive] — `onNewToken` lifecycle, data vs notification message distinction

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries are already in the catalog, verified by file read
- Architecture: HIGH — all patterns copied verbatim from Phase 6 codebase
- Cloud Function design: HIGH — `onNewReview` directly mirrors `onNewSellerOrder`
- Room migration: HIGH — copied MIGRATION_8_9 column pattern
- Permission flow: MEDIUM — API verified against official docs; exact composable placement is Claude's discretion
- Pitfalls: HIGH — derived from existing codebase analysis and known Android notification caveats

**Research date:** 2026-07-17
**Valid until:** 2026-08-17 (stable Android/Firebase ecosystem; WorkManager/Room APIs don't change frequently)
