---
phase: 09
slug: seller-storefronts-favorite-sellers
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 09 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from 09-RESEARCH.md § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + Turbine + MockK + Compose UI Test (Android, 3 modules); Jest 29 (Cloud Functions) |
| **Config file** | Standard AGP test config (no separate runner config); `functions/jest.config.js` |
| **Quick run command** | `./gradlew :domain:testDebugUnitTest :app:testDebugUnitTest` |
| **Full suite command** | `./gradlew testDebugUnitTest` (all 3 modules) |
| **Instrumented (migration)** | `./gradlew :data:connectedDebugAndroidTest` (device/emulator required) |
| **Functions tests** | `cd functions && npm test` |
| **Estimated runtime** | ~30–60 s (JVM unit suite) |

---

## Sampling Rate

- **After every task commit:** `./gradlew :app:assembleDebug` + affected-module `testDebugUnitTest`
- **After every plan wave:** `./gradlew testDebugUnitTest` (all 3 modules)
- **Before `/gsd:verify-work`:** Full suite green + `:data:connectedDebugAndroidTest` (Room v10→v11 migration) green
- **Max feedback latency:** ~60 s (JVM); migration + Compose UI tests are device-gated (deferred per project convention)

---

## Per-Requirement Verification Map

> Task IDs are assigned by the planner; this maps each phase requirement to its automated proof.

| Requirement | Behavior | Test Type | Automated Command | File Status |
|-------------|----------|-----------|-------------------|-------------|
| FAVS-01 | Follow toggles state optimistically, reverts on failure | Unit (ViewModel) | `./gradlew :app:testDebugUnitTest --tests "*SellerStorefrontViewModelTest"` | ❌ W0 (extend stub test) |
| FAVS-01 | Logged-out follow tap shows auth-gate dialog (D-03) | Unit (ViewModel) | same | ❌ W0 |
| FAVS-01 | Self-follow is blocked (`currentUser.uuid == sellerId`) | Unit (ViewModel) | same | ❌ W0 |
| FAVS-01 | followSeller writes Room + Firestore fire-and-forget | Unit (Repository fake) | `./gradlew :data:testDebugUnitTest --tests "*FollowedSellersRepositoryTest"` | ❌ W0 |
| FAVS-01 | unfollowSeller deletes from Room + Firestore | Unit (Repository fake) | same | ❌ W0 |
| FAVS-02 | FollowedSellersViewModel emits list from Room (rebinds on uid) | Unit (ViewModel) | `./gradlew :app:testDebugUnitTest --tests "*FollowedSellersViewModelTest"` | ❌ W0 |
| FAVS-02 | Followed Sellers screen renders list / empty state | Compose UI test | `./gradlew :app:connectedDebugAndroidTest --tests "*FollowedSellersScreenTest"` | ❌ W0 (device-deferred) |
| FAVS-03 | Storefront header shows seller name + renders Follow button | Compose UI test | `./gradlew :app:connectedDebugAndroidTest --tests "*SellerStorefrontScreenTest"` | ✅ extend existing (device-deferred) |
| FAVS-03 | Aggregate rating = review-count-weighted mean over ACTIVE products; 0-review handling | Unit (pure/VM) | `./gradlew :app:testDebugUnitTest --tests "*SellerStorefrontViewModelTest"` | ❌ W0 |
| FAVS-04 | Seller follower COUNT surfaced on dashboard (never identity list) | Unit (SellerDashboardViewModel) | `./gradlew :app:testDebugUnitTest --tests "*SellerDashboardViewModelTest"` | ❌ W0 |
| FAVS-04 | followerCount trigger increments on create / decrements on delete | Jest structural | `cd functions && npm test -- --testPathPattern=onFollowedSellerWrite` | ❌ W0 |
| — | Room v10→v11 migration creates followed_sellers table | Instrumented migration | `./gradlew :data:connectedDebugAndroidTest --tests "*WenuCommerceMigrationTest"` | ✅ extend existing (device-deferred) |
| — | followerCount trigger idempotency (same-state = no-op) | Jest unit | `cd functions && npm test -- --testPathPattern=onFollowedSellerWrite` | ❌ W0 |

*Status: ❌ W0 = test must be authored/extended in the plan · ✅ = existing file extended*

---

## Wave 0 Requirements (tests to author/extend before/with implementation)

- [ ] `app/src/test/.../seller_storefront/SellerStorefrontViewModelTest.kt` — extend stub: follow optimistic toggle + revert, auth-gate on logged-out, self-follow block, weighted-mean rating
- [ ] `app/src/androidTest/.../seller_storefront/SellerStorefrontScreenTest.kt` — extend stub: Follow button render/states, auth dialog, empty-bio, header
- [ ] `data/src/test/.../repository/FollowedSellersRepositoryTest.kt` — new (fake DAO + fake Firestore; no real Firestore)
- [ ] `app/src/test/.../customer/customer_followed_sellers/FollowedSellersViewModelTest.kt` — new (Turbine + MainDispatcherRule)
- [ ] `app/src/androidTest/.../customer/customer_followed_sellers/FollowedSellersScreenTest.kt` — new (Compose UI)
- [ ] `app/src/test/.../seller/seller_dashboard/SellerDashboardViewModelTest.kt` — follower-count surface (count-only)
- [ ] `functions/test/onFollowedSellerWrite.test.ts` — new, mirrors `onNewReview.test.ts` (pure helper + structural contract greps)
- [ ] `data/src/androidTest/.../WenuCommerceMigrationTest.kt` — extend with `migrate10To11_createsFollowedSellersTable`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Room v10→v11 migration on-device | FAVS-08 infra | MigrationTestHelper needs a connected device/emulator; JVM can't exercise device SQLite | `./gradlew :data:connectedDebugAndroidTest --tests "*WenuCommerceMigrationTest"` on a device |
| End-to-end follower-count trigger (live Firestore increment/decrement) | FAVS-04 | Requires deployed Cloud Function + live Firestore write | Follow/unfollow on a device; confirm seller dashboard count changes |
| Follow/Unfollow optimistic UI on-device | FAVS-01 | Real Firestore latency + revert behavior | Toggle Follow on a physical device with throttled network |
| Compose UI (storefront + followed list) | FAVS-02/03 | Compose UI tests are device-gated per project convention | `./gradlew :app:connectedDebugAndroidTest` on a device |

---

## Security Domain (ASVS L1 + STRIDE — from RESEARCH § Security Domain)

| Threat | STRIDE | Mitigation (verify in plan `<threat_model>`) |
|--------|--------|----------------------------------------------|
| Client writes `followerCount` directly | Tampering | Firestore rule: clients cannot write `followerCount` on USERS docs; Admin SDK (Cloud Function) is the only writer |
| Follower identity enumeration by seller | Info Disclosure | Structural: follow docs under `/USERS/{customerId}/followed_sellers/...`; rule `request.auth.uid == userId` blocks cross-user reads (FAVS-04) |
| Anonymous follow circumventing auth | Elevation | D-03: no anonymous branch; ViewModel guards `userId.isNullOrBlank()` before any repo call |
| Self-follow counter inflation | Tampering | ViewModel guard: skip follow if `currentUser.uuid == sellerId` |
| Double-follow / unfollow-when-not-following drift | Tampering | Doc-level idempotency (fixed-path upsert); trigger no-ops when existence state is unchanged; Follow button disabled during LOADING |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or a Wave 0 test dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all ❌ references above
- [ ] No watch-mode flags in verify commands
- [ ] Feedback latency < ~60 s (JVM suite)
- [ ] `nyquist_compliant: true` set once the planner maps every task to a verify/W0 dependency

**Approval:** pending
