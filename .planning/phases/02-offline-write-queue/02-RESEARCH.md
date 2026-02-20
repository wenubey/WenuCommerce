# Phase 2: Offline Write Queue - Research

**Researched:** 2026-02-20
**Domain:** WorkManager offline sync, PendingOperationEntity queue, Compose UI indicators, Koin WorkerFactory
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Pending sync indicator
- Global banner at the top of the screen (similar position to the existing offline banner)
- Shows count only: "3 items pending sync" — no breakdown by type
- Dismissible by the user; reappears only when the pending count increases (new offline write added)
- When offline AND pending items exist, show a single combined banner (e.g., "Offline — 3 items pending sync") instead of stacking two separate banners
- Amber/warning color tone to distinguish from the offline banner
- Shows a sync-in-progress animation (spinning icon or progress indicator) when the device reconnects and is actively syncing

#### Queued action feedback
- When a user performs a write action while offline, show confirmation with a generic snackbar: "Saved locally — will sync when online"
- Snackbar only appears for offline actions — online writes go directly to Firestore without queuing
- Sellers and customers get the same snackbar + banner treatment — no role-specific differences
- First offline action shows the snackbar; subsequent rapid offline actions do not repeat it (banner is already visible)
- Certain high-stakes actions (e.g., checkout/payment) should be blocked while offline with a clear message — Claude determines which specific actions to block

#### Sync completion & failure
- Successful sync is fully silent — banner simply disappears, no animation or confirmation
- Failed operations show an error snackbar: "Some items failed to sync"
- Auto-retry with a limit — retry automatically with backoff up to N times, then give up and notify
- Permanently failed operations (exhausted retries) stay in the queue marked as "failed" — user can see them and manually retry or discard

#### Queue management
- Tapping the pending sync banner opens a full-screen queue management screen
- Each item in the list shows type + status only: "Cart update — Pending" or "Cart update — Failed"
- Users can retry a failed item or discard it; pending items have no actions (auto-managed)

### Claude's Discretion
- Exact retry count and backoff strategy
- Which specific high-stakes actions to block while offline
- Queue screen layout and styling details
- WorkManager constraints and scheduling details
- PendingOperationEntity schema design

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SYNC-03 | Offline writes queue in PendingOperationEntity, auto-sync via WorkManager when online | WorkManager 2.11.1 with network constraints + exponential backoff; Room entity for operation queue; unique work chaining for ordered sync |
| SYNC-04 | User sees "pending sync" indicator when offline mutations are queued | DAO Flow<Int> for pending count; global Compose banner pattern (already exists for offline banner); DataStore for dismiss state; AnimatedVisibility for sync animation |

</phase_requirements>

---

## Summary

WorkManager 2.11.1 is the latest stable version (released January 28, 2026) and is not yet in the project — it must be added to `libs.versions.toml` and both `data/build.gradle.kts` and `app/build.gradle.kts`. The minimum SDK was updated from API 21 to API 23 in WorkManager 2.11.0, which aligns perfectly with this project's `minSdk = 24`. The core pattern is **lazy writes**: write to Room immediately (PendingOperationEntity), queue a WorkManager task with network constraints, and drain the queue sequentially when connectivity is restored.

The `PendingOperationEntity` table stores offline mutations with fields: `id` (auto-generated primary key), `operationType` (enum: ADD_TO_CART, UPDATE_CART, REMOVE_FROM_CART, etc.), `entityId` (the cart item / product / user ID being mutated), `payloadJson` (serialized mutation data), `status` (PENDING / IN_PROGRESS / FAILED), `retryCount`, `createdAt`, `lastAttemptAt`, and optionally `errorMessage`. The conflict resolution policy from Phase 1 research applies: Cart = local wins for adds / server for stock; Products = server wins; User = last-write-wins with `updatedAt` guard.

WorkManager integration with Koin requires the `koin-androidx-workmanager` dependency (version 4.0.1 to match the project's Koin BOM) and calling `workManagerFactory()` in the `startKoin` block. Workers are declared with the `worker { }` DSL in a Koin module and receive constructor-injected dependencies (repositories, DAOs, DispatcherProvider). The WorkManager initializer must be disabled in AndroidManifest.xml and replaced with Koin-managed initialization.

The UI pattern for the pending-sync banner is identical to the existing `OfflineConnectivityBanner` — a global `AnimatedVisibility` overlay in `MainActivity`, backed by a ViewModel that observes `pendingOperationDao.observePendingCount()`. The banner must merge with the offline banner when both conditions are true. A separate `dismissedPendingCount` value stored in DataStore tracks the last-dismissed count; the banner reappears only when the pending count increases above this value. The sync-in-progress animation is a `CircularProgressIndicator` shown only when `SyncWorker.state == RUNNING`.

**Primary recommendation:** Add WorkManager 2.11.1 + koin-androidx-workmanager 4.0.1; create `PendingOperationEntity` with operation-type enum and JSON payload; build a `SyncWorker` that drains the queue with exponential backoff (3 retries max, 30-second initial delay); wire the pending-count DAO Flow into a global banner ViewModel; merge the offline + pending banners into a single composable with conditional text.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.work:work-runtime-ktx` | 2.11.1 | WorkManager with Kotlin + coroutines support | Official Jetpack library for persistent background work; minSdk 23 matches project (24) |
| `io.insert-koin:koin-androidx-workmanager` | 4.0.1 | Koin DI for WorkManager workers | Matches project Koin BOM 4.0.1; enables constructor injection in Workers |
| Room (existing) | 2.6.1 | PendingOperationEntity persistence | Already in project; queue must survive app restarts |
| DataStore (existing) | 1.1.2 | Dismissed banner state | Already in project; lightweight key-value for UI state |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.work:work-testing` | 2.11.1 | WorkManager testing utilities | Add to `androidTest` for Worker unit tests and TestClock |
| `androidx.compose.material3:material3` | 2025.01.00 BOM | UI components (banner, snackbar) | Already in project; used for `CircularProgressIndicator` animation |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| WorkManager unique work chaining | Manual coroutine queue draining | WorkManager persists across process death; coroutines are lost on force-stop |
| PendingOperationEntity with JSON payload | Separate entity per operation type (CartPendingOp, ProductPendingOp) | JSON payload scales to any entity type; separate entities require more DAOs and Workers |
| DataStore for dismiss state | In-memory flag in ViewModel | DataStore persists across app restarts; ViewModel state is lost |

**Installation (libs.versions.toml):**
```toml
[versions]
work = "2.11.1"

[libraries]
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
koin-workmanager = { group = "io.insert-koin", name = "koin-androidx-workmanager" }  # Uses Koin BOM 4.0.1
```

**Add to data/build.gradle.kts:**
```kotlin
dependencies {
    implementation(libs.work.runtime.ktx)
    implementation(libs.koin.workmanager)
    androidTestImplementation(libs.work.testing)
}
```

**Add to app/build.gradle.kts (for worker classes in app module if needed):**
```kotlin
dependencies {
    implementation(libs.work.runtime.ktx)
    implementation(libs.koin.workmanager)
}
```

---

## Architecture Patterns

### Recommended Project Structure

```
data/src/main/java/com/wenubey/data/
├── local/
│   ├── entity/
│   │   └── PendingOperationEntity.kt       # NEW — queue table
│   ├── dao/
│   │   └── PendingOperationDao.kt          # NEW — Flow<Int> count + CRUD
│   ├── WenuCommerceDatabase.kt             # MODIFIED — add PendingOperationEntity to @Database
│   └── SyncManager.kt                      # MODIFIED — expose pending-count Flow
├── worker/
│   └── SyncWorker.kt                       # NEW — drains queue with retry logic
├── repository/
│   └── *RepositoryImpl.kt                  # MODIFIED — enqueue pending ops when offline
└── di/
    └── WorkerModule.kt                     # NEW — Koin module for workers

app/src/main/java/com/wenubey/wenucommerce/
├── core/
│   └── connectivity/
│       ├── PendingSyncBanner.kt            # NEW — merged offline + pending banner
│       ├── PendingSyncViewModel.kt         # NEW — observes pending count + dismiss state
│       └── OfflineConnectivityBanner.kt    # MODIFIED — merge logic with pending banner
├── queue_management/
│   ├── QueueManagementScreen.kt            # NEW — full-screen queue list
│   └── QueueManagementViewModel.kt         # NEW — observe all pending ops, retry/discard actions
├── di/
│   └── DataModule.kt                       # MODIFIED — add workerModule to appModules
└── WenuCommerceApplication.kt              # MODIFIED — disable WorkManager auto-init, call workManagerFactory()
```

### Pattern 1: PendingOperationEntity Design

**What:** A single Room entity representing any queued mutation. Operation type stored as enum string; payload as JSON. Status tracks lifecycle (PENDING → IN_PROGRESS → completed/FAILED).

**When to use:** All offline writes — cart mutations, profile updates, review submissions, etc.

**Example:**
```kotlin
// data/local/entity/PendingOperationEntity.kt
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,              // OperationType.name (ADD_TO_CART, UPDATE_PROFILE, etc.)
    val entityId: String,                   // ID of the cart item / product / user being mutated
    val payloadJson: String,                // JSON-serialized mutation data (add/update/delete payload)
    val status: String,                     // OperationStatus.name (PENDING, IN_PROGRESS, FAILED)
    val retryCount: Int = 0,
    val createdAt: String,                  // ISO 8601 timestamp
    val lastAttemptAt: String? = null,
    val errorMessage: String? = null,
)

enum class OperationType {
    ADD_TO_CART,
    UPDATE_CART_QUANTITY,
    REMOVE_FROM_CART,
    UPDATE_PROFILE,
    SUBMIT_REVIEW,
    // Add more as features are implemented
}

enum class OperationStatus {
    PENDING,      // Not yet attempted
    IN_PROGRESS,  // Currently being synced by worker
    FAILED,       // Exhausted retries
}
```

**DAO:**
```kotlin
@Dao
interface PendingOperationDao {
    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'PENDING' OR status = 'IN_PROGRESS'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_operations WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPending(): PendingOperationEntity?

    @Query("SELECT * FROM pending_operations ORDER BY createdAt DESC")
    fun observeAllOperations(): Flow<List<PendingOperationEntity>>

    @Insert
    suspend fun insert(operation: PendingOperationEntity): Long

    @Update
    suspend fun update(operation: PendingOperationEntity)

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_operations SET status = :status, lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, timestamp: String)

    @Query("UPDATE pending_operations SET retryCount = retryCount + 1, lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun incrementRetryCount(id: Long, timestamp: String)
}
```

### Pattern 2: SyncWorker with Exponential Backoff

**What:** A CoroutineWorker that drains the queue one operation at a time. Uses WorkManager's built-in exponential backoff by returning `Result.retry()` on network failures. Manually checks `runAttemptCount` to enforce a max-retry limit (WorkManager has no native max-retry API).

**When to use:** Enqueued automatically when a pending operation is inserted and network is unavailable; re-enqueued with constraints when network returns.

**Example:**
```kotlin
// data/worker/SyncWorker.kt
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val pendingOperationDao: PendingOperationDao,
    private val cartRepository: CartRepository,         // Injected via Koin
    private val profileRepository: ProfileRepository,   // etc.
    private val dispatcherProvider: DispatcherProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(dispatcherProvider.io()) {
        val maxRetries = 3  // Claude's discretion: 3 retries = 4 total attempts (initial + 3)

        // WorkManager doesn't expose max retries natively; check manually
        if (runAttemptCount > maxRetries) {
            Timber.w("SyncWorker max retries exceeded, marking operation as failed")
            return@withContext Result.failure()
        }

        val operation = pendingOperationDao.getNextPending()
        if (operation == null) {
            // Queue drained
            return@withContext Result.success()
        }

        try {
            pendingOperationDao.updateStatus(operation.id, OperationStatus.IN_PROGRESS.name, Clock.System.now().toString())

            when (OperationType.valueOf(operation.operationType)) {
                OperationType.ADD_TO_CART -> {
                    val payload = Json.decodeFromString<AddToCartPayload>(operation.payloadJson)
                    cartRepository.addToCart(payload.productId, payload.quantity)
                }
                OperationType.UPDATE_PROFILE -> {
                    val payload = Json.decodeFromString<UpdateProfilePayload>(operation.payloadJson)
                    profileRepository.updateProfile(payload)
                }
                // Handle other operation types
                else -> throw IllegalStateException("Unknown operation type: ${operation.operationType}")
            }

            // Success: delete from queue
            pendingOperationDao.deleteById(operation.id)

            // Re-enqueue worker to process next operation
            enqueueNextSync(applicationContext)
            Result.success()

        } catch (e: Exception) {
            Timber.e(e, "SyncWorker failed for operation ${operation.id}")
            pendingOperationDao.incrementRetryCount(operation.id, Clock.System.now().toString())

            if (runAttemptCount >= maxRetries) {
                // Exhausted retries: mark as FAILED, keep in queue for manual retry
                pendingOperationDao.updateStatus(
                    operation.id,
                    OperationStatus.FAILED.name,
                    Clock.System.now().toString()
                )
                Result.failure()  // Don't retry
            } else {
                Result.retry()  // Exponential backoff
            }
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "sync_pending_operations"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    Duration.ofSeconds(30)  // Initial delay; doubles on each retry
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,  // Replace old worker with new one
                workRequest
            )
        }

        private fun enqueueNextSync(context: Context) {
            // Chain next sync immediately if more items pending
            enqueue(context)
        }
    }
}
```

**Critical notes:**
- WorkManager 2.11.1 does NOT have a native `setMaxRetries()` API. The `runAttemptCount` property must be checked manually in `doWork()`.
- `Result.retry()` triggers automatic exponential backoff: 30s, 60s, 120s (with `BackoffPolicy.EXPONENTIAL` and 30-second initial delay).
- After max retries, the operation is marked `FAILED` but NOT deleted — it stays in the queue for the user to manually retry or discard from the queue management screen.

### Pattern 3: Koin WorkManager Integration

**What:** Disable WorkManager's default auto-initialization and replace it with Koin-managed initialization. Workers receive constructor-injected dependencies.

**When to use:** All Worker classes that need repository or DAO access.

**Example:**
```kotlin
// app/src/main/AndroidManifest.xml — disable auto-init
<application>
    <provider
        android:name="androidx.startup.InitializationProvider"
        android:authorities="${applicationId}.androidx-startup"
        tools:node="merge">
        <meta-data
            android:name="androidx.work.WorkManagerInitializer"
            android:value="androidx.startup"
            tools:node="remove" />
    </provider>
</application>

// app/src/main/java/.../WenuCommerceApplication.kt
class WenuCommerceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@WenuCommerceApplication)
            workManagerFactory()  // Enable Koin worker factory
            modules(appModules)
        }
    }
}

// app/src/main/java/.../di/DataModule.kt — add worker module
val workerModule = module {
    worker { SyncWorker(get(), get(), get(), get(), get(), get()) }
}

val appModules = listOf(
    firebaseModule,
    repositoryModule,
    dispatcherModule,
    // ... existing modules
    workerModule,  // NEW
)
```

**Koin will automatically provide `Context` and `WorkerParameters` — only inject custom dependencies (DAOs, repositories).**

### Pattern 4: Merged Offline + Pending Banner

**What:** A single global banner that shows offline status and pending-sync count. Three states: offline only, pending only, or combined. Banner is dismissible; reappears only when pending count increases. Shows sync animation when worker is running.

**When to use:** Global overlay in `MainActivity`, replacing the standalone `OfflineConnectivityBanner`.

**Example:**
```kotlin
// PendingSyncViewModel.kt
class PendingSyncViewModel(
    connectivityObserver: ConnectivityObserver,
    syncManager: SyncManager,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    val isOnline = connectivityObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pendingCount = syncManager.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val dismissedCountKey = intPreferencesKey("dismissed_pending_count")
    private val dismissedCount = dataStore.data
        .map { it[dismissedCountKey] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val shouldShowBanner: StateFlow<Boolean> = combine(
        isOnline,
        pendingCount,
        dismissedCount
    ) { online, pending, dismissed ->
        !online || (pending > 0 && pending > dismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun dismissBanner() {
        viewModelScope.launch {
            dataStore.edit { it[dismissedCountKey] = pendingCount.value }
        }
    }
}

// PendingSyncBanner.kt
@Composable
fun PendingSyncBanner(
    isOnline: Boolean,
    pendingCount: Int,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = Color(0xFFFFC107),  // Amber — same as offline banner
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (!isOnline) Icons.Default.WifiOff else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                )
            }

            Text(
                text = when {
                    !isOnline && pendingCount > 0 -> "Offline — $pendingCount items pending sync"
                    !isOnline -> stringResource(R.string.no_internet_connection)
                    else -> "$pendingCount items pending sync"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                )
            }
        }
    }
}

// In MainActivity.kt
val pendingSyncVm: PendingSyncViewModel = koinViewModel()
val isOnline by pendingSyncVm.isOnline.collectAsStateWithLifecycle()
val pendingCount by pendingSyncVm.pendingCount.collectAsStateWithLifecycle()
val shouldShowBanner by pendingSyncVm.shouldShowBanner.collectAsStateWithLifecycle()
val isSyncing by pendingSyncVm.isSyncing.collectAsStateWithLifecycle()  // From WorkInfo state

AnimatedVisibility(
    visible = shouldShowBanner,
    modifier = Modifier.align(Alignment.TopCenter),
    enter = slideInVertically { -it },
    exit = slideOutVertically { -it },
) {
    PendingSyncBanner(
        isOnline = isOnline,
        pendingCount = pendingCount,
        isSyncing = isSyncing,
        onDismiss = { pendingSyncVm.dismissBanner() },
        modifier = Modifier.clickable { navController.navigate(QueueManagementRoute) },
    )
}
```

**Key decisions:**
- Dismiss state stored in DataStore with the count at dismissal time; banner reappears only when count increases.
- Tapping the banner navigates to the queue management screen (not just the dismiss button).
- Sync animation (CircularProgressIndicator) shown only when `WorkInfo.state == RUNNING`.

### Pattern 5: Queue Management Screen

**What:** A full-screen list of all pending operations (both PENDING and FAILED). Users can retry or discard FAILED operations; PENDING operations show no actions.

**When to use:** Accessed by tapping the pending-sync banner.

**Example:**
```kotlin
// QueueManagementViewModel.kt
class QueueManagementViewModel(
    private val pendingOperationDao: PendingOperationDao,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    val operations = pendingOperationDao.observeAllOperations()
        .map { entities -> entities.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retryOperation(id: Long) {
        viewModelScope.launch(dispatcherProvider.io()) {
            pendingOperationDao.updateStatus(id, OperationStatus.PENDING.name, Clock.System.now().toString())
            // Trigger SyncWorker to process the now-pending operation
            SyncWorker.enqueue(applicationContext)
        }
    }

    fun discardOperation(id: Long) {
        viewModelScope.launch(dispatcherProvider.io()) {
            pendingOperationDao.deleteById(id)
        }
    }
}

data class PendingOperationUiModel(
    val id: Long,
    val displayName: String,     // "Cart update", "Profile update", etc.
    val status: OperationStatus,
    val createdAt: String,       // Formatted timestamp
)

// QueueManagementScreen.kt
@Composable
fun QueueManagementScreen(
    viewModel: QueueManagementViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val operations by viewModel.operations.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending Sync Queue") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(operations, key = { it.id }) { op ->
                QueueItemCard(
                    operation = op,
                    onRetry = { viewModel.retryOperation(op.id) },
                    onDiscard = { viewModel.discardOperation(op.id) },
                )
            }
        }
    }
}

@Composable
fun QueueItemCard(
    operation: PendingOperationUiModel,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${operation.status.name} • ${operation.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (operation.status == OperationStatus.FAILED) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    }
                    IconButton(onClick = onDiscard) {
                        Icon(Icons.Default.Delete, contentDescription = "Discard")
                    }
                }
            }
        }
    }
}
```

### Anti-Patterns to Avoid

- **Not disabling WorkManager auto-initialization:** If the default initializer runs alongside Koin's `workManagerFactory()`, Workers won't receive injected dependencies. Always disable via AndroidManifest and use Koin-managed init.
- **Deleting FAILED operations automatically:** Per user decision, failed operations must stay in the queue for manual retry or discard. Only `Result.success()` should delete the operation.
- **Stacking two separate banners:** The offline banner and pending-sync banner must merge into a single composable with conditional text. Stacking creates a jarring UX.
- **Not checking `runAttemptCount` in Worker:** WorkManager has no native max-retry API. Without a manual check, the worker will retry indefinitely (capped at 5 hours max backoff, but still wasteful).
- **Using `ExistingWorkPolicy.KEEP` for sync worker:** If an old worker is still running when a new operation is enqueued, `KEEP` prevents the new worker from starting. Use `REPLACE` to ensure the latest worker always runs.
- **JSON payload without type safety:** Always define a sealed class or data class per operation type and serialize/deserialize with `kotlinx.serialization`. Raw JSON strings are error-prone.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Exponential backoff retry logic | Custom delay calculation with coroutine delay | WorkManager `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ...)` | WorkManager handles exponential backoff natively; persists across app restarts; custom coroutines are lost on force-stop |
| Network state monitoring for sync trigger | Manual `NetworkCallback` + coroutine launch | WorkManager `Constraints.setRequiredNetworkType(NetworkType.CONNECTED)` | WorkManager constraints are declarative and persist; manual triggers require managing lifecycle and state |
| Operation queue ordering | In-memory LinkedList or custom queue | Room `PendingOperationEntity` with `ORDER BY createdAt ASC` | Room persists across restarts; in-memory queues are lost; Room DAO queries are SQL-backed and guaranteed ordered |
| Worker dependency injection | Manual singleton access or `WorkerFactory` subclass | Koin `workManagerFactory()` + `worker { }` DSL | Koin handles constructor injection; manual factory requires boilerplate for every worker |

**Key insight:** WorkManager is purpose-built for persistent, constraint-based background work. The hardest part of this phase is not WorkManager itself — it is the operation payload design. A single `PendingOperationEntity` with a type enum and JSON payload scales to any mutation type, but requires careful serialization and deserialization per operation.

---

## Common Pitfalls

### Pitfall 1: WorkManager Auto-Init Not Disabled

**What goes wrong:** If `androidx.work.WorkManagerInitializer` is not removed from AndroidManifest, WorkManager initializes with its default `WorkerFactory` before Koin's `workManagerFactory()` runs. Workers fail to receive injected dependencies, crashing with `NullPointerException` or `NoBeanDefFoundException`.

**Why it happens:** WorkManager auto-initializes via Jetpack Startup by default. Koin's WorkerFactory must be the ONLY factory.

**How to avoid:** Add `tools:node="remove"` to the `WorkManagerInitializer` meta-data in AndroidManifest.xml (see Pattern 3 above).

**Warning signs:** Worker crashes with "lateinit property repository has not been initialized" or Koin error "No bean definition found for type [Repository]".

### Pitfall 2: No Max Retry Check in Worker

**What goes wrong:** WorkManager does not have a native max-retry API. Without a manual `runAttemptCount` check, the worker retries indefinitely (up to 5 hours max backoff). This wastes battery and network on operations that will never succeed (e.g., server returned 404).

**Why it happens:** Developers assume `setBackoffCriteria` includes a max-retry limit. It does not — it only controls delay duration.

**How to avoid:** Always check `if (runAttemptCount > maxRetries)` at the start of `doWork()` and return `Result.failure()` with an error state update.

**Warning signs:** Worker logs show 10+ retry attempts; battery drain from background work; operations stuck in IN_PROGRESS state.

### Pitfall 3: Banner Dismiss State Not Persisted

**What goes wrong:** If the dismiss state is stored in ViewModel-only (`MutableStateFlow`), the banner reappears on app restart even if the user dismissed it and the pending count hasn't changed.

**Why it happens:** ViewModel state is lost on process death. The banner logic must persist the dismissed count to survive restarts.

**How to avoid:** Store the dismissed count in DataStore with a Preferences key. Compare `pendingCount > dismissedCount` to decide whether to show the banner.

**Warning signs:** Banner always shows on app restart if pending count > 0, even if user dismissed it before killing the app.

### Pitfall 4: Blocking High-Stakes Actions Without Clear UI

**What goes wrong:** Per user decision, "checkout/payment should be blocked while offline with a clear message." If the checkout button is simply disabled with no explanation, users will be confused.

**Why it happens:** Developers disable UI without explaining why.

**How to avoid:** Replace the checkout button with a full-width banner or dialog: "Checkout requires an internet connection. Please go online to complete your purchase."

**Warning signs:** User taps a grayed-out button with no feedback; support tickets asking "why can't I checkout?"

### Pitfall 5: Syncing Failed Operations in Parallel

**What goes wrong:** If multiple failed operations are retried in parallel (e.g., user taps "Retry All"), Firestore rate limits may cause additional failures or race conditions (e.g., cart item added before cart created).

**Why it happens:** Parallel execution seems faster, but Firestore writes have rate limits and ordering requirements.

**How to avoid:** Process operations sequentially: `getNextPending()` in `SyncWorker`, complete or fail, then enqueue the next sync. Never run multiple SyncWorker instances concurrently.

**Warning signs:** Firestore errors "TOO_MANY_REQUESTS" or "Document does not exist" on retry; cart updates applied out of order.

### Pitfall 6: Not Handling Worker CANCELLED State

**What goes wrong:** If the user force-stops the app or clears app data while a worker is running, the worker state is `CANCELLED`. If `doWork()` doesn't check `isStopped`, the operation remains in IN_PROGRESS state forever.

**Why it happens:** Developers assume `doWork()` runs to completion. It doesn't — WorkManager can cancel mid-execution.

**How to avoid:** Wrap critical sections in `if (isStopped) return Result.failure()` checks. On failure, reset the operation status from IN_PROGRESS back to PENDING.

**Warning signs:** Operations stuck in IN_PROGRESS state; queue draining stops after force-stop.

---

## Code Examples

### WorkManager Setup with Koin

```kotlin
// Source: https://insert-koin.io/docs/reference/koin-android/workmanager/
// libs.versions.toml
[versions]
work = "2.11.1"

[libraries]
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
koin-workmanager = { group = "io.insert-koin", name = "koin-androidx-workmanager" }

// app/build.gradle.kts
dependencies {
    implementation(libs.work.runtime.ktx)
    implementation(libs.koin.workmanager)
}

// WenuCommerceApplication.kt
class WenuCommerceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@WenuCommerceApplication)
            workManagerFactory()  // Enable Koin WorkerFactory
            modules(appModules)
        }
    }
}

// AndroidManifest.xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>

// Koin module
val workerModule = module {
    worker { SyncWorker(get(), get(), get(), get(), get(), get()) }
}
```

### SyncWorker with Max Retry Check

```kotlin
// Source: Official Android documentation + WorkManager 2.11.1 release notes
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val pendingOperationDao: PendingOperationDao,
    private val cartRepository: CartRepository,
    private val dispatcherProvider: DispatcherProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(dispatcherProvider.io()) {
        val maxRetries = 3

        // WorkManager has no native max-retry API; check manually
        if (runAttemptCount > maxRetries) {
            val operation = pendingOperationDao.getNextPending() ?: return@withContext Result.success()
            pendingOperationDao.updateStatus(
                operation.id,
                OperationStatus.FAILED.name,
                Clock.System.now().toString()
            )
            Timber.w("Max retries exceeded for operation ${operation.id}")
            return@withContext Result.failure()
        }

        val operation = pendingOperationDao.getNextPending()
        if (operation == null) {
            return@withContext Result.success()  // Queue drained
        }

        try {
            pendingOperationDao.updateStatus(operation.id, OperationStatus.IN_PROGRESS.name, Clock.System.now().toString())

            when (OperationType.valueOf(operation.operationType)) {
                OperationType.ADD_TO_CART -> {
                    val payload = Json.decodeFromString<AddToCartPayload>(operation.payloadJson)
                    cartRepository.addToCart(payload.productId, payload.quantity)
                }
                // Handle other types
            }

            // Success: delete from queue
            pendingOperationDao.deleteById(operation.id)
            enqueueNextSync(applicationContext)
            Result.success()

        } catch (e: Exception) {
            Timber.e(e, "SyncWorker failed for operation ${operation.id}")
            pendingOperationDao.incrementRetryCount(operation.id, Clock.System.now().toString())

            if (runAttemptCount >= maxRetries) {
                pendingOperationDao.updateStatus(operation.id, OperationStatus.FAILED.name, Clock.System.now().toString())
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_pending_operations",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        private fun enqueueNextSync(context: Context) {
            enqueue(context)
        }
    }
}
```

### PendingOperationEntity + DAO

```kotlin
// data/local/entity/PendingOperationEntity.kt
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,              // OperationType.name
    val entityId: String,                   // Cart item / Product / User ID
    val payloadJson: String,                // JSON-serialized mutation payload
    val status: String,                     // OperationStatus.name
    val retryCount: Int = 0,
    val createdAt: String,
    val lastAttemptAt: String? = null,
    val errorMessage: String? = null,
)

enum class OperationType {
    ADD_TO_CART,
    UPDATE_CART_QUANTITY,
    REMOVE_FROM_CART,
    UPDATE_PROFILE,
    SUBMIT_REVIEW,
}

enum class OperationStatus {
    PENDING,
    IN_PROGRESS,
    FAILED,
}

// data/local/dao/PendingOperationDao.kt
@Dao
interface PendingOperationDao {
    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'PENDING' OR status = 'IN_PROGRESS'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_operations WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPending(): PendingOperationEntity?

    @Query("SELECT * FROM pending_operations ORDER BY createdAt DESC")
    fun observeAllOperations(): Flow<List<PendingOperationEntity>>

    @Insert
    suspend fun insert(operation: PendingOperationEntity): Long

    @Update
    suspend fun update(operation: PendingOperationEntity)

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_operations SET status = :status, lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, timestamp: String)

    @Query("UPDATE pending_operations SET retryCount = retryCount + 1, lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun incrementRetryCount(id: Long, timestamp: String)
}

// data/local/WenuCommerceDatabase.kt — add to @Database
@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        UserEntity::class,
        PendingOperationEntity::class,  // NEW
    ],
    version = 2,  // Increment version
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class WenuCommerceDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userDao(): UserDao
    abstract fun pendingOperationDao(): PendingOperationDao  // NEW
}
```

### Merged Offline + Pending Banner

```kotlin
// PendingSyncViewModel.kt
class PendingSyncViewModel(
    connectivityObserver: ConnectivityObserver,
    private val pendingOperationDao: PendingOperationDao,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    val isOnline = connectivityObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pendingCount = pendingOperationDao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val dismissedCountKey = intPreferencesKey("dismissed_pending_count")
    private val dismissedCount = dataStore.data
        .map { it[dismissedCountKey] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val shouldShowBanner: StateFlow<Boolean> = combine(
        isOnline,
        pendingCount,
        dismissedCount
    ) { online, pending, dismissed ->
        !online || (pending > 0 && pending > dismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Observe WorkManager state to detect sync-in-progress
    val isSyncing: StateFlow<Boolean> = workManagerStateFlow()
        .map { it == WorkInfo.State.RUNNING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun workManagerStateFlow(): Flow<WorkInfo.State> = callbackFlow {
        val workManager = WorkManager.getInstance(applicationContext)
        val liveData = workManager.getWorkInfosForUniqueWorkLiveData("sync_pending_operations")
        val observer = Observer<List<WorkInfo>> { workInfos ->
            trySend(workInfos.firstOrNull()?.state ?: WorkInfo.State.SUCCEEDED)
        }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }

    fun dismissBanner() {
        viewModelScope.launch {
            dataStore.edit { it[dismissedCountKey] = pendingCount.value }
        }
    }
}

// PendingSyncBanner.kt
@Composable
fun PendingSyncBanner(
    isOnline: Boolean,
    pendingCount: Int,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clickable { onTap() },
        color = Color(0xFFFFC107),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (!isOnline) Icons.Default.WifiOff else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                )
            }

            Text(
                text = when {
                    !isOnline && pendingCount > 0 -> "Offline — $pendingCount items pending sync"
                    !isOnline -> stringResource(R.string.no_internet_connection)
                    else -> "$pendingCount items pending sync"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                )
            }
        }
    }
}

// In MainActivity.kt
val pendingSyncVm: PendingSyncViewModel = koinViewModel()
val isOnline by pendingSyncVm.isOnline.collectAsStateWithLifecycle()
val pendingCount by pendingSyncVm.pendingCount.collectAsStateWithLifecycle()
val shouldShowBanner by pendingSyncVm.shouldShowBanner.collectAsStateWithLifecycle()
val isSyncing by pendingSyncVm.isSyncing.collectAsStateWithLifecycle()

AnimatedVisibility(
    visible = shouldShowBanner,
    modifier = Modifier.align(Alignment.TopCenter),
    enter = slideInVertically { -it },
    exit = slideOutVertically { -it },
) {
    PendingSyncBanner(
        isOnline = isOnline,
        pendingCount = pendingCount,
        isSyncing = isSyncing,
        onDismiss = { pendingSyncVm.dismissBanner() },
        onTap = { navController.navigate(QueueManagementRoute) },
    )
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual coroutine queue draining | WorkManager with network constraints | WorkManager 1.0 stable (2019) | WorkManager persists across restarts; coroutines are lost on force-stop |
| `LiveData` for WorkInfo observation | `Flow`-based `getWorkInfosFlow()` | WorkManager 2.9.0 (2024) | Flow integrates with Compose; `collectAsStateWithLifecycle` replaces `observeAsState` |
| `setRequiresNetwork(true)` | `setRequiredNetworkType(NetworkType.CONNECTED)` | WorkManager 2.10.0 (2025) | More granular control: CONNECTED, UNMETERED, NOT_ROAMING, TEMPORARILY_UNMETERED |
| WorkManager minSdk 21 | WorkManager minSdk 23 | WorkManager 2.11.0 (January 2026) | Aligns with project minSdk 24; removes legacy API 21-22 code paths |
| Hilt `HiltWorkerFactory` | Koin `workManagerFactory()` | Koin 3.2.0 (2022) | Koin is simpler and already in the project; Hilt requires Gradle plugin + annotation processing |

**Deprecated/outdated:**
- `WorkManager.getInstance().enqueue(workRequest)` without unique work name: Replaced by `enqueueUniqueWork` with conflict policies to prevent duplicate workers.
- Separate `WorkerFactory` subclass for DI: Koin `workManagerFactory()` abstracts this; no manual factory subclass needed.

---

## Open Questions

1. **Should cart mutations be deduplicated in the queue?**
   - What we know: If a user adds product X to cart, increments quantity, then decrements quantity while offline, three separate operations are queued. Syncing them in order is correct but wasteful.
   - What's unclear: Whether to implement a deduplication layer that merges consecutive operations on the same entity.
   - Recommendation (Claude's discretion): Do NOT deduplicate in Phase 2. Merge logic is complex and error-prone (e.g., add + delete = no-op, but add + update + delete = delete only). Queue all operations as-is; deduplication is a future optimization.

2. **High-stakes actions: which to block?**
   - What we know: Per user decision, "checkout/payment should be blocked while offline with a clear message — Claude determines which specific actions to block."
   - What's unclear: Whether to also block review submission, profile photo upload, or product creation.
   - Recommendation (Claude's discretion): Block only checkout/payment in Phase 2. Reviews and profile updates can be queued safely; payment requires immediate server validation (fraud detection, inventory lock). Add a full-width banner on checkout screen: "Checkout requires an internet connection."

3. **Exact retry count and backoff strategy**
   - What we know: Per user decision, "auto-retry with a limit — retry automatically with backoff up to N times, then give up and notify."
   - What's unclear: Optimal N and backoff duration.
   - Recommendation (Claude's discretion): 3 retries (4 total attempts: initial + 3 retries) with exponential backoff starting at 30 seconds. This gives: attempt 1 (immediate), attempt 2 (+30s), attempt 3 (+60s), attempt 4 (+120s). Total time to failure: ~3.5 minutes. Longer is wasteful for permanent failures (e.g., 404); shorter is insufficient for temporary network blips.

---

## Sources

### Primary (HIGH confidence)
- [WorkManager Releases](https://developer.android.com/jetpack/androidx/releases/work) — Version 2.11.1 confirmed; minSdk 23 requirement
- [Build an offline-first app](https://developer.android.com/topic/architecture/data-layer/offline-first) — Lazy write pattern, conflict resolution, WorkManager sync queue
- [Koin WorkManager Integration](https://insert-koin.io/docs/reference/koin-android/workmanager/) — `workManagerFactory()` setup, `worker { }` DSL
- [Chaining work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/chain-work) — Unique work policies, sequential task chaining
- Codebase inspection: `OfflineConnectivityBanner.kt`, `MainActivity.kt`, `SyncManager.kt`, `PendingOperationDao` (does not exist yet)

### Secondary (MEDIUM confidence)
- [Offline-First Android: Build resilient apps](https://medium.com/@vishalpvijayan4/offline-first-android-build-resilient-apps-for-spotty-networks-with-room-datastore-workmanager-4a23144e8ea2) — WorkManager sync patterns, verified with official docs
- [WorkManager in 2025: 5 Patterns That Actually Work in Production](https://medium.com/@hiren6997/workmanager-in-2025-5-patterns-that-actually-work-in-production-fde952c0d095) — Runtime adaptation pattern (check conditions inside worker)
- [Android Worker Max Retries](https://medium.com/@chaitanyaduse/android-worker-max-retries-42a636684fce) — `runAttemptCount` manual check required, verified with official docs
- [BackoffPolicy API Reference](https://developer.android.com/reference/androidx/work/BackoffPolicy) — EXPONENTIAL default, 10-second minimum backoff

### Tertiary (LOW confidence)
None — all findings cross-verified with official sources or codebase.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — WorkManager 2.11.1 version confirmed via official releases page; Koin integration verified with official Koin docs
- Architecture: HIGH — PendingOperationEntity pattern verified with offline-first architecture guide; lazy write pattern is official Android recommendation
- WorkManager patterns: HIGH — Constraints, backoff, unique work verified with official Android docs
- UI patterns: HIGH — Banner pattern verified via existing `OfflineConnectivityBanner.kt`; DataStore already in project
- Pitfalls: HIGH — Manual max-retry check confirmed via official issue tracker; WorkManager auto-init conflict documented in Koin docs

**Research date:** 2026-02-20
**Valid until:** 2026-03-20 (WorkManager 2.11.x is stable; Android architecture patterns are long-term)
