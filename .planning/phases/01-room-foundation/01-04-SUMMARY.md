---
phase: 01-room-foundation
plan: 04
subsystem: ui
tags: [compose, shimmer, pull-to-refresh, empty-state, snackbar, offline-first, material3]

# Dependency graph
requires:
  - phase: 01-02
    provides: SyncManager with manualSync() and syncEvents SharedFlow
  - phase: 01-03
    provides: ConnectivityObserver and ConnectivityViewModel for isOnline flow
provides:
  - EmptyNetworkState composable (offline + empty Room first-launch UI)
  - ShimmerProductCard / ShimmerProductGrid skeleton placeholders
  - ShimmerCategoryChip / ShimmerCategoryRow skeleton placeholders
  - shimmerEffect() Modifier extension
  - Pull-to-refresh wired to SyncManager.manualSync() on CustomerHomeScreen
  - Sync failure snackbar in MainActivity via SyncEvent.SyncFailed collection
  - isOnline StateFlow exposed from CustomerHomeViewModel

affects: [customer-home, product-browsing, search, offline-ux]

# Tech tracking
tech-stack:
  added:
    - material3 PullToRefreshBox (ExperimentalMaterial3Api)
    - Compose animation infiniteRepeatable tween for shimmer gradient
  patterns:
    - shimmerEffect() Modifier extension using composed{} + InfiniteTransition for reusable skeleton animation
    - when{} conditional rendering in Compose screen: empty-offline state / loading-shimmer state / normal content
    - PullToRefreshBox wrapping scrollable content; isRefreshing driven by ViewModel state
    - syncEvents SharedFlow collected in MainActivity LaunchedEffect to surface snackbar globally

key-files:
  created:
    - app/src/main/java/com/wenubey/wenucommerce/core/components/EmptyNetworkState.kt
    - app/src/main/java/com/wenubey/wenucommerce/core/components/ShimmerProductCard.kt
    - app/src/main/java/com/wenubey/wenucommerce/core/components/ShimmerCategoryChip.kt
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeAction.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeState.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "shimmerEffect() modifier lives in ShimmerProductCard.kt and is shared via import by ShimmerCategoryChip — no separate ShimmerModifier.kt file needed since the package is small"
  - "PullToRefreshBox wraps only the main content area; search bar stays outside so it is always accessible during refresh"
  - "isOnline StateFlow exposed directly on CustomerHomeViewModel (not passed as composable param) — cleaner data flow with SharingStarted.WhileSubscribed(5000)"
  - "Scaffold added to MainActivity solely for SnackbarHost — paddings ignored (padding = _ -> no layout shift to existing overlay Box)"
  - "SyncEvent sealed interface is top-level in SyncManager.kt (not nested) — imported as com.wenubey.data.local.SyncEvent"

patterns-established:
  - "Shimmer pattern: shimmerEffect() Modifier extension via composed{} + InfiniteTransition animating linearGradient startOffsetX"
  - "Loading state pattern: when{} block with isEmpty+isOnline check -> EmptyNetworkState; isLoading+isEmpty+isOnline -> ShimmerGrid; else -> content"
  - "Sync failure surfacing: SyncManager.syncEvents SharedFlow -> LaunchedEffect in Activity -> SnackbarHostState.showSnackbar"

requirements-completed: [SYNC-05, SYNC-06]

# Metrics
duration: 4min
completed: 2026-02-19
---

# Phase 1 Plan 04: Loading Experience & Empty States Summary

**Shimmer skeleton placeholders (product cards + category chips), pull-to-refresh wired to SyncManager.manualSync(), first-launch EmptyNetworkState composable, and sync failure snackbar surfaced from SyncManager.syncEvents in MainActivity**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-19T12:26:28Z
- **Completed:** 2026-02-19T12:30:44Z
- **Tasks:** 2
- **Files modified:** 9 (3 created, 6 modified)

## Accomplishments
- Created `EmptyNetworkState` composable with CloudOff icon, descriptive text, and retry button — shown when Room is empty and device is offline
- Created `ShimmerProductCard`/`ShimmerProductGrid` and `ShimmerCategoryChip`/`ShimmerCategoryRow` with `shimmerEffect()` Modifier extension using `InfiniteTransition` animated linearGradient
- Wired `CustomerHomeScreen` with `PullToRefreshBox`, conditional rendering for all loading/empty/offline states, and shimmer overlays during refresh
- Wired `MainActivity` to collect `SyncManager.syncEvents` and show `SnackbarDuration.Short` snackbar on `SyncEvent.SyncFailed`

## Task Commits

Each task was committed atomically:

1. **Task 1: Create EmptyNetworkState and shimmer skeleton composables** - `c9c6dd6` (feat)
2. **Task 2: Wire empty state, shimmer, pull-to-refresh, and sync failure snackbar** - `09e76fc` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified
- `core/components/EmptyNetworkState.kt` — Offline + empty Room first-launch composable with retry button
- `core/components/ShimmerProductCard.kt` — `shimmerEffect()` modifier + `ShimmerProductCard` + `ShimmerProductGrid`
- `core/components/ShimmerCategoryChip.kt` — `ShimmerCategoryChip` + `ShimmerCategoryRow`
- `customer/customer_home/CustomerHomeAction.kt` — Added `OnPullToRefresh` data object
- `customer/customer_home/CustomerHomeState.kt` — Added `isRefreshing: Boolean` field
- `customer/customer_home/CustomerHomeViewModel.kt` — Added `SyncManager`, `ConnectivityObserver` dependencies; `isOnline` StateFlow; `onPullToRefresh()` handler
- `customer/CustomerHomeScreen.kt` — `PullToRefreshBox`, conditional rendering (empty/shimmer/content), shimmer during refresh
- `MainActivity.kt` — `koinInject<SyncManager>()`, `Scaffold` + `SnackbarHost`, `LaunchedEffect` collecting `syncEvents`
- `res/values/strings.xml` — Added `empty_network_title`, `empty_network_subtitle`, `retry`, `sync_failed_snackbar`

## Decisions Made
- `shimmerEffect()` extension lives in `ShimmerProductCard.kt` and shared via import — no separate file needed for a 2-composable package
- `PullToRefreshBox` wraps only the main content area, with the search bar above it so it remains accessible during refresh
- `isOnline` StateFlow exposed directly on `CustomerHomeViewModel` rather than being passed as a composable parameter — cleaner coroutine scope lifetime
- `Scaffold` added to `MainActivity` solely for `SnackbarHost`; padding lambda uses `_` (ignored) to prevent layout shift to the existing Box overlay
- `SyncEvent` sealed interface is top-level in `SyncManager.kt` — imported as `com.wenubey.data.local.SyncEvent` (not `SyncManager.SyncEvent`)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 01 (Room Foundation) is now complete — all 4 plans executed
- Offline-first UX foundation is complete: Room-first data flow, real-time sync via SyncManager, global offline banner, first-launch empty state, shimmer loading states, pull-to-refresh, and sync failure snackbar
- Phase 02 can begin (Cart & Checkout, or next planned phase per ROADMAP.md)

---
*Phase: 01-room-foundation*
*Completed: 2026-02-19*
