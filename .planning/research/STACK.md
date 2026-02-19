# Stack Research: Offline-First, Payments, Notifications, Testing

**Research Date:** 2026-02-19
**Research Type:** Project Research — Stack dimension (subsequent milestone)
**Scope:** What libraries and patterns are needed to add offline-first (Room + Firebase sync), Stripe payments, FCM notifications, and NiA-style testing to WenuCommerce.

> **Note on version verification:** External search tools were unavailable during this session. Versions cited are from training data (knowledge cutoff January 2025). All versions marked [VERIFY] must be confirmed against Maven Central / official release pages before adding to `libs.versions.toml`. Confidence levels are assigned per section.

---

## 1. Offline-First: Room as Source of Truth + Firebase Sync

### Decision Summary

Room is already declared in `libs.versions.toml` (v2.6.1) but has no DAOs, entities, or Database class implemented. The architecture shift is: all reads go through Room, writes go to Room first then sync to Firestore via a background sync mechanism. Firebase becomes the cloud backup layer, not the primary data source.

### Room — Upgrade from 2.6.1

**Confidence: HIGH** — Room 2.6.1 was current at knowledge cutoff. There may be a minor update.

```toml
# libs.versions.toml — update existing entry
room = "2.6.1"   # [VERIFY] Check for 2.7.x at developer.android.com/jetpack/androidx/releases/room
```

No API-breaking changes expected between 2.6.x and any 2.7.x. The existing KSP plugin pairing `ksp = "2.1.0-1.0.29"` must remain aligned with Kotlin 2.1.0 — Room's KSP processor version must match. The existing `room` Gradle plugin already declared in `libs.versions.toml` handles schema migrations.

**Why stay on Room (not SQLDelight or other):** Room integrates natively with KSP (already set up), Paging 3 (already in deps via `room-paging`), and has a Kotlin-coroutines-native API via `room-ktx`. No migration cost — dependencies already declared.

**New artifacts needed:** None — `room-runtime`, `room-ktx`, `room-compiler`, `room-paging`, `room-testing` are all already in `libs.versions.toml`.

**What needs to be built (not added):**
- `AppDatabase` class annotated `@Database`
- Entity classes for: `ProductEntity`, `CartItemEntity`, `WishlistItemEntity`, `OrderEntity`, `CategoryEntity`, `NotificationEntity`
- DAO interfaces per entity
- TypeConverters for complex fields (lists, enums, nested objects)
- Schema migrations (Room plugin handles schema export to `data/schemas/`)

### WorkManager — Background Firebase Sync

**Confidence: HIGH (version needs verification)**

WorkManager is the prescribed Android solution for deferrable, guaranteed background work. It survives app termination and device reboots — essential for queuing offline writes (cart additions, order placements, reviews) and syncing them when connectivity returns.

```toml
# libs.versions.toml — NEW entries
workManager = "2.9.1"   # [VERIFY] latest stable at developer.android.com/jetpack/androidx/releases/work
```

```toml
# [libraries]
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
```

```toml
# [plugins] — none needed, WorkManager is runtime-only
```

**Gradle dependency placement:** `data` module's `build.gradle.kts` for the sync workers; optionally `app` for scheduling triggers.

**Why WorkManager over foreground service or AlarmManager:** WorkManager enforces Doze-mode compatibility, handles battery optimization constraints (`NetworkType.CONNECTED`), supports retry with exponential backoff, and provides observable `WorkInfo` for "pending sync" UI indicators (PROJECT.md requirement: "Pending sync status visible to users").

**Sync pattern to implement:**
1. All writes go to Room immediately (optimistic local write).
2. After each write, enqueue a `OneTimeWorkRequest` (`SyncProductWorker`, `SyncCartWorker`, etc.) with `Constraints(NetworkType.CONNECTED)`.
3. Worker reads Room entity, writes to Firestore, marks entity as synced.
4. Firestore real-time listener (existing pattern via `callbackFlow`) pushes remote changes back to Room on connectivity restore.

**Why NOT Firebase Offline Persistence alone:** Firestore's built-in offline cache (`setPersistenceEnabled(true)`) does handle basic caching but: (a) it doesn't expose a queryable local database — you still go through Firestore SDK for reads, (b) no Room entities means no Paging 3 integration, (c) no explicit pending-write status visibility, (d) cache eviction is opaque. Room as source of truth is the NiA-recommended pattern and gives full control.

### Connectivity Awareness

**Confidence: HIGH**

```toml
# libs.versions.toml — NEW entries
connectivityManager = "1.0.0"   # not a separate library; use Android framework ConnectivityManager
```

Use `ConnectivityManager` from the Android framework (no additional dependency needed) via `NetworkCallback` to observe network state as a `StateFlow<Boolean>`. This drives "offline mode" UI banners and gates sync enqueueing.

Alternatively, use the `androidx.core:core-ktx` already in deps (1.15.0) which exposes `connectivityManager` extension properties.

---

## 2. Stripe Android SDK

### Decision Summary

Payments flow: Customer taps "Checkout" → app calls Firebase Cloud Function (server-side) to create a Stripe `PaymentIntent` and returns a `client_secret` → app initializes Stripe `PaymentSheet` with that secret → Stripe SDK handles PCI-compliant card collection and confirmation → Cloud Function webhook confirms payment → order created in Firestore/Room.

This keeps all Stripe secret keys on the server (Cloud Functions), never in the Android app. Only the publishable key goes to the client.

### Stripe Android SDK

**Confidence: MEDIUM** — Stripe releases frequently. Version was ~20.x in late 2024.

```toml
# libs.versions.toml — NEW entries
stripe = "20.52.0"   # [VERIFY] https://github.com/stripe/stripe-android/releases — check latest stable
```

```toml
# [libraries]
stripe-android = { group = "com.stripe", name = "stripe-android", version.ref = "stripe" }
```

**Gradle dependency placement:** `app` module only. Stripe SDK is UI-facing (PaymentSheet is a Compose/View-based bottom sheet).

**Alternative — `financial-connections` artifact:** Only needed if adding bank account linking (ACH). Not needed for card payments. Skip it.

**Why PaymentSheet over custom card UI:** PaymentSheet is Stripe's prebuilt, PCI-SAQ-A-certified UI. It handles card input, 3DS authentication, Apple/Google Pay, and saved payment methods without custom implementation. The PROJECT.md requirement is "Stripe PaymentSheet" explicitly.

**Integration points:**
- `PaymentConfiguration.init(context, publishableKey)` in `Application.onCreate()` (or lazily).
- `PaymentSheet` initialized in the Checkout ViewModel / Compose screen.
- `PaymentSheet.Result` sealed class (Completed, Canceled, Failed) drives post-payment navigation.
- Publishable key stored in `BuildConfig` (injected from `local.properties`, already the project pattern).

**Firebase Cloud Functions requirements (not Android lib):**
- `stripe` Node.js package on Functions side.
- Function: `createPaymentIntent(amount, currency, customerId)` → returns `{ clientSecret }`.
- Webhook handler: `handleStripeWebhook` to confirm payment and trigger order creation.

**What NOT to use:**
- `stripe-terminal` — for physical POS hardware, not needed.
- `stripe-3ds2android` — bundled automatically inside `stripe-android`, don't add separately.
- Custom card entry with `CardInputWidget` — deprecated in favor of `PaymentSheet`.

---

## 3. FCM Notifications — Expanded Capabilities

### Current State

FCM is already integrated: `firebase-messaging-ktx` in deps, `MessagingService.kt` exists, `NotificationPreferences.kt` for user settings. The existing setup handles token registration and basic message receipt.

### What's Missing

The PROJECT.md requirements add: notification history viewable in-app, per-type preferences (discount/order/promotional), and triggering from order status changes.

**No new Android libraries are needed for FCM itself.** The existing `firebase-messaging-ktx` (via Firebase BOM 33.8.0) covers all FCM functionality.

**What needs to be built:**
1. `NotificationEntity` in Room — persists received notifications for in-app history.
2. `NotificationDao` — insert on receive, query by type, mark as read.
3. `MessagingService.kt` enhancement — on `onMessageReceived()`, write to Room `NotificationEntity` before posting system notification.
4. `NotificationPreferencesRepository` — expand beyond banner visibility to per-type enable/disable (discount, order, promotional). DataStore (already in deps) is the right tool — it already has `NotificationPreferences.kt` as a base.
5. Cloud Functions (server-side) — trigger FCM sends on order status transitions via Firestore trigger (`functions.firestore.document('orders/{id}').onUpdate()`).

**Android notification channels (no new library):**
- Create `NotificationChannel` objects for each notification type in `Application.onCreate()`.
- Channel IDs: `CHANNEL_ORDERS`, `CHANNEL_DISCOUNTS`, `CHANNEL_PROMOTIONS`.
- Per-channel importance levels control OS-level notification behavior.

### DataStore — Already Present, Extend Usage

**Confidence: HIGH** — DataStore 1.1.2 is current.

```toml
# Already declared:
dataStore = "1.1.2"   # [VERIFY] developer.android.com/jetpack/androidx/releases/datastore
```

DataStore Preferences handles notification enable/disable per type. No additional library needed. Extend the existing `NotificationPreferences.kt` pattern.

---

## 4. Testing — NiA-Style Comprehensive Coverage

### NiA Testing Philosophy Applied to WenuCommerce

Now in Android (NiA) uses: **fakes over mocks** for repositories, **`TestDispatcher`** for coroutine control, **`HiltTestRunner`** for DI in instrumented tests, **`ComposeTestRule`** for UI, and **`turbine`** for Flow testing. This project uses Koin instead of Hilt, which changes one piece.

### Turbine — Flow Testing

**Confidence: HIGH** — This is the most impactful missing test library.

```toml
# libs.versions.toml — NEW entries
turbine = "1.2.0"   # [VERIFY] https://github.com/cashapp/turbine/releases
```

```toml
# [libraries]
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

**Gradle dependency placement:** `testImplementation` in all three modules (`domain`, `data`, `app`).

**Why turbine over manual `collectAsState` in tests:** Testing `StateFlow` and `Flow` with `collect {}` in coroutine tests requires careful `launch`/`cancel` orchestration. Turbine's `Flow.test {}` DSL provides `awaitItem()`, `awaitComplete()`, `awaitError()` — eliminating race conditions in flow assertion. It's the industry standard for Kotlin Flow testing.

**Example pattern:**
```kotlin
@Test
fun `cart item added updates cart state`() = runTest {
    val viewModel = CartViewModel(fakeCartRepository)
    viewModel.cartState.test {
        awaitItem() // initial empty state
        viewModel.onAction(CartAction.AddItem(testProduct))
        val state = awaitItem()
        assertThat(state.items).hasSize(1)
        cancelAndIgnoreRemainingEvents()
    }
}
```

### MockK — Upgrade from 1.12.4

**Confidence: HIGH** — 1.12.4 is significantly outdated (2022). Current is ~1.13.x.

```toml
# libs.versions.toml — UPDATE existing entry
mockk = "1.13.14"   # [VERIFY] https://github.com/mockk/mockk/releases — check latest stable
```

**Why upgrade:** 1.12.4 has known issues with Kotlin 2.x coroutine mocking. The project uses Kotlin 2.1.0 — MockK 1.13.x aligns coroutine support with `kotlinx.coroutines` 1.8+. The `coEvery` / `coVerify` APIs remain identical, so no test rewrites needed.

**No change to Mockito:** Keep `mockito-core` 5.12.0 and `mockito-kotlin` 5.4.0 for any Java-interop mocking. However, the project should standardize on MockK for new tests (Kotlin-first, coroutine-aware) and only use Mockito for legacy or third-party Java code.

### Koin Test — Missing from Current Setup

**Confidence: HIGH**

```toml
# libs.versions.toml — NEW entries (Koin BOM already declared at 4.0.1)
# No new version entry needed — use existing koin-bom
```

```toml
# [libraries]
koin-test = { module = "io.insert-koin:koin-test" }
koin-test-junit4 = { module = "io.insert-koin:koin-test-junit4" }
```

**Gradle dependency placement:** `testImplementation` in `app` module (and any module with Koin modules to verify).

**Why needed:** Koin provides `KoinTest` interface and `declareMock<T>()` for overriding DI in tests without starting the full Koin container. Critical for ViewModel integration tests that go through DI. Without it, ViewModels must be constructed manually in every test — verbose and fragile as dependencies grow.

**NiA equivalent:** NiA uses `HiltAndroidRule` for DI in instrumented tests. For Koin the equivalent is `KoinTestRule` (JUnit4 rule) or `startKoin {}` in `@Before`. `koin-test-junit4` provides `KoinTestRule` out of the box.

### kotlinx.coroutines.test — Upgrade

**Confidence: HIGH**

```toml
# libs.versions.toml — UPDATE existing entry
kotlinTest = "1.9.0"   # Current is likely 1.9.x or 1.10.x
# [VERIFY] https://github.com/Kotlin/kotlinx.coroutines/releases
```

The `kotlinx-coroutines-test` version must match or be compatible with `kotlinx-coroutines-core` used by the Firebase/Ktor dependencies. Check transitive dependency resolution — if Firebase BOM pulls in `1.7.x` coroutines, test library should be `1.7.x` or higher.

**Key API to use:** `runTest {}` (already the pattern in TESTING.md), `TestCoroutineScheduler`, `UnconfinedTestDispatcher` for eager execution in ViewModel tests, `StandardTestDispatcher` for controlled advancement.

**`MainDispatcherRule` — implement as shared test utility:**
```kotlin
// domain/src/test/java/com/wenubey/domain/util/MainDispatcherRule.kt
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```
Place in a `test-fixtures` source set or duplicate per module (simpler given 3-module constraint).

### Compose UI Testing — Already Present, Patterns Needed

**Confidence: HIGH** — `androidx.compose.ui:ui-test-junit4` is already in deps.

No new library needed. The existing `ui-test-junit4` and `ui-test-manifest` cover all Compose UI testing. What's missing is usage patterns.

**Key APIs:**
- `createComposeRule()` — for standalone Compose tests (no Activity needed).
- `createAndroidComposeRule<ComponentActivity>()` — when Activity context is required.
- `onNodeWithText()`, `onNodeWithTag()`, `performClick()`, `assertIsDisplayed()`.
- `waitUntil {}` — for async state propagation in tests.

**Semantic test tags:** Add `Modifier.testTag("add_to_cart_button")` in composables to enable reliable UI targeting without relying on text content.

### Room Testing — Already Present

**Confidence: HIGH** — `room-testing` already in deps.

```kotlin
// Pattern for in-memory Room database tests (instrumented)
@RunWith(AndroidJUnit4::class)
class CartDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var cartDao: CartDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // tests only
            .build()
        cartDao = db.cartDao()
    }

    @After
    fun closeDb() = db.close()
}
```

### Jacoco — Test Coverage Reporting

**Confidence: HIGH** — No version needed (built into AGP).

Jacoco is bundled with AGP 8.7.3. Add the plugin and configuration to `app/build.gradle.kts`:

```kotlin
// In app/build.gradle.kts
plugins {
    id("jacoco")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    // ... classDirectories, sourceDirectories, executionData
}
```

No `libs.versions.toml` entry needed. This satisfies the PROJECT.md testing requirement.

---

## 5. Version Compatibility Matrix

| Library | Current | Recommended | Confidence | Action |
|---------|---------|-------------|------------|--------|
| Room | 2.6.1 | 2.6.1+ | HIGH | [VERIFY] minor update |
| WorkManager | — | 2.9.1 | HIGH | [VERIFY] add new |
| Stripe Android SDK | — | 20.52.0 | MEDIUM | [VERIFY] check GitHub releases |
| Turbine | — | 1.2.0 | HIGH | [VERIFY] add new |
| MockK | 1.12.4 | 1.13.14 | HIGH | [VERIFY] upgrade |
| koin-test | — | via BOM 4.0.1 | HIGH | add new |
| koin-test-junit4 | — | via BOM 4.0.1 | HIGH | add new |
| coroutines-test | 1.9.0 | 1.9.0+ | MEDIUM | [VERIFY] check alignment |
| Compose BOM | 2025.01.00 | current stable | MEDIUM | [VERIFY] quarterly |
| Firebase BOM | 33.8.0 | current stable | MEDIUM | [VERIFY] quarterly |

---

## 6. What NOT to Use

### Do Not Add

| Library | Reason |
|---------|--------|
| **Hilt** | Project decision made in PROJECT.md: "Keep Koin, avoid migration cost". Koin 4.0.1 is production-grade. |
| **SQLDelight** | Room is already in deps, KSP configured, entities fit Kotlin data classes. No reason to add a competing ORM. |
| **Retrofit** | Ktor is already the HTTP client. Retrofit would duplicate networking. Use Ktor for Stripe Cloud Function calls. |
| **RxJava / LiveData** | Project uses Kotlin Flows and StateFlow throughout. Adding Rx would split the reactive model. |
| **Stripe Terminal SDK** | Physical POS hardware SDK — irrelevant for a mobile e-commerce app. |
| **Firebase Offline Persistence** (as primary) | Firestore's built-in offline cache does not provide queryable local storage, Paging 3 integration, or visible sync status. Room serves this role. Can enable Firestore persistence as a supplementary cache during the transition period, then disable once Room sync is stable. |
| **Robolectric** | The project already uses instrumented tests with AndroidJUnit4. Robolectric adds a parallel shadow framework that conflicts with real Firebase mocking. Run instrumented tests on emulator instead. |
| **JUnit 5 (Jupiter)** | Compose UI testing requires JUnit 4 (`@get:Rule` + `createComposeRule()`). JUnit 5 does not support JUnit 4 rules without `junit-vintage-engine` adapter — adds complexity with no benefit. Stick with JUnit 4 throughout. |
| **Espresso** (as primary UI test) | Already in deps but superseded by Compose UI testing APIs. Keep for any View-based tests, but all new UI tests should use `ComposeTestRule`. |
| **MockWebServer** | Ktor already has `ktor-client-mock` in deps (2.3.12) for HTTP mocking. MockWebServer would be redundant. |

---

## 7. Module Placement Summary

```
domain/
  build.gradle.kts
    testImplementation: turbine, mockk (upgraded), coroutines-test, koin-test

data/
  build.gradle.kts
    implementation: work-runtime-ktx  (sync workers live here)
    testImplementation: turbine, mockk (upgraded), coroutines-test, koin-test, room-testing
    androidTestImplementation: room-testing

app/
  build.gradle.kts
    implementation: stripe-android
    testImplementation: turbine, mockk (upgraded), coroutines-test, koin-test, koin-test-junit4
    androidTestImplementation: ui-test-junit4 (already), ui-test-manifest (already)
```

WorkManager is in `data` because sync workers are data-layer concerns (they call DAOs and Firestore). Stripe is in `app` because PaymentSheet is a UI component.

---

## 8. Architecture Patterns

### Offline Write Queue Pattern

```
User Action → ViewModel → Repository (data module)
                            ├── Room DAO write (immediate, returns)
                            └── Enqueue WorkManager OneTimeWorkRequest
                                    ↓ (when CONNECTED)
                               SyncWorker
                                    ├── Read from Room (pending sync flag)
                                    ├── Write to Firestore
                                    └── Mark Room entity as synced
```

**Pending sync flag:** Add `isSynced: Boolean` and `updatedAt: Long` fields to all Room entities. `SyncWorker` queries `WHERE isSynced = 0` and processes the queue.

### Conflict Resolution Strategy

**Last-write-wins with server timestamp:** When syncing, include `updatedAt` from the Room entity. Cloud Function (or Firestore rule) rejects writes where server `updatedAt > client updatedAt`. This is simple and appropriate for a v1 e-commerce app where simultaneous edits are rare.

More complex strategies (operational transforms, CRDTs) are out of scope per PROJECT.md's 3-module architecture constraint.

### Repository Layer Changes for Offline-First

Every repository goes from:
```
Repository → Firestore (read + write)
```
To:
```
Repository → Room DAO (read, always)
           → Room DAO (write) + WorkManager enqueue (write, always)
           ← Firestore listener → Room DAO (remote sync, background)
```

Expose data as `Flow<List<T>>` from Room DAOs. The existing `callbackFlow` pattern for Firestore listeners becomes the sync trigger that writes into Room, not the UI data source.

### NiA-Style Fake Repositories for Testing

Instead of mocking Firebase (complex, brittle), create `FakeCartRepository`, `FakeProductRepository`, etc. that implement domain interfaces and hold in-memory state:

```kotlin
// domain/src/test/java/.../fake/FakeCartRepository.kt
class FakeCartRepository : CartRepository {
    private val _items = MutableStateFlow<List<CartItem>>(emptyList())
    override fun observeCart(): Flow<List<CartItem>> = _items
    override suspend fun addItem(item: CartItem) {
        _items.update { it + item }
    }
    // etc.
}
```

This is the NiA pattern: fakes are deterministic, no network, no coroutine complexity. Tests that use fakes run in milliseconds.

---

## 9. Recommended `libs.versions.toml` Changes

### New Version Entries

```toml
workManager = "2.9.1"        # [VERIFY]
stripe = "20.52.0"           # [VERIFY]
turbine = "1.2.0"            # [VERIFY]
mockk = "1.13.14"            # [VERIFY] — upgrade from 1.12.4
```

### New Library Entries

```toml
# WorkManager
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }

# Stripe
stripe-android = { group = "com.stripe", name = "stripe-android", version.ref = "stripe" }

# Turbine (Flow testing)
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

# Koin Testing (version from existing koin-bom at 4.0.1)
koin-test = { module = "io.insert-koin:koin-test" }
koin-test-junit4 = { module = "io.insert-koin:koin-test-junit4" }
```

### Modified Entries

```toml
# Upgrade MockK (was 1.12.4)
mockk = "1.13.14"   # [VERIFY]
```

---

## 10. Verification Checklist

Before adding to `libs.versions.toml`, verify each version:

- [ ] **WorkManager**: `developer.android.com/jetpack/androidx/releases/work`
- [ ] **Stripe Android SDK**: `github.com/stripe/stripe-android/releases`
- [ ] **Turbine**: `github.com/cashapp/turbine/releases`
- [ ] **MockK**: `github.com/mockk/mockk/releases`
- [ ] **Room**: `developer.android.com/jetpack/androidx/releases/room` (may have minor update)
- [ ] **kotlinx-coroutines**: confirm test version aligns with transitive `kotlinx-coroutines-core` pulled by Firebase BOM 33.8.0
- [ ] **Koin test artifacts**: confirm `koin-test` and `koin-test-junit4` are included in Koin BOM 4.0.1 (they are in Koin 3.x+ but verify 4.0.1 artifact names)

---

*Research completed: 2026-02-19*
*External version verification unavailable during research session — all [VERIFY] items must be confirmed before implementation.*
