---
phase: quick-1
plan: 1
subsystem: database
tags: [firestore, sync, coroutines, sharedflow, offline]

# Dependency graph
requires:
  - phase: 01-04
    provides: SyncManager, SyncEvent, sync failure snackbar wiring in MainActivity
provides:
  - SyncManager.manualSync() that reliably throws when offline and emits SyncEvent.SyncFailed via suspending emit()
affects: [any phase touching SyncManager or offline sync behavior]

# Tech tracking
tech-stack:
  added: []
  patterns: [Force server-only Firestore fetch with Source.SERVER in manual/one-shot operations to guarantee exception on offline; use suspending emit() in suspend functions instead of tryEmit() for guaranteed SharedFlow delivery]

key-files:
  created: []
  modified:
    - data/src/main/java/com/wenubey/data/local/SyncManager.kt

key-decisions:
  - "Source.SERVER used in manualSync() only — startSync() snapshot listeners remain unchanged because addSnapshotListener callbacks are non-suspending"
  - "emit() replaces tryEmit() in manualSync() catch block — guaranteed delivery because manualSync is already a suspend function"

patterns-established:
  - "One-shot Firestore fetches in suspend functions use Source.SERVER to prevent silent cache fallback when offline"
  - "Suspending emit() preferred over tryEmit() inside suspend functions for reliable SharedFlow event delivery"

requirements-completed: [QUICK-BUG-01]

# Metrics
duration: 3min
completed: 2026-02-19
---

# Quick Fix 1: Sync Failure Snackbar Summary

**Source.SERVER forces offline exception in manualSync(), and suspending emit() guarantees SyncEvent.SyncFailed delivery so the snackbar finally appears on failed pull-to-refresh**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-02-19T00:00:00Z
- **Completed:** 2026-02-19T00:03:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Fixed Firestore `get()` in `manualSync()` to use `Source.SERVER` — forces a real network fetch instead of silently falling back to local cache, ensuring `FirebaseFirestoreException` is thrown when offline
- Replaced `tryEmit()` with suspending `emit()` in the `manualSync()` catch block — guarantees `SyncEvent.SyncFailed` is delivered to all collectors even under backpressure
- Left `startSync()` `tryEmit()` calls untouched — those run inside non-suspending `addSnapshotListener` callbacks where `emit()` cannot be called

## Task Commits

1. **Task 1: Fix SyncManager.manualSync() to detect offline and emit failure reliably** - `ab90f79` (fix)

## Files Created/Modified
- `data/src/main/java/com/wenubey/data/local/SyncManager.kt` - Added `Source` import; changed both `get()` calls to `get(Source.SERVER)`; changed `tryEmit()` to `emit()` in manualSync catch block

## Decisions Made
- Source.SERVER applied only to `manualSync()` (one-shot operations), not `startSync()` (real-time listeners). Real-time listeners are always online-connected when active, so SERVER source is not needed there.
- `emit()` used in `manualSync()` catch because the function is already `suspend` — no behavioral change in the success path, only the catch path is affected.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Snackbar "Sync failed — showing cached data" will now appear when pull-to-refresh is triggered with no network connection
- No blockers; Phase 2 can proceed as planned

---
*Phase: quick-1*
*Completed: 2026-02-19*
