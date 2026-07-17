---
phase: 08-notifications
plan: 03
subsystem: notifications
tags: [fcm, notification-channels, workmanager, deep-link, robolectric]

# Dependency graph
requires:
  - phase: 08-01
    provides: "suspend FirestoreRepository.updateFcmToken (FcmTokenWorker awaits it)"
  - phase: 08-02
    provides: "server FCM on order_updates_channel + type keys order_status/new_order/new_review + notifId in data payload"
provides:
  - "NotificationChannels.createAll — 3 centralised channels (Order Updates HIGH / Account DEFAULT / Promotions LOW), created once in WenuCommerce.onCreate (NOTF-06)"
  - "MessagingService new_review router branch + showNewReviewNotification + buildNewReviewNotificationIntent (NOTF-04)"
  - "SyncEvent.NewReview(productId) + emitSyncIfNewReview optional refresh trigger"
  - "onNewToken enqueues unique FcmTokenWorker (fcm_token_refresh) with retry/backoff instead of fire-and-forget (NOTF-07)"
  - "MainActivity new_review deep-link branch → SellerProductReviews(productId, productTitle)"
  - "all 3 notification builders send on order_updates_channel (matches 08-02 server channelId; D-03 migration complete on client)"
affects:
  - "plan 08-04 (NotificationHistoryScreen reads the notifications docs; channels + deep-link already declared here)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Centralised NotificationManager.createNotificationChannels(listOf(...)) in Application.onCreate (clone of ensureOrderStatusChannel, one call for all 3)"
    - "CoroutineWorker token-refresh job mirroring SyncWorker (CONNECTED + EXPONENTIAL(30s) + REPLACE, runAttemptCount < MAX_RETRIES gate) but distinct UNIQUE_WORK_NAME"
    - "type-router FCM branch + pure companion intent-builder/emit helper (clone of buildNewOrderNotificationIntent/emitSyncIfNewOrder), unit-tested via Robolectric"
    - "TestListenableWorkerBuilder + inline WorkerFactory to inject mocked deps into a CoroutineWorker unit test"

key-files:
  created:
    - app/src/main/java/com/wenubey/wenucommerce/notification/NotificationChannels.kt
    - data/src/main/java/com/wenubey/data/worker/FcmTokenWorker.kt
    - app/src/test/java/com/wenubey/wenucommerce/notification/NotificationChannelsTest.kt
    - data/src/test/java/com/wenubey/data/worker/FcmTokenWorkerTest.kt
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/notification/OrderNotificationConstants.kt
    - app/src/main/java/com/wenubey/wenucommerce/notification/MessagingService.kt
    - app/src/main/java/com/wenubey/wenucommerce/notification/SyncEvent.kt
    - app/src/main/java/com/wenubey/wenucommerce/WenuCommerce.kt
    - app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt
    - app/src/test/java/com/wenubey/wenucommerce/MessagingServiceSyncBusTest.kt
    - data/build.gradle.kts

key-decisions:
  - "device_login routed to ACCOUNT_CHANNEL_ID and the old inline device_login_channel creation removed; dead ensureOrderStatusChannel companion + NotificationChannel/Build imports removed from MessagingService (channels fully centralised)"
  - "FcmTokenWorker fetches the current token from FirebaseMessaging.token.await() rather than trusting onNewToken's token arg (mitigates T-08-10 stale-token hijack)"
  - "buildNewReviewNotificationIntent + MainActivity branch require non-blank productId; crafted blank-productId new_review is dropped, not deep-linked (T-08-09)"
  - "kept ORDER_STATUS_CHANNEL_ID / _NAME / _DESCRIPTION in OrderNotificationConstants for backward compat per plan (no longer referenced by any builder)"

requirements-completed: [NOTF-04, NOTF-06, NOTF-07]

# Metrics
duration: ~30m
completed: 2026-07-17
---

# Phase 8 Plan 03: MessagingService type-router + 3 channels + deep-link + FcmTokenWorker Summary

**Wired the Android delivery side of Phase 8 notifications: the three notification channels are now created once in `WenuCommerce.onCreate`, `MessagingService` routes the new `new_review` type to a notification on `order_updates_channel` and deep-links a tap to `SellerProductReviews`, and `onNewToken` enqueues a unique WorkManager `FcmTokenWorker` (with retry/backoff) that awaits the suspend `updateFcmToken` write — replacing the old fire-and-forget path and connecting 08-01/08-02's server changes to the device.**

## Performance

- **Duration:** ~30 min
- **Tasks:** 2 completed (both tdd)
- **Files:** 4 created + 8 modified

## Task Commits

1. **Task 1 — centralise 3 channels + extend OrderNotificationConstants (NOTF-06)** — `d101bd2` (feat)
2. **Task 2 — new_review router + intent builder + onNewToken enqueue + channel swap (NOTF-04, NOTF-07)** — `bd18d98` (feat)

Tests are committed inside each feat commit (matches the repo's `test(...)` convention of co-locating the Robolectric/worker tests with the feature; both tasks were `tdd=true` — test written before/alongside implementation, red→green verified per target).

## What Shipped

**Task 1 (`d101bd2`)**
- `NotificationChannels.createAll(context)`: single `nm.createNotificationChannels(listOf(...))` for **Order Updates** (`order_updates_channel`, HIGH), **Account** (`account_channel`, DEFAULT), **Promotions** (`promotions_channel`, LOW); no-op below API 26.
- `WenuCommerce.onCreate` calls `NotificationChannels.createAll(this)` **before** `startKoin`.
- `MessagingService`: removed `onCreate`'s `ensureOrderStatusChannel` call, removed the inline `device_login_channel` creation from `showNotification` (now uses `ACCOUNT_CHANNEL_ID`), and removed the now-dead `ensureOrderStatusChannel` companion + unused `NotificationChannel`/`Build` imports.
- `OrderNotificationConstants`: added `FCM_TYPE_NEW_REVIEW`, `FCM_DATA_KEY_PRODUCT_ID/PRODUCT_TITLE/NOTIF_ID`, `NAV_TARGET_NEW_REVIEW`, `EXTRA_PRODUCT_ID/PRODUCT_TITLE`, and the three channel ids; kept `ORDER_STATUS_CHANNEL_ID` for back-compat.
- `NotificationChannelsTest` (Robolectric): 3 channels with exact ids + HIGH/DEFAULT/LOW importances.

**Task 2 (`bd18d98`)**
- `MessagingService.onMessageReceived`: added a `new_review` branch (before the device_login fallback) → `emitSyncIfNewReview` + `showNewReviewNotification`.
- `showNewReviewNotification` (clone of `showNewOrderNotification`): reads `productId`, builds via `buildNewReviewNotificationIntent`, posts on `ORDER_UPDATES_CHANNEL_ID`.
- `buildNewReviewNotificationIntent(context, data)`: returns `null` on wrong type or blank `productId`; else launch intent with `EXTRA_NAV_TARGET=NAV_TARGET_NEW_REVIEW`, `EXTRA_PRODUCT_ID`, `EXTRA_PRODUCT_TITLE`, CLEAR_TOP|SINGLE_TOP.
- All three builders (`showOrderStatusNotification`, `showNewOrderNotification`, `showNewReviewNotification`) now use `ORDER_UPDATES_CHANNEL_ID` — client channel migration matches 08-02's server `channelId`.
- `SyncEvent.NewReview(productId)` + `emitSyncIfNewReview` (mirrors `emitSyncIfNewOrder`).
- `onNewToken` body replaced with `FcmTokenWorker.enqueue(this)` (removed the now-unused `firestoreRepository` field + import).
- `FcmTokenWorker` (`:data`): `CoroutineWorker` cloning `SyncWorker` — `firebaseMessaging.token.await()` → `firestoreRepository.updateFcmToken(token).getOrThrow()` → `Result.success`; `Result.retry` while `runAttemptCount < MAX_RETRIES` (3) else `Result.failure`; companion `UNIQUE_WORK_NAME = "fcm_token_refresh"` + `enqueue()` (CONNECTED, EXPONENTIAL 30s, REPLACE). Registered `worker { FcmTokenWorker(get(), get(), get(), get(), get()) }` in `DataModule.workerModule`.
- `MainActivity`: first `when` branch `isNewReview && !productId.isNullOrBlank() -> navigate(SellerProductReviews(productId, productTitle))`; dual namespaced/raw extra read for `productId`/`productTitle`; extended `clearNotificationExtras()`.
- Tests: `MessagingServiceSyncBusTest` +8 `new_review` cases (intent extras, empty-title default, null on wrong-type/blank, emit true/false paths); `FcmTokenWorkerTest` (`TestListenableWorkerBuilder` + inline `WorkerFactory`) success/retry/failure + distinct `UNIQUE_WORK_NAME`.

## Test Results

| Gate | Result |
|------|--------|
| `./gradlew :app:testDebugUnitTest --tests "*NotificationChannels*"` | GREEN — 4/4 |
| `./gradlew :app:testDebugUnitTest --tests "*MessagingService*"` | GREEN — 16/16 (8 baseline + 8 new_review) |
| `./gradlew :data:testDebugUnitTest --tests "*FcmTokenWorker*"` | GREEN — 4/4 |
| `./gradlew testDebugUnitTest` (all 3 modules) | GREEN |
| `./gradlew :app:assembleDebug` | GREEN |

## Deviations from Plan

### Auto-fixed / auto-added (Rule 3 — blocking / Rule 2 — correctness)

**1. [Rule 3 - Blocking] `work-testing` added to `:data` `testImplementation`**
- **Found during:** Task 2 — the plan's behavior spec mandates `TestListenableWorkerBuilder`, but `libs.work.testing` was only in `:data` `androidTestImplementation`, so the unit test could not resolve it.
- **Fix:** added `testImplementation(libs.work.testing)` in `data/build.gradle.kts`. Already-cataloged lib (work 2.11.1) — **no new package** (T-08-SC accept holds).
- **Commit:** `bd18d98`.

**2. [Rule 2 - Correctness] Removed dead `ensureOrderStatusChannel` + inline `device_login_channel` creation and their now-unused imports**
- **Found during:** Task 1 — the plan says channels come "solely from `NotificationChannels`". Leaving the old per-service creation would re-create the orphaned `order_status_channel`/`device_login_channel` and contradict the single-source-of-truth intent (D-03).
- **Fix:** deleted the `ensureOrderStatusChannel` companion + its `onCreate` call, removed the inline channel block from `showNotification` (now `ACCOUNT_CHANNEL_ID`), and dropped the resulting unused `NotificationChannel`/`Build` imports.
- **Commit:** `d101bd2`.

Everything else executed exactly as written. `ORDER_STATUS_CHANNEL_ID`/`_NAME`/`_DESCRIPTION` kept in `OrderNotificationConstants` for backward compat per the plan (no builder references them anymore).

## Known Stubs

None introduced. (The single pre-existing `// TODO` at `MessagingService.kt:21` is a Phase 6 comment about settings-screen navigation, out of this plan's scope.)

## Threat Flags

None. All new surface is inside the plan's `<threat_model>`:
- **T-08-09** (blank/absent productId) — `buildNewReviewNotificationIntent` returns null and the MainActivity branch requires non-blank `productId`; crafted payloads are dropped, not deep-linked. Covered by 4 tests.
- **T-08-10** (stale-token hijack) — `onNewToken` → unique WorkManager job that survives process death and awaits the write; worker fetches the current token from `FirebaseMessaging` rather than the passed arg.
- **T-08-11** (work-name collision) — `UNIQUE_WORK_NAME = "fcm_token_refresh"`, asserted distinct from `SyncWorker`'s `"sync_pending_operations"`.
- **T-08-SC** (gradle installs) — no new packages; `work-testing` was already in `libs.versions.toml`.

## Pending Follow-up (MANUAL — 08-VALIDATION Manual-Only)

- **On-device new_review deep-link tap** → verify a review-notification tap opens `SellerProductReviews` for the right product (cold-start + warm/back-stack paths).
- **On-device channel presence** → verify three channels (Order Updates / Account / Promotions) appear in system notification settings.
- **On-device token refresh** → verify `FcmTokenWorker` runs on token rotation and writes `USERS/{uid}.fcmToken` (WorkManager survives process death).
- **08-04** wires the `NotificationHistoryScreen`; the channels, deep-link constants, and `notifId` key this plan declares are the client-side inputs it builds on.

## Self-Check: PASSED

- Created files present: `NotificationChannels.kt`, `FcmTokenWorker.kt`, `NotificationChannelsTest.kt`, `FcmTokenWorkerTest.kt`.
- Commits `d101bd2` and `bd18d98` exist in git.
- must_haves structural greps: MainActivity→`SellerProductReviews(` (1), `FcmTokenWorker.enqueue` in onNewToken (1), `NotificationChannels.createAll` in WenuCommerce (1), `createNotificationChannels` in helper (1), `UNIQUE_WORK_NAME = "fcm_token_refresh"` (1), `FCM_TYPE_NEW_REVIEW` in MessagingService (3), all 3 builders on `ORDER_UPDATES_CHANNEL_ID`.
