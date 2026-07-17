# Phase 8: Notifications - Context

**Gathered:** 2026-07-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Customers and sellers receive timely push notifications for every relevant event
(order status → customer, new order → seller, new review → seller), can tap any
notification to deep-link to the right screen, and can view a persistent in-app
notification history. Android 13+ POST_NOTIFICATIONS is requested gracefully with
a rationale and handles denial. Three system channels (Order Updates, Account,
Promotions) exist. The FCM token is refreshed reliably via WorkManager. Delivers
NOTF-01 through NOTF-08.

NOT in this phase: marketing/promotional notification content (the Promotions
channel is reserved but empty), notification grouping/summary, quiet hours,
per-type mute toggles, rich notifications (images/actions).

**Already live from Phase 6 (this codebase) — EXTEND, don't rebuild:** NOTF-01
(order_status → customer), NOTF-02 (new_order → seller), and the order-side of
NOTF-04 (deep-link to order detail / seller Orders tab) all ship today via
MessagingService + onOrderStatusChange/onNewSellerOrder + the MainActivity
deep-link consumer. There is one `order_status_channel` and a `device_login_channel`.
</domain>

<decisions>
## Implementation Decisions

### Notification history — Firestore-backed, Room-mirrored (NOTF-08)
- **D-01:** Every Cloud Function that dispatches a push ALSO writes a
  `notifications/{uid}/items/{id}` doc (uid = recipient) carrying type, title,
  body, deep-link payload (orderId / sellerOrderId / productId / productTitle),
  createdAt, and a `read` flag. The app mirrors these into a Room `notifications`
  table and the history screen reads Room (Room-first observe, synced from
  Firestore). This mirrors the Phase 6 orders/sellerOrders server-authoritative +
  Room-first pattern: history is complete regardless of FCM delivery / foreground
  state / app-killed, and survives reinstall. The FCM push is still sent for the
  system notification; Firestore is the history source of truth.
- **D-01b:** Track read/unread (mark-read on open); an unread badge on the history
  entry point is a nice-to-have (Claude's discretion).

### Permission flow — contextual + graceful (NOTF-05)
- **D-02:** Request POST_NOTIFICATIONS **contextually after login**, on the first
  landing on the authenticated home, gated behind an in-app rationale dialog
  (explain value: order updates, review alerts) shown BEFORE the system dialog.
  On denial: do NOT re-prompt automatically; expose an "Enable notifications"
  affordance in Profile/Settings that deep-links to the system app-notification
  settings. Android 13+ (API 33) only — pre-33 needs no runtime permission
  (channels only).

### Channels — centralised three (NOTF-06)
- **D-03:** Three channels, created ONCE in a central place (Application onCreate
  or a NotificationChannels helper), replacing the scattered creation in
  MessagingService: **"Order Updates"** (IMPORTANCE_HIGH) ← order_status +
  new_order + new_review; **"Account"** (IMPORTANCE_DEFAULT) ← device_login /
  security; **"Promotions"** (IMPORTANCE_LOW) ← reserved, empty this phase.
  Migrate the existing order_status usage onto the Order Updates channel.

### Review notification + deep-link (NOTF-03, NOTF-04)
- **D-04:** New **`onNewReview` Cloud Function trigger** firing on a review doc
  create (under `PRODUCTS/{id}/REVIEWS`) → FCM to the product's seller (type
  `new_review`, productId + productTitle) + a notifications doc for the seller.
  Tapping deep-links to the Phase 7 **SellerProductReviews(productId, productTitle)**
  screen. Extend the MainActivity deep-link consumer with a `new_review` target
  (reuse the existing currentBackStackEntry-gated pattern).

### FCM token lifecycle — WorkManager (NOTF-07)
- **D-05:** `onNewToken` enqueues a **unique one-time WorkManager job** (retry +
  backoff) to update the Firestore user doc's `fcmToken`, replacing the current
  fire-and-forget `firestoreRepository.updateFcmToken(token)`. Also ensure/refresh
  the token on login. Reuse the existing WorkManager infra (SyncWorker pattern).

### Type routing + persistence (NOTF-01/02/03)
- **D-06:** MessagingService gains a unified type router: order_status, new_order,
  new_review, device_login → each maps to its channel (D-03) and builds the
  deep-link intent (existing constants + new new_review). Server-side, all
  push-dispatching functions write the notifications doc so history is complete
  even when onMessageReceived is not called (background notification-messages).

### Claude's Discretion
- Exact `notifications` Firestore doc path/shape, the Room `NotificationEntity`
  columns + migration (schema v9 → v10), the permission mechanism (Accompanist
  permissions vs a manual ActivityResultContracts.RequestPermission), the unread
  badge, and any notification de-dup/coalescing.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 8 requirements & roadmap
- `.planning/REQUIREMENTS.md` §NOTF-01..08
- `.planning/ROADMAP.md` §"Phase 8: Notifications" — goal, success criteria, plan outline (08-01..04)

### Existing notification infra (Phase 6 + this session — EXTEND)
- `app/src/main/java/com/wenubey/wenucommerce/notification/MessagingService.kt` — FCM service, type routing (order_status/new_order/device_login), channel creation, `onNewToken`, notification build + PendingIntent
- `app/src/main/java/com/wenubey/wenucommerce/notification/OrderNotificationConstants.kt` — FCM data keys, deep-link extras, channel id, nav targets (add new_review + channel ids)
- `app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt` — deep-link consumer LaunchedEffect (currentBackStackEntry-gated; order_detail + seller_orders) — extend with new_review → SellerProductReviews
- `functions/src/index.ts` — `onOrderStatusChange` + `onNewSellerOrder` FCM triggers (the analog for `onNewReview`); server-authoritative + Firestore writes; `submitReview` (the review create that onNewReview triggers on)
- `firestore.rules` — extend for `notifications/{uid}` (owner-read, server-only write), same pattern as /orders

### Phase 7 deep-link target
- `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductReviewsScreen.kt` + route `SellerProductReviews(productId, productTitle)` (navigation/AppNavigationObjects.kt) — the new_review deep-link destination

### Room + WorkManager + architecture
- `data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt` (schema is v9 now → v10 for the notifications table) + `data/schemas/`
- `data/src/main/java/com/wenubey/data/worker/SyncWorker.kt` + the WorkManager/Koin WorkerFactory setup — the token-refresh job pattern
- `data/src/main/java/com/wenubey/data/repository/NotificationPreferences.kt` — existing (DataStore) notification prefs; check for overlap
- `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/STACK.md`, `.planning/codebase/CONVENTIONS.md`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets (extend, don't rebuild)
- MessagingService — type routing + channel + notification build + PendingIntent + deep-link intent builders (buildOrderStatusNotificationIntent / buildNewOrderNotificationIntent). Add a new_review path + centralise channels + WorkManager token.
- OrderNotificationConstants — FCM keys, EXTRA_* deep-link extras, NAV_TARGET_*; add new_review target + the three channel ids.
- MainActivity deep-link LaunchedEffect — currentBackStackEntry-gated consumer; add a new_review branch → navigate SellerProductReviews.
- functions onOrderStatusChange / onNewSellerOrder — the FCM-trigger analog to copy for onNewReview; extend all three to also write the notifications doc.
- SyncWorker + WorkManager (WorkerFactory via Koin) — token-refresh job.
- SellerProductReviews screen/route (Phase 7) — new_review destination.

### Established Patterns
- Server-authoritative Cloud Functions + Firestore rules (owner-read, server-only write); Room-first mirror synced from Firestore; StateFlow UDF ViewModels; type-safe Navigation Compose; Koin DI; WorkManager offline queue; Room schema export + explicit migrations (next migration MIGRATION_9_10).

### Integration Points
- New onNewReview Cloud Function; notifications-doc writes added to onOrderStatusChange/onNewSellerOrder/onNewReview.
- notifications/{uid} rules block in firestore.rules.
- Room NotificationEntity + NotificationDao + MIGRATION_9_10 (v10).
- Permission request in the post-login home + an "Enable notifications" affordance in Profile/Settings.
- Notification history screen + route + ViewModel (Room-backed).
</code_context>

<specifics>
## Specific Ideas

- Reuse the Phase 6/7 server-authoritative + Room-first shape verbatim for the
  notifications collection (like orders/sellerOrders): Firestore is the history
  source of truth, Room mirrors it, the screen observes Room.
- The three channel names are fixed by the requirement: "Order Updates",
  "Account", "Promotions".
</specifics>

<deferred>
## Deferred Ideas

- Promotional/marketing notification content + the pipeline to send them (the
  Promotions channel is created but unused this phase).
- Notification grouping/summary, quiet hours, per-type mute toggles, rich
  notifications (images/actions/inline reply).
- Unread-count badge on the app's bottom nav / history entry (nice-to-have;
  Claude's discretion whether to include a basic version).

None of the above blocks NOTF-01..08.

</deferred>

---

*Phase: 8-notifications*
*Context gathered: 2026-07-17*
