---
phase: 02-offline-write-queue
plan: 03
subsystem: ui
tags: [compose, navigation, animated-visibility, offline-banner, type-safe-navigation]

# Dependency graph
requires:
  - phase: 02-offline-write-queue
    provides: PendingSyncBanner overlay in MainActivity, QueueManagementScreen with TopAppBar
provides:
  - Route-aware banner suppression — banner hides while QueueManagementScreen is active
  - AnimatedVisibility driven by shouldShowBanner && !isOnQueueManagementScreen
affects: [any future screens that need conditional banner suppression per route]

# Tech tracking
tech-stack:
  added: []
  patterns: [NavDestination companion hasRoute(KClass) for type-safe route checking in Compose]

key-files:
  created: []
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt

key-decisions:
  - "with(NavDestination) scope used to access hasRoute(KClass) companion extension — required because hasRoute(KClass<T>) is declared as @JvmStatic inside NavDestination.Companion and cannot be called as a plain extension from outside without companion scope"
  - "isOnQueueManagementScreen derived from currentBackStackEntryAsState() directly in setContent — reactive to back-stack changes, no ViewModel involvement needed"
  - "AnimatedVisibility exit animation (slideOutVertically) already configured — banner slides out automatically when navigating to QueueManagementScreen as isOnQueueManagementScreen flips to true"

patterns-established:
  - "Route-guard pattern: use with(NavDestination) { currentBackStackEntry?.destination?.hasRoute(T::class) } to check active route without string literals"

requirements-completed: [SYNC-03, SYNC-04]

# Metrics
duration: 3min
completed: 2026-02-20
---

# Phase 2 Plan 03: Suppress Banner Overlay on QueueManagement Route Summary

**Route-aware AnimatedVisibility suppresses amber offline banner while QueueManagementScreen is active, restoring it on back-navigation via NavDestination.hasRoute(KClass) companion extension**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-20T08:56:35Z
- **Completed:** 2026-02-20T08:59:55Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Banner overlay no longer covers QueueManagementScreen's TopAppBar while offline
- TopAppBar title "Pending Sync Queue" and back arrow are now fully visible on the queue screen
- Banner reappears automatically when the user navigates back (slideInVertically animation)
- Fix is surgical: one boolean, one condition change, no ViewModel or composable modifications

## Task Commits

Each task was committed atomically:

1. **Task 1: Suppress banner overlay on QueueManagement route** - `6548e79` (feat)

**Plan metadata:** _(final docs commit follows)_

## Files Created/Modified
- `app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt` - Added currentBackStackEntryAsState + isOnQueueManagementScreen boolean; updated AnimatedVisibility visible condition

## Decisions Made
- Used `with(NavDestination)` scope to call `hasRoute(QueueManagement::class)` — the KClass overload is a `@JvmStatic` extension declared inside `NavDestination.Companion`, so it requires companion scope to resolve correctly. The plain call `destination?.hasRoute(QueueManagement::class)` resolves to the String overload `hasRoute(route: String, arguments: Bundle?)`, causing a type mismatch compile error.
- `currentBackStackEntryAsState()` placed immediately after `rememberNavController()` — state reads must be ordered before the composables that depend on them.
- No changes to `PendingSyncBanner`, `PendingSyncViewModel`, `shouldShowBanner` logic, or `dismissBanner()` — the fix is additive only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed hasRoute(KClass) companion scope resolution**
- **Found during:** Task 1 (build verification)
- **Issue:** Plan specified `destination?.hasRoute(QueueManagement::class)` directly, but the KClass overload lives inside `NavDestination.Companion`. The compiler resolved to the String overload `hasRoute(String, Bundle?)`, causing `Argument type mismatch: actual type is KClass<QueueManagement>, but String was expected`.
- **Fix:** Wrapped call in `with(NavDestination) { ... }` to bring companion extensions into scope.
- **Files modified:** `app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt`
- **Verification:** Build succeeded `BUILD SUCCESSFUL in 13s`
- **Committed in:** `6548e79` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug — compiler scope resolution)
**Impact on plan:** Auto-fix necessary for correctness. Semantically identical to plan intent. No scope creep.

## Issues Encountered
- `NavDestination.hasRoute(KClass<T>)` is a companion object extension — direct call fails without `with(NavDestination)` scope. Fixed immediately via Rule 1 (compilation error = blocking bug).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- UAT Test 4 gap closed: QueueManagementScreen TopAppBar is now fully visible while offline
- Phase 2 all plans complete (01, 02, 03)
- Ready for Phase 3: Offline Cart

---
*Phase: 02-offline-write-queue*
*Completed: 2026-02-20*
