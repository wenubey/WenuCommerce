# Pitfalls Research

**Project:** WenuCommerce — Android e-commerce offline-first refactoring
**Researched:** 2026-02-19
**Scope:** Room + Firebase sync, Stripe via Cloud Functions, FCM notifications, comprehensive testing
**Current state analysed:** Firebase-direct data access with `callbackFlow` listeners, no Room usage yet,
Koin 4.0.1 DI, 3 modules (domain/data/app), Kotlin 2.1.0, Compose + Material 3, Room 2.6.1 already
declared in `libs.versions.toml` and both `app/build.gradle.kts` and `data/build.gradle.kts` but zero
`@Entity`/`@Dao`/`@Database` classes exist.

---

## Critical Pitfalls

These mistakes cause project-wide rewrites, data corruption, or fundamentally broken features.

---

### CP-1: Using Domain Model Classes Directly as Room Entities

**Description:**
The domain models (`Product`, `ProductVariant`, `ProductImage`, `ProductShipping`, `Category`, `User`,
etc.) are already annotated `@Serializable` and designed for Firestore serialization. They contain
nested objects (`List<ProductVariant>`, `List<ProductImage>`, `ProductShipping`) and Kotlin features
(`data class` with default parameter values, `Map<String, String>` for attributes). Attempting to
annotate these classes directly with `@Entity` will fail because:
- Room cannot serialize nested objects without explicit `@Embedded` or `@TypeConverter` declarations.
- `Map<String, String>` (used in `ProductVariant.attributes`) has no built-in Room converter.
- `List<ProductVariant>` and `List<ProductImage>` as fields require custom JSON `@TypeConverter`s.
- The `Serializable` annotation and Room's KSP annotations conflict in subtle ways.
- Putting `@Entity` on domain models violates Clean Architecture — the domain layer must not know
  about Room (an infrastructure concern).

**Warning Signs:**
- KSP compilation errors like "Cannot figure out how to save this field into database."
- `@TypeConverter` being added to files in the `domain` module.
- Domain models importing `androidx.room.*`.
- A single `Product` class attempting to serve Firestore, Room, and UI simultaneously.

**Prevention Strategy:**
Create a separate `entity` layer inside the `data` module. For each domain model that needs to be
cached, create a corresponding `*Entity` class (e.g., `ProductEntity`, `CategoryEntity`, `CartItemEntity`).
Add mapper extension functions: `Product.toEntity()` and `ProductEntity.toDomain()`. Keep the domain
models clean. Register `@TypeConverter`s in the `data` module's `@Database` class for all non-primitive
types (use `kotlinx.serialization.json.Json.encodeToString` / `decodeFromString` for lists and maps).

**Which Phase Should Address It:**
Before any Room entity is written. Must be the very first design decision in the offline-first phase.
Establish the entity layer structure before writing a single `@Entity` annotation.

---

### CP-2: Two-Way Sync Without Conflict Resolution Strategy

**Description:**
The current app writes directly to Firestore and reads via real-time `callbackFlow` listeners. Once
Room is introduced as a local cache, every write path splits: write to Room first, then sync to
Firestore. If the app goes offline mid-write (Firestore write queued), then a different device edits
the same document online, both devices have conflicting states. For an e-commerce app, the conflict
scenarios with real business consequences are:

- **Cart items:** Customer adds item offline; Firestore stock was already decremented by another order
  on the server. Room says "in cart," Firestore says "out of stock."
- **Product stock:** Seller edits product price offline; another admin suspends the product. On sync,
  which write wins?
- **Order status:** Order status transitions (PENDING → PROCESSING → SHIPPED) must be unidirectional.
  An offline-written status of PENDING cannot overwrite an online-written SHIPPED.

Without an explicit conflict resolution strategy defined upfront, the team defaults to "last write
wins," which silently corrupts stock counts, order states, and payment records.

**Warning Signs:**
- Sync logic that simply calls `firestore.set(entity.toDomain())` without reading the server version
  first.
- No `updatedAt` timestamp comparison before applying a remote update to the local cache.
- A `SyncManager` or `SyncWorker` that does not distinguish between entity types in terms of
  conflict priority.
- Cart and Order entities being treated with the same merge strategy as Product entities.

**Prevention Strategy:**
Define conflict resolution policy per entity type before writing any sync code:

| Entity | Policy | Rationale |
|--------|--------|-----------|
| Products (catalog) | Server wins | Admin/Seller edits are authoritative |
| Cart items | Local wins for additions; server wins for stock validation | Cart is user-intent |
| Orders | Immutable after creation; status only advances forward | Unidirectional state machine |
| User profile | Last-write-wins with `updatedAt` timestamp guard | Low-stakes, single author |

Use Firestore `serverTimestamp()` for `updatedAt` fields on all documents. In Room, store a `syncedAt`
timestamp per entity. Before writing a remote update to Room, compare `serverUpdatedAt` with local
`syncedAt`. Only apply the update if the server version is newer.

For payment-related entities (Orders, Purchases), treat Firestore as the source of truth and Room as
a read-through cache only — never write payment data to Room optimistically.

**Which Phase Should Address It:**
Architecture design phase, before implementing any sync worker. The conflict policy must be documented
and agreed before the first `@Entity` is written.

---

### CP-3: Performing Payment Logic Client-Side

**Description:**
Stripe payments must never be processed directly from the Android client. The `Product` model already
has `stripeProductId` and `ProductVariant` has `stripePriceId` fields, and the plan is to use Firebase
Cloud Functions. However, the specific pitfall is implementing any of the following on the client:

- Creating `PaymentIntent` directly from the Android app using a Stripe secret key.
- Storing the Stripe secret key in `local.properties` or `BuildConfig` (it will leak via APK reverse
  engineering regardless of ProGuard).
- Performing stock validation (checking `stockQuantity` in the ViewModel) and creating an order before
  the server confirms the payment intent succeeded.
- Implementing refund, capture, or charge operations from the client.

Creating a `PaymentIntent` from the client means the secret key is embedded in the APK — Stripe's
own documentation calls this a critical security violation that will result in account termination.

The current codebase already has `Firebase.functions` wired in `DataModule.kt` and `firebase-functions-ktx`
in `libs.versions.toml`, which is the correct setup for server-side payment processing.

**Warning Signs:**
- Any `BuildConfig` field containing the string "sk_" (Stripe secret key prefix).
- `StripeApiClient` or `PaymentIntentParams` being imported in a ViewModel or Repository that lives
  in the `app` or `data` module without first calling a Cloud Function.
- `decrementStock()` being called from the ViewModel before a payment confirmation webhook.
- `Purchase` being written to the `User.purchaseHistory` list from the client before payment
  confirmation.

**Prevention Strategy:**
All payment operations flow through a single Firebase Cloud Function:

```
Client (Android) --> Cloud Function: createPaymentIntent(cartItems, userId)
                                    |
                          Validates stock (server-side read)
                          Creates Stripe PaymentIntent
                          Returns { clientSecret, paymentIntentId }
                                    |
Client <-- clientSecret
Client --> Stripe SDK: confirmPayment(clientSecret)
                                    |
Stripe --> Cloud Function webhook: handlePaymentSuccess(paymentIntentId)
                                    |
                          Decrements stock (transactional)
                          Creates Order document
                          Appends to User.purchaseHistory
                          Triggers FCM notification
```

The Android client only ever receives and uses the `clientSecret`. It never touches stock counts,
order creation, or purchase history writes directly. The Stripe Android SDK handles the payment sheet
UI. Webhooks handle all post-payment state changes.

**Which Phase Should Address It:**
Before writing a single line of cart or checkout code. The Cloud Function API contract must be
designed first, and the Android client must be written to call that contract.

---

### CP-4: Treating the `callbackFlow` Repository Pattern as Compatible with Room's Flow

**Description:**
The current repository pattern uses `callbackFlow` for Firestore real-time listeners that emit
`Flow<List<T>>`. Room DAOs also return `Flow<List<T>>` from database queries. These two flows have
fundamentally different lifecycles and threading models:

- Firestore `callbackFlow` never completes on its own — the listener fires whenever Firestore pushes
  an update. The flow only ends when `close()` is called (which happens in `awaitClose`).
- Room's `Flow` from `@Query` is a cold flow that emits a new value every time the backing table
  changes. It also never completes on its own.

The pitfall: an offline-first `ProductRepository` that tries to merge these two flows using operators
like `combine` or `flatMapLatest` without understanding what happens when:
- Firestore goes offline: the `callbackFlow` suspends (it won't emit, but it also won't close with
  an error — it silently pauses).
- Room emits stale data: the UI shows cached data, which is correct, but there's no visible signal
  to the user that the data is stale.
- Both emit simultaneously after reconnect: the UI flickers between Room data and Firestore data
  if both are collected independently.

The correct pattern is a single-source-of-truth architecture: Room is the only source the UI
observes. Firestore writes to Room. This is the "offline-first" pattern. The mistake is observing
both sources simultaneously.

**Warning Signs:**
- Repository methods that `combine` a `callbackFlow` (Firestore) with a `roomDao.observeX()` flow.
- ViewModels that independently collect both a Firestore flow and a Room flow.
- Two `StateFlow` properties in a ViewModel — one for "local" data and one for "remote" data.
- `collectLatest` on a Firestore flow followed by a Room upsert, without any debouncing or error
  handling for the offline case.

**Prevention Strategy:**
Implement the Repository as a strict single-source-of-truth pattern:

```kotlin
// Correct: UI observes Room only
fun observeProducts(categoryId: String): Flow<List<Product>> =
    productDao.observeByCategory(categoryId)        // Room Flow — UI watches this
        .map { entities -> entities.map { it.toDomain() } }

// In the sync layer (WorkManager or init block):
fun startFirestoreSync(categoryId: String) {
    firestoreListener = productsCollection
        .whereEqualTo("categoryId", categoryId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { /* log, do not crash */ return }
            val entities = snapshot?.documents?.mapNotNull { it.toEntity() } ?: return
            scope.launch { productDao.upsertAll(entities) }   // Write to Room
        }
}
```

The Firestore listener writes to Room. Room notifies the UI. The UI never touches Firestore directly.

**Which Phase Should Address It:**
This is the foundational architecture decision for the offline-first phase. Must be established before
implementing any feature that touches the Room layer.

---

### CP-5: Room Schema Migrations Breaking Existing Users on Each Feature Addition

**Description:**
Room 2.6.1 is declared in both `build.gradle.kts` files but no `@Database` class exists yet. This is
a clean slate, but the pitfall is failing to set up the migration infrastructure before the first
production release. Once the app is shipped with a Room schema (version 1), every subsequent change
to any `@Entity` requires an explicit `Migration` object or `fallbackToDestructiveMigration()`. Common
mistakes:

- Shipping the app with `fallbackToDestructiveMigration()` enabled. Fine for development, catastrophic
  for production — every update wipes the local cache including the offline cart.
- Adding a non-nullable field to an `@Entity` without providing a default value in the Migration.
  Room will crash on startup for all existing users.
- Forgetting to export the schema JSON (the `room { schemaDirectory(...) }` block is already in
  `data/build.gradle.kts`, which is correct) and then being unable to write accurate migrations
  because the previous schema is unknown.
- Adding the Cart `@Entity` in phase 1, then adding an `estimatedDeliveryDate` column in a later
  phase without a migration — this crashes all users who installed the app between those phases.

**Warning Signs:**
- `fallbackToDestructiveMigration()` left in the `Room.databaseBuilder()` call after the first
  feature is shipped.
- The `/data/schemas/` directory is empty or not committed to version control.
- `@Entity` classes being added without a corresponding entry in a `ROOM_MIGRATIONS.md` or
  changelog.
- Database version number not incremented when adding a column to an existing entity.

**Prevention Strategy:**
1. Set up `schemaDirectory` in `data/build.gradle.kts` — already done. Commit the generated JSON
   files to git so schema history is preserved.
2. Use `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2, MIGRATION_2_3, ...)`. Never use
   `fallbackToDestructiveMigration()` after the first production build.
3. For development builds, use `allowDestructiveMigrationOnDowngrade()` only.
4. Plan entity versions: design all entities needed for the Cart + Order + Offline Product cache
   phases together, so schema version 1 is stable before first ship. Avoid schema version 1 with
   only Cart and schema version 2 three days later with Product.
5. Write migration tests using `MigrationTestHelper` from `room-testing` (already in deps).

**Which Phase Should Address It:**
Before the first Room entity is marked as production-ready. The `Room.databaseBuilder()` setup and
migration strategy must be decided at the same time the first `@Entity` is written.

---

## Moderate Pitfalls

These mistakes cause significant rework, performance problems, or difficult-to-debug behaviour.
They do not corrupt data but they slow the project down substantially.

---

### MP-1: Nested Object Serialization Asymmetry Between Firestore and Room

**Description:**
Firestore documents and Room tables handle nested objects completely differently. In Firestore,
`Product.variants: List<ProductVariant>` is stored as a Firestore array of maps — this works with
`doc.toObject(Product::class.java)` via Firestore's built-in deserializer. In Room, this same field
must be stored as a single JSON-encoded column via a `@TypeConverter`. The `toMap()` extension
functions that currently exist (e.g., `Product.toMap()`, `ProductVariant.toMap()`) are for Firestore
and cannot be reused for Room serialization.

The pitfall is attempting to reuse `toMap()` or the `@Serializable` annotation's default JSON output
to drive both Firestore writes and Room `@TypeConverter`s. The JSON schema from kotlinx.serialization
may differ from Firestore's internal map representation if Firestore-specific types (like `Timestamp`,
`GeoPoint`) are involved, causing deserialization failures on the Room side.

**Warning Signs:**
- `@TypeConverter` functions that call `product.toMap()` (the Firestore map) and then `JSONObject`
  to store in Room.
- Firestore `Timestamp` types appearing inside Room entities.
- `ProductVariant.attributes: Map<String, String>` stored as a string that uses Firestore's map
  format instead of standard JSON.

**Prevention Strategy:**
Use `kotlinx.serialization.json.Json` for all Room `@TypeConverter`s:

```kotlin
object ProductConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromVariants(variants: List<ProductVariant>): String =
        json.encodeToString(variants)

    @TypeConverter
    fun toVariants(value: String): List<ProductVariant> =
        json.decodeFromString(value)
}
```

The domain models are already annotated `@Serializable`, making this straightforward. Do not use
Gson or Moshi — the project already uses kotlinx.serialization; adding another JSON library adds
unnecessary APK weight and inconsistency.

**Which Phase Should Address It:**
When writing the first `@TypeConverter` for Room entities. Establish the pattern with the Cart or
Product entity and apply it uniformly.

---

### MP-2: WorkManager for Sync Without Proper Constraint Handling

**Description:**
The standard approach for background Firestore-to-Room sync in offline-first apps is a `WorkManager`
`PeriodicWorkRequest` or a `OneTimeWorkRequest` triggered when connectivity is restored. The pitfall
is setting up the `Worker` without considering:

- **Network constraint alone is insufficient:** `Constraints.Builder().setRequiredNetworkType(CONNECTED)`
  ensures the worker runs when connected, but it does not handle the case where Firestore is reachable
  but the user is authenticated with an expired token. The worker must refresh the auth token before
  attempting Firestore reads.
- **Worker not idempotent:** If the worker is interrupted (app killed, battery low) mid-sync, does it
  resume correctly? A worker that partially syncs 50 of 100 products and then fails will leave the
  local cache in an inconsistent state on restart if it doesn't track progress.
- **Cart sync via WorkManager is dangerous for payments:** The cart represents transient purchase
  intent. Running a periodic sync that overwrites local cart state from Firestore could silently
  remove items the user added offline. Cart sync must be immediate (foreground) and user-visible, not
  a background periodic job.
- **Sync storms on reconnect:** If 10 different entity types all trigger sync workers when network
  is restored, the device performs 10 concurrent Firestore queries. This exhausts Firestore's free
  tier read limits and causes `TOO_MANY_REQUESTS` errors.

**Warning Signs:**
- `OneTimeWorkRequest` chains for different entity types all triggered by the same
  `NetworkCallback.onAvailable()`.
- `Worker.doWork()` that catches all exceptions and returns `Result.success()` regardless.
- A sync worker that re-downloads the entire PRODUCTS collection on every run instead of using
  `updatedAt > lastSyncedAt` filtering.
- Cart items being synced via WorkManager background tasks.

**Prevention Strategy:**
- Use `updatedAt > lastSyncedAt` Firestore queries to sync only changed documents. Store
  `lastSyncedAt` per collection in `DataStore`.
- Make workers idempotent: use `upsert` (not `insert`) operations in Room DAOs.
- Refresh the Firebase auth token inside the worker using `FirebaseAuth.currentUser?.getIdToken(true)`
  before any Firestore call.
- Stagger sync workers with `ExistingPeriodicWorkPolicy.KEEP` to prevent sync storms.
- Never sync Cart data via WorkManager. The cart is local-only (Room + optional immediate Firestore
  write when online). Only Orders sync via WorkManager, and only as a read (order status pull from
  Firestore server).

**Which Phase Should Address It:**
When implementing the background sync infrastructure. Design the sync worker contracts before writing
any `Worker` class.

---

### MP-3: Koin ViewModel Injection Conflicts with SavedStateHandle After Process Death

**Description:**
The current project uses `viewModelOf(::SomeViewModel)` via Koin 4.0.1. This pattern works for
constructor injection of repositories and dispatchers. The pitfall occurs when ViewModels that need
`SavedStateHandle` (for navigation argument recovery after process death) are added during the
checkout or order tracking phases. Koin's `viewModelOf` does not automatically inject
`SavedStateHandle` — it requires explicit `viewModel { SomeViewModel(get(), get()) }` syntax with
`get()` for the handle.

Additionally, the `CustomerProductDetailViewModel` will likely receive `productId` as a navigation
parameter. If `SavedStateHandle` is not used, navigating back to a product detail screen after the
process is killed (e.g., Stripe payment UI went to the foreground and Android killed the app in the
background) will crash or show an empty screen.

For the payment flow specifically, if the user is sent to the Stripe payment sheet and the app is
killed, the pending `PaymentIntent` ID must survive process death to reconcile the payment status
on resume. Storing this ID only in ViewModel memory will lose it.

**Warning Signs:**
- ViewModels receiving navigation arguments via constructor parameter (`productId: String`) without
  `SavedStateHandle`.
- `PaymentIntentId` stored as a plain `var` in a ViewModel.
- `viewModelOf(::CheckoutViewModel)` in `ViewmodelModule.kt` when `CheckoutViewModel` needs to
  survive the Stripe payment redirection.

**Prevention Strategy:**
For ViewModels that handle navigation arguments or multi-step flows (checkout, order tracking):

```kotlin
// In ViewmodelModule.kt
viewModel { params -> CheckoutViewModel(get(), get(), params.get()) }

// In CheckoutViewModel
class CheckoutViewModel(
    private val orderRepository: OrderRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val pendingPaymentIntentId = savedStateHandle
        .getStateFlow("pendingPaymentIntentId", "")
}
```

Use `SavedStateHandle.getStateFlow()` for any state that must survive process death during payment
flows. Persist the `paymentIntentId` to `SavedStateHandle` immediately when received from the Cloud
Function, before showing the Stripe payment sheet.

**Which Phase Should Address It:**
When building the Checkout ViewModel. Do not retrofit this after the checkout flow is built.

---

### MP-4: Koin Module Circular Dependencies When Adding Room DAOs

**Description:**
The current `repositoryModule` registers repository implementations as singletons. Adding Room
introduces DAOs and a `@Database` class, which also need to be singletons. The common mistake is
registering the `RoomDatabase` instance inside `repositoryModule` alongside Firestore singletons,
creating a module that has too many responsibilities and is difficult to test in isolation.

A secondary issue: Koin `singleOf` resolves dependencies by type. If two different repository
implementations both depend on `ProductDao`, and `ProductDao` is registered twice (once incorrectly
in a factory block), Koin will create multiple database instances — corrupting the Room write-ahead
log.

**Warning Signs:**
- `RoomDatabase` built inside `repositoryModule` or `firebaseModule`.
- `singleOf(::ProductDao)` — DAOs are not instantiated directly; they are retrieved via
  `database.productDao()`.
- `KoinApplication: ERROR - No definition found for class:'ProductDao'` at startup.
- Multiple `AppDatabase.getDatabase(context)` calls without a singleton guard.

**Prevention Strategy:**
Create a dedicated `databaseModule` in `data` that owns all Room concerns:

```kotlin
val databaseModule = module {
    single<AppDatabase> {
        Room.databaseBuilder(get(), AppDatabase::class.java, "wenu_commerce_db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    single { get<AppDatabase>().productDao() }
    single { get<AppDatabase>().cartDao() }
    single { get<AppDatabase>().orderDao() }
    single { get<AppDatabase>().categoryDao() }
}
```

Repositories receive DAOs via constructor injection. The `AppDatabase` singleton is the only place
`Room.databaseBuilder` is called.

**Which Phase Should Address It:**
When writing the first Room `@Database` class. Set up the Koin `databaseModule` before any DAO is
used in a repository.

---

### MP-5: Incomplete Firestore Security Rules After Adding Offline Sync

**Description:**
Adding offline-first sync changes the data access patterns in ways that require security rules review.
Currently, the app reads data via authenticated requests. With offline sync, `WorkManager` sync jobs
run under the currently authenticated user's identity — but consider:

- **Admin products sync:** If the `ProductEntity` is cached for the Admin role (to allow offline
  moderation), the Firestore security rules must permit Admin users to read all products regardless
  of status. But the same rules may currently permit any authenticated user to read pending products.
- **Seller product sync:** A seller syncing their own products offline is fine. But the sync query
  `whereEqualTo("sellerId", sellerId)` must be protected by a server-side rule that validates
  `request.auth.uid == sellerId`. If this rule is missing, any authenticated user who knows a
  `sellerId` can sync another seller's draft products.
- **Order data:** Orders synced to the local Room database must be protected by rules that only allow
  the buyer or the seller involved to read the order. Client-side filtering is not sufficient security.
- **Cart data:** If the cart is Firestore-backed (even partially), the rules must prevent cross-user
  cart access.

The pitfall is adding new Firestore queries in the sync workers without a corresponding security
rules update.

**Warning Signs:**
- `allow read: if request.auth != null;` as the only rule for a collection.
- Sync workers that query collections without a `whereEqualTo("userId", uid)` filter AND without
  a corresponding Firestore rule validating the same constraint.
- No `firestore.rules` file committed to the repository.
- Any Firestore query in the sync worker that reads more documents than the specific user's data.

**Prevention Strategy:**
For each new Firestore collection accessed by the sync layer, write and test the security rule in
the Firebase Emulator before deploying. Minimum rules pattern:

```
match /ORDERS/{orderId} {
  allow read: if request.auth.uid == resource.data.customerId
               || request.auth.uid == resource.data.sellerId;
  allow write: if false;  // Server-side only via Cloud Functions
}
```

Run the Firebase Emulator locally during sync development so security rule violations throw
immediately rather than silently succeeding in dev and failing in production.

**Which Phase Should Address It:**
With every new collection added to the sync layer. Not a one-time task — it accompanies each
feature phase that introduces a new data type.

---

### MP-6: Testing ViewModels That Use Both Firestore Flows and Room Flows

**Description:**
The current `ExampleUnitTest.kt` files in all three modules are empty scaffolding. The plan is to add
comprehensive testing. The pitfall is attempting to unit test the new offline-first ViewModels with
mocked Firestore repository interfaces while the repository implementations internally manage both
Firestore listeners and Room queries.

The specific problems:
- `CustomerHomeViewModel` currently collects from `categoryRepository.observeCategories()` — a
  `callbackFlow` that wraps a Firestore `addSnapshotListener`. Mocking this with MockK requires
  returning a fake `Flow`. This works. But if the repository is refactored to return a Room-backed
  flow, the ViewModel test continues to pass with the mock even though the real implementation is
  broken.
- `TestCoroutineDispatcher` vs `UnconfinedTestDispatcher`: Kotlin's coroutines test API changed
  significantly between versions. The project uses `kotlinx-coroutines-test` version `1.9.0`
  (from `libs.versions.toml`: `kotlinTest = "1.9.0"`). This version uses `runTest` with
  `UnconfinedTestDispatcher` by default. The older pattern of `TestCoroutineScope` will not compile.
  Any test tutorial written before 2023 will use the wrong API.
- `MutableStateFlow` in ViewModels tested without `Lifecycle` awareness: `collectAsStateWithLifecycle()`
  in the UI requires a lifecycle owner. In unit tests, `StateFlow.value` should be read directly
  rather than collected, but if a test uses `turbine` or `collect`, it will hang unless the flow is
  cancelled after assertions.

**Warning Signs:**
- Unit tests importing `TestCoroutineScope` or `runBlockingTest` (deprecated API).
- ViewModel tests that do not set a `Dispatchers.setMain(testDispatcher)` before testing.
- Tests that mock `ProductRepository` but test `ProductRepositoryImpl` — these are integration tests
  masquerading as unit tests; they will be fragile.
- ViewModel tests with `Thread.sleep()` instead of `advanceTimeBy()` or `runCurrent()`.

**Prevention Strategy:**
Establish a test base class pattern before writing any feature tests:

```kotlin
abstract class ViewModelTest {
    protected val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUpDispatchers() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownDispatchers() {
        Dispatchers.resetMain()
    }
}
```

For Repository implementations that use both Room and Firestore, write integration tests using
the Firebase Emulator (for Firestore) and an in-memory Room database. Do not attempt to unit test
`ProductRepositoryImpl` with MockK mocks of Firestore — the Firestore SDK is not mockable cleanly.
Mock the repository interface in ViewModel tests; test the repository implementation against real
(emulated) backends.

**Which Phase Should Address It:**
When writing the first ViewModel test. Establish the test infrastructure (`ViewModelTest` base class,
`DispatcherProviderFake`, `FakeProductRepository`) before writing any real tests.

---

### MP-7: FCM Token Lifecycle Not Handled During Offline Periods

**Description:**
The current `MessagingService.onNewToken()` calls `firestoreRepository.updateFcmToken(token)` — a
direct Firestore write. If the device is offline when Firebase rotates the FCM token (which happens
periodically), the `onNewToken()` callback fires but the Firestore write fails silently inside a
`fire-and-forget` coroutine. The result: the stored FCM token in Firestore becomes stale. Cloud
Function-triggered notifications (order updates, seller approvals) are delivered to the wrong token
and silently dropped.

This is especially problematic for the order flow: when an order status changes, the seller and
buyer both need FCM notifications. If either has a stale token due to offline token rotation, they
miss the notification.

**Warning Signs:**
- `firestoreRepository.updateFcmToken(token)` called without a `Result` check or retry logic.
- No mechanism to re-check or re-upload the FCM token on app start when connectivity is restored.
- FCM token update written as a fire-and-forget without `WorkManager` queuing.

**Prevention Strategy:**
On every app start (in `AuthViewModel` or `MainActivity`), re-validate the FCM token:

```kotlin
// On startup, after confirming the user is authenticated:
Firebase.messaging.token.addOnSuccessListener { currentToken ->
    val storedToken = dataStore.fcmToken.first()
    if (currentToken != storedToken) {
        // Enqueue a WorkManager OneTimeWorkRequest to update Firestore
        // This ensures the update happens even if currently offline
    }
}
```

Use a `WorkManager` `OneTimeWorkRequest` with `NetworkConstraint.CONNECTED` to reliably upload the
FCM token. Never rely on the `onNewToken()` callback alone for persistence.

**Which Phase Should Address It:**
When implementing FCM for order/seller notifications. Retrofit the existing token handling at the
same time.

---

## Minor Pitfalls

These are recoverable annoyances that cause wasted time but do not require architectural changes.

---

### MiP-1: `Purchase.quantity` Typed as `Double` Instead of `Int`

**Description:**
`Purchase.quantity: Double = 0.0` (in `domain/model/Purchase.kt`) is semantically incorrect —
product quantities should be integers. When the Purchase model is mapped to a Room entity, a `Double`
quantity column will require a `@TypeConverter` unnecessarily and will produce nonsensical data
(e.g., "0.5 units purchased"). This will cause confusion in order summary UI and incorrect total
price calculations.

**Warning Signs:**
- Order summary showing "Quantity: 1.0" instead of "Quantity: 1".
- `totalPrice = quantity * price` producing floating-point arithmetic issues for integer quantities.

**Prevention Strategy:**
Change `quantity: Double` to `quantity: Int` in `Purchase.kt` before writing any Room entity or
cart calculation logic. This is a clean slate — no migration cost yet.

**Which Phase Should Address It:**
When building the Cart domain model and Cart-to-Order conversion logic.

---

### MiP-2: Timestamps Stored as `String` Instead of `Long` Cause Ordering Bugs

**Description:**
All domain models use `String` for timestamps: `createdAt: String = ""`, `updatedAt: String = ""`,
`publishedAt: String = ""`. Currently these store `System.currentTimeMillis().toString()`. This works
for display, but when Room queries need to sort or filter by time (e.g., "show orders from last 30
days," "sort products by newest"), comparing strings that represent milliseconds-as-strings gives
correct results only as long as the string length is consistent. If any code ever stores a formatted
date string (e.g., `"2026-02-19"`) instead of a milliseconds string, all ordering queries break
silently.

**Warning Signs:**
- Room DAO queries using `ORDER BY createdAt DESC` returning wrong order.
- Any `SimpleDateFormat` or `DateTimeFormatter` output being stored in a timestamp field.
- The sync layer comparing `serverTimestamp()` (a Firestore `Timestamp` type) with a
  `System.currentTimeMillis().toString()` string.

**Prevention Strategy:**
When creating Room entities, store timestamps as `Long` (epoch milliseconds) instead of `String`.
The mapper functions (`toDomain()` and `toEntity()`) handle the conversion. Use `Long` in Room,
`String` in Firestore (to match existing documents), convert in the mapper layer. Do not change the
domain model `String` fields — changing them now would require a Firestore migration across all
existing documents. Accept the `String` in the domain model; use `Long` internally in Room entities.

**Which Phase Should Address It:**
When writing the first Room entity with a timestamp field. Add a note to the entity design doc.

---

### MiP-3: `fallbackToDestructiveMigration()` Left Enabled After First Public Build

**Description:**
During development of offline-first features, `fallbackToDestructiveMigration()` is convenient —
it drops and recreates the database on schema changes without writing explicit migrations. The pitfall
is forgetting to remove it before the first production release (or before any beta distribution via
Google Play Internal Testing). Any user who installs the app during one release and updates to the
next will lose their local cart, order cache, and any offline-pending state.

**Warning Signs:**
- `fallbackToDestructiveMigration()` present in the `Room.databaseBuilder()` call in a non-debug
  build variant.
- No `MIGRATION_X_Y` objects defined after the second schema version change.

**Prevention Strategy:**
Use build variant scoping:

```kotlin
val builder = Room.databaseBuilder(context, AppDatabase::class.java, "wenu_commerce_db")
if (BuildConfig.DEBUG) {
    builder.fallbackToDestructiveMigration()
} else {
    builder.addMigrations(*ALL_MIGRATIONS)
}
```

This is a simple guard. Add it from the very first `Room.databaseBuilder()` call so it cannot be
forgotten later.

**Which Phase Should Address It:**
When writing `Room.databaseBuilder()` for the first time. Do not defer this.

---

### MiP-4: Room Declared in `app/build.gradle.kts` But Should Only Be in `data`

**Description:**
`libs.versions.toml` shows Room dependencies declared in both `app/build.gradle.kts` and
`data/build.gradle.kts`. The Room `@Database`, `@Dao`, and `@Entity` classes belong in the `data`
module, not `app`. Having Room in `app` creates a temptation to put database access code in
ViewModels or other `app`-layer classes, bypassing the repository abstraction. It also means KSP
runs the Room annotation processor twice across two modules, increasing build time.

**Warning Signs:**
- `@Entity` or `@Dao` files appearing under `app/src/main/java/com/wenubey/wenucommerce/`.
- ViewModels importing `androidx.room.*`.
- Room compilation errors appearing in the `app` module during builds.

**Prevention Strategy:**
Remove Room dependencies from `app/build.gradle.kts`. Room should live entirely in the `data`
module. The `app` module consumes repositories from the `data` module via the `domain` interfaces —
it has no need for Room directly. This also reduces build time by running KSP only in `data`.

**Which Phase Should Address It:**
Before writing the first Room entity. Perform the `build.gradle.kts` cleanup as a prerequisite step.

---

### MiP-5: Stripe Android SDK Version Compatibility with Compose

**Description:**
Stripe's Android SDK (specifically the Payment Sheet component) has historically had composability
issues with Jetpack Compose because the Payment Sheet is an Activity-based flow. As of Stripe Android
SDK 20.x+, `PaymentSheet.FlowController` is the recommended approach for fully custom Compose UIs,
while `PaymentSheet` (the pre-built sheet) launches as a separate Activity via `registerForActivityResult`.

The pitfall is using the older `Stripe.confirmPayment()` directly from a composable without an
Activity result contract, or trying to use the Payment Sheet from inside a `NavHost` composable
(it requires the `ComponentActivity` context, not just any `Context`).

**Warning Signs:**
- `PaymentSheet(activity, ...)` called with a `FragmentActivity` that is a Navigation `NavHost`
  rather than the root `ComponentActivity`.
- `LocalContext.current` passed to `PaymentSheet` — this may be a `ContextWrapper` inside Compose
  that is not the required `ComponentActivity`.
- Compilation errors about `ActivityResultLauncher` inside a composable function.

**Prevention Strategy:**
Launch the Stripe Payment Sheet from `MainActivity` using `rememberLauncherForActivityResult` or
the Payment Sheet's own `rememberPaymentSheet` Compose integration. Pass the `clientSecret` from the
ViewModel to the composable; the composable launches the sheet. The navigation to the payment result
screen happens via a callback from the sheet's completion handler, not via Navigation Compose directly.

Verify the exact Stripe Android SDK version (currently not in `libs.versions.toml` — it has not been
added yet) is compatible with the Compose BOM `2025.01.00` before adding it.

**Which Phase Should Address It:**
When beginning the Stripe integration phase. Check Stripe SDK release notes for the current Compose
compatibility status before adding the dependency.

---

### MiP-6: `Product.searchKeywords` List Too Large for Room `@TypeConverter`

**Description:**
`SearchKeywordsGenerator` produces denormalized keyword arrays stored in Firestore. A product with
a long title, many tags, and multiple attributes can easily generate 50-200 keyword tokens. Storing
this as a JSON-encoded `List<String>` column in Room is fine for retrieval, but if offline search
is implemented using Room's `LIKE` operator against a JSON blob column, it will be extremely slow
and produce false matches (e.g., searching "red" will match a product whose keyword JSON contains
the substring "red" in a word like "credentials").

**Warning Signs:**
- Room DAO queries using `WHERE searchKeywords LIKE '%query%'` against the JSON-encoded column.
- FTS (`@Fts4` or `@Fts5`) not used for offline text search.
- Query results returned by offline Room search not matching what Firestore search returns.

**Prevention Strategy:**
For offline product search in Room, use Room's Full-Text Search (`@Fts4` or `@Fts5`) tables:
- Create a shadow FTS table: `ProductFtsEntity` with a `content` column that is a concatenation of
  searchable fields.
- Use `MATCH` queries against the FTS table for offline search.
- Do not search the `searchKeywords` JSON blob with `LIKE`.

Alternatively, if the product catalog is small (under 5,000 products), store individual keywords as
a separate `ProductKeywordEntity` table with a foreign key to `ProductEntity`, then use a `JOIN` for
search. This is more normalized but adds complexity.

**Which Phase Should Address It:**
When implementing offline search. Do not design the Room entity assuming `searchKeywords` will work
as a search index — it will not without FTS.

---

## Phase-Specific Warnings Summary

| Phase Topic | Critical Pitfall | Moderate Pitfall | Mitigation |
|-------------|-----------------|------------------|------------|
| Room entity design | CP-1 (domain models as entities) | MP-1 (TypeConverter inconsistency) | Separate entity layer with mappers; kotlinx.serialization TypeConverters |
| Room database setup | CP-5 (schema migrations) | MP-4 (Room in app module), MiP-3 (destructiveMigration in prod) | Dedicate Room to data module; migration from day one |
| Offline sync architecture | CP-4 (dual-source flow observation) | MP-2 (WorkManager constraints) | Single-source-of-truth; Room as the only UI source |
| Conflict resolution design | CP-2 (no conflict strategy) | — | Define policy per entity type before writing sync code |
| Stripe / Payment integration | CP-3 (client-side payment logic) | MP-3 (SavedStateHandle for payment intent) | Cloud Functions for all payment operations; SavedStateHandle for clientSecret |
| FCM notifications | — | MP-7 (stale FCM token offline) | WorkManager-backed token upload with network constraint |
| Testing infrastructure | — | MP-6 (ViewModel test dispatcher setup) | Shared ViewModelTest base class; Firebase Emulator for integration tests |
| Cart domain model | — | — | Fix MiP-1 (quantity: Int) before Cart entity design |
| Koin DI expansion | — | MP-4 (Koin circular deps with DAOs) | Dedicated databaseModule; DAOs retrieved via database instance |
| Security | — | MP-5 (Firestore rules not updated) | Firestore security rule review accompanies every new sync collection |
| Offline search | — | — | FTS table or normalized keyword table; never LIKE on JSON blob (MiP-6) |

---

## Confidence Assessment

| Area | Confidence | Source |
|------|------------|--------|
| Room entity pitfalls (CP-1, CP-5, MP-1, MP-4, MiP-3, MiP-4) | HIGH | Direct codebase analysis: Room in libs.versions.toml, no @Entity classes yet, Room in both build.gradle.kts files |
| Firebase sync architecture (CP-2, CP-4, MP-2) | HIGH | Direct codebase analysis: existing callbackFlow patterns, no SyncWorker exists |
| Stripe payment pitfalls (CP-3, MP-3, MiP-5) | HIGH | Domain models show stripeProductId/stripePriceId, Firebase.functions already wired, no Stripe SDK in toml yet |
| FCM token handling (MP-7) | HIGH | Direct code inspection: MessagingService.onNewToken uses fire-and-forget Firestore write |
| Testing infrastructure (MP-6) | HIGH | Test files are empty scaffolding; coroutines-test 1.9.0 in deps (UnconfinedTestDispatcher era) |
| Domain model type issues (MiP-1, MiP-2) | HIGH | Direct inspection of Purchase.kt (quantity: Double) and all models using String timestamps |
| Firestore security rules (MP-5) | MEDIUM | No firestore.rules file found in the project; inferred from missing access pattern protection |
| Offline search design (MiP-6) | MEDIUM | SearchKeywordsGenerator pattern observed; Room FTS recommendation based on established Android patterns |
