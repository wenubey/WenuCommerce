---
phase: 07-reviews-ratings
plan: 02
subsystem: [ui]
tags: [jetpack-compose, material3, reviews, ratings, navigation-compose, koin, mvvm, udf, turbine]

# Dependency graph
requires:
  - phase: 07-01
    provides: "ProductReviewRepository (observeReviewsForProduct Room-first, submitReview callable, markReviewHelpful, getMyReviewForProduct) + SellerOrderDao.getByUserAndStatus + Room reviews cache (v9)"
  - phase: 06-order-tracking
    provides: "sellerOrders(userId, DELIVERED) model + OrderStatusBadge chip pattern reused for VerifiedPurchaseBadge"
provides:
  - "Customer-facing reviews UX on product detail: aggregate rating header, segmented sort toggle, verified-purchase badge, helpful action"
  - "Delivered-order UI gate (D-07) driving the Write/Edit review affordance"
  - "Full-screen WriteReviewScreen (write + edit pre-fill) reusing the detail VM submit path"
  - "Reusable core components: VerifiedPurchaseBadge, StarRatingDisplay, extracted ReviewCard, StarRatingSelector"
  - "WriteReview type-safe navigation route + composable destination"
affects: [07-03-cards-seller-visibility]

# Tech tracking
tech-stack:
  added: []  # no new packages — all libraries already in libs.versions.toml (SingleChoiceSegmentedButtonRow is bundled Material 3)
  patterns:
    - "Delivered-order UI eligibility gate: SellerOrderDao.getByUserAndStatus + itemsJson decode (runCatching) → hasDeliveredOrder, a UX affordance only (server is the authority)"
    - "Client-side review sort with recency tie-break (compareByDescending rating thenByDescending createdAt)"
    - "Optimistic helpful-vote disable via helpfulVotedIds set in ViewModel state (D-04)"
    - "Secondary full-screen form (WriteReviewScreen) sharing the productId-scoped CustomerProductDetailViewModel — no duplicated business logic"

key-files:
  created:
    - "app/src/main/java/com/wenubey/wenucommerce/core/components/VerifiedPurchaseBadge.kt"
    - "app/src/main/java/com/wenubey/wenucommerce/core/components/ReviewCard.kt"
    - "app/src/main/java/com/wenubey/wenucommerce/customer/customer_reviews/StarRatingSelector.kt"
    - "app/src/main/java/com/wenubey/wenucommerce/customer/customer_reviews/WriteReviewScreen.kt"
    - "app/src/test/java/com/wenubey/wenucommerce/testing/fakes/FakeSellerOrderDao.kt"
  modified:
    - "app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailState.kt"
    - "app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailAction.kt"
    - "app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailViewModel.kt"
    - "app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt"
    - "app/src/main/java/com/wenubey/wenucommerce/navigation/AppNavigationObjects.kt"
    - "app/src/main/java/com/wenubey/wenucommerce/navigation/TabNavRoutes.kt"
    - "app/src/test/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailViewModelTest.kt"
    - "app/src/test/java/com/wenubey/wenucommerce/testing/fakes/FakeProductReviewRepository.kt"
    - "app/src/androidTest/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreenTest.kt"

key-decisions:
  - "Nav wiring uses the real files (AppNavigationObjects.kt routes + TabNavRoutes.kt composable destinations), not the nonexistent AppNavigation.kt the plan referenced"
  - "WriteReviewScreen reuses CustomerProductDetailViewModel scoped to the productId (route arg) rather than a new review VM — submit path is shared, business logic not duplicated"
  - "Reviews header/aggregate counts read product.averageRating/reviewCount (denormalised, D-03) not reviews.size, so counts survive Room-cache lag"
  - "Extracted the private ReviewCard to core/components/ReviewCard.kt with a shared read-only StarRatingDisplay (C-01) reused by the aggregate header and the card"
  - "Helpful action changed from the old star IconButton to a TextButton 'Helpful (N)' with hasVoted-driven optimistic disable (C-06/D-04)"

patterns-established:
  - "hasDeliveredOrder gate: authRepository.currentUser.uuid → sellerOrderDao.getByUserAndStatus(uid, DELIVERED) → decode itemsJson (runCatching per entity) → any { productId }"
  - "sortedReviews pure helper reapplied both in the observe collect block and on OnSortOrderChanged"
  - "WriteReviewScreen pops back when isSubmittingReview transitions true→false with no reviewSubmitError; success snackbar shown on the detail screen"

requirements-completed: [REVW-01, REVW-04, REVW-05, REVW-06]

# Metrics
duration: 13min
completed: 2026-07-16
---

# Phase 7 Plan 02: Reviews & Ratings — Customer Review UI Summary

**Customer-facing reviews UX on product detail: aggregate rating header, Most-recent/Highest-rated segmented sort with recency tie-break, Verified Purchase badges, optimistic Helpful votes, and a delivered-order-gated Write/Edit review form that reuses the detail ViewModel's submit path.**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-07-16T02:47:00Z (approx)
- **Completed:** 2026-07-16T03:00:15Z
- **Tasks:** 3 of 3 auto tasks complete
- **Files created:** 5 · **Files modified:** 9

## Accomplishments

- **ViewModel review logic (Task 1):** `CustomerProductDetailViewModel` gained the `hasDeliveredOrder` gate via `SellerOrderDao.getByUserAndStatus` + `itemsJson` decode (D-07), `existingReview` pre-fill from `getMyReviewForProduct` (D-05), a pure `sortedReviews` helper (MOST_RECENT default / HIGHEST_RATED with recency tie-break — REVW-06), a `submitReview` handler with success/error snackbar (REVW-01), and optimistic Helpful disable via `helpfulVotedIds` (D-04). State + Action extended accordingly; `ReviewSortOrder` enum added.
- **Review components + write form (Task 2):** `VerifiedPurchaseBadge` (C-03, REVW-05), `StarRatingSelector` (C-02, 44dp targets + per-star accessibility copy), extracted+extended `ReviewCard` in `core/components` (C-06: star display, verified badge, relative date, Helpful TextButton), and full-screen `WriteReviewScreen` (C-07: pre-fill on edit, title/body fields, `0/1000` counter, submit spinner). Added the `WriteReview(productId, existingReviewId?)` route and its composable destination.
- **Product-detail wiring (Task 3):** `AggregateRatingHeader` (C-04, average + star row + "N reviews" / "No ratings yet" — REVW-04), `ReviewSortToggle` (C-05, `SingleChoiceSegmentedButtonRow`), gated `WriteReviewAffordance` (C-08, Write/Edit copy), badge-enhanced cards, and the loading/empty/error review Screen States with the exact UI-SPEC copy. Compose UI test authored covering verified badge, D-07 gate, aggregate header, and sort segments.

## Task Commits

Each task committed atomically (test + impl together per logical unit, per project convention):

1. **Task 1: State/Action/ViewModel — gate, pre-fill, sort, submit, helpful + Turbine tests** - `f17cf51` (feat)
2. **Task 2: VerifiedPurchaseBadge + StarRatingSelector + extracted ReviewCard + WriteReviewScreen + route** - `979760d` (feat)
3. **Task 3: Product-detail wiring — header, sort toggle, gated affordance, badge cards, states + Compose test** - `fe3faa6` (feat)

_TDD note: for Task 1 the failing ViewModel behaviour tests were written alongside the implementation and confirmed green; Tasks 2/3 are UI-composition tasks gated by `assembleDebug` + the authored Compose test (device run deferred)._

## Test Results — all green

| Suite | Command | Result |
|-------|---------|--------|
| Detail ViewModel unit | `./gradlew :app:testDebugUnitTest --tests "*CustomerProductDetailViewModelTest"` | **green** (gate, pre-fill, sort tie-break, submit success/failure, helpful optimistic disable, form open/dismiss) |
| Full :app unit suite | `./gradlew :app:testDebugUnitTest` | **445 tests, 0 failures** |
| App build | `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL** |
| Compose UI test compile | `./gradlew :app:assembleDebugAndroidTest` | **compiles** (execution deferred — needs emulator) |

## Files Created/Modified

- `core/components/VerifiedPurchaseBadge.kt` — primaryContainer chip, REVW-05 (created)
- `core/components/ReviewCard.kt` — extracted card + shared `StarRatingDisplay`, verified badge, relative date, Helpful TextButton (created)
- `customer/customer_reviews/StarRatingSelector.kt` — interactive 1-5 star picker (created)
- `customer/customer_reviews/WriteReviewScreen.kt` — full-screen write/edit form (created)
- `testing/fakes/FakeSellerOrderDao.kt` — in-memory DAO fake for the delivered-order gate tests (created)
- `customer/customer_products/CustomerProductDetailState.kt` — 8 review-form fields + `ReviewSortOrder` (modified)
- `customer/customer_products/CustomerProductDetailAction.kt` — OpenReviewForm/DismissReviewForm/SubmitReview/OnSortOrderChanged (modified)
- `customer/customer_products/CustomerProductDetailViewModel.kt` — gate, pre-fill, sort, submit, helpful; +SellerOrderDao ctor param (modified)
- `customer/customer_products/CustomerProductDetailScreen.kt` — aggregate header, sort toggle, affordance, badge cards, states (modified)
- `navigation/AppNavigationObjects.kt` — `WriteReview` route (modified)
- `navigation/TabNavRoutes.kt` — `WriteReview` composable destination + `onWriteReview` from detail (modified)
- `customer/customer_products/CustomerProductDetailViewModelTest.kt` — new gate/pre-fill/sort/submit/helpful cases (modified)
- `testing/fakes/FakeProductReviewRepository.kt` — `getMyReviewForProduct` override + `myReviewResult` (modified)
- `androidTest/.../CustomerProductDetailScreenTest.kt` — verified badge, gate, aggregate, sort assertions (modified)

## Decisions Made

- **Real navigation files, not the plan's `AppNavigation.kt`:** routes live in `AppNavigationObjects.kt`, destinations in `TabNavRoutes.kt`. Added the `WriteReview` route there and passed `onWriteReview` down from the detail screen. (Plan-referenced `AppNavigation.kt` does not exist; the prompt directed using the actual files.)
- **WriteReviewScreen shares the detail ViewModel** scoped to the productId route arg (Koin `koinViewModel()` reads the `productId` from the back-stack `SavedStateHandle`), so the submit path is reused with no duplicated business logic (plan requirement).
- **Aggregate counts read `product.averageRating` / `product.reviewCount`** (denormalised, D-03) rather than `reviews.size`, so the header stays correct even while the Room review cache is catching up.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `FakeProductReviewRepository` missing `getMyReviewForProduct` override**
- **Found during:** Task 1 (writing the pre-fill test)
- **Issue:** 07-01 added `getMyReviewForProduct` to the `ProductReviewRepository` interface, but the app-module test fake had not been updated — it would fail to compile once the ViewModel called it.
- **Fix:** Added the override + a `myReviewResult` stub field to `FakeProductReviewRepository`.
- **Files modified:** `app/src/test/.../fakes/FakeProductReviewRepository.kt`
- **Verification:** `:app:testDebugUnitTest --tests "*CustomerProductDetailViewModelTest"` green.
- **Committed in:** `f17cf51` (Task 1 commit)

**2. [Rule 3 - Blocking] `onWriteReview` callback needed on the detail screen for the nav wiring**
- **Found during:** Task 2 (registering the `WriteReview` destination in `TabNavRoutes.kt`)
- **Issue:** The affordance navigates to `WriteReview(productId, existingReviewId)`, but `CustomerProductDetailScreen` had no navigation callback to reach the NavController.
- **Fix:** Added `onWriteReview: (productId, existingReviewId?) -> Unit` (default no-op) to the screen signature; Task 3 wires the affordance to it.
- **Files modified:** `CustomerProductDetailScreen.kt`, `TabNavRoutes.kt`
- **Verification:** `:app:assembleDebug` green.
- **Committed in:** `979760d` (Task 2) / wired fully in `fe3faa6` (Task 3)

---

**Total deviations:** 2 auto-fixed (both Rule 3 blocking). No scope creep — both were required to compile the planned work.

## Issues Encountered

- **`grep -c "SingleChoiceSegmentedButtonRow"` returns 2, not 1 (acceptance-criterion artifact):** the import line and the single usage both match `grep -c`. The functional intent (one segmented sort row in the composable) is satisfied and the must_haves `contains` check passes; the "returns 1" expectation did not account for the Kotlin import line. No code change warranted.
- **`:app:lintDebug` crashes in `RememberInCompositionDetector` (`IncompatibleClassChangeError`):** a pre-existing AGP/Compose-lint version-incompatibility tooling bug, unrelated to this plan's code (the crash is inside the lint framework's Compose detector, not a lint finding on 07-02 files). Out of scope per the executor SCOPE BOUNDARY. The controllable gates — `assembleDebug`, `testDebugUnitTest` (445/0), and the Compose-test compile — are all green. Tracked as deferred.

## Known Stubs

None. `hasDeliveredOrder` queries the real `SellerOrderDao`; `existingReview` reads the real repository; `submitReview` / `markReviewHelpful` invoke the real repository (which routes to the 07-01 callables); the aggregate header and cards render real `Product` / `ProductReview` data. No placeholder/empty-value stubs introduced.

## Threat Flags

None. No new security surface beyond the plan's `<threat_model>`. The `hasDeliveredOrder` UI gate is defence-in-depth only (T-07-08) — the submitReview Cloud Function from 07-01 remains the authority. Helpful double-tap is mitigated by optimistic `helpfulVotedIds` disable (T-07-09) + the server already-exists guard; empty/out-of-range submits are blocked by the disabled-until-rating Submit button (T-07-10) plus server re-validation.

## Deferred Items

- **Device run of `CustomerProductDetailScreenTest`** — `connectedDebugAndroidTest --tests "*CustomerProductDetailScreenTest"` needs a connected emulator/device (not available headless this session). The suite compiles clean (`:app:assembleDebugAndroidTest` green); track for the phase-gate device smoke test. No `@Ignore` used.
- **`:app:lintDebug` tooling crash** — pre-existing `RememberInCompositionDetector` `IncompatibleClassChangeError`; resolve by aligning the Compose lint / AGP versions in a future maintenance pass. Does not affect the code gates.

## User Setup Required

None — no external service configuration. (07-01's `firebase deploy` of the review callables/rules/index remains the outstanding deployment gate for the write path to be live end-to-end; see 07-01-SUMMARY.)

## Next Phase Readiness

- Customer review surface is complete and gated; 07-03 (product cards + seller visibility of reviews) can build on the extracted `ReviewCard` / `StarRatingDisplay` / `VerifiedPurchaseBadge` components and the `hasDeliveredOrder` pattern.
- Once 07-01's Firebase artifacts are deployed, submit/edit/helpful flow end-to-end; the Compose UI test should be run on a device to close the deferred smoke test.

## Self-Check: PASSED

All 5 created files exist on disk (VerifiedPurchaseBadge, ReviewCard, StarRatingSelector, WriteReviewScreen, FakeSellerOrderDao) plus this SUMMARY. All 3 task commits (`f17cf51`, `979760d`, `fe3faa6`) present in git history.

---
*Phase: 07-reviews-ratings*
*Completed: 2026-07-16*
