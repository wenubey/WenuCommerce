---
status: partial
phase: 08-notifications
source: [08-VERIFICATION.md]
started: "2026-07-17T16:38:16Z"
updated: "2026-07-17T16:38:16Z"
---

## Current Test

[awaiting human testing]

## Tests

### 1. End-to-end push delivery — customer order-status
expected: Customer receives a system push on the "Order Updates" channel when a seller advances their order status; the row appears in Notification History with correct icon/title/timestamp; tapping it deep-links to CustomerOrderDetail and marks the badge read.
prereq: deploy `functions:onOrderStatusChange` (08-02 deploy PENDING) + physical API 33+ device
result: [pending]

### 2. End-to-end push delivery — seller new-order
expected: Seller receives a push on the "Order Updates" channel when a customer places an order; the notification doc appears in seller Notification History.
prereq: deploy `functions:onNewSellerOrder` (PENDING) + real FCM device
result: [pending]

### 3. End-to-end push delivery — seller new-review
expected: Seller receives a push when a customer posts a review on their product; the row appears in seller Notification History; tapping it opens SellerProductReviews for that product.
prereq: deploy `functions:onNewReview` (NEW trigger, PENDING) + real FCM device
result: [pending]

### 4. POST_NOTIFICATIONS system permission dialog (Android 13+)
expected: On an API 33+ device, first login shows the "Stay in the loop" rationale dialog once; "Enable" surfaces the OS POST_NOTIFICATIONS dialog; "Not now" suppresses re-prompt on cold restart; the Profile row reads "Tap to enable notifications" when denied and "Notifications are on" when granted (tapping it opens system app-notification settings).
result: [pending]

### 5. Three notification channels visible in system settings
expected: System Settings → App → Notifications shows exactly "Order Updates" (HIGH), "Account" (DEFAULT), and "Promotions" (LOW) — no orphaned `order_status_channel` / `device_login_channel`.
result: [pending]

### 6. Room 9→10 migration on-device
expected: `./gradlew :data:connectedDebugAndroidTest` with a 9→10 MigrationTestHelper case passes; existing data survives the migration.
prereq: connected device/emulator
result: [pending]

### 7. Notification tray-tap deep-link
expected: Tapping a push in the Android system tray (cold-start and warm/back-stack) opens the correct screen: CustomerOrderDetail for order_status, SellerProductReviews for new_review.
result: [pending]

### 8. Unread badge live update and decrement
expected: After receiving a push (or a Notification History row tap), the Notifications tab badge increments; after marking read it decrements; capped at "9+".
result: [pending]

### 9. Functions deploy retry (ops prerequisite for 1–3)
expected: `firebase deploy --only functions:onNewReview,functions:onOrderStatusChange,functions:onNewSellerOrder` exits 0 and all three appear in Firebase Console. (Prior 4 attempts failed on a transient Firebase-side "Internal error" — code is jest 87/87 green; needs GCP recovery.)
result: [pending]

## Summary

total: 9
passed: 0
issues: 0
pending: 9
skipped: 0
blocked: 0

## Gaps
