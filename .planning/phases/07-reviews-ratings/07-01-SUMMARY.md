---
phase: 07-reviews-ratings
plan: 01
subsystem: [database, api, infra]
tags: [firebase-functions, firestore-rules, room, cloud-callable, reviews, ratings, offline-first]

# Dependency graph
requires:
  - phase: 06-order-tracking
    provides: "server-authoritative cancelSellerOrder callable + firestore.rules server-only pattern + sellerOrders(userId, DELIVERED) model"
  - phase: 01-room-foundation
    provides: "Room-first read pattern, schema export, explicit migration chain"
provides:
  - "submitReview + markReviewHelpful Cloud Function callables (the ONLY review write path)"
  - "exported pure helpers buildReviewData + computeNewAggregate (Firestore-free, unit-testable)"
  - "Firestore rules locking PRODUCTS/{id}/REVIEWS + helpfulVotes to server-only writes"
  - "merged sellerOrders(userId ASC, status ASC) composite index"
  - "Room reviews table (schema v9) + ReviewEntity/ReviewDao/ReviewMapper + MIGRATION_8_9"
  - "SellerOrderDao.getByUserAndStatus (delivered-order gate source for 07-02)"
  - "ProductReviewRepositoryImpl re-routed to callables + Room-first observe + getMyReviewForProduct"
  - "SyncWorker SUBMIT_REVIEW wired to the offline queue"
affects: [07-02-product-detail-ui, 07-03-cards-seller-visibility]

# Tech tracking
tech-stack:
  added: []  # no new packages — all existing deps (RESEARCH confirmed)
  patterns:
    - "Server-authoritative review write path (onCall + auth guard + verified-purchase gate + atomic aggregate transaction)"
    - "Pure named-export helpers for Firestore-free Jest unit testing (mirrors buildFanoutDocs/decideWebhookAction)"
    - "Room-first observe with product-scoped write-through Firestore listener via channelFlow (Pitfall 4)"
    - "MockK callable path unit test (Tasks.forResult / HttpsCallableReference) — no real Firestore"

key-files:
  created:
    - "functions/test/submitReview.test.ts"
    - "functions/test/markReviewHelpful.test.ts"
    - "data/src/main/java/com/wenubey/data/local/entity/ReviewEntity.kt"
    - "data/src/main/java/com/wenubey/data/local/dao/ReviewDao.kt"
    - "data/src/main/java/com/wenubey/data/local/mapper/ReviewMapper.kt"
    - "data/src/test/java/com/wenubey/data/local/mapper/ReviewMapperTest.kt"
    - "data/src/test/java/com/wenubey/data/ProductReviewRepositoryTest.kt"
    - "data/schemas/com.wenubey.data.local.WenuCommerceDatabase/9.json"
  modified:
    - "functions/src/index.ts"
    - "functions/test/rules.test.ts"
    - "firestore.rules"
    - "firestore.indexes.json"
    - "data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt"
    - "data/src/main/java/com/wenubey/data/local/dao/SellerOrderDao.kt"
    - "data/src/main/java/com/wenubey/data/util/Constants.kt"
    - "data/src/main/java/com/wenubey/data/repository/ProductReviewRepositoryImpl.kt"
    - "data/src/main/java/com/wenubey/data/worker/SyncWorker.kt"
    - "domain/src/main/java/com/wenubey/domain/repository/ProductReviewRepository.kt"
    - "app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt"
    - "data/src/androidTest/java/com/wenubey/data/repository/ProductReviewRepositoryImplEmulatorTest.kt"

key-decisions:
  - "submitReview is the sole review write path; verified-purchase gate queries sellerOrders(userId==uid, status==DELIVERED) then filters items[] for productId server-side"
  - "Edit replaces in place: reuse existing review doc id, preserve createdAt + helpfulCount, count unchanged in the aggregate (computeNewAggregate isEdit branch)"
  - "createdAt written as epoch-millis string (Timestamp.now().toMillis().toString()) to match the legacy client format for lexicographic sort (Pitfall 7)"
  - "markReviewHelpful uses a helpfulVotes/{uid} transaction — repeat vote throws already-exists; helpfulCount increment is atomic (D-04, Pitfall 5)"
  - "ProductReviewRepositoryImpl grows 3->5 params (add FirebaseFunctions + ReviewDao); Koin binding switched from singleOf to explicit single(...)"
  - "observeReviewsForProduct is Room-first with a product-scoped write-through listener (channelFlow), NOT a global SyncManager listener (Pitfall 4)"
  - "setReviewVisibility left as a direct client update pending 07-03's seller callable path (documented in code)"

patterns-established:
  - "computeNewAggregate: new -> count+1 running mean; edit -> count unchanged, avg delta-corrected ((avg*count) - oldRating + newRating) / count"
  - "Callable error mapping at the repo boundary: FAILED_PRECONDITION -> 'No delivered order found for this product', UNAUTHENTICATED -> 'Sign in required', ALREADY_EXISTS -> benign no-op"

requirements-completed: [REVW-01, REVW-02, REVW-03, REVW-04, REVW-07]

# Metrics
duration: 22min
completed: 2026-07-16
---

# Phase 7 Plan 01: Reviews & Ratings — Server + Data Foundation Summary

**Server-authoritative review write path: submitReview/markReviewHelpful Cloud Function callables become the ONLY way review docs + rating aggregates change, backed by server-only Firestore rules, a Room v9 reviews cache, and a wired offline queue — reviews can no longer be spoofed, double-submitted, vote-stuffed, or aggregate-tampered.**

## Performance

- **Duration:** ~22 min
- **Tasks:** 4 of 4 auto tasks complete (Task 5 is the human deployment checkpoint — see below)
- **Files created:** 8 · **Files modified:** 12

## Accomplishments

- **submitReview + markReviewHelpful callables** with exported pure helpers (`buildReviewData`, `computeNewAggregate`) — verified-purchase gate (REVW-02), one-per-product edit-in-place (REVW-03), server-set `isVerifiedPurchase` (REVW-05), atomic aggregate recompute (REVW-04), idempotent helpful votes (D-04).
- **Firestore rules tightened**: the permissive `PRODUCTS/{document=**}` wildcard replaced with scoped `REVIEWS/{reviewId}` + `helpfulVotes/{voterId}` sub-matches — reads open, all writes server-only (D-02). Merged the `sellerOrders(userId, status)` composite index (Pitfall 2) without overwriting the 3 existing indexes.
- **Room reviews table (schema v9)**: `ReviewEntity` + `ReviewDao` + `ReviewMapper` + `MIGRATION_8_9`; 9.json schema exported and KSP-validated. Added `SellerOrderDao.getByUserAndStatus` for the 07-02 delivered-order gate.
- **Repository re-routed**: `submitReview`/`markReviewHelpful` call the callables with `FirebaseFunctionsException` error mapping; `observeReviewsForProduct` is Room-first with a product-scoped write-through listener; new `getMyReviewForProduct` (D-05). **SyncWorker SUBMIT_REVIEW** TODO wired to `reviewRepository.submitReview` (Pitfall 6).

## Task Commits

1. **Task 1: submitReview + markReviewHelpful callables + pure helpers + Jest** - `e871fa7` (feat)
2. **Task 2: Firestore rules server-only + merged index + rules Jest** - `9f625cc` (feat)
3. **Task 3: Room ReviewEntity/DAO/Mapper + MIGRATION_8_9 (v9) + SellerOrderDao + Constants** - `c1f57db` (feat)
4. **Task 4: re-route repo to callables + Room-first observe + wire SyncWorker + Koin** - `042714d` (feat)

_TDD note: RED was confirmed for each task (structural/behavioural Jest and Kotlin tests failed pre-implementation), then GREEN. Test + impl were committed together per logical unit rather than split RED/GREEN commits._

## Test Results — all green

| Suite | Command | Result |
|-------|---------|--------|
| Functions TypeScript | `cd functions && npx tsc --noEmit` | clean |
| Functions Jest (non-rules) | `cd functions && npx jest --testPathIgnorePatterns=rules` | **67 passed** (7 suites; 17 new: submitReview 14 + markReviewHelpful 3) |
| Firestore rules Jest | `cd functions && npm run test:rules` (firestore emulator) | **16 passed** (original 12 + 4 new REVIEWS cases) |
| Room mapper unit | `./gradlew :data:testDebugUnitTest --tests "*ReviewMapperTest"` | **3 passed** |
| Repository unit | `./gradlew :data:testDebugUnitTest --tests "*ProductReviewRepository*"` | **5 passed** |
| :data full unit | `./gradlew :data:testDebugUnitTest` | green |
| :domain full unit | `./gradlew :domain:testDebugUnitTest` | green |
| App build | `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL** (Koin arity + Room v9 schema validated) |
| Schema export | `data/schemas/.../9.json` | present, `reviews` table + 2 indices, 14 cols |
| androidTest compile | `./gradlew :data:compileDebugAndroidTestSources` | compiles (execution deferred — see below) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] observeReviewsForProduct write-through could not use `onStart { launch{} }`**
- **Found during:** Task 4
- **Issue:** `Flow.onStart {}` runs in a `FlowCollector` receiver, not a `CoroutineScope`, so `launch{}` failed to compile (`Argument type mismatch: FlowCollector vs CoroutineScope`).
- **Fix:** Rewrote `observeReviewsForProduct` with `channelFlow { launch(io) { productReviewSnapshots(...).collect { reviewDao.upsertAll(...) } }; reviewDao.observeByProduct(...).map{...}.collect { send(it) } }`. Same product-scoped write-through semantics (Pitfall 4), listener torn down when collection stops.
- **Files modified:** `ProductReviewRepositoryImpl.kt`
- **Commit:** `042714d`

**2. [Rule 3 - Blocking] FirebaseFunctionsException constructor is internal**
- **Found during:** Task 4
- **Issue:** The `ProductReviewRepositoryTest` error-mapping cases tried to construct `FirebaseFunctionsException(...)` directly — its constructor is `internal`.
- **Fix:** Mock it with MockK and stub `.code` (`every { fnException.code } returns FAILED_PRECONDITION`), then `Tasks.forException(fnException)`. Assertions unchanged (no softening).
- **Files modified:** `ProductReviewRepositoryTest.kt`
- **Commit:** `042714d`

**3. [Rule 2 - Correctness] Obsolete emulator test asserted the removed client-transaction behavior**
- **Found during:** Task 4
- **Issue:** `ProductReviewRepositoryImplEmulatorTest` exercised the old client-side submit/aggregate/duplicate/markHelpful transactions, which no longer exist (server-only now). Per the plan, these must NOT be `@Ignore`d.
- **Fix:** Rewrote the emulator test to cover the still-valid paths: `getReviewsForProduct` (visible-only), Room-first `observeReviewsForProduct` write-through, `getMyReviewForProduct` (own review + null), and `setReviewVisibility`. Reviews are seeded directly (simulating the Admin-SDK server write). The server write path is now covered by the functions Jest + rules tests. The suite compiles; execution is a deferred device-run (needs a device + firestore/auth emulators).
- **Files modified:** `ProductReviewRepositoryImplEmulatorTest.kt`
- **Commit:** `042714d`

## Deferred Items

- **Device-run of `ProductReviewRepositoryImplEmulatorTest`** — requires a connected device/emulator with the firestore + auth emulators running (`connectedDebugAndroidTest`). Not runnable in this headless environment. The suite compiles clean; JVM unit tests + functions Jest cover the logic. Track for the phase-gate device smoke test.
- **`setReviewVisibility` seller callable** — left as a direct client update; 07-03 will route it through a server callable (review docs are server-only now). Documented inline in `ProductReviewRepositoryImpl.kt`.
- **Environment: `eslint`/`gsd-sdk` unavailable** — the project's global eslint is v10 with no `eslint.config.js` (pre-existing, out of scope); `gsd-sdk` CLI is not installed here (STATE/ROADMAP updated manually below). Neither affects the code gates (tsc/jest/gradle all green).

## Known Stubs

None. All wired: submitReview/markReviewHelpful invoke real callables, observe reads real Room DAO, SyncWorker calls the real repository. No placeholder/empty-value stubs introduced.

## Threat Flags

None. No security surface introduced beyond the plan's `<threat_model>`. The callables + rules + index mitigate T-07-01..T-07-05 exactly as dispositioned; T-07-06/07/SC were `accept`/not-applicable.

## PENDING: `firebase deploy` (Task 5 — blocking-human checkpoint)

This plan is **code + config only** — the review write path and rating aggregate are NOT live until the Firebase artifacts are deployed. This is the billable/side-effect deployment gate (CLAUDE.md: Firebase Functions + rules/schema require approval; ordering is safety-critical). The executor did NOT run any `firebase deploy` (user_setup / orchestrator responsibility).

**Deploy in THIS ORDER from the repo root:**

1. `firebase deploy --only firestore:indexes` — then WAIT for the `sellerOrders(userId, status)` index to reach status **Enabled** in the Firebase console (Firestore → Indexes). Deploying functions before the index is Enabled makes the first submitReview fail with `FAILED_PRECONDITION: missing-index` (Pitfall 2).
2. `firebase deploy --only firestore:rules`
3. `firebase deploy --only functions:submitReview,functions:markReviewHelpful`

**Then smoke-test** on a device/emulator with a signed-in customer who has a DELIVERED order for a product: submit a 1-5 star review → confirm it appears, the product-doc aggregate updates, and a second submit REPLACES (count does not increase). Confirm a customer with NO delivered order is rejected.

## Self-Check: PASSED

All 8 created files exist on disk (submitReview/markReviewHelpful tests, ReviewEntity/DAO/Mapper, ReviewMapperTest, ProductReviewRepositoryTest, 9.json schema, SUMMARY). All 4 task commits (`e871fa7`, `9f625cc`, `c1f57db`, `042714d`) present in git history.
