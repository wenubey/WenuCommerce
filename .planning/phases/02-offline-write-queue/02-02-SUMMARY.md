---
phase: 02-offline-write-queue
plan: 02
subsystem: ui-layer
tags: [offline-first, pending-sync-ui, queue-management, datastore-preferences]
completed: 2026-02-20
duration_minutes: 6

dependency_graph:
  requires: [02-01-pending-operations-queue, 01-04-connectivity-observer]
  provides: [pending-sync-banner, queue-management-screen, sync-event-snackbars]
  affects: [offline-cart-ui, offline-profile-ui, offline-reviews-ui]

tech_stack:
  added:
    - DataStore Preferences (dismiss state persistence)
    - WorkManager Flow API (getWorkInfosForUniqueWorkFlow)
  patterns:
    - Merged state banner (offline + pending combined)
    - Dismiss-with-threshold (reappear on count increase)
    - Conditional visibility (state-driven AnimatedVisibility)
    - User-friendly operation display (enum-to-string mapping)

key_files:
  created:
    - app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncBanner.kt
    - app/src/main/java/com/wenubey/wenucommerce/queue_management/QueueManagementViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/queue_management/QueueManagementScreen.kt
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/PreferencesModule.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt
    - app/src/main/java/com/wenubey/wenucommerce/navigation/AppNavigationObjects.kt
    - app/src/main/java/com/wenubey/wenucommerce/navigation/TabNavRoutes.kt
    - app/src/main/java/com/wenubey/wenucommerce/core/connectivity/OfflineConnectivityBanner.kt
    - data/src/main/java/com/wenubey/data/worker/SyncWorker.kt
    - data/src/main/java/com/wenubey/data/local/SyncManager.kt
    - app/src/main/res/values/strings.xml

decisions:
  - slug: merged-banner-states
    summary: "PendingSyncBanner merges offline-only, pending-only, and combined states with conditional text/icons rather than separate composables"
    rationale: "Single composable reduces duplication and ensures consistent styling. State-driven text/icon switching provides clear user feedback for each scenario."
    alternatives: ["Separate OfflineBanner and PendingSyncBanner", "Static banner with generic message"]

  - slug: dismiss-threshold-reappear
    summary: "Banner dismiss stores current pending count; reappears when count increases (not on app restart)"
    rationale: "User expects dismissed banner to stay hidden until new work is queued. Storing dismissed count (not boolean) allows threshold comparison."
    alternatives: ["Boolean dismissed flag (would hide forever)", "Time-based dismiss (e.g., hide for 1 hour)"]

  - slug: workmanager-flow-observation
    summary: "isSyncing state derived from WorkManager.getWorkInfosForUniqueWorkFlow() Flow API"
    rationale: "Flow-based observation provides reactive updates when SyncWorker state changes. Requires WorkManager 2.11+ but cleaner than polling."
    alternatives: ["WorkInfo.State polling with Timer", "Custom LiveData from WorkManager"]

  - slug: queue-management-no-swipe-actions
    summary: "Retry/discard actions use IconButtons instead of swipe-to-dismiss gestures"
    rationale: "Explicit buttons reduce accidental deletions and are more accessible. Failed operations are rare, so gesture optimization not needed."
    alternatives: ["SwipeToDismiss with reveal actions", "Long-press context menu"]

  - slug: operation-display-names
    summary: "OperationType enum mapped to user-friendly strings (e.g., ADD_TO_CART -> 'Cart update')"
    rationale: "Technical enum names confuse users. Grouping cart operations under 'Cart update' simplifies the UI without losing meaning."
    alternatives: ["Show raw enum names", "Store displayName in entity", "i18n string resources per type"]

  - slug: sync-events-extended
    summary: "SyncEvent sealed interface extended with OfflineWriteQueued and SyncPartialFailure for future snackbar triggers"
    rationale: "Infrastructure ready for repositories to emit events when writes are queued offline or some operations fail. No breaking changes to existing SyncFailed handling."
    alternatives: ["Wait to add events until Phase 3", "Use separate event flow for queue events"]

metrics:
  tasks_completed: 2
  files_created: 4
  files_modified: 9
  commits: 2
  lines_added: ~570
---

# Phase 2 Plan 2: Pending Sync UI Layer Summary

**One-liner:** Merged offline + pending sync banner with DataStore dismiss persistence, queue management screen with retry/discard actions, and navigation wiring.

## Objective Achieved

Created the pending-sync UI layer: PendingSyncViewModel combining offline state, pending count, dismiss state, and sync-in-progress; PendingSyncBanner composable merging offline + pending states with conditional text/icons; QueueManagementScreen showing operations with retry/discard on failed items; navigation routing; SyncEvent extensions for offline-write snackbars.

**Status:** All tasks complete. No blockers. Offline write queue is now visible and manageable in the UI. Ready for Phase 3 to wire repositories.

## What Was Built

### 1. PendingSyncViewModel

**File:** `app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncViewModel.kt`

**State flows:**
- `isOnline: StateFlow<Boolean>` — from ConnectivityObserver.isOnline, WhileSubscribed(5000), initialValue=true
- `pendingCount: StateFlow<Int>` — from PendingOperationDao.observePendingCount(), WhileSubscribed(5000), initialValue=0
- `dismissedCount: StateFlow<Int>` — from pendingSync DataStore, WhileSubscribed(5000), initialValue=0
- `shouldShowBanner: StateFlow<Boolean>` — combine(isOnline, pendingCount, dismissedCount): `!online || (pending > 0 && pending > dismissed)`
- `isSyncing: StateFlow<Boolean>` — from WorkManager.getWorkInfosForUniqueWorkFlow(), maps to `workInfos.any { it.state == WorkInfo.State.RUNNING }`

**Functions:**
- `dismissBanner()`: Stores current pendingCount in DataStore as dismissed threshold

**Dependencies:**
- ConnectivityObserver (from data module)
- PendingOperationDao (from 02-01)
- DataStore<Preferences> qualified with `named("pendingSync")` (new in this plan)
- Application (for WorkManager.getInstance())

**Banner visibility logic:**
- Show if offline (regardless of pending count)
- Show if online AND pending count > 0 AND pending count > dismissed count
- Hide if online AND (no pending operations OR user dismissed at this count)

This ensures the banner auto-dismisses when queue drains to zero, but reappears when new writes are queued.

### 2. PendingSyncBanner Composable

**File:** `app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncBanner.kt`

**Parameters:** isOnline, pendingCount, isSyncing, onDismiss, onTap, modifier

**Layout:**
- Surface (amber Color(0xFFFFC107), fillMaxWidth, statusBarsPadding, clickable { onTap() })
- Row (16dp horizontal / 10dp vertical padding, 8dp spacing)
- Left icon (conditional):
  - If isSyncing: CircularProgressIndicator (20dp size, White, 2dp stroke)
  - Else if !isOnline: Icons.Default.WifiOff (White)
  - Else: Icons.Default.CloudUpload (White)
- Text (weight 1f, White, bodyMedium bold):
  - `!isOnline && pendingCount > 0`: "Offline -- N items pending sync"
  - `!isOnline`: "No internet connection"
  - `isSyncing`: "Syncing N items..."
  - else: "N items pending sync"
- Right: IconButton (Close icon, White) — only shown if pendingCount > 0

**Behavior:**
- Tapping banner navigates to QueueManagement screen
- Close button dismisses banner (stores dismissed count)
- Offline-only banner (no pending items) is NOT dismissible (no close button shown)

### 3. DataStore Integration

**File:** `app/src/main/java/com/wenubey/wenucommerce/di/PreferencesModule.kt`

**Added:**
- `PENDING_SYNC_PREFERENCE_NAME = "pendingSyncPreferences"`
- `private val Context.pendingSyncPreferences: DataStore<Preferences> by preferencesDataStore(name = PENDING_SYNC_PREFERENCE_NAME)`
- `single(named("pendingSync")) { androidContext().pendingSyncPreferences }` in preferencesModule

**Why separate DataStore:** Isolates pending sync state from notification preferences and device ID. Follows existing pattern of one DataStore per concern.

### 4. MainActivity Integration

**File:** `app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt`

**Changes:**
- Replaced `ConnectivityViewModel` with `PendingSyncViewModel`
- Collect isOnline, pendingCount, shouldShowBanner, isSyncing via collectAsStateWithLifecycle()
- Replaced `AnimatedVisibility(!isOnline)` with `AnimatedVisibility(shouldShowBanner)`
- Replaced `OfflineConnectivityBanner()` with `PendingSyncBanner(isOnline, pendingCount, isSyncing, onDismiss = { pendingSyncVm.dismissBanner() }, onTap = { navController.navigate(QueueManagement) })`
- Extended SyncEvent collection to handle OfflineWriteQueued and SyncPartialFailure (new snackbar messages)

**Sync event snackbars:**
- SyncEvent.SyncFailed: "Sync failed — showing cached data" (existing)
- SyncEvent.OfflineWriteQueued: "Saved locally -- will sync when online" (new, for when repositories enqueue writes)
- SyncEvent.SyncPartialFailure: "Some items failed to sync" (new, for when SyncWorker marks operations FAILED)

### 5. QueueManagementViewModel

**File:** `app/src/main/java/com/wenubey/wenucommerce/queue_management/QueueManagementViewModel.kt`

**State flows:**
- `operations: StateFlow<List<PendingOperationUiModel>>` — observes pendingOperationDao.observeAllOperations(), maps entities to UI models, WhileSubscribed(5000), initialValue=emptyList()

**Functions:**
- `retryOperation(id: Long)`: Updates status to PENDING, resets timestamp, enqueues SyncWorker
- `discardOperation(id: Long)`: Deletes operation from database

**PendingOperationUiModel:**
- `id: Long`
- `displayName: String` — user-friendly operation name (e.g., "Cart update", "Profile update")
- `statusText: String` — "Pending" | "Syncing…" | "Failed"
- `status: OperationStatus` — enum for conditional UI rendering
- `createdAt: String` — formatted timestamp (e.g., "Feb 20, 14:30")

**toUiModel() logic:**
- OperationType mapping:
  - ADD_TO_CART / UPDATE_CART_QUANTITY / REMOVE_FROM_CART → "Cart update"
  - UPDATE_PROFILE → "Profile update"
  - SUBMIT_REVIEW → "Review submission"
  - null (unknown type) → "Unknown operation"
- OperationStatus mapping: PENDING → "Pending", IN_PROGRESS → "Syncing…", FAILED → "Failed"
- Timestamp formatting: ISO 8601 string parsed to `MMM dd, HH:mm` format with runCatching fallback

### 6. QueueManagementScreen

**File:** `app/src/main/java/com/wenubey/wenucommerce/queue_management/QueueManagementScreen.kt`

**Layout:**
- Scaffold with TopAppBar:
  - Title: "Pending Sync Queue"
  - NavigationIcon: ArrowBack (onNavigateBack)
- Body (conditional):
  - Empty state: Centered text "No pending operations"
  - Operations list: LazyColumn (16dp contentPadding, 8dp vertical spacing)

**OperationItem card:**
- Left: Column with operation name, status + timestamp, error message (if FAILED)
- Right (FAILED only): Row with Refresh (retry) and Delete (discard) IconButtons

**Behavior:**
- Operations sorted newest-first (from DAO query)
- Retry resets status to PENDING and triggers SyncWorker
- Discard deletes operation immediately
- Action buttons only visible for FAILED operations

### 7. Navigation Wiring

**Files:**
- `app/src/main/java/com/wenubey/wenucommerce/navigation/AppNavigationObjects.kt`: Added `@Serializable data object QueueManagement`
- `app/src/main/java/com/wenubey/wenucommerce/navigation/TabNavRoutes.kt`: Added `composable<QueueManagement>` route with QueueManagementScreen and onNavigateBack = { navController.popBackStack() }

**Navigation flow:**
- User taps PendingSyncBanner → navController.navigate(QueueManagement)
- User taps back button → navController.popBackStack()

### 8. SyncEvent Extensions

**File:** `data/src/main/java/com/wenubey/data/local/SyncManager.kt`

**Added:**
- `data object OfflineWriteQueued : SyncEvent` — emitted by repositories when offline writes are queued (wired in Phase 3+)
- `data class SyncPartialFailure(val message: String) : SyncEvent` — emitted when some operations fail (wired in Phase 3+)

**Existing:**
- `data class SyncFailed(val message: String) : SyncEvent` — unchanged

**MainActivity collection:**
- All three event types collected and mapped to snackbar messages
- OfflineWriteQueued → "Saved locally -- will sync when online"
- SyncPartialFailure → event.message or "Some items failed to sync"
- SyncFailed → "Sync failed — showing cached data"

### 9. ViewmodelModule Updates

**File:** `app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt`

**Removed:** `viewModelOf(::ConnectivityViewModel)` — no longer needed (replaced by PendingSyncViewModel)

**Added:**
- `viewModel { PendingSyncViewModel(get(), get(), get(named("pendingSync")), get()) }` — uses explicit viewModel { } constructor for named qualifier
- `viewModelOf(::QueueManagementViewModel)` — standard viewModelOf pattern

**Imports:** Added `import org.koin.core.qualifier.named`

### 10. Superseded OfflineConnectivityBanner

**File:** `app/src/main/java/com/wenubey/wenucommerce/core/connectivity/OfflineConnectivityBanner.kt`

**Change:** Added comment `// Superseded by PendingSyncBanner — retained for potential reuse` at top of file

**Rationale:** File kept intact to avoid breaking any potential references. ConnectivityViewModel.kt also kept (other ViewModels like CustomerHomeViewModel may use ConnectivityObserver directly).

### 11. SyncWorker Public Constant

**File:** `data/src/main/java/com/wenubey/data/worker/SyncWorker.kt`

**Change:** `const val UNIQUE_WORK_NAME = "sync_pending_operations"` (was private, now public)

**Rationale:** PendingSyncViewModel needs the work name to observe WorkManager Flow with `getWorkInfosForUniqueWorkFlow(SyncWorker.UNIQUE_WORK_NAME)`.

## Verification Results

1. **Compilation:** `./gradlew :app:compileDebugKotlin` — PASSED
2. **Full build:** `./gradlew assembleDebug` — PASSED (16s, BUILD SUCCESSFUL)
3. **PendingSyncViewModel registered:** ViewmodelModule uses `viewModel { }` constructor with named DataStore qualifier
4. **PendingSyncBanner wired:** MainActivity uses PendingSyncBanner in AnimatedVisibility with shouldShowBanner state
5. **DataStore exported:** PreferencesModule provides `single(named("pendingSync")) { androidContext().pendingSyncPreferences }`
6. **QueueManagement route:** TabNavRoutes.kt contains `composable<QueueManagement> { QueueManagementScreen(...) }`
7. **SyncEvent extended:** SyncManager.kt contains OfflineWriteQueued and SyncPartialFailure subtypes
8. **MainActivity snackbar handlers:** LaunchedEffect collects all three SyncEvent types with correct messages

## Deviations from Plan

None. Plan executed exactly as written. All specified files created/modified. All verification criteria passed.

## Commits

| Commit | Message | Files |
|--------|---------|-------|
| f6e2174 | feat(02-02): create PendingSyncViewModel and merged PendingSyncBanner | PendingSyncViewModel.kt, PendingSyncBanner.kt, OfflineConnectivityBanner.kt, PreferencesModule.kt, ViewmodelModule.kt, MainActivity.kt, SyncWorker.kt, strings.xml |
| 588c250 | feat(02-02): create QueueManagementScreen with retry/discard actions | QueueManagementViewModel.kt, QueueManagementScreen.kt, AppNavigationObjects.kt, TabNavRoutes.kt, SyncManager.kt |

## What's Next

**Phase 3 (offline cart):** Wire CartRepository to enqueue ADD_TO_CART / UPDATE_CART_QUANTITY / REMOVE_FROM_CART operations when offline. Update SyncWorker when() block to dispatch cart operations to CartRepository for Firestore sync.

**Expected behavior after this plan:**
- User sees merged offline + pending sync banner at top of all screens
- Offline-only state shows "No internet connection" with WifiOff icon (not dismissible)
- Pending-only state shows "N items pending sync" with CloudUpload icon (dismissible)
- Combined state shows "Offline -- N items pending sync" with WifiOff icon (dismissible)
- Syncing state shows "Syncing N items..." with CircularProgressIndicator
- Banner tap navigates to queue management screen
- Queue screen shows all operations sorted newest-first
- FAILED operations show Retry and Discard buttons
- Retry resets to PENDING and enqueues SyncWorker
- Discard deletes operation immediately
- Banner auto-dismisses when queue drains to zero
- Banner reappears when new operations queued (pending count > dismissed count)
- Snackbar shows "Saved locally -- will sync when online" when repository enqueues offline write (wired in Phase 3+)

## Self-Check

**Verifying created files exist:**
```
FOUND: app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncViewModel.kt
FOUND: app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncBanner.kt
FOUND: app/src/main/java/com/wenubey/wenucommerce/queue_management/QueueManagementViewModel.kt
FOUND: app/src/main/java/com/wenubey/wenucommerce/queue_management/QueueManagementScreen.kt
```

**Verifying commits exist:**
```
FOUND: f6e2174
FOUND: 588c250
```

## Self-Check: PASSED
