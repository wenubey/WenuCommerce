---
phase: 08-notifications
verified: 2026-07-17T17:00:00Z
status: human_needed
score: 9/10 must-haves verified
overrides_applied: 0
human_verification:
  - test: "End-to-end push delivery — customer order-status notification"
    expected: "Customer receives a system push notification on 'Order Updates' channel when a seller advances their order status; notification appears in Notification History with correct icon/title/timestamp; tapping it deep-links to CustomerOrderDetail and marks the badge read"
    why_human: "Requires deployed Cloud Functions (onOrderStatusChange — 08-02 deploy PENDING due to transient GCP error) + real FCM delivery on a physical device; cannot be verified from source or JVM tests"
  - test: "End-to-end push delivery — seller new-order notification"
    expected: "Seller receives a push on 'Order Updates' channel when a customer places an order; notification doc appears in seller Notification History"
    why_human: "Requires deployed onNewSellerOrder function (deploy PENDING) + real FCM; device-only"
  - test: "End-to-end push delivery — seller new-review notification"
    expected: "Seller receives a push when a customer posts a review on their product; notification appears in seller Notification History; tapping it opens SellerProductReviews for that product"
    why_human: "Requires deployed onNewReview function (NEW trigger, deploy PENDING) + real FCM; device-only"
  - test: "POST_NOTIFICATIONS system permission dialog on Android 13+"
    expected: "On an API 33+ device, logging in shows the 'Stay in the loop' rationale dialog once; tapping 'Enable' surfaces the OS POST_NOTIFICATIONS dialog; tapping 'Not now' suppresses re-prompt on cold restart; Profile row updates to 'Tap to enable notifications' when denied and 'Notifications are on' when granted"
    why_human: "OS dialog is not automatable; requires physical API 33+ device; rationale-once gate (DataStore) can only be confirmed by live UX"
  - test: "Three notification channels visible in Android system settings"
    expected: "System Settings > App > Notifications shows exactly 'Order Updates' (HIGH), 'Account' (DEFAULT), and 'Promotions' (LOW) channels — no orphaned 'order_status_channel' or 'device_login_channel'"
    why_human: "Notification channel creation is an OS-side operation; requires on-device inspection of system settings"
  - test: "Room 9→10 migration on-device"
    expected: "./gradlew :data:connectedDebugAndroidTest with a 9→10 MigrationTestHelper case passes; existing data survives the migration"
    why_human: "MigrationTestHelper requires an Android device/emulator (connected instrumented test); JVM unit tests cannot exercise device SQLite"
  - test: "Notification tray-tap deep-link from system tray"
    expected: "Tapping a push notification in the Android system tray (cold-start and warm/back-stack) opens the correct screen: CustomerOrderDetail for order_status, SellerProductReviews for new_review"
    why_human: "Tray-tap lifecycle (singleTop + onNewIntent) requires physical execution; source/unit tests cover intent-builder shape but not OS tap dispatch"
  - test: "Unread badge live update and decrement"
    expected: "After receiving a push (or via Notification History row tap), the Notifications tab badge increments; after marking a notification read, badge decrements; badge is capped at '9+'"
    why_human: "Badge count is driven by Room observeUnreadCount; visible behavior (increment/decrement) requires live Firestore sync delivering a real notification doc — device-only"
  - test: "Functions deploy retry"
    expected: "firebase deploy --only functions:onNewReview,functions:onOrderStatusChange,functions:onNewSellerOrder exits 0 and all three functions appear in Firebase Console"
    why_human: "Previous 4 deploy attempts failed on a transient Firebase-side 'Internal error' (not a code bug — jest 87/87 green); requires GCP recovery and a successful CLI deploy run"
---

# Phase 8: Notifications — Verification Report

**Phase Goal:** Customers and sellers receive timely push notifications for all relevant events and can view notification history in-app; Android 13+ permission is handled gracefully
**Verified:** 2026-07-17T17:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Room database at schema version 10 with notifications table + exported 10.json | VERIFIED | `data/schemas/.../10.json` version=10; 11 tables include "notifications"; `WenuCommerceDatabase` version=10; `MIGRATION_9_10` declared and registered in DataModule |
| 2 | A user's notifications sync from Firestore into Room and are observable per-user, newest first | VERIFIED | `NotificationRepositoryImpl.observeNotifications` uses `channelFlow` with per-user Firestore snapshot listener over `notifications/{uid}/items` ordered `createdAt DESC`; writes through `dao.upsertAll`; emits `dao.observeByUser(userId)` |
| 3 | A notification can be marked read and unread count is observable | VERIFIED | `NotificationDao.markAsRead` updates `isRead=1`; `observeUnreadCount` counts `isRead=0`; impl calls Room optimistically then mirrors to Firestore best-effort |
| 4 | Firestore rules allow only owner to read; update only the read field; client create/delete denied | VERIFIED | `firestore.rules` line 75–81: `match /notifications/{userId}/items/{notifId}` — owner-uid read, `diff(...).affectedKeys().hasOnly(['read'])` update, `allow create, delete: if false`; 6 rules-jest cases (22/22 green per 08-01 SUMMARY) |
| 5 | Three notification channels (Order Updates HIGH, Account DEFAULT, Promotions LOW) created once in Application.onCreate | VERIFIED | `NotificationChannels.createAll(this)` called in `WenuCommerce.onCreate` before `startKoin`; `NotificationChannels` calls `nm.createNotificationChannels(listOf(...))` with 3 channels; `NotificationChannelsTest` Robolectric 4/4 green |
| 6 | MessagingService routes new_review type to the correct channel and deep-link; onNewToken enqueues FcmTokenWorker | VERIFIED | `MessagingService.onMessageReceived` branch on `FCM_TYPE_NEW_REVIEW` at line 70; `FcmTokenWorker.enqueue(this)` in `onNewToken` at line 44; `MessagingServiceSyncBusTest` 16/16 green |
| 7 | Tapping a new_review notification navigates to SellerProductReviews | VERIFIED | `MainActivity` has `isNewReview && !productId.isNullOrBlank() -> navController.navigate(SellerProductReviews(productId, productTitle))` at line 150; intent builder returns null on blank productId (T-08-09 mitigated) |
| 8 | FCM token refresh uses a unique WorkManager FcmTokenWorker with retry, not fire-and-forget | VERIFIED | `FcmTokenWorker.UNIQUE_WORK_NAME = "fcm_token_refresh"` distinct from `"sync_pending_operations"`; `doWork` awaits `firebaseMessaging.token` + `updateFcmToken`; retries up to MAX_RETRIES=3; `FcmTokenWorkerTest` 4/4 green |
| 9 | In-app Notification History screen backed by Room with unread badge and deep-link row tap | VERIFIED | `NotificationHistoryScreen` 340 lines (loading/empty/populated/error states + NotificationRow + PullToRefreshBox); `NotificationHistoryViewModel` 146 lines wired to `observeNotifications` + `markAsRead`; `unreadCount StateFlow` powers BadgedBox in both `CustomerTabScreen` and `SellerTabScreen` (capped "9+"); `NotificationHistoryViewModelTest` 8/8 green |
| 10 | POST_NOTIFICATIONS manifest entry + rationale dialog gate + Profile "Enable notifications" affordance | VERIFIED (code path) / UNCERTAIN (live behavior) | `AndroidManifest.xml` declares `POST_NOTIFICATIONS`; `NotificationPermissionRationaleDialog` is an M3 `AlertDialog`; `CustomerTabScreen`/`SellerTabScreen` gate on `Build.VERSION.SDK_INT >= TIRAMISU` + DataStore `notification_permission_requested`; `CustomerProfileScreen`/`SellerProfileScreen` have Notifications menu item; actual OS dialog + once-per-install behavior requires device (human_verification #4) |

**Score:** 9/10 truths verified at code level (Truth 10 is code-verified but runtime-behavior requires device)

---

### Deferred Items

None — all gaps are device/deploy items, not future-phase deferrals.

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `domain/src/main/java/com/wenubey/domain/model/Notification.kt` | Domain Notification model (pure Kotlin) | VERIFIED | Present; 11 fields per plan spec |
| `domain/src/main/java/com/wenubey/domain/repository/NotificationRepository.kt` | Domain contract (observeNotifications, observeUnreadCount, markAsRead, startListener, stopListener) | VERIFIED | All 5 methods present |
| `data/src/main/java/com/wenubey/data/local/entity/NotificationEntity.kt` | Room entity tableName="notifications" | VERIFIED | `@Entity(tableName = "notifications", indices = [Index("userId"), Index("createdAt")])` |
| `data/src/main/java/com/wenubey/data/local/dao/NotificationDao.kt` | observeByUser / upsertAll / markAsRead / observeUnreadCount | VERIFIED | All 4 methods present with correct @Query/@Upsert annotations |
| `data/src/main/java/com/wenubey/data/repository/NotificationRepositoryImpl.kt` | Room-first Firestore sync + markAsRead + startListener/stopListener | VERIFIED | 183 lines; channelFlow + callbackFlow pattern; blank-uid guard |
| `data/schemas/com.wenubey.data.local.WenuCommerceDatabase/10.json` | Exported Room schema version 10 | VERIFIED | version=10; notifications table present with indices |
| `firestore.rules` | notifications/{uid}/items owner-read + read-flag-update-only + server-only create/delete | VERIFIED | Lines 75–81 exactly as planned |
| `functions/src/index.ts` | onNewReview trigger + notifications-doc writes in all 3 triggers + channelId order_updates_channel | VERIFIED | `export const onNewReview` at line 1444; all 3 triggers write `collection("notifications")`; zero occurrences of `order_status_channel` |
| `functions/test/onNewReview.test.ts` | Unit/structural tests for onNewReview (min 20 lines) | VERIFIED | 112 lines; 12 test cases including notification-doc-before-send ordering assertion |
| `app/src/main/java/com/wenubey/wenucommerce/notification/NotificationChannels.kt` | centralised createAll() for the 3 channels | VERIFIED | `createNotificationChannels(listOf(...))` with 3 channels at correct importances |
| `data/src/main/java/com/wenubey/data/worker/FcmTokenWorker.kt` | One-time WorkManager token-refresh job | VERIFIED | `UNIQUE_WORK_NAME = "fcm_token_refresh"`; CONNECTED + EXPONENTIAL + REPLACE; MAX_RETRIES=3 |
| `app/src/main/java/com/wenubey/wenucommerce/notification/MessagingService.kt` | unified type router + FCM_TYPE_NEW_REVIEW branch + onNewToken enqueue | VERIFIED | new_review branch at line 70; `FcmTokenWorker.enqueue(this)` at line 44 |
| `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryViewModel.kt` | StateFlow from Room + markAsRead + unreadCount (min 40 lines) | VERIFIED | 146 lines; observeNotifications + markAsRead + unreadCount StateFlow wired |
| `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryScreen.kt` | history list UI with 4 states + row tap + pull-to-refresh (min 60 lines) | VERIFIED | 340 lines; loading/empty/populated/error states; PullToRefreshBox; nav callbacks |
| `app/src/main/java/com/wenubey/wenucommerce/notification/permission/NotificationPermissionRationaleDialog.kt` | M3 AlertDialog rationale | VERIFIED | `AlertDialog` at line 27 |
| `app/src/main/java/com/wenubey/wenucommerce/navigation/AppNavigationObjects.kt` | data object NotificationHistory route | VERIFIED | `data object NotificationHistory` at line 134 |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AuthRepositoryImpl` | `NotificationRepository.startListener` | `authStateListener` non-null branch | VERIFIED | Lines 87 (stopListener) and 95 (startListener) confirmed |
| `NotificationRepositoryImpl` | `notifications/{uid}/items` Firestore subcollection | `callbackFlow addSnapshotListener → dao.upsertAll` | VERIFIED | `firestore.collection("notifications").document(userId).collection("items")` at line 61 |
| `MainActivity` | `SellerProductReviews` route | new_review deep-link branch in LaunchedEffect | VERIFIED | `navController.navigate(SellerProductReviews(productId, productTitle))` at line 150 |
| `MessagingService.onNewToken` | `FcmTokenWorker.enqueue` | onNewToken body | VERIFIED | `FcmTokenWorker.enqueue(this)` at line 44 |
| `WenuCommerce.onCreate` | `NotificationChannels.createAll` | onCreate before startKoin | VERIFIED | `NotificationChannels.createAll(this)` at line 25 |
| `NotificationHistoryViewModel` | `NotificationRepository.observeNotifications` | auth user id → observe → state | VERIFIED | `notificationRepository.observeNotifications(uid)` at line 76 |
| `NotificationHistoryScreen row tap` | deep-link nav callbacks (onNavigateToOrderDetail / onNavigateToSellerOrder / onNavigateToProductReviews) | one-shot NavigationDestination channel effect | VERIFIED | Lines 92–96 in NotificationHistoryScreen |
| CustomerTabScreen post-login | POST_NOTIFICATIONS launcher | rationale dialog → `rememberLauncherForActivityResult(RequestPermission())` | VERIFIED | `RequestPermission` at line 107 in CustomerTabScreen |
| `functions/src/index.ts onNewReview` | `PRODUCTS/{productId}.sellerId` | `productSnap.data().sellerId lookup` | VERIFIED | grep confirms `sellerId = productSnap.data()?.sellerId` pattern in onNewReview source |
| `functions/src/index.ts` (all 3 triggers) | `notifications/{uid}/items` | `db.collection('notifications').doc(uid).collection('items')` | VERIFIED | Lines 1277, 1376, 1472 in index.ts |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `NotificationHistoryScreen` | `state.notifications` | `NotificationHistoryViewModel` → `notificationRepository.observeNotifications(uid)` → `NotificationDao.observeByUser` → Room `notifications` table | Room table populated by Firestore snapshot listener (per-user `callbackFlow`); data is real when Cloud Functions have deployed and pushed notifications docs | FLOWING (code path complete; live data requires functions deploy — see human_verification #9) |
| `CustomerTabScreen` BadgedBox | `unreadCount: StateFlow<Int>` | `NotificationHistoryViewModel.unreadCount` → `notificationDao.observeUnreadCount(userId)` | Counts `isRead=0` rows in Room `notifications` table | FLOWING (same deploy dependency as above) |

---

### Behavioral Spot-Checks

Step 7b is SKIPPED for the Cloud Functions layer — it requires a running Firebase emulator with deployed functions, which is not available. JVM unit tests cover all testable behaviors; device/deploy behaviors are enumerated under Human Verification.

Android/JVM unit gates (reported by orchestrator as BUILD SUCCESSFUL):
- `./gradlew testDebugUnitTest` (all 3 modules): GREEN
- `./gradlew :app:assembleDebug`: GREEN
- `cd functions && npx jest` (87 tests / 8 suites): GREEN
- `cd functions && npm run test:rules` (22/22): GREEN

---

### Probe Execution

No `probe-*.sh` scripts declared or discovered for this phase. Not applicable.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| NOTF-01 | 08-02 | FCM push to customer on order status change | SATISFIED (code) / device-pending | `onOrderStatusChange` writes `notifications/{customerUid}/items` + sends FCM on `order_updates_channel`; jest structural tests assert both writes; deploy pending |
| NOTF-02 | 08-02 | FCM push to seller on new order placed | SATISFIED (code) / device-pending | `onNewSellerOrder` writes `notifications/{sellerUid}/items` + FCM on `order_updates_channel`; jest assertions green; deploy pending |
| NOTF-03 | 08-02 | FCM push to seller on new product review | SATISFIED (code) / device-pending | `onNewReview` trigger deployed in code; resolves recipient via `PRODUCTS/{productId}.sellerId` server-side; history doc written before FCM send; 12 jest cases green; deploy pending |
| NOTF-04 | 08-03, 08-04 | Tapping notification deep-links to relevant screen | SATISFIED (code) / tray-tap device-pending | `MessagingService` builds correct intent extras; `MainActivity` routes `new_review` → `SellerProductReviews`; `NotificationHistoryScreen` row tap routes by type to order/seller/review screens; unit tests cover intent builder; tray-tap path requires device |
| NOTF-05 | 08-04 | Android 13+ POST_NOTIFICATIONS permission with rationale, graceful denial | SATISFIED (code) / OS dialog device-pending | Manifest declares permission; `NotificationPermissionRationaleDialog` coded; DataStore `notification_permission_requested` gate prevents re-nag; `LifecycleResumeEffect` rechecks state; Profile affordance opens system settings; OS dialog flow requires device |
| NOTF-06 | 08-03 | Three notification channels: Order Updates, Account, Promotions | SATISFIED | `NotificationChannels.createAll` creates 3 channels with correct ids/importances; called from `WenuCommerce.onCreate`; `NotificationChannelsTest` 4/4 green; old `order_status_channel` fully removed |
| NOTF-07 | 08-01, 08-03 | FCM token refresh handled via WorkManager, not fire-and-forget | SATISFIED | `updateFcmToken` is `suspend`; `FcmTokenWorker` awaits token + `updateFcmToken`; unique work name; CONNECTED constraint + EXPONENTIAL backoff; `FcmTokenWorkerTest` 4/4 green |
| NOTF-08 | 08-01, 08-04 | In-app notification history screen | SATISFIED (code) / live-data device-pending | Room `notifications` table at v10; `NotificationRepositoryImpl` Room-first Firestore sync; `NotificationHistoryScreen` 4-state UI; unread badge; `NotificationHistoryViewModelTest` 8/8 green; Compose UI test authored (device run deferred) |

All 8 NOTF requirements (NOTF-01..08) are traceable to shipped code. No NOTF requirement is orphaned.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `MessagingService.kt` | 21 | `// TODO add Navigation Helper Functionality going to the Settings Screen when a notification is clicked` | Info | Pre-existing Phase 6 comment (present in commit `cd2f63d`, before any Phase 8 commit); out of scope for Phase 8; no reference issue required because it predates this phase |

No TBD/FIXME/XXX markers found in any Phase 8 modified files. The single TODO on line 21 of MessagingService.kt is confirmed pre-existing (present in `cd2f63d` — a Phase 6 commit — before Phase 8 began). It is not a Phase 8 debt marker.

---

### Human Verification Required

#### 1. End-to-End Push — Customer Order Status (NOTF-01)

**Test:** Deploy functions (prerequisite: `firebase deploy --only functions:onNewReview,functions:onOrderStatusChange,functions:onNewSellerOrder`). Install app on API 33+ device. Log in as customer, have a seller advance order status.
**Expected:** System push notification appears on "Order Updates" channel; Notification History row appears with ShoppingBag icon, correct title/timestamp, and unread badge on Notifications tab; tapping the row deep-links to CustomerOrderDetail and marks the row read (badge decrements).
**Why human:** Requires deployed Cloud Functions + real FCM delivery + live Firestore sync + device SQLite. Deploy is currently blocked by transient GCP error (not a code issue).

#### 2. End-to-End Push — Seller New Order (NOTF-02)

**Test:** Customer places an order on a seller's product.
**Expected:** Seller device receives push notification on "Order Updates" channel; Notification History shows new_order row with Receipt icon.
**Why human:** Same deploy + FCM + device dependency as above.

#### 3. End-to-End Push — Seller New Review (NOTF-03)

**Test:** Customer posts a review on a purchased product.
**Expected:** Seller receives "New review" push on "Order Updates" channel; tapping it opens SellerProductReviews for that product; seller Notification History row shows Star icon.
**Why human:** New trigger (onNewReview) is not yet deployed; requires real Firestore review doc create to fire the onDocumentCreated trigger.

#### 4. POST_NOTIFICATIONS System Dialog + Once-Per-Install Gate (NOTF-05)

**Test:** Fresh install on API 33+ device. Log in → rationale dialog "Stay in the loop" appears. Tap "Not now". Cold-restart the app and log in again — confirm no re-prompt. Open Profile → confirm "Notifications" row reads "Tap to enable notifications" in red; tap → system app-notification settings opens. Enable notifications there and return to app → row updates to "Notifications are on". Log out/in again, tap "Enable" → OS `POST_NOTIFICATIONS` dialog appears; grant it.
**Expected:** Dialog appears exactly once per install; denial persists across restarts; Profile affordance reflects live permission state.
**Why human:** OS dialog is not Robolectric-testable; DataStore once-per-install gate behavior requires real cold-start lifecycle.

#### 5. Three Notification Channels in System Settings (NOTF-06)

**Test:** On any device with the app installed: System Settings → Apps → WenuCommerce → Notifications.
**Expected:** Exactly three channels visible: "Order Updates" (HIGH), "Account" (DEFAULT), "Promotions" (LOW). No "order_status_channel" or "device_login_channel" present.
**Why human:** Notification channel creation is an OS-side registration; `NotificationChannelsTest` (Robolectric) covers the code path but the actual system settings panel requires device inspection.

#### 6. Notification Tray-Tap Deep-Link (NOTF-04 tray path)

**Test:** With deployed functions + push delivery working, let a push arrive and sit in the notification tray. Tap from the tray (cold-start scenario AND warm back-stack scenario).
**Expected:** Both scenarios open the correct screen (CustomerOrderDetail for order_status, SellerProductReviews for new_review) via `singleTop + onNewIntent` routing in `MainActivity`.
**Why human:** Tray-tap lifecycle (PendingIntent → onNewIntent dispatch) requires physical OS execution; unit tests cover intent-builder shape and MainActivity branch logic but not OS tap dispatch.

#### 7. Unread Badge Live Update and Decrement (NOTF-08 badge)

**Test:** After receiving a notification (live), confirm badge shows correct count; mark a notification read; confirm badge decrements; receive 10+ notifications; confirm badge shows "9+".
**Expected:** Badge count matches Room `observeUnreadCount`; Room updates immediately on `markAsRead`; cap is enforced at "9+".
**Why human:** Live badge behavior requires real Firestore-synced notification docs in Room; unit test covers ViewModel unreadCount but live Room-to-UI rendering requires device.

#### 8. Room 9→10 Migration On-Device (NOTF-08 data layer)

**Test:** `./gradlew :data:connectedDebugAndroidTest` (add 9→10 `MigrationTestHelper` case if not already present).
**Expected:** Migration adds the notifications table cleanly; existing rows in other tables are preserved.
**Why human:** MigrationTestHelper requires a connected Android device/emulator (instrumented test); JVM cannot exercise device SQLite directly.

#### 9. Functions Deploy Retry (ops prerequisite for items 1–3, 7)

**Test:** Retry `firebase deploy --only functions:onNewReview,functions:onOrderStatusChange,functions:onNewSellerOrder` when GCP recovers from the transient "Internal error" that blocked 4 previous attempts.
**Expected:** CLI exits 0; all 3 functions appear as deployed in Firebase Console.
**Why human:** Previous failures were Firebase-side infrastructure errors, not code issues. Code is committed and jest-green (87 tests). Retry requires waiting for GCP recovery.

---

### Gaps Summary

No code gaps found. All must-have truths are verified at the source/unit-test level. The 9 human verification items above are all device-only or deployment-ops items explicitly documented in `08-VALIDATION.md` (Manual-Only table) and the `08-04-PLAN.md` Task 3 `checkpoint:human-verify` gate. They are NOT code failures.

The single ops-pending item (functions deploy blocked by transient GCP error) is documented in STATE.md and is retry-able without any code changes.

---

_Verified: 2026-07-17T17:00:00Z_
_Verifier: Claude (gsd-verifier)_
