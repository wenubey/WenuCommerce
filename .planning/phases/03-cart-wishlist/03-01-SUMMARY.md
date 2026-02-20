---
phase: 03-cart-wishlist
plan: 01
subsystem: database
tags: [room, sqlite, kotlin, coroutines, flow, koin, workmanager, firestore, serialization]

# Dependency graph
requires:
  - phase: 01-room-foundation
    provides: Room setup, ProductEntity pattern, DAO pattern, SyncManager, database builder
  - phase: 02-offline-write-queue
    provides: PendingOperationEntity, PendingOperationDao, SyncWorker structure, OperationType enum

provides:
  - CartItemEntity: Room entity with composite PK (userId, productId) for cart_items table
  - WishlistItemEntity: Room entity with composite PK (userId, productId) for wishlist_items table
  - CartItemDao: Full CRUD DAO with Flow observation and quantity update
  - WishlistItemDao: Full CRUD DAO with isWishlisted Flow and anonymous-user support
  - CartItem/WishlistItem: Clean domain models (no userId field)
  - CartItemMapper/WishlistItemMapper: Entity-to-domain mappers
  - CartRepository: Domain interface for cart operations + Firestore sync methods
  - CartRepositoryImpl: Room-first implementation with offline queue and SyncWorker enqueue
  - WenuCommerceDatabase v3: Migration adding both new tables atomically
  - SyncWorker: Cart TODOs wired — ADD_TO_CART, UPDATE_CART_QUANTITY, REMOVE_FROM_CART dispatch to CartRepository

affects:
  - 03-02 (CartScreen UI — needs CartRepository + CartItem domain model)
  - 03-03 (WishlistScreen UI — needs WishlistItemDao + WishlistItem domain model)
  - 04-checkout (needs CartRepository.clearCart on payment success)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Composite primary key Room entity (userId, productId) for user-scoped data
    - Repository inject Context for SyncWorker.enqueue (Application context via Koin androidContext)
    - Payload DTO classes (@Serializable) in repository file for offline queue JSON serialization
    - SyncManager.emitOfflineWriteQueued() helper for external event emission

key-files:
  created:
    - domain/src/main/java/com/wenubey/domain/model/CartItem.kt
    - domain/src/main/java/com/wenubey/domain/model/WishlistItem.kt
    - domain/src/main/java/com/wenubey/domain/repository/CartRepository.kt
    - data/src/main/java/com/wenubey/data/local/entity/CartItemEntity.kt
    - data/src/main/java/com/wenubey/data/local/entity/WishlistItemEntity.kt
    - data/src/main/java/com/wenubey/data/local/dao/CartItemDao.kt
    - data/src/main/java/com/wenubey/data/local/dao/WishlistItemDao.kt
    - data/src/main/java/com/wenubey/data/local/mapper/CartItemMapper.kt
    - data/src/main/java/com/wenubey/data/local/mapper/WishlistItemMapper.kt
    - data/src/main/java/com/wenubey/data/repository/CartRepositoryImpl.kt
    - data/schemas/com.wenubey.data.local.WenuCommerceDatabase/3.json
  modified:
    - domain/src/main/java/com/wenubey/domain/model/Purchase.kt
    - data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt
    - data/src/main/java/com/wenubey/data/worker/SyncWorker.kt
    - data/src/main/java/com/wenubey/data/local/SyncManager.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt

key-decisions:
  - "CartRepositoryImpl takes Application Context as constructor param for SyncWorker.enqueue() — injected via Koin androidContext()"
  - "AddToCartPayload and UpdateCartQuantityPayload are @Serializable data classes declared in CartRepositoryImpl.kt file (not a separate package)"
  - "SyncManager.emitOfflineWriteQueued() added as public method since syncEvents SharedFlow is read-only externally"
  - "REMOVE_FROM_CART stores productId directly as payloadJson (not a wrapper class) since it is a single value"
  - "clearCart() does not queue a PendingOperation — it is used post-checkout when Firestore already reflects the cleared state"
  - "Purchase.quantity changed from Double to Int to fix STATE.md blocker — existing serialized purchaseHistoryJson uses runCatching so backward-compatible"

patterns-established:
  - "User-scoped entities use composite PK (userId, productId) — prevents duplicates, simplifies upsert semantics"
  - "Add-to-cart increment: getCartItem() first, then updateQuantity() if exists, upsert() if new — never bare @Upsert for quantity-sensitive entities"
  - "Offline queue payload DTOs co-located in repository impl file as @Serializable data classes"
  - "SyncWorker cart dispatch: ADD_TO_CART deserializes AddToCartPayload; REMOVE_FROM_CART uses raw payloadJson as productId"

requirements-completed: [CART-01, CART-02, CART-03, CART-04]

# Metrics
duration: 5min
completed: 2026-02-20
---

# Phase 03 Plan 01: Room Data Layer for Cart and Wishlist Summary

**Room data layer for cart and wishlist: entities with composite PKs, DAOs with Flow observation, CartRepository with offline queue, SyncWorker cart TODO wiring, and DB migration v2->v3 creating both tables atomically**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-02-20T17:28:37Z
- **Completed:** 2026-02-20T17:33:47Z
- **Tasks:** 2
- **Files modified:** 15

## Accomplishments

- Created Room data layer for cart and wishlist (entities, DAOs, mappers, domain models)
- Fixed `Purchase.quantity: Double` to `Int` (STATE.md blocker resolved)
- CartRepository with Room-first reads, offline queue writes, and Firestore sync methods
- SyncWorker cart TODOs fully wired: ADD_TO_CART, UPDATE_CART_QUANTITY, REMOVE_FROM_CART dispatch to CartRepository
- WenuCommerceDatabase migrated v2->v3 with both new tables in single migration

## Task Commits

Each task was committed atomically:

1. **Task 1: Entities, DAOs, domain models, mappers, DB migration v2->v3** - `50f2f0c` (feat)
2. **Task 2: CartRepository interface, CartRepositoryImpl, SyncWorker wiring, Koin registration** - `4affc4b` (feat)

## Files Created/Modified

**Created:**
- `domain/src/main/java/com/wenubey/domain/model/CartItem.kt` - Cart domain model (no userId)
- `domain/src/main/java/com/wenubey/domain/model/WishlistItem.kt` - Wishlist domain model (no userId)
- `domain/src/main/java/com/wenubey/domain/repository/CartRepository.kt` - Cart domain interface
- `data/src/main/java/com/wenubey/data/local/entity/CartItemEntity.kt` - cart_items Room entity, composite PK (userId, productId)
- `data/src/main/java/com/wenubey/data/local/entity/WishlistItemEntity.kt` - wishlist_items Room entity, composite PK (userId, productId)
- `data/src/main/java/com/wenubey/data/local/dao/CartItemDao.kt` - Cart CRUD, observeCartItems, observeUniqueProductCount
- `data/src/main/java/com/wenubey/data/local/dao/WishlistItemDao.kt` - Wishlist CRUD, isWishlisted Flow, anonymous-user getItemsForUser
- `data/src/main/java/com/wenubey/data/local/mapper/CartItemMapper.kt` - CartItemEntity <-> CartItem mappers
- `data/src/main/java/com/wenubey/data/local/mapper/WishlistItemMapper.kt` - WishlistItemEntity <-> WishlistItem mappers
- `data/src/main/java/com/wenubey/data/repository/CartRepositoryImpl.kt` - Room-first impl with offline queue and AddToCartPayload/UpdateCartQuantityPayload DTOs
- `data/schemas/com.wenubey.data.local.WenuCommerceDatabase/3.json` - Room schema export v3

**Modified:**
- `domain/src/main/java/com/wenubey/domain/model/Purchase.kt` - `quantity: Double` -> `quantity: Int`
- `data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt` - Version 3, added new entities and DAOs, MIGRATION_2_3
- `data/src/main/java/com/wenubey/data/worker/SyncWorker.kt` - Added CartRepository param, wired 3 cart operation types
- `data/src/main/java/com/wenubey/data/local/SyncManager.kt` - Added emitOfflineWriteQueued() public method
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` - Added MIGRATION_2_3, cartItemDao, wishlistItemDao, CartRepositoryImpl bindings

## Decisions Made

- **CartRepositoryImpl Context injection:** Takes Android `Context` as constructor parameter for `SyncWorker.enqueue(context)`. Koin's `androidContext()` provides this automatically via `get()`.
- **Payload co-location:** `AddToCartPayload` and `UpdateCartQuantityPayload` declared in `CartRepositoryImpl.kt` rather than a separate package — small serialization-only DTOs don't warrant their own file/package.
- **SyncManager event helper:** Added `emitOfflineWriteQueued()` because `syncEvents` is exposed as read-only `SharedFlow`; `_syncEvents.tryEmit` is private. The helper keeps encapsulation intact.
- **REMOVE_FROM_CART payload:** Stores `productId` directly as `payloadJson` string (no wrapper class needed for a single value); SyncWorker reads `operation.payloadJson` directly as the productId.
- **clearCart() no queue:** Checkout flow (Phase 4) calls clearCart() after payment success when Firestore already reflects the cleared state; queueing is unnecessary.
- **Purchase.quantity type fix:** Changing Double to Int; existing `purchaseHistoryJson` deserialization uses `runCatching` in UserMapper, ensuring backward compatibility with any previously serialized data.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed ProductImage.url field name to downloadUrl**
- **Found during:** Task 2 (CartRepositoryImpl creation)
- **Issue:** Plan referenced `product.images.firstOrNull()?.url` but ProductImage field is named `downloadUrl`
- **Fix:** Changed to `product.images.firstOrNull()?.downloadUrl ?: ""`
- **Files modified:** `data/src/main/java/com/wenubey/data/repository/CartRepositoryImpl.kt`
- **Verification:** Build passes with correct field name
- **Committed in:** `4affc4b` (Task 2 commit)

**2. [Rule 2 - Missing Critical] Added SyncManager.emitOfflineWriteQueued() method**
- **Found during:** Task 2 (CartRepositoryImpl event emission)
- **Issue:** Plan said to call `syncManager.syncEvents.tryEmit()` but `syncEvents` is a read-only `SharedFlow`; `tryEmit` is only available on `MutableSharedFlow`
- **Fix:** Added `fun emitOfflineWriteQueued()` to SyncManager that calls `_syncEvents.tryEmit(SyncEvent.OfflineWriteQueued)` internally
- **Files modified:** `data/src/main/java/com/wenubey/data/local/SyncManager.kt`
- **Verification:** Build passes; encapsulation maintained
- **Committed in:** `4affc4b` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing critical)
**Impact on plan:** Both necessary for correctness. No scope creep.

## Issues Encountered

None beyond the two auto-fixed deviations above.

## User Setup Required

None — no external service configuration required for this data layer plan.

## Next Phase Readiness

- CartRepository and WishlistItemDao injectable via Koin for ViewModels
- Room-first data layer ready for Cart and Wishlist UI screens (Plan 03-02 and 03-03)
- WenuCommerceDatabase v3 migration in place — existing users will migrate seamlessly
- SyncWorker fully connected: cart operations will sync to Firestore when online
- Pre-Phase 4 concern remains: Firestore security rules for `users/{uid}/cart` must be written before cart sync can function in production

---
*Phase: 03-cart-wishlist*
*Completed: 2026-02-20*

## Self-Check: PASSED

All 12 created files exist on disk. Both task commits (50f2f0c, 4affc4b) verified in git log.
