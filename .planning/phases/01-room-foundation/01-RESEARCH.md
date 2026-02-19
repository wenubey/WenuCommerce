# Phase 1: Room Foundation - Research

**Researched:** 2026-02-19
**Domain:** Android Room database, Firestore write-through sync, ConnectivityManager, Jetpack Compose offline UI
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Connectivity indicator
- Top banner, visible on every screen (global, not per-screen)
- Warning yellow/amber color tone to signal degraded experience
- Includes a no-wifi icon + "No internet connection" text
- Overlays on top of content (no layout shift)
- Animates in (slide down) and out (slide up) — smooth transitions
- Auto-dismisses immediately when connectivity returns (no "Back online" message)

#### Offline browsing experience
- The offline banner is the sole staleness cue — no "last synced" timestamps or visual degradation on content
- First launch with no network: empty state with friendly illustration + "Connect to the internet to get started" + retry button
- Uncached product images show a generic product placeholder icon — layout stays consistent
- All cached content is fully interactive offline — browsing, product detail, categories all work from cache
- Prices shown as-is from cache — no disclaimers; accuracy is validated at checkout time
- Search works against cached Room data — results may be incomplete but functional
- Deleted products (server-side) are handled on reconnect: show cached data while offline, then show "product unavailable" after sync detects deletion

#### Sync & loading strategy
- Pull-to-refresh available on content screens + automatic background sync
- Firestore snapshot listeners stay active for real-time updates — data pushes to Room as it arrives
- On app launch with network: navigate to home immediately with progressive loading — show content as it syncs in
- Loading indicator: shimmer/skeleton placeholders mimicking content layout (product cards, category lists)
- Shimmer appears both on first launch (empty Room) and during pull-to-refresh
- No additional feedback on pull-to-refresh completion — data just updates
- On sync failure: brief snackbar "Sync failed — showing cached data" then continue with cached content

### Claude's Discretion
- Shimmer skeleton exact design and card shapes
- Room entity field mapping and DAO query design
- Firestore listener lifecycle management (when to attach/detach)
- ConnectivityObserver implementation approach
- Schema version 1 entity design details
- Error retry logic for Firestore listeners

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SYNC-01 | Room database serves as single source of truth for all data reads | Room DAO `Flow<T>` queries enable reactive UI; entities + mappers isolate domain from storage layer |
| SYNC-02 | Firestore snapshot listeners write-through to Room in background | `callbackFlow` pattern captures Firestore changes; `upsert`/`insert` in DAOs persist locally; UI observes Room only |
| SYNC-05 | Connectivity state observed via ConnectivityManager and surfaced in UI | `registerDefaultNetworkCallback` + `callbackFlow` wrapping; global Compose banner with `AnimatedVisibility(slideInVertically)` |
| SYNC-06 | Existing repositories (Product, Category, Auth) migrated to Room-first pattern | Write-through pattern: Firestore listener → Room write → DAO Flow → ViewModel; Auth caches `UserEntity` for offline profile read |
</phase_requirements>

---

## Summary

WenuCommerce already has Room 2.6.1 declared as a dependency in `data/build.gradle.kts` with the `androidx.room` Gradle plugin configured and `schemaDirectory("$projectDir/schemas")` set. However, no entities, DAOs, or `RoomDatabase` subclass exist yet — the dependency is in place but completely unused. The `app/build.gradle.kts` also declares Room, which must be removed before writing entities to eliminate duplicate KSP annotation processing.

The migration pattern is well-established: Firestore snapshot listeners become the *write path* (Firestore → Room), and DAO `Flow<T>` queries become the *read path* (Room → ViewModel). This is a mechanical but careful refactor of three repository implementations — `ProductRepositoryImpl`, `CategoryRepositoryImpl`, and `AuthRepositoryImpl`. The biggest technical complexity is entity design: the domain models (`Product`, `Category`, `User`) contain nested objects and `List<T>` fields that Room cannot store natively and must be serialized to JSON strings via TypeConverters.

The connectivity indicator is a global Compose composable wrapping the nav graph root, using `ConnectivityManager.registerDefaultNetworkCallback` wrapped in a `callbackFlow`. The app already has a precedent for this pattern: `EmailVerificationNotificationBar` uses `AnimatedVisibility` in a `Scaffold.topBar` slot. The connectivity banner will use the same approach — a `Box` overlay with `slideInVertically` / `slideOutVertically` to avoid layout shift.

**Primary recommendation:** Build the Room database in the `data` module only, design entities as flat-as-possible with JSON TypeConverters for nested/list fields, migrate repositories one at a time using the write-through pattern, and wire the global connectivity banner into `MainActivity`'s `setContent` block as a `Box` overlay above the `RootNavigationGraph`.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.room:room-runtime` | 2.6.1 | Room persistence engine | Already declared in `data/build.gradle.kts` |
| `androidx.room:room-ktx` | 2.6.1 | Coroutines/Flow support for DAOs | Required for `Flow<T>` DAO return types |
| `androidx.room:room-compiler` (KSP) | 2.6.1 | Annotation processor — generates DAO impls | Already declared via `ksp(libs.room.compiler)` |
| `androidx.room` Gradle plugin | 2.6.1 | Manages `schemaDirectory` output | Already applied in `data/build.gradle.kts`; controls schema JSON location |
| `kotlinx.serialization` | 2.1.0 (Kotlin) | JSON serialization for TypeConverters | Already in the project as a Kotlin plugin |
| `android.net.ConnectivityManager` | Android API 24+ | Network state detection | Platform API — no dependency needed |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.room:room-paging` | 2.6.1 | Paging 3 integration | Already declared; needed when product lists require paging |
| `androidx.room:room-testing` | 2.6.1 | Migration testing helpers | Add to `androidTest` for schema migration verification |
| Koin `single { }` DSL | 4.0.1 | DI wiring of database + DAOs | Already in project; add `databaseModule` to `appModules` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `kotlinx.serialization` for TypeConverters | Gson | Gson adds 100KB+ and is not null-safe; project already uses kotlinx.serialization throughout |
| `callbackFlow` for ConnectivityObserver | `BroadcastReceiver` | Broadcast receiver is the legacy approach; `NetworkCallback` + `callbackFlow` is lifecycle-aware and Flow-native |
| `Box` overlay for global banner | `Scaffold.topBar` on every screen | Per-screen approach requires banner VM on every screen's Scaffold; `Box` overlay at root needs no changes per-screen |

---

## Architecture Patterns

### Recommended Project Structure

```
data/src/main/java/com/wenubey/data/
├── local/
│   ├── WenuCommerceDatabase.kt        # @Database abstract class, version = 1
│   ├── entity/
│   │   ├── ProductEntity.kt           # @Entity — flat, with JSON columns for nested types
│   │   ├── CategoryEntity.kt          # @Entity — subcategories stored as JSON string
│   │   └── UserEntity.kt             # @Entity — cached current user only (single row)
│   ├── dao/
│   │   ├── ProductDao.kt             # Flow<List<ProductEntity>>, upsertAll, deleteById
│   │   ├── CategoryDao.kt            # Flow<List<CategoryEntity>>, upsertAll
│   │   └── UserDao.kt               # Flow<UserEntity?>, upsert, clear
│   ├── converter/
│   │   └── RoomTypeConverters.kt     # @TypeConverters — List<String>, List<ProductImage>, etc.
│   └── mapper/
│       ├── ProductMapper.kt          # ProductEntity <-> Product domain model
│       ├── CategoryMapper.kt         # CategoryEntity <-> Category domain model
│       └── UserMapper.kt            # UserEntity <-> User domain model
├── repository/
│   ├── ProductRepositoryImpl.kt      # MODIFIED: Firestore → Room, DAO → ViewModel
│   ├── CategoryRepositoryImpl.kt     # MODIFIED: same pattern
│   └── AuthRepositoryImpl.kt        # MODIFIED: Room-first user caching
└── connectivity/
    └── ConnectivityObserver.kt       # callbackFlow wrapping NetworkCallback
```

```
app/src/main/java/com/wenubey/wenucommerce/
├── core/
│   └── connectivity/
│       ├── ConnectivityBannerState.kt
│       └── OfflineConnectivityBanner.kt  # Global amber banner composable
├── di/
│   └── DataModule.kt                # ADD: databaseModule with Room singleton + DAOs
└── MainActivity.kt                  # MODIFIED: Box overlay wrapping RootNavigationGraph
```

### Pattern 1: Room-First Repository (Write-Through)

**What:** The repository exposes two paths. The Firestore listener writes through to Room. The UI-facing method reads exclusively from the Room DAO Flow.

**When to use:** All repository methods that currently expose `callbackFlow` directly from Firestore.

**Example (Category):**
```kotlin
// Source: Android Developers - offline-first pattern
class CategoryRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val categoryDao: CategoryDao,       // NEW
    dispatcherProvider: DispatcherProvider,
) : CategoryRepository {

    // READ PATH: UI observes Room DAO — never Firestore directly
    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeActiveCategories()
            .map { entities -> entities.map { it.toDomain() } }

    // SYNC PATH: Firestore listener writes to Room; call this from a service/scope
    fun startSync(scope: CoroutineScope) {
        scope.launch(ioDispatcher) {
            callbackFlow {
                val listener = firestore.collection(CATEGORIES_COLLECTION)
                    .whereEqualTo("isActive", true)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) { close(error); return@addSnapshotListener }
                        val entities = snapshot?.documents?.mapNotNull { doc ->
                            doc.toObject(Category::class.java)?.toEntity()
                        } ?: emptyList()
                        trySend(entities)
                    }
                awaitClose { listener.remove() }
            }.catch { e ->
                Timber.e(e, "Category sync failed")
                // UI continues from Room cache; snackbar handled at VM level
            }.collect { entities ->
                categoryDao.upsertAll(entities)
            }
        }
    }
}
```

### Pattern 2: Room Entity Design with TypeConverters

**What:** Domain models contain nested objects (`ProductShipping`, `List<ProductImage>`, `List<ProductVariant>`) that Room cannot store natively. Serialize them to JSON strings in TypeConverters.

**When to use:** Any entity field that is a data class or `List<T>` of data classes.

**Example:**
```kotlin
// Entity — in data/local/entity/ProductEntity.kt
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val sellerId: String,
    val categoryId: String,
    val categoryName: String,
    val subcategoryId: String,
    val subcategoryName: String,
    val basePrice: Double,
    val compareAtPrice: Double?,
    val currency: String,
    val status: String,                  // ProductStatus.name
    val condition: String,               // ProductCondition.name
    val averageRating: Double,
    val reviewCount: Int,
    val totalStockQuantity: Int,
    val viewCount: Int,
    val purchaseCount: Int,
    val updatedAt: String,
    val createdAt: String,
    // JSON-serialized nested types:
    val imagesJson: String,              // List<ProductImage>
    val variantsJson: String,            // List<ProductVariant>
    val shippingJson: String,            // ProductShipping
    val tagsJson: String,                // List<String> (tag IDs)
    val tagNamesJson: String,            // List<String>
    val searchKeywordsJson: String,      // List<String>
)

// TypeConverter — in data/local/converter/RoomTypeConverters.kt
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class RoomTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(value)
    // Repeat for List<ProductImage>, List<ProductVariant>, ProductShipping
}

// Database — in data/local/WenuCommerceDatabase.kt
@Database(
    entities = [ProductEntity::class, CategoryEntity::class, UserEntity::class],
    version = 1,
    exportSchema = true,   // Required — schema JSON tracked in git
)
@TypeConverters(RoomTypeConverters::class)
abstract class WenuCommerceDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userDao(): UserDao
}
```

### Pattern 3: ConnectivityObserver with callbackFlow

**What:** Wraps `ConnectivityManager.NetworkCallback` in a `callbackFlow`. Emits initial state synchronously before registering the callback so collectors always have a current value.

**When to use:** Global, singleton — created once in Koin, collected in a top-level ViewModel.

**Example:**
```kotlin
// Source: Android Developers - Monitor connectivity status
class ConnectivityObserver(private val context: Context) {

    val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Emit initial state before registering callback
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        trySend(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            override fun onLost(network: Network) {
                trySend(false)
            }
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                val hasInternet = networkCapabilities
                    .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(hasInternet)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
      .shareIn(scope = applicationScope, started = SharingStarted.WhileSubscribed(5_000), replay = 1)
}
```

**Critical note on initial state:** If the device has no default network when the callback is registered, `onAvailable` is never called. The explicit initial-state emit before registering the callback prevents this bug.

### Pattern 4: Global Connectivity Banner (Overlay, No Layout Shift)

**What:** A `Box` wrapping the entire navigation graph with the banner as an overlay composable. Uses `AnimatedVisibility` with `slideInVertically`/`slideOutVertically`.

**When to use:** Global banner that must appear on every screen without modifying any per-screen Scaffold.

**Example:**
```kotlin
// In MainActivity setContent / or a root composable wrapping RootNavigationGraph
@Composable
fun AppRoot(navController: NavHostController, startDestination: Any) {
    val connectivityVm: ConnectivityViewModel = koinViewModel()
    val isOnline by connectivityVm.isOnline.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        RootNavigationGraph(navController = navController, startDestination = startDestination)

        AnimatedVisibility(
            visible = !isOnline,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it },   // -it = slide from off-screen top
            exit = slideOutVertically { -it },
        ) {
            OfflineConnectivityBanner()
        }
    }
}

@Composable
fun OfflineConnectivityBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFC107),  // Amber — Material Yellow 700
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color.White,
            )
            Text(
                text = "No internet connection",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

**Why `Box` not `Scaffold.topBar`:** The existing `CustomerTabScreen`, `SellerTabScreen`, and `AdminTabScreen` each have their own Scaffolds. Adding the banner to each Scaffold's `topBar` would require changes across every tab screen. The `Box` overlay at the root is placed once and overlays without causing layout shift.

### Pattern 5: Koin Database Module

**What:** Declare the `WenuCommerceDatabase` as a singleton, then expose each DAO as a singleton resolved from the database.

```kotlin
// data/di/DatabaseModule.kt (new file, registered in DataModule.kt appModules list)
val databaseModule = module {
    single {
        Room.databaseBuilder(
            context = get(),
            klass = WenuCommerceDatabase::class.java,
            name = "wenu_commerce_database",
        )
        // DEBUG ONLY — production must not use fallbackToDestructiveMigration
        .apply {
            if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
        }
        .build()
    }
    single { get<WenuCommerceDatabase>().productDao() }
    single { get<WenuCommerceDatabase>().categoryDao() }
    single { get<WenuCommerceDatabase>().userDao() }
}

val connectivityModule = module {
    single { ConnectivityObserver(get()) }
}
```

### Anti-Patterns to Avoid

- **ViewModel directly observing Firestore callbackFlow:** The ViewModel would have no cached fallback when offline. After migration, ViewModels must only observe DAO Flows.
- **Making domain models into @Entity:** Room annotations on domain models couple the domain layer to Room. Always create separate `*Entity` data classes in `data/local/entity/`.
- **`fallbackToDestructiveMigration()` in release build config:** Destroys all user data on schema changes. Use only in debug. Release builds must have explicit `Migration` objects.
- **Initializing ConnectivityObserver per-screen:** The `NetworkCallback` is a system resource. One global observer shared via Koin is correct. Multiple registrations waste resources.
- **Not emitting initial state in ConnectivityObserver:** If `registerDefaultNetworkCallback` is called when no network exists, the callback never fires. Always emit current state before registering.
- **Room in `app` module:** Room dependencies are in `data/build.gradle.kts` AND `app/build.gradle.kts`. The `ksp(room.compiler)` in `app` triggers duplicate annotation processing. Remove Room from `app/build.gradle.kts` before creating entities.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON serialization in TypeConverters | Custom string encoding | `kotlinx.serialization.json.Json` | Already in project; handles nulls, generics, and nested types correctly |
| Network state detection | Custom ping loop or `isNetworkAvailable` method | `ConnectivityManager.NetworkCallback` | Platform API handles airplane mode, VPN, and capability changes; ping loops are expensive and unreliable |
| Offline data shimmer | Custom loading animation | The project already uses progress indicators; design shimmer with `Box` + animated gradient brush | Shimmer is straightforward with `rememberInfiniteTransition` + `Brush.linearGradient` |
| Schema migration | Hand-writing SQL ALTER statements | Room `Migration(from, to)` with `addColumn` | Room validates against exported schema JSON; hand-rolled SQL bypasses this validation |

**Key insight:** The hardest part of this phase is not the Room setup itself — it is disciplined separation: entities live in `data`, domain models in `domain`, mappers in `data`. Violating this during a time-pressured refactor is the most common cause of a "Room migration" that must be re-done.

---

## Common Pitfalls

### Pitfall 1: Room Declared in Both `app` and `data` Modules

**What goes wrong:** KSP processes Room annotations twice. This causes build warnings or errors about duplicate generated files and can produce stale generated code.

**Why it happens:** The `app/build.gradle.kts` in this project already has `ksp(libs.room.compiler)`, `implementation(libs.room.runtime)`, `implementation(libs.room.ktx)`, and `implementation(libs.room.paging)`. This was likely added in anticipation but Room entities will only live in `:data`.

**How to avoid:** Remove the four Room dependency declarations from `app/build.gradle.kts` before creating any `@Entity` or `@Dao` class.

**Warning signs:** Build output containing `warning: Duplicate class` or `error: Cannot find implementation for [DAO interface]` in the `:app` compile task.

### Pitfall 2: Initial Connectivity State Not Emitted

**What goes wrong:** If the device is offline when the app launches and the banner's `Flow<Boolean>` collector starts, `onAvailable` never fires because no network transitions occur. The banner never shows — the user sees an empty screen with no indication of why.

**Why it happens:** `registerDefaultNetworkCallback` only fires callbacks on state *changes*, not on the current state at registration time.

**How to avoid:** Always call `connectivityManager.activeNetwork` and check capabilities before registering the callback, then `trySend` the initial state immediately.

**Warning signs:** Banner works when toggling airplane mode but doesn't appear when app is launched in airplane mode.

### Pitfall 3: Forgetting `exportSchema = true` and Not Tracking Schema JSON in Git

**What goes wrong:** The schema JSON file is not generated, making it impossible to write `MigrationTestHelper` tests or verify schema history. The requirement states "Room schema version 1 is committed with schema JSON files tracked in git" — this is a success criterion.

**Why it happens:** `exportSchema` defaults to `true` when the Room Gradle plugin's `schemaDirectory` is configured, but developers sometimes set `exportSchema = false` to silence build warnings and forget the cost.

**How to avoid:** `exportSchema = true` in `@Database`. The `schemaDirectory` is already configured in `data/build.gradle.kts` as `"$projectDir/schemas"`. After first build, `data/schemas/com.wenubey.data.local.WenuCommerceDatabase/1.json` will be created — add it to git.

**Warning signs:** Build warning "Schema export directory is not provided" or missing `schemas/` directory in `data/`.

### Pitfall 4: `onConflict = OnConflictStrategy.REPLACE` Loses Locally-Generated Data

**What goes wrong:** Using `REPLACE` strategy in DAOs nukes the existing row and inserts a new one. For entities that have local-only fields or partial server data, this silently loses data.

**Why it happens:** `REPLACE` is the simplest strategy and is commonly copy-pasted.

**How to avoid:** Use `@Upsert` (Room 2.5.0+) which issues an `INSERT OR REPLACE` that Room handles correctly — it updates the existing row rather than deleting and re-inserting. For this phase, the conflict policy is "server wins" for Products and Categories, so `@Upsert` is correct. `UserEntity` is a single-row cache — `@Upsert` is also correct.

**Warning signs:** Correlated data associated with a product (e.g., a local `isFavorited` flag if added later) disappearing on sync.

### Pitfall 5: Firestore Listener Scope Leaks

**What goes wrong:** If the `callbackFlow` that drives Firestore-to-Room sync is collected in a `ViewModel.viewModelScope`, the listener is tied to that screen's ViewModel lifecycle. When the user navigates away, the listener detaches and Room stops receiving updates until they navigate back.

**Why it happens:** The existing pattern in `CustomerHomeViewModel` launches the Firestore listener in `viewModelScope`. For a write-through sync pattern, the listener needs to outlive individual ViewModels.

**How to avoid:** Collect the sync flow in a long-lived application-scope coroutine (e.g., a `SyncService` or `applicationScope` injected via Koin). The ViewModel only observes the DAO Flow. Firestore listener lifecycle is managed independently at the application scope level.

**Warning signs:** Products stop updating after navigating away from home screen and returning.

### Pitfall 6: `List<T>` Column Without Default Value

**What goes wrong:** If a new column is added to an entity with a TypeConverter but without a default value, older rows in the database fail to deserialize, crashing the app.

**Why it happens:** Schema version 1 is the initial schema so this doesn't apply immediately, but matters for future migrations.

**How to avoid:** Always provide a default value for JSON columns in entity constructors (e.g., `val imagesJson: String = "[]"`). This ensures deserialization succeeds even if the column is null (from an old schema).

---

## Code Examples

### Room Database Declaration (Schema v1)

```kotlin
// Source: Android Developers - Room database documentation
// data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt

@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        UserEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class WenuCommerceDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userDao(): UserDao
}
```

### ProductDao (read-path)

```kotlin
@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE status = 'ACTIVE' AND categoryId = :categoryId")
    fun observeProductsByCategory(categoryId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE status = 'ACTIVE' AND categoryId = :categoryId AND subcategoryId = :subcategoryId")
    fun observeProductsByCategoryAndSubcategory(
        categoryId: String,
        subcategoryId: String,
    ): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE sellerId = :sellerId")
    fun observeSellerProducts(sellerId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE status = :status")
    fun observeProductsByStatus(status: String): Flow<List<ProductEntity>>

    // Search: filter locally on indexed text fields
    @Query("""
        SELECT * FROM products WHERE status = 'ACTIVE' AND (
            title LIKE '%' || :query || '%' OR
            categoryName LIKE '%' || :query || '%' OR
            searchKeywordsJson LIKE '%' || :query || '%'
        )
    """)
    suspend fun searchActiveProducts(query: String): List<ProductEntity>

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM products")
    suspend fun clearAll()
}
```

### CategoryDao

```kotlin
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isActive = 1")
    fun observeActiveCategories(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun clearAll()
}
```

### TypeConverters using kotlinx.serialization

```kotlin
// data/src/main/java/com/wenubey/data/local/converter/RoomTypeConverters.kt
import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.wenubey.domain.model.product.ProductImage
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.Subcategory

class RoomTypeConverters {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @TypeConverter fun stringListToJson(value: List<String>): String = json.encodeToString(value)
    @TypeConverter fun jsonToStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrElse { emptyList() }

    @TypeConverter fun imageListToJson(value: List<ProductImage>): String = json.encodeToString(value)
    @TypeConverter fun jsonToImageList(value: String): List<ProductImage> =
        runCatching { json.decodeFromString<List<ProductImage>>(value) }.getOrElse { emptyList() }

    @TypeConverter fun variantListToJson(value: List<ProductVariant>): String = json.encodeToString(value)
    @TypeConverter fun jsonToVariantList(value: String): List<ProductVariant> =
        runCatching { json.decodeFromString<List<ProductVariant>>(value) }.getOrElse { emptyList() }

    @TypeConverter fun shippingToJson(value: ProductShipping): String = json.encodeToString(value)
    @TypeConverter fun jsonToShipping(value: String): ProductShipping =
        runCatching { json.decodeFromString<ProductShipping>(value) }.getOrElse { ProductShipping() }

    @TypeConverter fun subcategoryListToJson(value: List<Subcategory>): String = json.encodeToString(value)
    @TypeConverter fun jsonToSubcategoryList(value: String): List<Subcategory> =
        runCatching { json.decodeFromString<List<Subcategory>>(value) }.getOrElse { emptyList() }
}
```

**Note:** `runCatching { }.getOrElse { emptyList() }` is defensive — a malformed JSON stored from an old app version will not crash the app; it will return an empty list instead.

### Connectivity Observer (complete)

```kotlin
// data/src/main/java/com/wenubey/data/connectivity/ConnectivityObserver.kt
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class ConnectivityObserver(private val context: Context) {

    val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Emit initial state before registering callback to handle launch-time offline
        val initialOnline = connectivityManager.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } ?: false
        trySend(initialOnline)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            override fun onLost(network: Network) {
                trySend(false)
            }
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                val hasInternet = networkCapabilities
                    .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(hasInternet)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
```

### Offline Banner (Compose)

```kotlin
// app/src/main/java/com/wenubey/wenucommerce/core/connectivity/OfflineConnectivityBanner.kt
@Composable
fun OfflineConnectivityBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),   // Respect system status bar
        color = Color(0xFFFFC107),  // Amber 500 (Material Yellow)
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color.White,
            )
            Text(
                text = stringResource(R.string.no_internet_connection),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

// Wired into app root:
Box(modifier = Modifier.fillMaxSize()) {
    RootNavigationGraph(navController = navController, startDestination = startDestination)

    val isOnline by connectivityVm.isOnline.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = !isOnline,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
    ) {
        OfflineConnectivityBanner()
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `BroadcastReceiver` for connectivity | `ConnectivityManager.NetworkCallback` | API 21 (deprecated in API 28) | Callback is lifecycle-aware; more reliable on Android 10+ where broadcast is restricted |
| `KAPT` for Room annotation processing | `KSP` | Room 2.4.0+ | KSP is ~2x faster; already configured in this project via `ksp(libs.room.compiler)` |
| `@Insert(onConflict = OnConflictStrategy.REPLACE)` | `@Upsert` | Room 2.5.0 | `@Upsert` generates more efficient SQL; avoids delete+insert semantics |
| Manual schema export via KSP arg | `androidx.room` Gradle plugin + `schemaDirectory` | Room 2.6.0 | Plugin handles multi-variant schema output; already configured in `data/build.gradle.kts` |
| `LiveData` from DAO queries | `Flow<T>` from DAO queries | Room 2.2.0 | `Flow<T>` integrates with coroutines and Compose `collectAsStateWithLifecycle` |

**Deprecated/outdated:**
- `room.schemaLocation` KSP argument: Replaced by `room { schemaDirectory() }` in Gradle plugin. The project already uses the plugin correctly.
- `fallbackToDestructiveMigration()` in production: Acceptable only in DEBUG builds. The phase success criteria explicitly prohibits it in release config.

---

## Open Questions

1. **Sync service lifecycle: ViewModel scope vs. Application scope**
   - What we know: The existing `CustomerHomeViewModel` launches Firestore listeners in `viewModelScope`, meaning listeners detach on navigation away from home. After Room migration, the write-through sync flow must outlive ViewModels.
   - What's unclear: Whether to use a simple `applicationScope` (launched once in `WenuCommerce.onCreate`), a `WorkManager` periodic sync, or a dedicated Service. Phase 2 introduces offline writes, which may require WorkManager anyway.
   - Recommendation: For Phase 1, use `applicationScope` (a `CoroutineScope(SupervisorJob() + Dispatchers.IO)` injected via Koin) for the Firestore sync listeners. This keeps the implementation simple. WorkManager is a Phase 2+ concern.

2. **UserEntity: What fields to cache?**
   - What we know: `AuthRepositoryImpl` currently holds user state in a `MutableStateFlow<User?>` backed by a Firestore listener. The domain `User` model has 16 fields including nested `List<Device>`, `List<Purchase>`, and `BusinessInfo?`.
   - What's unclear: Whether `UserEntity` should cache the full User or just the auth-critical fields (uuid, role, name, email, isEmailVerified).
   - Recommendation (Claude's discretion): Cache the full User but store complex nested fields (`signedDevices`, `purchaseHistory`, `businessInfo`) as JSON strings, same pattern as Product. The conflict resolution policy is last-write-wins with `updatedAt` guard (as specified in prior decisions), which is trivially implemented with `@Upsert`.

3. **First-launch empty Room: where does the empty state live?**
   - What we know: The decision says "First launch with no network: empty state with friendly illustration + retry button." The empty state must be distinguished from "still loading" (shimmer) vs "loaded, genuinely empty."
   - What's unclear: Whether the DAO `Flow<List<ProductEntity>>` emitting an empty list is the signal for "empty state" or whether a separate "sync attempted" flag is needed.
   - Recommendation: Use a three-state model in the ViewModel: `Loading` (shimmer), `Empty` (no network + empty cache), `Loaded` (data present). The DAO flow emitting empty + ConnectivityObserver emitting `false` = `Empty` state. This avoids an additional persistence layer.

---

## Sources

### Primary (HIGH confidence)
- Android Developers - [Referencing complex data using Room](https://developer.android.com/training/data-storage/room/referencing-data) — TypeConverter patterns, `@TypeConverters` on database class
- Android Developers - [Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions) — schema export, `fallbackToDestructiveMigration` guidance
- Android Developers - [Read network state](https://developer.android.com/develop/connectivity/network-ops/reading-network-state) — `NetworkCallback`, `registerDefaultNetworkCallback`, initial state handling
- Android Developers - [Animation composables and modifiers](https://developer.android.com/develop/ui/compose/animation/composables-modifiers) — `slideInVertically`, `slideOutVertically`, `AnimatedVisibility`
- Codebase inspection: `data/build.gradle.kts` — Room Gradle plugin + schemaDirectory already configured
- Codebase inspection: `app/build.gradle.kts` — duplicate Room declarations confirmed, must be removed
- Codebase inspection: `ProductRepositoryImpl`, `CategoryRepositoryImpl`, `AuthRepositoryImpl` — current Firestore-direct patterns that must migrate

### Secondary (MEDIUM confidence)
- [A First Look At The New Android Room Gradle Plugin](https://medium.com/tech-takeaways/a-first-look-at-the-new-android-room-gradle-plugin-e91fab243207) — schemaDirectory multi-module behavior, verified against official docs
- [How to Observe Internet Connectivity in Android — Modern Way with Kotlin Flow](https://khush7068.medium.com/how-to-observe-internet-connectivity-in-android-modern-way-with-kotlin-flow-7868a322c806) — callbackFlow + NetworkCallback pattern, cross-verified with official docs
- [Offline-First Android: Build resilient apps for spotty networks](https://medium.com/@vishalpvijayan4/offline-first-android-build-resilient-apps-for-spotty-networks-with-room-datastore-workmanager-4a23144e8ea2) — write-through sync architecture, cross-verified with official architecture guides

### Tertiary (LOW confidence)
- None — all findings were cross-verified with official sources or codebase inspection.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions confirmed in `libs.versions.toml`; Room 2.6.1 and plugin already present
- Architecture: HIGH — TypeConverter and write-through patterns verified with official Android docs
- ConnectivityObserver: HIGH — `registerDefaultNetworkCallback` + `callbackFlow` verified with official docs
- Pitfalls: HIGH — duplicate Room in `app` module confirmed by direct codebase inspection; initial state issue documented in official docs

**Research date:** 2026-02-19
**Valid until:** 2026-03-19 (Room API is stable; ConnectivityManager API stable since API 24)
