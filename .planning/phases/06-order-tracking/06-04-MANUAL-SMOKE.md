# Phase 6 Plan 04 — Manual FCM Deep-Link Smoke

End-to-end verification for ORDR-10. Instrumentation can't fully simulate
the FCM-tap cold-start path, so the loop below must be exercised on a
real device or Google Play Services emulator before the plan is signed off.

## Prerequisites

- Customer device: physical Android device OR emulator image **with Google
  Play Services** (e.g. `Pixel 6 API 33 — Google APIs`). Signed in as a
  customer account that has at least one placed order spanning at least
  one seller's products.
- Seller device: separate physical device OR a second emulator, signed in
  as the seller who owns that order. (You can also do this from the Web
  admin if you have one; on device is the realistic path.)
- Firebase project: same `google-services.json` on both devices. FCM
  enabled in the Firebase console for this project.
- Both devices online for the network steps; airplane-mode toggles called
  out per step where needed.

## Pre-flight (one-time)

1. Customer device: launch the app, sign in, allow notification
   permission when prompted. Confirm in Android system settings
   "Apps → WenuCommerce → Notifications" that a channel named
   **"Order updates"** is visible (channel id `order_status_channel`).
2. Seller device: launch the app, sign in, navigate to the seller orders
   tab and locate the test sub-order in `PENDING` status.

## Step 1 — Foreground push receipt (PENDING → CONFIRMED)

- Customer device: open the app, stay on the **Home** screen (foreground).
- Seller device: advance the sub-order `PENDING → CONFIRMED`.
- **Expected** on customer device within ~10 s:
  - System notification appears in the tray AND/OR an in-app surface
    fires (foreground notifications are app-controlled).
  - Title: `Order confirmed`. Body references the seller name.
  - Logcat shows `MessagingService.onMessageReceived` was invoked with
    `data.type == order_status`.
  - The customer's order list / order detail Room cache reflects the new
    `aggregateStatus` (06-02 consumers will collect on SyncBus and call
    `syncCustomerOrders`).
- Record: `[ ] PASS`  `[ ] FAIL` (timestamp:                 )
- If FAIL: check Firebase console → Cloud Messaging logs for the
  outbound send (RESEARCH §2.4 troubleshooting tree).

## Step 2 — Background tap (CONFIRMED → SHIPPED)

- Customer device: press Home to background the app (do not force-stop).
- Seller device: advance `CONFIRMED → SHIPPED`. Optionally enter a
  tracking number.
- **Expected** on customer device:
  - Notification appears in tray with title `Order shipped`.
  - **Tap the notification.**
  - App resumes (no fresh launch animation — `singleTop` keeps the
    existing instance) and navigates directly to the **Order Detail**
    screen for the correct `orderId`.
  - The stepper shows `SHIPPED` highlighted with a timestamp.
- Record: `[ ] PASS`  `[ ] FAIL` (timestamp:                 )
- If FAIL: check Logcat for `onNewIntent` invocation; verify
  `EXTRA_ORDER_ID` is non-null at the LaunchedEffect.

## Step 3 — Killed / cold-start tap (SHIPPED → DELIVERED)

- Customer device: **force-stop** the app (Settings → Apps → WenuCommerce
  → Force Stop). Confirm in recents that the app is fully closed.
- Seller device: advance `SHIPPED → DELIVERED`.
- **Expected** on customer device:
  - Notification appears in tray with title `Order delivered`.
  - **Tap the notification.**
  - Cold start: splash → directly lands on **Order Detail** for the
    correct `orderId`. The startup auth path (`AuthViewModel`
    `isInitialized`) gates the LaunchedEffect; once `RootNavigationGraph`
    has rendered, the deep-link navigation pops in.
- Record: `[ ] PASS`  `[ ] FAIL` (timestamp:                 )
- If FAIL: confirm in Logcat that `intent.getStringExtra(EXTRA_ORDER_ID)`
  was non-null at first composition. If null, the FCM SDK did not
  promote the `data` payload to intent extras — verify the Cloud
  Function payload still carries `data.orderId` (functions/src/index.ts
  line ~691) and the channel ID matches.

## Step 4 — Cancellation push (CANCELLED)

- Customer device: leave app in foreground OR background — either path is
  acceptable here.
- Seller device: from a pre-SHIPPED sub-order on a different order, invoke
  `Cancel sub-order`. The `cancelSellerOrder` callable runs the Stripe
  partial refund + sets status to `CANCELLED`.
- **Expected** on customer device:
  - Push notification arrives with title `Order cancelled`.
  - Tapping deep-links to that order's detail; the stepper shows the
    cancellation marker and refund footer (06-03 UI).
- Record: `[ ] PASS`  `[ ] FAIL` (timestamp:                 )

## Step 5 — Multi-seller aggregate sanity

- Place an order containing items from two different sellers (so the
  parent has two sub-orders).
- Seller A advances their sub-order `PENDING → CONFIRMED` then `→ SHIPPED`.
- Seller B leaves their sub-order at `PENDING` initially, then later
  advances to `CANCELLED`.
- **Expected** on customer device:
  - First seller's advance: notification fires; `aggregateStatus` on the
    parent stays at the least-advanced non-CANCELLED state (`PENDING`)
    until seller B moves.
  - Seller B's cancel: notification fires; `aggregateStatus` becomes
    `PARTIALLY_CANCELLED` (server `computeAggregateStatus` mapping).
  - Order list row badge reflects the aggregate.
- Record: `[ ] PASS`  `[ ] FAIL` (timestamp:                 )

## Sign-off

All 5 steps pass on **device:** ____________________  **build:** ____________________

**Executor:** ____________________  **Date:** ____________________

## Failure escalation

- Server-side issues (no FCM dispatch) → check Firebase Functions logs
  for `onOrderStatusChange` invocation + error trace.
- Client-side issues (notification arrives but tap doesn't deep-link) →
  check `AndroidManifest.xml` `launchMode` is `singleTop`, and that
  `MainActivity.onNewIntent` is the version that calls `setIntent(intent)`
  (without `setIntent`, the second tap will re-read the cold-start
  intent and either re-navigate or no-op).
- Permission missing (no notification at all on Android 13+) → the user
  did not grant `POST_NOTIFICATIONS` — runtime permission rationale is
  deferred to Phase 8 per CONTEXT D4.
