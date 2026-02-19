---
phase: 01-room-foundation
plan: 03
subsystem: ui
tags: [android, kotlin, connectivity, room, koin, compose, offline]

# Dependency graph
requires:
  - phase: 01-01
    provides: UserDao and Room database wiring in databaseModule

provides:
  - ConnectivityObserver in data module using callbackFlow + registerDefaultNetworkCallback
  - ConnectivityViewModel exposing isOnline StateFlow
  - OfflineConnectivityBanner composable (amber, WifiOff icon, slide animation)
  - Global offline banner overlay wired in MainActivity
  - AuthRepositoryImpl Room user caching (upsert on state change, clearAll on logout/delete)
  - Offline cold start user profile availability via Room cache

affects:
  - All screens (banner appears globally via MainActivity overlay)
  - AuthRepositoryImpl consumers (unchanged interface, enriched offline behaviour)
  - Future phases using connectivity state

# Tech tracking
tech-stack:
  added: []
  patterns:
    - callbackFlow for wrapping system callbacks (ConnectivityManager.NetworkCallback)
    - Initial state emission before callback registration (prevents missed events on cold launch)
    - Box + AnimatedVisibility overlay pattern for global UI overlays (no layout shift)
    - Room as read-cache for Firestore data (dual-write on every Firestore snapshot)

key-files:
  created:
    - data/src/main/java/com/wenubey/data/connectivity/ConnectivityObserver.kt
    - app/src/main/java/com/wenubey/wenucommerce/core/connectivity/ConnectivityViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/core/connectivity/OfflineConnectivityBanner.kt
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/AppModules.kt
    - app/src/main/res/values/strings.xml
    - data/src/main/java/com/wenubey/data/repository/AuthRepositoryImpl.kt

key-decisions:
  - "ConnectivityViewModel initialValue=true to avoid a false-offline flash on startup before the flow emits"
  - "Initial network state emitted inside callbackFlow before registering callback — required for correct airplane-mode cold launch"
  - "Box overlay in MainActivity wraps the entire navigation graph so the banner appears on every screen without per-screen wiring"
  - "Room user cache cleared in both logOut() and deleteAccount() — also cleared via authStateListener when auth.currentUser becomes null"
  - "connectivityModule placed between syncModule and repositoryModule in appModules to ensure Context is available before ConnectivityObserver is created"

patterns-established:
  - "callbackFlow pattern: emit initial state before registering callback, awaitClose to unregister"
  - "Global overlay pattern: Box + AnimatedVisibility(align=TopCenter) in MainActivity for app-wide banners"
  - "Room dual-write: Firestore listener updates StateFlow AND upserts to Room on every snapshot delivery"

requirements-completed:
  - SYNC-05
  - SYNC-06

# Metrics
duration: 4min
completed: 2026-02-19
---

# Phase 1 Plan 3: Connectivity Banner and Auth Room Caching Summary

**callbackFlow-based ConnectivityObserver with amber offline banner overlay in MainActivity, plus AuthRepositoryImpl dual-writing User to Room on every Firestore snapshot for offline profile reads**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-19T12:19:10Z
- **Completed:** 2026-02-19T12:23:15Z
- **Tasks:** 2
- **Files modified:** 9 (3 created, 6 modified)

## Accomplishments
- ConnectivityObserver wraps ConnectivityManager.NetworkCallback in callbackFlow with initial state emission before callback registration, ensuring correct airplane-mode cold launch behavior
- Global amber offline banner with WifiOff icon and slide animation wired as Box overlay in MainActivity — appears on every screen without layout shift
- AuthRepositoryImpl now dual-writes User to Room on every Firestore snapshot delivery and clears the cache on logout/account deletion, enabling offline profile reads

## Task Commits

Each task was committed atomically:

1. **Task 1: ConnectivityObserver, ConnectivityViewModel, OfflineConnectivityBanner, global wiring** - `436228a` (feat)
2. **Task 2: AuthRepositoryImpl Room user caching** - `f8a3655` (feat)

## Files Created/Modified
- `data/src/main/java/com/wenubey/data/connectivity/ConnectivityObserver.kt` - Network state Flow using callbackFlow + registerDefaultNetworkCallback with initial state emission
- `app/src/main/java/com/wenubey/wenucommerce/core/connectivity/ConnectivityViewModel.kt` - Exposes isOnline StateFlow (SharingStarted.WhileSubscribed(5000), initialValue=true)
- `app/src/main/java/com/wenubey/wenucommerce/core/connectivity/OfflineConnectivityBanner.kt` - Amber Surface with WifiOff icon and "No internet connection" text
- `app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt` - Replaced Surface-only pattern with Box overlay; added AnimatedVisibility banner
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` - Added connectivityModule with single { ConnectivityObserver(get()) }
- `app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt` - Added viewModelOf(::ConnectivityViewModel)
- `app/src/main/java/com/wenubey/wenucommerce/di/AppModules.kt` - Added connectivityModule between syncModule and repositoryModule
- `app/src/main/res/values/strings.xml` - Added no_internet_connection string resource
- `data/src/main/java/com/wenubey/data/repository/AuthRepositoryImpl.kt` - Added UserDao parameter; upsert on Firestore snapshots, initializeUserState, setCurrentUserAfterOnboarding, refreshCurrentUser; clearAll on logOut, deleteAccount, authStateListener null

## Decisions Made
- ConnectivityViewModel initialValue=true prevents a false-offline flash on startup before the callbackFlow emits its first value
- Initial state emitted inside callbackFlow before registerDefaultNetworkCallback — critical for correct airplane-mode cold launch (banner shows immediately)
- connectivityModule added between syncModule and repositoryModule in appModules to preserve dependency ordering
- Room user cleared in both explicit logout/delete paths AND via addAuthStateListener so cache is wiped regardless of how sign-out occurs

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 1 plans 01, 02, and 03 complete — Room foundation, sync infrastructure, connectivity UI, and auth caching all in place
- Plan 04 (final Phase 1 plan) is the remaining plan before Phase 1 is complete
- Global connectivity banner is fully operational; all screens get offline indication automatically
- AuthRepositoryImpl is offline-capable for user profile reads

---
*Phase: 01-room-foundation*
*Completed: 2026-02-19*
