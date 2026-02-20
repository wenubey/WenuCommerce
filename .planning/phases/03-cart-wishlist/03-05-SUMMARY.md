---
phase: 03-cart-wishlist
plan: 05
subsystem: ui
tags: [connectivity, offline, banner, snackbar, SyncManager, PendingSyncViewModel, StateFlow, Flow]

# Dependency graph
requires:
  - phase: 03-cart-wishlist
    provides: CartRepositoryImpl with SyncWorker.enqueue + emitOfflineWriteQueued call sites; PendingSyncViewModel shouldShowBanner combine flow; SyncManager with emitOfflineWriteQueued
  - phase: 02-offline-write-queue
    provides: PendingSyncBanner UI, SyncEvent.OfflineWriteQueued, PendingOperationDao
provides:
  - Connectivity-gated offline banner — shows only when device is actually offline
  - Connectivity-aware OfflineWriteQueued event emission — snackbar only fires when offline
  - All 5 UAT gap tests (2, 4, 5, 11, 12) closed
affects: [04-checkout, 05-auth, future phases using offline banner or sync snackbar]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Gate banner visibility purely on connectivity — let SyncWorker handle pending items silently when online"
    - "Use Flow.first() in suspend fun to snapshot current connectivity state before conditional emit"

key-files:
  created: []
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncViewModel.kt
    - data/src/main/java/com/wenubey/data/local/SyncManager.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt

key-decisions:
  - "[03-05]: shouldShowBanner condition simplified to `!online` — online state with pending items is not a user-visible concern since SyncWorker drains the queue automatically; dismiss infrastructure preserved for future refinement"
  - "[03-05]: emitOfflineWriteQueued() made suspend and checks connectivityObserver.isOnline.first() before emitting OfflineWriteQueued — guarantees snackbar only fires when device is actually offline"
  - "[03-05]: ConnectivityObserver injected as 5th constructor param in SyncManager; Koin resolves lazily so syncModule ordering relative to connectivityModule is irrelevant"

patterns-established:
  - "Connectivity gating via Flow.first(): use `connectivityObserver.isOnline.first()` in suspend functions to snapshot connectivity before conditional side effects"
  - "Banner visibility = offline state only: transient pending-count spikes during online operations do not surface to the user"

requirements-completed: [CART-01, CART-02, WISH-03]

# Metrics
duration: 2min
completed: 2026-02-20
---

# Phase 3 Plan 05: UAT Gap Closure Summary

**Connectivity-gated offline banner and OfflineWriteQueued snackbar fix — eliminates all 5 UAT false-positive flashes by gating both triggers on actual offline state**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-20T20:10:34Z
- **Completed:** 2026-02-20T20:12:34Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- `shouldShowBanner` in PendingSyncViewModel now returns `!online` only — online cart/wishlist operations no longer flash the offline banner
- `SyncManager.emitOfflineWriteQueued()` converted to suspend fun with `isOnline.first()` check — "Saved locally" snackbar suppressed when device is online
- DataModule updated to inject `ConnectivityObserver` into `SyncManager` as 5th constructor parameter
- All 5 reported UAT issues (tests 2, 4, 5, 11, 12) closed

## Task Commits

Each task was committed atomically:

1. **Task 1: Gate PendingSyncViewModel banner on offline state only** - `5ea9535` (fix)
2. **Task 2: Make SyncManager.emitOfflineWriteQueued() connectivity-aware** - `c10c482` (fix)

## Files Created/Modified
- `app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncViewModel.kt` - `shouldShowBanner` combine lambda changed from `!online || (pending > 0 && pending > dismissed)` to `!online`; KDoc updated to reflect new visibility rule
- `data/src/main/java/com/wenubey/data/local/SyncManager.kt` - Added `ConnectivityObserver` constructor param; `emitOfflineWriteQueued()` made suspend with `isOnline.first()` check; imports added for `ConnectivityObserver` and `kotlinx.coroutines.flow.first`
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` - `syncModule` SyncManager single updated from 4 to 5 `get()` calls

## Decisions Made
- `shouldShowBanner` simplified to pure offline check: when online, SyncWorker processes pending operations silently — no user-visible banner needed. The dismiss infrastructure (`dismissedCount`, `dismissBanner()`) is kept in place for potential future refinement without adding dead-code removal noise.
- `emitOfflineWriteQueued()` uses `Flow.first()` pattern for a point-in-time connectivity snapshot rather than subscribing to the whole flow — correct for a suspend call site that only needs current state before emitting.
- ConnectivityObserver is already a singleton in `connectivityModule`; Koin lazy resolution means module ordering is irrelevant for constructor injection.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both changes were straightforward; the existing suspend contexts in `CartRepositoryImpl.addToCart()` and `restoreCartItem()` already accommodate the newly-suspend `emitOfflineWriteQueued()` without modification.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Phase 3 all planned work complete (plans 01-05)
- UAT gap tests 2, 4, 5, 11, 12 closed; tests 1, 3, 6-10 were passing before this plan
- Phase 4 (Checkout / Stripe) can begin; pre-phase blockers documented in STATE.md remain (Stripe Cloud Function contract, webhook signing secret)

## Self-Check: PASSED

- FOUND: .planning/phases/03-cart-wishlist/03-05-SUMMARY.md
- FOUND: PendingSyncViewModel.kt (shouldShowBanner returns `!online`)
- FOUND: SyncManager.kt (5-param constructor, suspend emitOfflineWriteQueued with isOnline.first())
- FOUND: DataModule.kt (SyncManager with 5 get() calls)
- FOUND: commit 5ea9535 (Task 1 - banner gated on offline state only)
- FOUND: commit c10c482 (Task 2 - SyncManager connectivity-aware emit)
- Build: SUCCESSFUL (no compilation errors)

---
*Phase: 03-cart-wishlist*
*Completed: 2026-02-20*
