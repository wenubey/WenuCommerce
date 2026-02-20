---
phase: 02-offline-write-queue
plan: 01
subsystem: data-layer
tags: [offline-first, work-manager, room-migration, background-sync]
completed: 2026-02-20
duration_minutes: 6

dependency_graph:
  requires: [01-01-room-foundation, 01-02-sync-manager]
  provides: [pending-operations-queue, sync-worker, work-manager-koin-integration]
  affects: [offline-cart, offline-profile, offline-reviews]

tech_stack:
  added:
    - WorkManager 2.11.1
    - Koin WorkManager (androidx-workmanager)
    - Room v2 migration
  patterns:
    - Sequential queue draining
    - Exponential backoff retry
    - Network-constrained background work
    - Koin WorkerFactory dependency injection

key_files:
  created:
    - data/src/main/java/com/wenubey/data/local/entity/PendingOperationEntity.kt
    - data/src/main/java/com/wenubey/data/local/dao/PendingOperationDao.kt
    - data/src/main/java/com/wenubey/data/worker/SyncWorker.kt
    - data/schemas/com.wenubey.data.local.WenuCommerceDatabase/2.json
  modified:
    - gradle/libs.versions.toml
    - data/build.gradle.kts
    - app/build.gradle.kts
    - data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/AppModules.kt
    - app/src/main/java/com/wenubey/wenucommerce/WenuCommerce.kt
    - app/src/main/AndroidManifest.xml

decisions:
  - slug: operation-types-as-enum
    summary: "OperationType enum defines initial types (ADD_TO_CART, UPDATE_CART_QUANTITY, REMOVE_FROM_CART, UPDATE_PROFILE, SUBMIT_REVIEW) as starters; more will be added in future phases"
    rationale: "Enum provides type safety and exhaustive when() checking. Unknown types from future versions handled via runCatching + FAILED status."
    alternatives: ["String constants", "Sealed class hierarchy"]

  - slug: status-as-enum
    summary: "OperationStatus enum tracks lifecycle: PENDING → IN_PROGRESS → (deleted on success) or FAILED"
    rationale: "Three states are sufficient for queue management. Successful operations are deleted rather than marked complete to keep table lean."
    alternatives: ["Add SUCCESS status", "Use boolean flags"]

  - slug: max-retries-hardcoded
    summary: "MAX_RETRIES = 3 hardcoded in SyncWorker companion object"
    rationale: "Three retries with exponential backoff (30s initial) balances reliability vs. battery/network cost. Can be made configurable if user research shows need."
    alternatives: ["Make configurable via DataStore", "Use WorkManager constraints for adaptive retry"]

  - slug: sequential-queue-draining
    summary: "SyncWorker processes one operation at a time (getNextPending() + re-enqueue after success)"
    rationale: "Sequential processing prevents race conditions (e.g., two cart updates to same item) and simplifies error handling. Performance is acceptable for typical queue sizes."
    alternatives: ["Parallel batch processing with conflict resolution", "Priority queue"]

  - slug: repository-wiring-deferred
    summary: "SyncWorker when() block contains TODOs for repository dispatching; actual Firestore writes wired in Phase 3+"
    rationale: "Phase 2 establishes queue structure and retry logic. Cart/profile repositories gain offline-write methods in their respective phases."
    alternatives: ["Wire repositories now with no-op stubs", "Delay SyncWorker until Phase 3"]

  - slug: workmanager-auto-init-disabled
    summary: "WorkManagerInitializer removed via AndroidManifest.xml provider + tools:node=\"remove\""
    rationale: "CRITICAL for Koin WorkerFactory. Without this, WorkManager auto-initializes with default factory before Koin's, causing NullPointerException when workers access injected dependencies."
    alternatives: ["Manual WorkManager.initialize() call", "Use WorkManager's Configuration.Provider interface"]

metrics:
  tasks_completed: 2
  files_created: 4
  files_modified: 8
  commits: 2
  lines_added: ~900
---

# Phase 2 Plan 1: Offline Write Queue Foundation Summary

**One-liner:** Room-backed pending operations queue with WorkManager sync worker, exponential backoff retry, and Koin dependency injection.

## Objective Achieved

Created the data layer infrastructure for offline write queuing: PendingOperationEntity with DAO providing Flow-based pending count and sequential draining, SyncWorker with 3-retry exponential backoff logic, WorkManager + Koin WorkerFactory integration, and database migration from v1 to v2.

**Status:** All tasks complete. No blockers. Repository wiring deferred to Phase 3+ per plan design.

## What Was Built

### 1. PendingOperationEntity + Enums

**File:** `data/src/main/java/com/wenubey/data/local/entity/PendingOperationEntity.kt`

- Room entity with `@PrimaryKey(autoGenerate = true)` for sequential IDs
- Fields: `operationType`, `entityId`, `payloadJson`, `status`, `retryCount`, `createdAt`, `lastAttemptAt`, `errorMessage`
- `OperationType` enum: ADD_TO_CART, UPDATE_CART_QUANTITY, REMOVE_FROM_CART, UPDATE_PROFILE, SUBMIT_REVIEW (starter set)
- `OperationStatus` enum: PENDING, IN_PROGRESS, FAILED (success = deleted)
- Enums stored as String name with `valueOf()` + runCatching fallback per project convention

### 2. PendingOperationDao

**File:** `data/src/main/java/com/wenubey/data/local/dao/PendingOperationDao.kt`

**Methods:**
- `observePendingCount(): Flow<Int>` — reactive count for UI banner (PENDING + IN_PROGRESS)
- `getNextPending(): PendingOperationEntity?` — oldest PENDING operation (sequential drain)
- `observeAllOperations(): Flow<List<PendingOperationEntity>>` — all operations newest-first (queue management screen)
- `insert(operation): Long` — add to queue, returns ID
- `update(operation)` — full entity update
- `deleteById(id)` — remove successful operation
- `updateStatus(id, status, timestamp, errorMessage?)` — atomic status + timestamp + error update
- `incrementRetryCount(id, timestamp)` — atomic retry counter increment

### 3. Database v2 Migration

**File:** `data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt`

- Added `PendingOperationEntity::class` to `@Database` entities array
- Incremented version from 1 to 2
- Added `abstract fun pendingOperationDao(): PendingOperationDao`
- Created `MIGRATION_1_2` companion object with CREATE TABLE SQL:
  - All fields defined (id, operationType, entityId, payloadJson, status, retryCount, createdAt, lastAttemptAt, errorMessage)
  - DEFAULT 'PENDING' for status, DEFAULT 0 for retryCount
- Wired migration in `databaseModule`: `.addMigrations(WenuCommerceDatabase.MIGRATION_1_2)` before `.build()`
- Room exported schema v2 JSON to `data/schemas/com.wenubey.data.local.WenuCommerceDatabase/2.json`

**Why migration matters:** Per 01-01 decision, `fallbackToDestructiveMigration()` is DEBUG-only. Release builds require explicit migrations to avoid IllegalStateException on app update. This migration ensures existing users upgrade cleanly from Room v1.

### 4. SyncWorker

**File:** `data/src/main/java/com/wenubey/data/worker/SyncWorker.kt`

**Structure:**
- Extends `CoroutineWorker(appContext, params)`
- Constructor-injected: `PendingOperationDao`, `DispatcherProvider` (Koin auto-provides Context + WorkerParameters)
- `doWork()` wrapped in `dispatcherProvider.io().run { }`

**Retry logic:**
- `MAX_RETRIES = 3` (companion const)
- Check `if (runAttemptCount > 3)` → mark current operation FAILED, return `Result.failure()`
- Check `if (isStopped)` → handle CANCELLED state before work (Pitfall 6 guard)
- On operation failure: `incrementRetryCount()` → if `retryCount + 1 >= 3`, mark FAILED; else return `Result.retry()`

**Queue draining:**
- `getNextPending()` returns oldest PENDING operation
- If null, queue drained → `Result.success()`
- Mark operation IN_PROGRESS with current timestamp (`Instant.now().toString()`)
- Parse `OperationType.valueOf()` with `runCatching` fallback → unknown types marked FAILED
- Execute operation via when() block (currently TODOs for Phase 3+ repository wiring)
- On success: `deleteById()` + `enqueue(applicationContext)` to process next item

**Enqueue function:**
- `Constraints` with `setRequiredNetworkType(NetworkType.CONNECTED)` — prevents sync without network
- `OneTimeWorkRequestBuilder<SyncWorker>()` with `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))` — 30s exponential backoff
- `enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)` — only one work request active at a time

**Repository wiring (deferred):**
- when() block on `OperationType` has `TODO("Wire ${operationType} to repository in Phase 3+")` for all types
- This structure is intentional: Phase 2 establishes retry logic and queue foundation; Phase 3+ adds actual Firestore write dispatching per feature

### 5. WorkManager + Koin Integration

**Files modified:**
- `gradle/libs.versions.toml`: Added `work = "2.11.1"`, `work-runtime-ktx`, `work-testing`, `koin-workmanager` libraries
- `data/build.gradle.kts` + `app/build.gradle.kts`: Added WorkManager and Koin WorkManager dependencies
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt`: Created `workerModule = module { worker { SyncWorker(get(), get(), get(), get()) } }` — Koin worker DSL auto-provides Context + WorkerParameters
- `app/src/main/java/com/wenubey/wenucommerce/di/AppModules.kt`: Added `workerModule` to list (after syncModule, before repositoryModule)
- `app/src/main/java/com/wenubey/wenucommerce/WenuCommerce.kt`: Added `workManagerFactory()` call in `startKoin { }` block (after androidContext, before modules)
- `app/src/main/AndroidManifest.xml`: Added `<provider>` block to remove `WorkManagerInitializer` with `tools:node="remove"`

**Why disable auto-init:** Without manifest removal, WorkManager auto-initializes with its default factory BEFORE Koin's WorkerFactory is set. When SyncWorker tries to access constructor-injected dependencies, WorkManager uses the default factory (which has no DI context), causing NullPointerException. Disabling auto-init ensures Koin WorkerFactory is the only factory used.

## Verification Results

1. **Compilation:** `./gradlew :data:compileDebugKotlin :app:compileDebugKotlin` — PASSED
2. **Full build:** `./gradlew assembleDebug` — PASSED
3. **Room schema export:** `data/schemas/com.wenubey.data.local.WenuCommerceDatabase/2.json` exists with pending_operations table definition
4. **PendingOperationDao available:** Koin provides `get<PendingOperationDao>()` via `databaseModule`
5. **SyncWorker structure:** CoroutineWorker inheritance, companion enqueue(), retry logic, isStopped guard all present
6. **WorkManager auto-init disabled:** AndroidManifest.xml contains WorkManagerInitializer removal provider block
7. **Koin WorkerFactory active:** WenuCommerce.kt calls `workManagerFactory()` before modules

## Deviations from Plan

None. Plan executed exactly as written. All specified files created/modified. All verification criteria passed.

## Commits

| Commit | Message | Files |
|--------|---------|-------|
| f7ec600 | feat(02-01): add PendingOperationEntity, DAO, and database v2 migration | gradle/libs.versions.toml, data/build.gradle.kts, app/build.gradle.kts, PendingOperationEntity.kt, PendingOperationDao.kt, WenuCommerceDatabase.kt, DataModule.kt, schema v2 JSON |
| f42d35c | feat(02-01): create SyncWorker with Koin WorkerFactory integration | SyncWorker.kt, DataModule.kt (workerModule), AppModules.kt, WenuCommerce.kt, AndroidManifest.xml |

## What's Next

**Phase 2 Plan 2 (02-02):** Wire repositories to support offline writes.

**Expected behavior after this plan:**
- PendingOperationDao available for injection in repositories
- SyncWorker can be enqueued via `SyncWorker.enqueue(context)` (will fail with TODO until Phase 3+)
- Database v2 migration runs on first app launch after this change
- WorkManager respects Koin dependency injection for workers

**Repository wiring in future phases:**
- Phase 3 (offline cart): CartRepository adds methods to insert ADD_TO_CART / UPDATE_CART_QUANTITY / REMOVE_FROM_CART operations into queue
- Phase 4+ (offline profile/reviews): ProfileRepository and ReviewRepository add offline-write queue insertion
- Phase 3+: SyncWorker when() block TODOs replaced with actual repository method calls

## Self-Check

**Verifying created files exist:**
```
FOUND: data/src/main/java/com/wenubey/data/local/entity/PendingOperationEntity.kt
FOUND: data/src/main/java/com/wenubey/data/local/dao/PendingOperationDao.kt
FOUND: data/src/main/java/com/wenubey/data/worker/SyncWorker.kt
FOUND: data/schemas/com.wenubey.data.local.WenuCommerceDatabase/2.json
```

**Verifying commits exist:**
```
FOUND: f7ec600
FOUND: f42d35c
```

## Self-Check: PASSED
