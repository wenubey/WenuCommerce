---
phase: 7
slug: reviews-ratings
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-16
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + Robolectric (JVM unit, :app/:data/:domain) · Compose UI test (instrumented) · Firebase-emulator androidTest (:data) · Jest + ts-jest (functions) |
| **Config file** | `gradle/libs.versions.toml`, `functions/jest.config.js`, `functions/tsconfig.json` |
| **Quick run command** | `./gradlew :domain:testDebugUnitTest` (fastest domain feedback) or the affected module's `:X:testDebugUnitTest` |
| **Full suite command** | `./gradlew testDebugUnitTest && (cd functions && npx tsc --noEmit && npx jest)` |
| **Estimated runtime** | ~30–60 s JVM suite; ~5 s functions jest; emulator/migration androidTest only on device |

---

## Sampling Rate

- **After every task commit:** Run the affected module's `:X:testDebugUnitTest` (or `functions` jest for Cloud Function tasks)
- **After every plan wave:** Run the full suite command
- **Before `/gsd:verify-work`:** Full JVM suite + functions jest green; Firestore-rules jest (`cd functions && npm run test:rules`) green
- **Max feedback latency:** ~60 s

---

## Per-Task Verification Map

> Populated by the planner (each PLAN.md task carries `<acceptance_criteria>` +
> automated verify commands) and cross-checked by nyquist during execution.
> Anchor requirements → test types below.

| Requirement | Secure Behavior | Test Type | Automated Command |
|-------------|-----------------|-----------|-------------------|
| REVW-01 (1-5 star + optional text) | Rating required 1-5; text optional | functions unit (submitReview validation) + ViewModel unit | `cd functions && npx jest` · `:app:testDebugUnitTest` |
| REVW-02 (verified-purchase only) | Cloud Function rejects submit without a caller-owned DELIVERED sellerOrder containing productId | functions unit (pure verify helper) + rules jest | `cd functions && npx jest` · `npm run test:rules` |
| REVW-03 (one per product, edit) | Second submit replaces, never duplicates | functions unit + repo fake unit | `cd functions && npx jest` · `:data:testDebugUnitTest` |
| REVW-04 (aggregate avg+count) | ratingAverage/ratingCount recomputed in submit txn, denormalised to product | functions unit (pure aggregate helper) | `cd functions && npx jest` |
| REVW-05 (Verified Purchase badge) | Badge shown when isVerifiedPurchase | Compose UI test | `connectedDebugAndroidTest` (or Robolectric UI) |
| REVW-06 (sort recent/highest) | Toggle re-sorts; default recent; tie-break recency | ViewModel unit (sort fn) | `:app:testDebugUnitTest` |
| REVW-07 (count on cards) | Card reads denormalised reviewCount from product | ViewModel/mapper unit (already-rendered card) | `:app:testDebugUnitTest` |
| Room reviews cache (v8→v9) | MIGRATION_8_9 adds reviews table, preserves data | MigrationTestHelper androidTest | `:data:connectedDebugAndroidTest` |
| Firestore rules (reviews server-only) | Client cannot create/update review or product rating fields | rules-unit-testing jest | `cd functions && npm run test:rules` |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements (JUnit4/Robolectric, Compose UI test, Firebase-emulator androidTest, functions Jest + rules-unit-testing are all wired). No new framework install needed.
- New test files to add during execution: functions submitReview unit + rules-jest cases for reviews; :data ReviewDao/repo unit; MigrationTestHelper 8→9 case; :app review ViewModel + Compose UI tests.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end submit → aggregate → card refresh on a real device | REVW-01/04/07 | Requires deployed Cloud Function + real Firestore | On emulator/device with a DELIVERED order: submit a review, confirm it appears, aggregate updates, and the product card shows the count |
| Room 8→9 migration on-device | Room cache | No emulator connected in CI this session | `./gradlew :data:connectedDebugAndroidTest` (WenuCommerceMigrationTest) |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
