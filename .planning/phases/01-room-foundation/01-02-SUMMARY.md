---
phase: 01-room-foundation
plan: "02"
subsystem: database
tags: [room, firestore, sync, offline, repository-pattern, kotlin-flow, koin]

# Dependency graph
requires:
  - phase: 01-01
    provides: Room entities (ProductEntity, CategoryEntity), DAOs (ProductDao, CategoryDao), mappers (toDomain/toEntity), Koin databaseModule

provides:
  - SyncManager with Firestore snapshot listeners for products and categories at application scope
  - SyncEvent sealed interface with SyncFailed for UI snackbar signaling
  - manualSync() for pull-to-refresh one-shot Firestore fetch
  - ProductRepositoryImpl migrated to Room-first: all observe/search methods query Room DAOs
  - CategoryRepositoryImpl migrated to Room-first: observeCategories() queries Room DAO
  - Offline-aware getCategories(): Firestore-first with Room fallback
  - SyncManager wired in Application.onCreate() — Firestore listeners active at app launch
  - Search now queries Room LIKE index — works offline against cached data

affects:
  - 01-03 (connectivity-aware UI — will consume SyncEvent.SyncFailed SharedFlow)
  - 01-04 (pull-to-refresh — will call SyncManager.manualSync())
  - All ViewModels observing product/category data — now backed by Room

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Write-through cache: Firestore writes update Room immediately for fast UI feedback"
    - "Read-from-DAO: all Flow-based observation delegates to Room DAO, never Firestore"
    - "SyncManager at application scope: Firestore snapshot listeners live in syncScope (SupervisorJob + IO dispatcher) independent of any ViewModel lifecycle"
    - "Offline fallback: getCategories() wraps Firestore in try/catch, falls back to categoryDao.observeActiveCategories().first()"
    - "SyncEvent SharedFlow (extraBufferCapacity=1): decoupled error signaling from sync to UI layer"

key-files:
  created:
    - data/src/main/java/com/wenubey/data/local/SyncManager.kt
  modified:
    - data/src/main/java/com/wenubey/data/repository/ProductRepositoryImpl.kt
    - data/src/main/java/com/wenubey/data/repository/CategoryRepositoryImpl.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/AppModules.kt
    - app/src/main/java/com/wenubey/wenucommerce/WenuCommerce.kt

key-decisions:
  - "SyncManager uses SupervisorJob so a product listener failure does not cancel the category listener"
  - "Observe methods return DAO Flows directly — no Firestore callbackFlow in repositories; Firestore lives only in SyncManager and one-shot fetch methods"
  - "deleteCategory uses categoryDao.deleteById() rather than upsert with isActive=false — cleaner since observeActiveCategories filters by isActive=1"
  - "approveProduct and suspendProduct update Room cache immediately after Firestore transaction for instant UI status reflection"
  - "getCategories falls back to categoryDao.observeActiveCategories().first() on Firestore failure — enables offline read for one-shot fetches too"
  - "syncModule declared separately from repositoryModule and placed before it in appModules list to ensure DAOs are resolved when SyncManager is created"

patterns-established:
  - "Room-first reads: all Flow observe methods return DAO Flows — repositories no longer set up Firestore listeners"
  - "Application-scoped sync: SyncManager owns all Firestore snapshot listeners, started once in Application.onCreate()"
  - "Write-through cache: every successful Firestore write upserts into Room for zero-latency UI updates"
  - "Offline search: search methods query Room LIKE columns rather than Firestore, yielding functional offline results"

requirements-completed: [SYNC-01, SYNC-02, SYNC-06]

# Metrics
duration: 3min
completed: 2026-02-19
---

# Phase 1 Plan 2: Room-first Repository Migration with Application-Scoped SyncManager Summary

**Firestore snapshot listeners moved to SyncManager at application scope; ProductRepositoryImpl and CategoryRepositoryImpl now read all observe/search data from Room DAOs for offline-capable browsing**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-19T12:13:34Z
- **Completed:** 2026-02-19T12:16:40Z
- **Tasks:** 2
- **Files modified:** 5 (+ 1 created)

## Accomplishments

- Created SyncManager with application-scoped coroutines (SupervisorJob + IO dispatcher) running Firestore snapshot listeners for products and categories; added SyncEvent.SyncFailed SharedFlow for UI error signaling and manualSync() for pull-to-refresh
- Migrated all Flow-based observe methods in ProductRepositoryImpl to Room DAO queries, replaced Firestore search with Room LIKE queries for offline capability, and added Room cache updates after write operations
- Migrated CategoryRepositoryImpl with Room-first observeCategories() and offline-aware getCategories() with Firestore-first / Room-cache-fallback strategy; wired SyncManager.startSync() in WenuCommerce.onCreate()

## Task Commits

Each task was committed atomically:

1. **Task 1: Create SyncManager and migrate ProductRepositoryImpl to Room-first** - `3cdd333` (feat)
2. **Task 2: Migrate CategoryRepositoryImpl to Room-first and wire SyncManager startup** - `0e4ee87` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `data/src/main/java/com/wenubey/data/local/SyncManager.kt` — Application-scoped Firestore-to-Room sync coordinator with snapshot listeners, SyncEvent SharedFlow, and manualSync()
- `data/src/main/java/com/wenubey/data/repository/ProductRepositoryImpl.kt` — Room-first product repository: observe/search methods use DAOs, write methods cache in Room after Firestore
- `data/src/main/java/com/wenubey/data/repository/CategoryRepositoryImpl.kt` — Room-first category repository: observeCategories() uses DAO, getCategories() has offline fallback
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` — Added syncModule with SyncManager single binding
- `app/src/main/java/com/wenubey/wenucommerce/di/AppModules.kt` — Added syncModule to appModules list before repositoryModule
- `app/src/main/java/com/wenubey/wenucommerce/WenuCommerce.kt` — Added SyncManager.startSync() call after Koin initialization

## Decisions Made

- SyncManager uses SupervisorJob so a product listener failure does not cancel the category listener
- deleteCategory uses categoryDao.deleteById() rather than upsert with isActive=false for cleaner semantics
- approveProduct and suspendProduct update Room cache immediately after Firestore transaction for instant UI reflection
- syncModule placed before repositoryModule in appModules to ensure DAOs are resolved at SyncManager creation time

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Room-first read path is complete for products and categories
- SyncManager is running at application scope — Firestore changes propagate to Room automatically
- SyncEvent.SyncFailed SharedFlow is ready for plan 01-03 (connectivity-aware UI snackbar)
- manualSync() is ready for plan 01-04 (pull-to-refresh trigger)
- No blockers — plan 01-03 can begin immediately

---
*Phase: 01-room-foundation*
*Completed: 2026-02-19*
