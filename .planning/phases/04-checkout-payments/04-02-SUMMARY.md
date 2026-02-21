---
phase: 04-checkout-payments
plan: 02
subsystem: data-foundation
tags: [room, stripe, payment, address, order, koin, firestore, kotlin]
dependency_graph:
  requires: []
  provides:
    - Order domain model with OrderItem, OrderStatus, ShippingAddress
    - Room v4 with orders and addresses tables (MIGRATION_3_4)
    - PaymentRepository interface and impl (Firebase Functions + Room + Firestore)
    - AddressRepository interface and impl (Room-first + Firestore sync)
    - Stripe SDK initialized in Application.onCreate()
    - Koin bindings for all new DAOs and repositories
  affects:
    - 04-03: CheckoutScreen uses PaymentRepository.createPaymentIntent + ShippingAddress
    - 04-04: OrderConfirmation uses PaymentRepository.observeOrderById + updateOrderStatus
    - 04-05: AddressManagement uses AddressRepository
tech_stack:
  added:
    - stripe-android 22.8.1 (Stripe PaymentSheet SDK)
    - lottie-compose 6.7.1 (animations for checkout flow)
  patterns:
    - Room-first with Firestore snapshot listener write-through (AddressRepository)
    - JSON columns for embedded lists (OrderEntity.itemsJson, shippingAddressJson)
    - runCatching + safe defaults in mappers (consistent with existing UserMapper pattern)
    - Enum stored as String name; valueOf() with runCatching fallback in mappers
key_files:
  created:
    - domain/src/main/java/com/wenubey/domain/model/order/Order.kt
    - domain/src/main/java/com/wenubey/domain/model/order/OrderItem.kt
    - domain/src/main/java/com/wenubey/domain/model/order/OrderStatus.kt
    - domain/src/main/java/com/wenubey/domain/model/order/ShippingAddress.kt
    - domain/src/main/java/com/wenubey/domain/repository/PaymentRepository.kt
    - domain/src/main/java/com/wenubey/domain/repository/AddressRepository.kt
    - data/src/main/java/com/wenubey/data/local/entity/OrderEntity.kt
    - data/src/main/java/com/wenubey/data/local/entity/AddressEntity.kt
    - data/src/main/java/com/wenubey/data/local/dao/OrderDao.kt
    - data/src/main/java/com/wenubey/data/local/dao/AddressDao.kt
    - data/src/main/java/com/wenubey/data/local/mapper/OrderMapper.kt
    - data/src/main/java/com/wenubey/data/local/mapper/AddressMapper.kt
    - data/src/main/java/com/wenubey/data/repository/PaymentRepositoryImpl.kt
    - data/src/main/java/com/wenubey/data/repository/AddressRepositoryImpl.kt
    - data/schemas/com.wenubey.data.local.WenuCommerceDatabase/4.json
  modified:
    - data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt
    - app/src/main/java/com/wenubey/wenucommerce/WenuCommerce.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt
    - app/build.gradle.kts
    - gradle/libs.versions.toml
decisions:
  - "OrderEntity stores items as JSON (itemsJson) and shippingAddress as JSON (shippingAddressJson) using embedded list pattern per research recommendation — no separate order_items table"
  - "AddressRepositoryImpl uses activeListeners map to prevent duplicate Firestore listeners per userId"
  - "PaymentRepositoryImpl uses Firebase.functions (via ktx import) for createPaymentIntent callable; result.getData() used instead of result.data (private property)"
  - "updateOrderStatus updates Room first (optimistic local update), then Firestore, consistent with Room-first policy"
  - "lottie-compose 6.7.1 added in this plan for use in checkout animations in subsequent UI plans"
metrics:
  duration: 7 min
  completed: 2026-02-21
  tasks_completed: 3
  files_created: 15
  files_modified: 5
---

# Phase 4 Plan 2: Checkout/Payments Data Foundation Summary

**One-liner:** Complete data layer for checkout: Order/ShippingAddress domain models, Room v4 migration (orders + addresses tables), PaymentRepository calling Firebase Functions createPaymentIntent, AddressRepository with Room-first + Firestore sync, and Stripe SDK initialized via BuildConfig.

## What Was Built

### Task 1: Order and Address domain models, Room entities, DAOs, mappers, migration v3->v4

**Domain models** in `domain/model/order/`:
- `OrderStatus` enum: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED (each with displayName)
- `ShippingAddress` @Serializable data class with `label` computed property and `toMap()` for Firestore
- `OrderItem` @Serializable data class with snapshotPrice and lineTotal
- `Order` @Serializable data class with all checkout fields; `companion object { fun default() }`

**Room entities** in `data/local/entity/`:
- `OrderEntity` — `@Entity(tableName = "orders")` with single PrimaryKey `id`; stores ShippingAddress and List<OrderItem> as JSON strings
- `AddressEntity` — `@Entity(tableName = "addresses", primaryKeys = ["userId", "addressId"])` — composite key matching CartItemEntity/WishlistItemEntity pattern

**DAOs**:
- `OrderDao` — upsert, getOrderById, observeOrderById, observeOrdersByUser, updateOrderStatus
- `AddressDao` — upsert, upsertAll, observeByUser, delete, deleteAllForUser

**Mappers**:
- `OrderMapper` — `OrderEntity.toDomain()` deserializes JSON with runCatching + safe defaults; `Order.toEntity()` serializes
- `AddressMapper` — straightforward field mapping; `ShippingAddress.toEntity(userId)` uses `id` as `addressId`

**WenuCommerceDatabase**: Bumped to version 4, added OrderEntity and AddressEntity to entities list, MIGRATION_3_4 creates both tables, added orderDao() and addressDao() abstract methods.

### Task 2: PaymentRepository, Stripe SDK setup, Koin wiring

**PaymentRepository** domain interface:
- `createPaymentIntent(userId, cartItems, shippingAddress): Result<PaymentIntentResult>`
- `createOrderInRoom(order: Order)`
- `getOrderById(orderId): Order?`
- `observeOrderById(orderId): Flow<Order?>`
- `updateOrderStatus(orderId, status): Result<Unit>`
- `PaymentIntentResult` data class (clientSecret, amountCents, orderId)

**PaymentRepositoryImpl**:
- `createPaymentIntent` calls `Firebase.functions.getHttpsCallable("createPaymentIntent").call(data).await()`, uses `result.getData()` (not private `result.data`)
- `updateOrderStatus` updates Room first, then Firestore with `FieldValue.serverTimestamp()`

**Stripe SDK**:
- `stripe-android = "22.8.1"` and `lottie-compose = "6.7.1"` added to `libs.versions.toml`
- Dependencies added to `app/build.gradle.kts`
- `STRIPE_PUBLISHABLE_KEY` BuildConfig field reads from `local.properties`
- `PaymentConfiguration.init(applicationContext, BuildConfig.STRIPE_PUBLISHABLE_KEY)` in `WenuCommerce.onCreate()` after `FirebaseApp.initializeApp()`

### Task 3: AddressRepository with Room-first pattern and Koin wiring

**AddressRepository** domain interface:
- `observeSavedAddresses(userId): Flow<List<ShippingAddress>>`
- `saveAddress(userId, address)`
- `deleteAddress(userId, addressId)`

**AddressRepositoryImpl** (Room-first pattern):
- `observeSavedAddresses` starts Firestore snapshot listener (once per userId via `activeListeners` map), then returns `addressDao.observeByUser(userId).map { ... }`
- Firestore listener on `users/{userId}/addresses` calls `deleteAllForUser` + `upsertAll` on each snapshot
- `saveAddress` writes to Firestore first, then Room (instant local availability)
- `deleteAddress` deletes from both Firestore and Room

**DataModule.kt** updates:
- Added `MIGRATION_3_4` to `addMigrations(...)` call
- Added `single { get<WenuCommerceDatabase>().orderDao() }` and `single { get<WenuCommerceDatabase>().addressDao() }`
- Added `singleOf(::PaymentRepositoryImpl).bind<PaymentRepository>()` and `singleOf(::AddressRepositoryImpl).bind<AddressRepository>()`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `result.data` private property access in PaymentRepositoryImpl**
- **Found during:** Task 2 compile
- **Issue:** `HttpsCallableResult.data` is private in the Firebase Functions Kotlin API; direct property access fails to compile
- **Fix:** Changed `result.data` to `result.getData()` (the public Java getter)
- **Files modified:** `data/src/main/java/com/wenubey/data/repository/PaymentRepositoryImpl.kt`
- **Commit:** db994fa (included in same commit after fix)

**2. [Rule 1 - Bug] Fixed `Result<Void!>` vs `Result<Unit>` type mismatch in updateOrderStatus**
- **Found during:** Task 2 compile
- **Issue:** `firestore.update(...).await()` returns `Void!` but the method expects `Result<Unit>`; missing explicit `Unit` return causes type mismatch
- **Fix:** Added explicit `Unit` at the end of the `runCatching` block after the Firestore await
- **Files modified:** `data/src/main/java/com/wenubey/data/repository/PaymentRepositoryImpl.kt`
- **Commit:** db994fa (included in same fix)

## Self-Check: PASSED

All 15 created files found on disk. All 3 task commits (61876a2, db994fa, 2f31dc4) verified in git log. Project compiles successfully with `./gradlew :app:compileDebugKotlin`.
