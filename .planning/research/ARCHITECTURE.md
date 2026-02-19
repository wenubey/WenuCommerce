# Architecture Research: Offline-First Room + Firebase Sync, Stripe Payments, FCM Notifications

**Research Date:** 2026-02-19
**Research Type:** Project Research — Architecture dimension
**Milestone Context:** Subsequent — integrating offline-first, payments, and notifications into existing Clean Architecture

---

## Question Being Answered

How should Room + Firebase offline sync be architected? What are the major components for the sync layer, Stripe payment flow, and FCM notification system — and how do they compose with the existing 3-module (domain / data / app) Clean Architecture using Koin?

---

## Existing Architecture Baseline

The current system has a well-defined foundation:

- **3 modules**: `domain` (contracts + models), `data` (Firebase repository impls), `app` (Compose UI + ViewModels)
- **MVVM** with `StateFlow` for state, sealed `Action` interfaces for events
- **Repository pattern**: Interfaces in `domain`, implementations in `data`
- **`safeApiCall()`** wrapper: wraps all suspend calls in `try-catch`, returns `Result<T>`
- **Firebase only**: No local database used today; `callbackFlow` from Firestore is the reactive stream
- **Koin 4.0.1**: `repositoryModule`, `viewModelModule`, `firebaseModule` registered in `app`
- **Room 2.6.1** already declared in `data/build.gradle.kts` but has zero entities or DAOs today

The critical constraint: **Room does not replace Firebase. Firebase remains the cloud source of truth and write destination. Room becomes the local read cache and write queue.**

---

## Component Definitions and Boundaries

### 1. Room Database Layer (new, in `data` module)

**Responsibility**: Persist all data the app reads (products, categories, cart, orders, reviews, notifications) as local entities. This is what ViewModels ultimately read from.

**Components**:

```
data/src/main/java/com/wenubey/data/local/
  WenuCommerceDatabase.kt        — @Database, lists all DAOs
  dao/
    ProductDao.kt                — queries for product entities
    CategoryDao.kt
    CartDao.kt
    OrderDao.kt
    ReviewDao.kt
    NotificationDao.kt
  entity/
    ProductEntity.kt             — @Entity mirror of Product domain model
    CategoryEntity.kt
    CartItemEntity.kt
    OrderEntity.kt
    ReviewEntity.kt
    NotificationEntity.kt
  mapper/
    ProductMapper.kt             — Entity ↔ Domain model conversions
    CategoryMapper.kt
    OrderMapper.kt
    (etc.)
```

**Boundaries**:
- Room entities are internal to `data`. Domain models are the shared currency. Mappers live in `data`.
- Domain layer never imports Room annotations or entity types.
- DAOs are injected only into repository implementations, never into ViewModels.

**Key design decisions**:
- Each entity mirrors its domain model but uses Room-compatible types (no nested objects; flatten or use `@TypeConverter` for lists/embedded structs).
- `ProductEntity` must serialize `variants: List<ProductVariant>` via a `TypeConverter` (JSON string or comma-separated IDs).
- Use `@PrimaryKey` on the Firebase document ID string (e.g., `productId: String`).

---

### 2. Sync Layer (new, in `data` module)

**Responsibility**: Keep Room and Firestore in sync. Firebase is truth for committed data. Room is the device cache. The sync layer bridges them in both directions.

**Sub-components**:

#### 2a. Read Sync — Firebase → Room

```
data/src/main/java/com/wenubey/data/sync/
  SyncManager.kt                 — orchestrates all sync workers
  ProductSyncWorker.kt           — WorkManager worker: fetch Firestore → upsert Room
  CategorySyncWorker.kt
  OrderSyncWorker.kt
  NotificationSyncWorker.kt
```

**Pattern**: Each `RepositoryImpl` uses `callbackFlow { ... awaitClose {} }` from Firestore as before, but instead of returning the flow directly to the ViewModel, it writes into Room on each emission, then Room's `@Query` `Flow<>` is what the ViewModel collects.

Concrete data flow for products:
```
Firestore snapshot listener
  → ProductRepositoryImpl.observeActiveProductsByCategory()
    → converts Firestore docs to ProductEntity
    → calls productDao.upsertAll(entities)
    → returns productDao.observeByCategory(categoryId)   ← Room Flow, not Firestore Flow
```

This means: **the public return type of `observeXxx()` repository methods does not change** (`Flow<List<Product>>`), but internally the emission now comes from Room, not Firestore. This is transparent to the ViewModel.

#### 2b. Write Sync — Room (pending) → Firebase

For writes that happen offline, use a **pending operations queue** approach:

```
data/src/main/java/com/wenubey/data/local/entity/
  PendingOperationEntity.kt      — id, type (CREATE/UPDATE/DELETE), payload (JSON), entityType, retryCount, createdAt, status
data/src/main/java/com/wenubey/data/local/dao/
  PendingOperationDao.kt         — insert, getAll, deleteById, markFailed
data/src/main/java/com/wenubey/data/sync/
  WriteQueueWorker.kt            — WorkManager PeriodicWork, drains queue when online
  ConnectivityObserver.kt        — observes network state (ConnectivityManager)
```

**Pattern for offline writes**:
1. ViewModel calls `cartRepository.addToCart(item)`
2. `CartRepositoryImpl.addToCart()` writes optimistically to Room (`cartDao.insert(entity)`)
3. It also enqueues a `PendingOperationEntity` (type=CREATE, entityType=CART_ITEM, payload=json)
4. Returns `Result.success(Unit)` immediately — UI reflects change instantly
5. `WriteQueueWorker` runs when network is available, drains pending operations in order, calls Firebase, marks each operation complete or failed

**Conflict resolution strategy**:
- Last-write-wins for user-owned data (cart, wishlist, profile fields)
- Server-wins for product inventory (`totalStockQuantity`) — read from Firestore, never write locally
- Order status is server-authoritative; client never writes status directly

#### 2c. Sync Status Exposure

```
domain/src/main/java/com/wenubey/domain/model/
  SyncStatus.kt                  — sealed class: Idle, Syncing, PendingCount(n: Int), Error(message)
domain/src/main/java/com/wenubey/domain/repository/
  SyncRepository.kt              — fun observeSyncStatus(): Flow<SyncStatus>
data/src/main/java/com/wenubey/data/repository/
  SyncRepositoryImpl.kt          — reads PendingOperationDao count, emits status
```

ViewModels that need to show sync indicators collect from `SyncRepository.observeSyncStatus()`.

---

### 3. Revised Repository Implementations

Each `XxxRepositoryImpl` gains a dual responsibility: Firebase for remote, Room for local.

**Constructor signature pattern** (example for ProductRepositoryImpl):

```kotlin
class ProductRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val productDao: ProductDao,
    private val pendingOperationDao: PendingOperationDao,
    private val dispatcherProvider: DispatcherProvider,
) : ProductRepository
```

**Method classification**:

| Method type | Local write first? | Firebase call? | Returns from? |
|-------------|-------------------|----------------|---------------|
| `observeXxx()` Flow | No | Starts listener, writes to Room | Room DAO Flow |
| `getXxxById()` | No | Falls back if Room miss | Room first, Firebase fallback |
| `createXxx()` / `updateXxx()` | Yes (optimistic) | Enqueue if offline | Immediate Result.success |
| Admin status changes | No | Direct Firebase write | Firebase result |
| `incrementViewCount()` | No | Direct Firebase (best-effort) | Firebase result |

---

### 4. Stripe Payment Flow

**Responsibility**: Allow customers to pay for cart items. Payment intent is created server-side (Firebase Cloud Function). Client uses Stripe Android SDK's `PaymentSheet`.

**New components**:

```
domain/src/main/java/com/wenubey/domain/model/
  Order.kt                       — orderId, customerId, sellerId, items: List<OrderItem>, status: OrderStatus, totalAmount, stripePaymentIntentId, createdAt, updatedAt
  OrderItem.kt                   — productId, variantId, quantity, priceAtPurchase
  OrderStatus.kt                 — enum: PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
  CartItem.kt                    — productId, variantId, quantity, priceSnapshot

domain/src/main/java/com/wenubey/domain/repository/
  CartRepository.kt              — addItem, removeItem, updateQuantity, clearCart, observeCart(): Flow<List<CartItem>>
  OrderRepository.kt             — createOrder, observeOrders, getOrderById, updateStatus (seller)
  PaymentRepository.kt           — createPaymentIntent(cartItems): Result<PaymentIntentClientSecret>

data/src/main/java/com/wenubey/data/repository/
  CartRepositoryImpl.kt          — Room-only (cart is always local until checkout)
  OrderRepositoryImpl.kt         — Room + Firestore sync
  PaymentRepositoryImpl.kt       — calls Firebase Cloud Function via Firebase.functions
```

**Stripe checkout data flow**:

```
CustomerCartScreen
  → CartViewModel.onAction(OnCheckoutClicked)
    → paymentRepository.createPaymentIntent(cartItems)   [suspends]
      → Firebase.functions("createPaymentIntent").call(params).await()
        ← returns { clientSecret: "pi_xxx_secret_yyy" }
      ← Result.success(clientSecret)
    → PaymentSheet.Configuration built with clientSecret
    → PaymentSheet.present() triggered from UI side-effect
      [Stripe SDK handles payment UI]
    → PaymentSheet.Result callback received in ViewModel
      → if PaymentSheetResult.Completed:
          orderRepository.createOrder(cartItems, paymentIntentId)
          cartRepository.clearCart()
          navigate to OrderConfirmationScreen
      → if PaymentSheetResult.Failed:
          state.copy(paymentError = result.error.message)
      → if PaymentSheetResult.Canceled:
          state.copy(checkoutStep = CheckoutStep.Review)
```

**Firebase Cloud Function contract** (server-side, not changed by client):
- Input: `{ items: [{productId, variantId, quantity, price}], customerId, currency }`
- Output: `{ clientSecret: String, paymentIntentId: String }`
- Server verifies prices against Firestore (prevents price tampering)
- Server creates Firestore order document in `PLACED` status after successful payment confirmation (via Stripe webhook)

**Koin registration**:
```kotlin
// In repositoryModule
singleOf(::CartRepositoryImpl).bind<CartRepository>()
singleOf(::OrderRepositoryImpl).bind<OrderRepository>()
singleOf(::PaymentRepositoryImpl).bind<PaymentRepository>()
```

**Stripe SDK dependency** (add to `app/build.gradle.kts`):
```kotlin
implementation("com.stripe:stripe-android:20.x.x")
```

---

### 5. FCM Notification System

**Responsibility**: Receive push notifications for order updates, discounts, promotions. Store notification history locally in Room. Respect user notification preferences.

**Existing foundation**: `MessagingService.kt` handles `onNewToken()` and `onMessageReceived()` but only shows `device_login_channel` notifications. This must be extended.

**New components**:

```
domain/src/main/java/com/wenubey/domain/model/
  AppNotification.kt             — notificationId, type: NotificationType, title, body, payload: Map<String,String>, isRead, createdAt
  NotificationType.kt            — enum: ORDER_STATUS_CHANGED, DISCOUNT_ANNOUNCEMENT, PROMOTIONAL, DEVICE_LOGIN

domain/src/main/java/com/wenubey/domain/repository/
  NotificationRepository.kt     — observeNotifications(): Flow<List<AppNotification>>, markAsRead(id), clearAll()

data/src/main/java/com/wenubey/data/local/entity/
  NotificationEntity.kt          — @Entity for Room

data/src/main/java/com/wenubey/data/repository/
  NotificationRepositoryImpl.kt  — persists to Room on receive; reads from Room

app/src/main/java/com/wenubey/wenucommerce/notification/
  MessagingService.kt            — extended (existing file)
```

**Extended `MessagingService` design**:

```kotlin
class MessagingService : FirebaseMessagingService() {

    private val notificationRepository: NotificationRepository by inject()
    private val notificationPreferences: NotificationPreferences by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        val type = NotificationType.fromString(message.data["type"])

        // Check user preference before showing
        if (!notificationPreferences.isTypeEnabled(type)) return

        // Persist to Room for notification history
        val appNotification = AppNotification(
            notificationId = message.messageId ?: UUID.randomUUID().toString(),
            type = type,
            title = message.notification?.title ?: message.data["title"] ?: "",
            body = message.notification?.body ?: message.data["body"] ?: "",
            payload = message.data,
            isRead = false,
            createdAt = System.currentTimeMillis().toString()
        )
        // Fire-and-forget coroutine (MessagingService has its own lifecycle)
        CoroutineScope(Dispatchers.IO).launch {
            notificationRepository.saveNotification(appNotification)
        }

        showNotification(type, appNotification)
    }

    private fun showNotification(type: NotificationType, notification: AppNotification) {
        val channelId = type.channelId   // each type has its own channel
        // ... existing notification display logic, extended for deep-link payload
    }
}
```

**Notification channel strategy**:

| NotificationType | Channel ID | Importance |
|-----------------|------------|------------|
| `ORDER_STATUS_CHANGED` | `order_updates_channel` | HIGH |
| `DISCOUNT_ANNOUNCEMENT` | `discount_channel` | DEFAULT |
| `PROMOTIONAL` | `promotional_channel` | LOW |
| `DEVICE_LOGIN` | `device_login_channel` | HIGH (existing) |

**Notification deep-link tap handling**:

```
PendingIntent in notification → MainActivity
  → MainActivity reads extras (existing NAVIGATE_TO_SETTINGS pattern)
  → Extend to: notificationType, orderId, productId
  → NavController.navigate(AppNav.OrderDetail(orderId))
```

**Notification preferences** (extend existing `NotificationPreferences`):
```kotlin
// In data/NotificationPreferences.kt (DataStore-backed)
suspend fun setTypeEnabled(type: NotificationType, enabled: Boolean)
fun isTypeEnabled(type: NotificationType): Boolean
fun observePreferences(): Flow<Map<NotificationType, Boolean>>
```

---

### 6. Koin DI Updates

The `repositoryModule` in `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` must grow:

```kotlin
// New: Room database singleton
single {
    Room.databaseBuilder(get(), WenuCommerceDatabase::class.java, "wenucommerce.db")
        .fallbackToDestructiveMigration()
        .build()
}

// New: DAOs (extracted from database singleton)
single { get<WenuCommerceDatabase>().productDao() }
single { get<WenuCommerceDatabase>().categoryDao() }
single { get<WenuCommerceDatabase>().cartDao() }
single { get<WenuCommerceDatabase>().orderDao() }
single { get<WenuCommerceDatabase>().reviewDao() }
single { get<WenuCommerceDatabase>().notificationDao() }
single { get<WenuCommerceDatabase>().pendingOperationDao() }

// Extended repositoryModule bindings
singleOf(::CartRepositoryImpl).bind<CartRepository>()
singleOf(::OrderRepositoryImpl).bind<OrderRepository>()
singleOf(::PaymentRepositoryImpl).bind<PaymentRepository>()
singleOf(::NotificationRepositoryImpl).bind<NotificationRepository>()
singleOf(::SyncRepositoryImpl).bind<SyncRepository>()
```

WorkManager workers use `KoinComponent` or constructor injection via `KoinWorkerFactory`:
```kotlin
// In WenuCommerce.kt Application class
WorkManager.initialize(this, Configuration.Builder()
    .setWorkerFactory(KoinWorkerFactory())
    .build()
)
```

---

## Data Flow Summary

### Read flow (offline-first):

```
UI Layer (Composable)
  ↕ collectAsStateWithLifecycle()
ViewModel (StateFlow)
  ↕ collect Flow<List<T>>
Repository Interface (domain)
  ↕ implemented by
RepositoryImpl (data)
  ├── Room DAO (primary: what UI reads from)
  └── Firestore listener (writes into Room on change)
```

### Write flow (optimistic, offline-safe):

```
UI Layer (user action)
  ↓ onAction()
ViewModel
  ↓ repository.write()
RepositoryImpl
  ├── Write to Room immediately (UI updates)
  └── If online: call Firebase directly, on success remove from queue
      If offline: enqueue PendingOperationEntity in Room
          ↓ (later, when network returns)
      WriteQueueWorker drains queue → Firebase writes
```

### Payment flow:

```
CartScreen → checkout → PaymentRepository.createPaymentIntent()
  → Firebase Cloud Function (server creates Stripe PaymentIntent)
  ← clientSecret returned to app
Stripe PaymentSheet UI (handles card input, 3DS, etc.)
  → PaymentSheet.Result callback
  → On success: OrderRepository.createOrder() → Room + Firestore
  → cart cleared, navigate to confirmation
```

### Notification flow:

```
Firebase FCM → MessagingService.onMessageReceived()
  → check NotificationPreferences
  → NotificationRepository.saveNotification() → Room
  → Android NotificationManager.notify()
User taps notification
  → MainActivity extras parsed
  → NavController.navigate() to relevant screen
NotificationHistoryScreen
  → NotificationViewModel.observeNotifications()
  → NotificationRepository.observeNotifications() → Room Flow
```

---

## Suggested Build Order

The components have hard dependencies that dictate sequence:

### Phase 1: Room Foundation (prerequisite for everything)

**Build first** — nothing else works without a usable Room database.

1. `WenuCommerceDatabase.kt` with `@Database` annotation (empty DAO list initially)
2. `TypeConverters.kt` for complex types (List serialization)
3. `ProductEntity` + `ProductDao` + `ProductMapper` — unblocks product list offline
4. `CategoryEntity` + `CategoryDao` + `CategoryMapper`
5. Koin `single { Room.databaseBuilder(...) }` + DAO singletons
6. Refactor `ProductRepositoryImpl` to write-through pattern (Firestore → Room → ViewModel reads Room)
7. Refactor `CategoryRepositoryImpl` similarly

**Validation**: App shows products and categories when launched without network. No UI changes required.

### Phase 2: Write Queue + Sync Status (prerequisite for cart/wishlist offline writes)

1. `PendingOperationEntity` + `PendingOperationDao`
2. `PendingOperation.kt` domain model + `PendingOperationType` enum
3. `ConnectivityObserver.kt` (wraps `ConnectivityManager`, exposes `Flow<Boolean>`)
4. `WriteQueueWorker.kt` (WorkManager worker, reads pending ops, calls Firebase)
5. `SyncStatus.kt` domain model + `SyncRepository` interface + `SyncRepositoryImpl`
6. `KoinWorkerFactory` setup in `WenuCommerce.kt`

**Validation**: Make an edit offline. Kill app. Restore network. Edit syncs to Firestore.

### Phase 3: Cart + Wishlist (depends on Phase 1 + 2)

1. `CartItem.kt` domain model
2. `CartItemEntity` + `CartDao` + mapper
3. `CartRepository` interface + `CartRepositoryImpl`
4. `CartViewModel` + `CartState` + `CartAction`
5. `CustomerCartScreen` (stub exists, fill in with real data)
6. `WishlistEntity` (similar pattern)

**Validation**: Customer adds item to cart, closes app, cart persists.

### Phase 4: Stripe Payment Flow (depends on Phase 3)

1. `Order.kt`, `OrderItem.kt`, `OrderStatus.kt` domain models
2. `OrderEntity` + `OrderDao` + mapper
3. `OrderRepository` interface + `OrderRepositoryImpl`
4. `PaymentRepository` interface + `PaymentRepositoryImpl` (calls Firebase Functions)
5. Add Stripe Android SDK dependency
6. `CheckoutViewModel` + `CheckoutState` + `CheckoutAction`
7. `CheckoutScreen` with `PaymentSheet` integration
8. `OrderConfirmationScreen` + `OrderDetailScreen`
9. Firebase Cloud Function `createPaymentIntent` (server-side, deployed independently)

**Validation**: Test Stripe payment with test card in staging. Order appears in order history.

### Phase 5: Order Tracking (depends on Phase 4)

1. `OrderRepository.observeOrders()` — Room Flow for customer order list
2. `OrderRepository.updateStatus()` — seller writes status to Firestore → syncs to Room
3. `SellerOrdersScreen` — attach to real `OrderRepository`
4. `CustomerOrderHistoryScreen` + `CustomerOrderDetailScreen`
5. FCM trigger: Cloud Function sends notification on order status change

**Validation**: Seller updates order to SHIPPED. Customer receives push notification and sees updated status.

### Phase 6: FCM Notification System (depends on Phase 1 + Phase 5 for order events)

1. `AppNotification.kt`, `NotificationType.kt` domain models
2. `NotificationEntity` + `NotificationDao`
3. `NotificationRepository` interface + `NotificationRepositoryImpl`
4. `NotificationPreferences` extended with per-type toggles
5. `MessagingService.kt` extended with type routing and Room persistence
6. Notification channel setup (all 4 channels registered in `WenuCommerce.onCreate()`)
7. Deep-link tap handling in `MainActivity`
8. `NotificationHistoryScreen` + `NotificationViewModel`
9. Notification settings in `CustomerProfileScreen` / `SellerProfileScreen`

**Validation**: FCM test message delivered, appears in notification history, preference toggle suppresses re-delivery.

### Phase 7: Reviews (depends on Phase 4 — needs completed purchase)

1. `ProductReview.kt` (already exists in domain), `ReviewEntity` + `ReviewDao`
2. `ProductReviewRepositoryImpl` write-through (already partially exists)
3. Purchase eligibility check (only reviewed if `purchaseHistory` contains `productId`)
4. `ReviewSubmitViewModel` + `ReviewSubmitScreen`
5. Product detail average rating update (Firestore Cloud Function aggregates)

**Validation**: Customer who bought product can submit review. Rating updates on product card.

---

## Dependency Graph Between Components

```
Room DB Foundation
  ├── Product/Category sync    (Phase 1)
  ├── Write Queue + Sync Status (Phase 2)
  │     └── Cart / Wishlist   (Phase 3)
  │           └── Stripe Checkout (Phase 4)
  │                 ├── Order Tracking (Phase 5)
  │                 │     └── FCM order events (Phase 6)
  │                 └── Reviews (Phase 7)
  └── Notification Room store  (Phase 6)
```

Nothing in Phase 4+ is buildable until Phase 1 and Phase 2 are solid. The sync layer is load-bearing infrastructure.

---

## What Does NOT Change

- Domain interfaces (`ProductRepository`, `CategoryRepository`, etc.) — method signatures stay the same. Callers are unaffected.
- ViewModel state/action pattern — `State` + `Action` + `onAction()` unchanged.
- `safeApiCall()` wrapper — continues to wrap Firebase calls inside repository implementations.
- Koin module structure — new bindings added to existing module files, not new module files.
- 3-module structure — Room entities, DAOs, and sync workers all live in `data`. Domain only sees repository interfaces and domain models. App only sees repository interfaces via DI.
- Navigation graph structure — new screens added to existing tab routes.

---

## Risks and Gaps to Watch

**Room migration**: Room will fail on schema changes unless migrations are written. `fallbackToDestructiveMigration()` is acceptable in early development but must be replaced before production with numbered migrations. Schema export must be enabled (`schemaDirectory` is already set in `data/build.gradle.kts`).

**Firestore listener lifetime**: `callbackFlow` Firestore listeners need to be active for sync to work. If the app is killed, WorkManager `ProductSyncWorker` must do a one-time full fetch on app restart to catch missed changes. Consider storing a `lastSyncedAt` timestamp per entity type in `DataStore` to limit refetch scope.

**Stripe PaymentSheet on Android**: The `PaymentSheet` callback (`FlowController.presentPaymentOptions()` or `PaymentSheet.present()`) is a result from an Activity result. In Compose, this must be wired with `rememberLauncherForActivityResult`. Stripe SDK's Compose-friendly API (`PaymentSheet` initialized in `Activity.onCreate()`) works best when the `Activity` reference is passed via the PaymentSheet initializer — not from a ViewModel directly.

**FCM and Foreground vs. Background**: FCM delivers `notification` payloads automatically when app is in background (system tray). `data` payloads always go to `onMessageReceived()`. For reliable Room persistence and channel routing, the FCM Cloud Function should send **data-only payloads** (no `notification` field), so `MessagingService.onMessageReceived()` always handles display.

**Offline write conflicts**: The `PendingOperationEntity` queue must preserve insertion order. Use an auto-incremented `id` as sort key. When draining the queue, process strictly in order (not in parallel) to avoid create-before-update race conditions.

---

*Architecture research by: Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)*
*Written: 2026-02-19*
