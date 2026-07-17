---
phase: 8
slug: notifications
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-17
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + Robolectric (JVM unit, :app/:data/:domain) · Compose UI test (instrumented) · Firebase-emulator androidTest (:data) · Jest + ts-jest (functions) · MigrationTestHelper (Room) |
| **Config file** | `gradle/libs.versions.toml`, `functions/jest.config.js`, `functions/tsconfig.json` |
| **Quick run command** | affected module's `:X:testDebugUnitTest` (or `cd functions && npx jest` for Cloud Function tasks) |
| **Full suite command** | `./gradlew testDebugUnitTest && (cd functions && npx tsc --noEmit && npx jest)` |
| **Estimated runtime** | ~30–60 s JVM; ~5 s functions jest; emulator/migration androidTest only on device |

---

## Sampling Rate

- **After every task commit:** affected module's `:X:testDebugUnitTest` (or functions jest)
- **After every plan wave:** full suite command
- **Before `/gsd:verify-work`:** full JVM suite + functions jest + rules jest (`cd functions && npm run test:rules`) green
- **Max feedback latency:** ~60 s

---

## Per-Task Verification Map

> Populated by the planner (each PLAN.md task carries `<acceptance_criteria>` +
> automated verify commands) and cross-checked during execution. Anchor
> requirements → test types below.

| Requirement | Secure/expected behavior | Test Type | Automated Command |
|-------------|--------------------------|-----------|-------------------|
| NOTF-01/02 (order/new-order push) | existing triggers now also write notifications doc | functions unit + rules jest | `cd functions && npx jest` · `npm run test:rules` |
| NOTF-03 (new review → seller) | onNewReview fires on review create, FCM to product.sellerId + notifications doc | functions unit (pure helper) | `cd functions && npx jest` |
| NOTF-04 (deep-link) | new_review target routes to SellerProductReviews; buildNewReviewNotificationIntent extras correct | :app unit (intent builder, like MessagingServiceSyncBusTest) + Compose nav | `:app:testDebugUnitTest` |
| NOTF-05 (POST_NOTIFICATIONS) | rationale shown pre-system-dialog; denial no-nag; settings affordance | :app ViewModel/permission-state unit + Compose UI | `:app:testDebugUnitTest` / `connectedDebugAndroidTest` |
| NOTF-06 (3 channels) | Order Updates/Account/Promotions created with correct ids + importance | :app unit (channel helper) | `:app:testDebugUnitTest` |
| NOTF-07 (token via WorkManager) | onNewToken enqueues unique FcmTokenWorker; worker awaits updateFcmToken | :app/:data unit (WorkManager test) | `:app:testDebugUnitTest` / `:data:testDebugUnitTest` |
| NOTF-08 (history) | NotificationEntity/Dao + Room-first observe; history ViewModel maps + read/unread | :data unit (mapper/dao) + :app ViewModel unit + Compose UI | `:data:testDebugUnitTest` · `:app:testDebugUnitTest` |
| Room notifications (v9→v10) | MIGRATION_9_10 adds notifications table, preserves data | MigrationTestHelper androidTest | `:data:connectedDebugAndroidTest` |
| Firestore rules (notifications) | client owner-read + read-flag update only; server-only create/delete | rules-unit-testing jest | `cd functions && npm run test:rules` |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements (JUnit4/Robolectric, Compose UI test, MigrationTestHelper, functions Jest + rules-unit-testing, WorkManager test deps all present). No new framework/dependency install (confirmed by research: WorkManager/Room/koin-workmanager/firebase-messaging/activity-compose all in the version catalog).
- New test files during execution: functions onNewReview unit + rules-jest notifications cases; :data NotificationDao/mapper unit + MigrationTest 9→10; :app MessagingService new_review intent + channel helper + FcmTokenWorker + notification-history ViewModel + permission-state unit + Compose UI.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end push → history → deep-link on device | NOTF-01..04/08 | Requires deployed functions + real FCM delivery | On device: trigger an order-status change / post a review → confirm the system notification, the in-app history row, and that tapping deep-links correctly |
| POST_NOTIFICATIONS system dialog on Android 13+ | NOTF-05 | OS dialog | On an API 33+ device: log in → rationale → system dialog → deny → confirm no re-nag + Settings affordance opens system settings |
| Room 9→10 migration on-device | NOTF-08 | Device SQLite | `./gradlew :data:connectedDebugAndroidTest` (WenuCommerceMigrationTest, add 9→10 case) |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
