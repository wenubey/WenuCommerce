# Phase 3: Cart & Wishlist - Research

**Researched:** 2026-02-20
**Domain:** Room offline-first cart/wishlist with Firestore sync, Compose UI (badges, swipe-to-dismiss, heart animation)
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Cart screen layout:**
- Compact list rows: small thumbnail on left, name + price on right, quantity inline
- Stepper control (- / count / +) for quantity changes
- Both swipe-to-delete and visible trash icon for item removal
- Bulk select: checkboxes on items with "Delete Selected" action
- Sticky bottom bar: subtotal only (no item count) + Checkout button
- Instant price updates on quantity change (no animation)
- Decrementing to 0 removes the item (brief undo snackbar)
- Tapping a cart item navigates to product detail screen
- Product info only per row (no seller name)
- Cart badge on bottom nav tab shows unique product count (not total items)
- Badge disappears when cart is empty (no "0" badge)
- Cart accessible via dedicated bottom navigation tab

**Add-to-cart flow:**
- Quantity selector (stepper) on product detail screen before adding to cart
- After adding: snackbar with "View Cart" action + badge animation on nav tab
- If product already in cart: button transforms to "In Cart (x2)" with stepper controls on detail page
- Adding same product again increments quantity by selected amount
- Maximum quantity per item limited by available stock
- Cart requires login — tapping Add to Cart when not logged in prompts login

**Stock warnings & edge cases:**
- Out-of-stock items: row grayed out with red/orange "Out of Stock" banner overlay, quantity controls disabled
- Deleted products by seller: same treatment as out-of-stock, labeled "No longer available"
- Over-stock (cart qty > available): auto-reduce quantity to available stock with snackbar notice
- Out-of-stock items excluded from subtotal calculation
- Checkout button disabled until all out-of-stock/unavailable items are removed from cart
- Low stock indicator: "Only X left" shown when stock is 3 or fewer
- Product detail: Add to Cart button disabled with "Out of Stock" label when stock is 0

**Wishlist heart interaction:**
- Heart icon: outlined when not wishlisted, filled red when wishlisted
- Scale bounce animation on toggle
- On product cards (browse/search): heart in action row below the card (not overlaid on image)
- On product detail: heart icon in top app bar
- No snackbar when toggling wishlist from browse/detail — heart animation is the only feedback
- Unwishlisting from wishlist screen: instant removal with undo snackbar
- Wishlist works without login; stored locally, synced to Firestore on login
- Wishlist items stay permanently until user manually removes (not affected by purchase)

**Wishlist screen:**
- Product display: grid layout matching browse/search screen style
- "Add All to Cart" button for bulk action
- Long-press multi-select: select specific items, then "Add Selected to Cart"
- Unavailable/deleted products show inline warning (same treatment as cart)
- Wishlist is a dedicated bottom navigation tab (no badge count)

**Empty states:**
- Illustrated character/scene style (vector SVG/drawable format)
- Empty cart: different scene from empty wishlist, both illustrated
- Playful messaging tone (e.g., "Your cart feels lonely!")
- CTA button text: "Start Shopping" for both cart and wishlist, navigates to browse/home
- No secondary hint text — just illustration, message, and CTA button
- Fade/crossfade transition when list becomes empty (last item removed)

### Claude's Discretion
- Exact illustration designs for empty states
- Spacing, typography, and color palette details
- Cart row exact dimensions and padding
- Stepper control exact styling
- Undo snackbar duration and placement
- Low stock indicator visual treatment (color, icon)
- Bulk select checkbox styling and selection bar appearance
- Long-press multi-select visual feedback pattern

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CART-01 | Customer can add product to cart from product detail with quantity selector | CartItemEntity + CartDao + CartRepository; stepper on detail screen; auth gate |
| CART-02 | Cart persists across app restarts via Room | CartItemEntity stored in Room; WenuCommerceDatabase v3 migration |
| CART-03 | Cart syncs to Firestore when online | SyncWorker wires ADD_TO_CART / UPDATE_CART_QUANTITY / REMOVE_FROM_CART TODOs; CartRepositoryImpl writes to users/{uid}/cart subcollection |
| CART-04 | Customer can update quantity (increment/decrement) or remove items | CartDao update/delete; stepper + trash icon + swipe-to-dismiss; decrement-to-zero removes |
| CART-05 | Cart shows subtotal, item count, and per-line totals | Derived computation in CartViewModel from CartItemEntity list; stock-aware (excludes OOS) |
| CART-06 | Out-of-stock or deleted products show warning inline | CartItemEntity.availableStock + isProductDeleted fields; UI overlay treatment |
| CART-07 | Cart badge/count visible on navigation tab | BadgedBox on NavigationBarItem; count = distinct productIds in cart; disappears at 0 |
| CART-08 | Empty cart shows illustration with CTA to browse products | AnimatedContent crossfade; vector drawable illustration; navigate to home |
| WISH-01 | Customer can add/remove products from wishlist via heart icon on product cards and detail | WishlistEntity + WishlistDao; heart toggle in CustomerProductCard action row + detail top bar |
| WISH-02 | Wishlist persists offline via Room, syncs to Firestore | WishlistEntity in Room; login-triggered sync to users/{uid}/wishlist; anonymous local-only |
| WISH-03 | Dedicated wishlist screen showing saved products | New CustomerWishlistScreen; LazyVerticalGrid layout; WishlistViewModel |
| WISH-04 | Customer can add to cart directly from wishlist | "Add to Cart" per item + "Add All to Cart" + long-press multi-select on wishlist screen |
| WISH-05 | Deleted or unavailable products show inline warning in wishlist | Same isProductDeleted / OOS check as cart; joins with product Room data |
</phase_requirements>

---

## Summary

Phase 3 builds on the Room-first offline infrastructure established in Phases 1 and 2. The core technical work is creating two new Room entities (CartItemEntity and WishlistEntity), their DAOs, repositories, and wiring the SyncWorker TODOs that were explicitly left for this phase. The Firestore schema follows the user-scoped subcollection pattern: `users/{uid}/cart/{productId}` and `users/{uid}/wishlist/{productId}`.

On the UI side, the most complex pieces are: (1) refactoring CustomerTabScreen from a Pager-based 3-tab layout to a NavigationBar-based 5-tab layout adding Cart and Wishlist tabs, (2) implementing the BadgedBox cart badge that reacts to Room Flow changes, (3) the cart screen with SwipeToDismissBox + stepper + bulk select + sticky bottom bar, and (4) the wishlist heart animation using `animateFloatAsState` for scale bounce.

The `Purchase.quantity: Double` field documented as a blocker in STATE.md must be fixed to `Int` before CartItemEntity is designed. The database migration from version 2 to version 3 must add both `cart_items` and `wishlist_items` tables in a single migration object. Wishlists are the only anonymous-capable feature: Room stores items without a userId for unauthenticated users, and on login a one-shot sync pushes local items to Firestore.

**Primary recommendation:** Create CartItemEntity and WishlistEntity in the data module, add Migration_2_3, wire the three SyncWorker cart TODOs to CartRepository, add Cart and Wishlist tabs to NavigationBar, implement CartScreen and WishlistScreen following established State/Action/ViewModel pattern.

---

## Standard Stack

### Core (already in project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Room | 2.6.1 | CartItemEntity, WishlistEntity, DAOs, migrations | Established in Phase 1 as single source of truth |
| Firestore | firebase-bom 33.8.0 | users/{uid}/cart and wishlist subcollections | Existing auth + product pattern |
| Koin | 4.0.1 | CartRepository, CartViewModel, WishlistRepository DI | Project DI framework |
| Kotlin Coroutines + Flow | via lifecycle 2.8.7 | Room DAO Flows → ViewModel StateFlow | Established pattern |
| Jetpack Compose Material3 | BOM 2025.01.00 | BadgedBox, SwipeToDismissBox, NavigationBar, LazyVerticalGrid | Existing UI stack |
| WorkManager | 2.11.1 | SyncWorker wires cart operation TODOs | Already set up in Phase 2 |
| kotlinx.serialization | via kotlin 2.1.0 | Payload JSON for PendingOperationEntity | Established serialization |

### No New Libraries Needed
All dependencies required for Phase 3 are already present in `libs.versions.toml`. No new library additions are needed:
- `BadgedBox` — in material3 (via BOM)
- `SwipeToDismissBox` — in material3 (via BOM)
- `LazyVerticalGrid` — in compose foundation (via BOM)
- `animateFloatAsState` — in compose animation (via BOM)
- `AnimatedContent` / `Crossfade` — in compose animation (via BOM)
- Vector drawable for illustrations — uses Android drawable resources (no extra dep)

**Installation:** No new dependencies required.

---

## Architecture Patterns

### Recommended Project Structure

```
data/src/main/java/com/wenubey/data/
├── local/
│   ├── entity/
│   │   ├── CartItemEntity.kt         # NEW: cart_items table
│   │   └── WishlistItemEntity.kt     # NEW: wishlist_items table
│   ├── dao/
│   │   ├── CartItemDao.kt            # NEW
│   │   └── WishlistItemDao.kt        # NEW
│   ├── mapper/
│   │   ├── CartItemMapper.kt         # NEW: entity ↔ domain
│   │   └── WishlistItemMapper.kt     # NEW: entity ↔ domain
│   └── WenuCommerceDatabase.kt       # MODIFY: add entities, v3 migration
├── repository/
│   ├── CartRepositoryImpl.kt         # NEW
│   └── WishlistRepositoryImpl.kt     # NEW

domain/src/main/java/com/wenubey/domain/
├── model/
│   ├── CartItem.kt                   # NEW domain model
│   └── WishlistItem.kt               # NEW domain model
├── repository/
│   ├── CartRepository.kt             # NEW interface
│   └── WishlistRepository.kt         # NEW interface

app/src/main/java/com/wenubey/wenucommerce/
├── customer/
│   ├── CustomerTabScreen.kt          # MODIFY: Pager→NavigationBar, add Wishlist tab, add badge
│   ├── CustomerTabs.kt               # MODIFY: add Wishlist entry
│   ├── CustomerCartScreen.kt         # REWRITE: full implementation
│   ├── CustomerWishlistScreen.kt     # NEW
│   ├── customer_cart/
│   │   ├── CartState.kt              # NEW
│   │   ├── CartAction.kt             # NEW
│   │   └── CartViewModel.kt          # NEW
│   ├── customer_wishlist/
│   │   ├── WishlistState.kt          # NEW
│   │   ├── WishlistAction.kt         # NEW
│   │   └── WishlistViewModel.kt      # NEW
│   └── customer_products/
│       ├── CustomerProductDetailScreen.kt  # MODIFY: add stepper + Add to Cart logic + heart
│       └── CustomerProductDetailViewModel.kt # MODIFY: add cart/wishlist actions
├── di/
│   ├── DataModule.kt                 # MODIFY: add CartRepository, WishlistRepository, new DAOs
│   └── ViewmodelModule.kt            # MODIFY: add CartViewModel, WishlistViewModel
└── navigation/
    └── AppNavigationObjects.kt       # Wishlist nav object already exists? Check — add if missing
```

### Pattern 1: Room-First Cart Entity

**What:** CartItemEntity stores one row per userId+productId combination. DAO returns Flow; repository maps to domain. This matches the exact pattern used for ProductEntity in Phase 1.

**Key design decisions:**
- Primary key: composite (userId, productId) — prevents duplicates, simplifies upsert
- `quantity: Int` not Double (fix the STATE.md blocker)
- `snapshotPrice: Double` — capture price at add-to-cart time; Phase 4 checkout uses this
- `availableStock: Int` — cached from product; refreshed on cart open for OOS check
- `isProductDeleted: Boolean` — set when product is no longer found in Room/Firestore
- `addedAt: String` — ISO timestamp for ordering

```kotlin
// Source: follows established ProductEntity pattern in data/local/entity/ProductEntity.kt
@Entity(
    tableName = "cart_items",
    primaryKeys = ["userId", "productId"]
)
data class CartItemEntity(
    val userId: String,
    val productId: String,
    val productTitle: String = "",
    val productImageUrl: String = "",
    val quantity: Int = 1,
    val snapshotPrice: Double = 0.0,
    val availableStock: Int = 0,
    val isProductDeleted: Boolean = false,
    val addedAt: String = "",
    val updatedAt: String = ""
)
```

### Pattern 2: Room-First Wishlist Entity

**What:** WishlistItemEntity stores wishlist membership. For unauthenticated users, userId is empty string; on login, items are synced to Firestore and userId is updated.

```kotlin
// Follows CartItemEntity pattern
@Entity(
    tableName = "wishlist_items",
    primaryKeys = ["userId", "productId"]
)
data class WishlistItemEntity(
    val userId: String,         // "" for anonymous users
    val productId: String,
    val productTitle: String = "",
    val productImageUrl: String = "",
    val productPrice: Double = 0.0,
    val availableStock: Int = 0,
    val isProductDeleted: Boolean = false,
    val addedAt: String = ""
)
```

### Pattern 3: Room Migration 2→3

**What:** Single Migration object creates both new tables. Database version increments to 3.

```kotlin
// Source: official Android docs at developer.android.com/training/data-storage/room/migrating-db-versions
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cart_items` (
                `userId` TEXT NOT NULL,
                `productId` TEXT NOT NULL,
                `productTitle` TEXT NOT NULL DEFAULT '',
                `productImageUrl` TEXT NOT NULL DEFAULT '',
                `quantity` INTEGER NOT NULL DEFAULT 1,
                `snapshotPrice` REAL NOT NULL DEFAULT 0.0,
                `availableStock` INTEGER NOT NULL DEFAULT 0,
                `isProductDeleted` INTEGER NOT NULL DEFAULT 0,
                `addedAt` TEXT NOT NULL DEFAULT '',
                `updatedAt` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`userId`, `productId`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `wishlist_items` (
                `userId` TEXT NOT NULL,
                `productId` TEXT NOT NULL,
                `productTitle` TEXT NOT NULL DEFAULT '',
                `productImageUrl` TEXT NOT NULL DEFAULT '',
                `productPrice` REAL NOT NULL DEFAULT 0.0,
                `availableStock` INTEGER NOT NULL DEFAULT 0,
                `isProductDeleted` INTEGER NOT NULL DEFAULT 0,
                `addedAt` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`userId`, `productId`)
            )
        """.trimIndent())
    }
}
```

### Pattern 4: Cart DAO with composite key

**What:** DAO uses userId scoping on all queries. Upsert handles add-same-product increment via repository logic (get existing → update quantity) since Room's `@Upsert` would overwrite.

```kotlin
// Follows ProductDao pattern in data/local/dao/ProductDao.kt
@Dao
interface CartItemDao {
    @Query("SELECT * FROM cart_items WHERE userId = :userId ORDER BY addedAt ASC")
    fun observeCartItems(userId: String): Flow<List<CartItemEntity>>

    @Query("SELECT COUNT(DISTINCT productId) FROM cart_items WHERE userId = :userId")
    fun observeUniqueProductCount(userId: String): Flow<Int>

    @Query("SELECT * FROM cart_items WHERE userId = :userId AND productId = :productId LIMIT 1")
    suspend fun getCartItem(userId: String, productId: String): CartItemEntity?

    @Upsert
    suspend fun upsert(item: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE userId = :userId AND productId = :productId")
    suspend fun deleteItem(userId: String, productId: String)

    @Query("DELETE FROM cart_items WHERE userId = :userId")
    suspend fun clearCart(userId: String)

    @Query("UPDATE cart_items SET quantity = :quantity, updatedAt = :updatedAt WHERE userId = :userId AND productId = :productId")
    suspend fun updateQuantity(userId: String, productId: String, quantity: Int, updatedAt: String)

    @Query("UPDATE cart_items SET isProductDeleted = :deleted, availableStock = :stock WHERE userId = :userId AND productId = :productId")
    suspend fun updateProductStatus(userId: String, productId: String, deleted: Boolean, stock: Int)
}
```

### Pattern 5: BadgedBox for Cart Tab

**What:** NavigationBarItem wraps the cart icon in BadgedBox. The badge count is derived from a Room Flow. Current project uses Pager + TabRow; this must be replaced with NavigationBar for the expanded 5-tab layout.

**Critical change:** The current `CustomerTabScreen.kt` uses `HorizontalPager + TabRow`. With 5 tabs (Home, Cart, Wishlist, Profile, plus a possible Search/Browse), HorizontalPager scroll is confusing for primary navigation. Standard approach is `NavigationBar + NavHost` or `NavigationBar + Pager with disabled swipe`. Keep Pager for consistency but disable user swipe; badge drives state externally.

```kotlin
// Source: developer.android.com/develop/ui/compose/components/badges
NavigationBarItem(
    selected = currentTabIndex == index,
    onClick = { /* scroll pager */ },
    icon = {
        if (tab == CustomerTabs.Cart && cartCount > 0) {
            BadgedBox(
                badge = {
                    Badge { Text("$cartCount") }
                }
            ) {
                Icon(...)
            }
        } else {
            Icon(...)
        }
    },
    label = { Text(...) }
)
```

### Pattern 6: SwipeToDismissBox for Cart Rows

**What:** Each cart row is wrapped in SwipeToDismissBox. End-to-start swipe triggers delete. The trash icon in the background content provides visual affordance. The visible trash button is a separate IconButton in the row itself.

```kotlin
// Source: developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss
val swipeState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
            onRemove(item)
            true
        } else false
    }
)
SwipeToDismissBox(
    state = swipeState,
    backgroundContent = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete",
                 modifier = Modifier.padding(end = 16.dp))
        }
    }
) {
    CartItemRow(item = item, onRemove = onRemove, onQuantityChange = onQuantityChange)
}
```

### Pattern 7: Heart Toggle Animation

**What:** Heart icon uses `animateFloatAsState` for scale bounce on toggle. State is isWishlisted Boolean from WishlistViewModel/repository.

```kotlin
// Source: developer.android.com/develop/ui/compose/animation/quick-guide
val scale by animateFloatAsState(
    targetValue = if (isWishlisted) 1.3f else 1.0f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    finishedListener = { /* reset to 1.0f after bounce */ },
    label = "heartScale"
)
// Approach: toggle causes brief scale-up then snap back
// Better: use LaunchedEffect + Animatable for a proper bounce sequence
```

**Recommended approach for bounce:** Use `Animatable` + `LaunchedEffect` to sequence scale: 1.0f → 1.4f → 1.0f on each toggle.

```kotlin
val scale = remember { Animatable(1f) }
LaunchedEffect(isWishlisted) {
    scale.animateTo(1.3f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
}
Icon(
    imageVector = if (isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
    tint = if (isWishlisted) Color.Red else MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
)
```

### Pattern 8: Empty State Crossfade

**What:** When last item is removed from cart, crossfade to the empty state illustration. `AnimatedContent` handles this without manual alpha tracking.

```kotlin
// Source: developer.android.com/develop/ui/compose/animation/quick-guide
AnimatedContent(
    targetState = cartItems.isEmpty(),
    label = "cartEmptyState"
) { isEmpty ->
    if (isEmpty) CartEmptyState(onBrowse = onNavigateToHome)
    else CartItemList(items = cartItems, ...)
}
```

### Pattern 9: SyncWorker Cart Operation Wiring

**What:** Phase 2 left three TODOs in SyncWorker that Phase 3 must implement. Each TODO resolves to a CartRepository suspend fun that writes to Firestore.

```kotlin
// In SyncWorker.doWork() — replace TODOs with actual calls
OperationType.ADD_TO_CART -> {
    val payload = json.decodeFromString<AddToCartPayload>(operation.payloadJson)
    cartRepository.syncAddToCart(operation.entityId, payload)
}
OperationType.UPDATE_CART_QUANTITY -> {
    val payload = json.decodeFromString<UpdateCartQuantityPayload>(operation.payloadJson)
    cartRepository.syncUpdateQuantity(operation.entityId, payload)
}
OperationType.REMOVE_FROM_CART -> {
    cartRepository.syncRemoveFromCart(userId = operation.entityId, productId = operation.payloadJson)
}
```

### Pattern 10: Firestore Collection Structure

**What:** Cart and wishlist are user subcollections, consistent with Firestore best practice for user-scoped data. This matches the project's existing user-centric data model.

```
users/{uid}/cart/{productId}    — document per cart item
users/{uid}/wishlist/{productId} — document per wishlist item
```

**Security rules skeleton (to write when implementing):**
```javascript
match /users/{userId}/cart/{itemId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
match /users/{userId}/wishlist/{itemId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

### Anti-Patterns to Avoid

- **Don't store cart items inside the user document as an array.** Arrays in Firestore have no per-element update semantics. Subcollection documents allow atomic per-item writes. (MEDIUM confidence — standard Firestore guidance)
- **Don't put KSP annotation processing in :app for new entities.** Per Phase 1 decision [01-01]: KSP room.compiler runs only in :data module. CartItemEntity and WishlistItemEntity must be in :data/local/entity/.
- **Don't use `@Upsert` alone for add-to-cart with quantity increment.** `@Upsert` replaces the row entirely. The repository must: getCartItem() → if exists, update quantity; else insert.
- **Don't reset the HorizontalPager swipe gesture.** The current CustomerTabScreen uses `userScrollEnabled = true` by default. With 5 tabs that include Cart and Wishlist, swiping between tabs may be confusing. Consider disabling horizontal swipe and relying solely on NavigationBar taps, or keeping pager scroll since it is established.
- **Don't make the wishlist require login on first write.** The context decision is explicit: wishlist works without login. Store with userId = "" for unauthenticated; sync on login.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Swipe-to-delete cart rows | Custom gesture detector + offset animations | `SwipeToDismissBox` (Material3) | Handles velocity, snap, background reveal |
| Badge on nav tab icon | Custom overlay with absolute positioning | `BadgedBox` + `Badge` (Material3) | Accessible, positioned correctly by Material spec |
| Undo snackbar | Custom floating overlay | `SnackbarHostState.showSnackbar()` with `SnackbarResult.ActionPerformed` | Already wired in MainActivity; action callback handles undo |
| Empty state transition | Manual alpha/visibility toggle | `AnimatedContent` | Handles crossfade, keeps state stable, avoids flicker |
| Heart scale animation | `AnimateVisibility` hacks | `Animatable` + `LaunchedEffect` | Precise control over multi-step bounce sequence |
| Composite PK upsert | Custom SQL REPLACE | Room `@Upsert` (after manual get-then-upsert for quantity) | `@Upsert` handles INSERT OR REPLACE at SQL level |

**Key insight:** Material3 already provides all specialized UI components needed. The project is at BOM 2025.01.00 which includes `SwipeToDismissBox` and `BadgedBox`. No third-party animation libraries are needed.

---

## Common Pitfalls

### Pitfall 1: Composite Primary Key with Upsert semantics for quantity increment
**What goes wrong:** Using `@Upsert` directly for "add to cart" causes quantity to reset to 1 if the product is already in cart, because @Upsert replaces the entire row.
**Why it happens:** `@Upsert` is equivalent to `INSERT OR REPLACE` — it deletes and reinserts, losing the existing quantity.
**How to avoid:** In CartRepositoryImpl: `val existing = cartItemDao.getCartItem(userId, productId)`. If existing != null, call `cartItemDao.updateQuantity(userId, productId, existing.quantity + requestedQty, now)`. If null, insert fresh.
**Warning signs:** Cart always shows quantity "1" regardless of how many times user added.

### Pitfall 2: Wishlist for anonymous users breaks on userId = ""
**What goes wrong:** If wishlist uses composite PK (userId, productId) and userId is "", multiple anonymous users on the same device share a wishlist, and migration/sync can produce constraint violations.
**Why it happens:** Empty string is a valid composite key value.
**How to avoid:** Anonymous wishlist can safely use userId="" since this is a single-user app (one device = one Room database). On login: read all rows with userId="" → upsert with real uid → delete old "" rows. This is a one-shot migration at login time.
**Warning signs:** Wishlist disappears after login, or duplicates appear.

### Pitfall 3: Cart badge count out of sync with actual cart
**What goes wrong:** Badge shows stale count if ViewModel collection scope is stopped while cart changes.
**Why it happens:** `SharingStarted.WhileSubscribed(5000)` stops collection 5s after no subscribers. If no screen is active showing the tab bar, the badge flow stops.
**How to avoid:** The badge count StateFlow should use `SharingStarted.Eagerly` or be collected in a ViewModel that stays alive for the session (e.g., a shared CartBadgeViewModel scoped to the activity, or collected directly in CustomerTabScreen's composable scope via collectAsStateWithLifecycle).
**Warning signs:** Badge flickers or shows wrong count when returning to the tab screen.

### Pitfall 4: NavigationBar tab switch vs Pager state conflict
**What goes wrong:** The existing CustomerTabScreen uses `HorizontalPager`. If Pager swipe and NavigationBar clicks get out of sync, the selected tab indicator shows the wrong tab.
**Why it happens:** PagerState.currentPage and the derived selectedTabIndex can diverge if animateScrollToPage() is cancelled mid-animation.
**How to avoid:** Use `derivedStateOf { pagerState.currentPage }` for the selected tab (already done in current code — maintain this pattern). For the badge, collect cart count in the same composable scope as the pager state.
**Warning signs:** Cart tab is selected visually but the pager shows a different page.

### Pitfall 5: Purchase.quantity is Double (STATE.md blocker)
**What goes wrong:** CartItemEntity uses `quantity: Int` but the domain model `Purchase.quantity` is `Double` — mapping between them causes type errors.
**Why it happens:** Historical design decision; marked as a known blocker in STATE.md.
**How to avoid:** Fix `Purchase.quantity` from `Double` to `Int` before implementing CartItemEntity. This is a single-line change in `domain/model/Purchase.kt` but may require mapper updates if Purchase is serialized anywhere.
**Warning signs:** Compilation errors in Purchase mappers or toMap().

### Pitfall 6: SyncWorker payload deserialization for cart operations
**What goes wrong:** ADD_TO_CART payload JSON must be deserializable in SyncWorker, but if the payload schema changes between app versions, old queued operations fail to parse.
**Why it happens:** PendingOperationEntity stores payloadJson as opaque String; no versioning.
**How to avoid:** Keep payload DTOs stable and annotate with `@Serializable`. Use `ignoreUnknownKeys = true` in the Json instance (already done in the project). Design AddToCartPayload with only necessary fields.
**Warning signs:** SyncWorker marks cart operations as FAILED with "deserialization error".

### Pitfall 7: Stock validation on add-to-cart requires fresh product data
**What goes wrong:** `availableStock` in CartItemEntity is the cached value at add-time. If stock changes (another customer buys it), the cart shows incorrect stock remaining.
**Why it happens:** Room is a cache; product stock changes are sync-driven by Firestore listener.
**How to avoid:** On cart screen open (and on add-to-cart), validate against the current ProductEntity from Room (which is updated by the Firestore product listener from Phase 1). Cross-reference CartItemEntity.productId with ProductEntity.totalStockQuantity.
**Warning signs:** "Only X left" shows wrong number; over-stock reduction snackbar fires incorrectly.

---

## Code Examples

Verified patterns from official sources and project codebase:

### CartItemDao Flow observation
```kotlin
// Follows ProductDao pattern: data/local/dao/ProductDao.kt
@Dao
interface CartItemDao {
    @Query("SELECT * FROM cart_items WHERE userId = :userId ORDER BY addedAt ASC")
    fun observeCartItems(userId: String): Flow<List<CartItemEntity>>

    @Query("SELECT COUNT(DISTINCT productId) FROM cart_items WHERE userId = :userId")
    fun observeUniqueProductCount(userId: String): Flow<Int>
}
```

### CartViewModel state pattern
```kotlin
// Follows CustomerHomeViewModel pattern: app/.../customer_home/CustomerHomeViewModel.kt
class CartViewModel(
    private val cartRepository: CartRepository,
    private val authRepository: AuthRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    init {
        val uid = authRepository.currentUser.value?.id ?: return
        viewModelScope.launch {
            cartRepository.observeCartItems(uid)
                .catch { e -> _state.update { it.copy(error = e.message) } }
                .collect { items -> _state.update { it.copy(cartItems = items) } }
        }
    }
}
```

### Badge on NavigationBarItem
```kotlin
// Source: developer.android.com/develop/ui/compose/components/badges
NavigationBarItem(
    icon = {
        if (tab == CustomerTabs.Cart && cartCount > 0) {
            BadgedBox(badge = { Badge { Text("$cartCount") } }) {
                Icon(tab.selectedIcon, contentDescription = null)
            }
        } else {
            Icon(
                if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null
            )
        }
    },
    ...
)
```

### Wishlist sync on login
```kotlin
// In WishlistRepository or AuthRepository post-login hook
suspend fun syncAnonymousWishlistOnLogin(userId: String) {
    val anonymousItems = wishlistItemDao.getItemsForUser("")
    if (anonymousItems.isEmpty()) return
    anonymousItems.forEach { item ->
        wishlistItemDao.upsert(item.copy(userId = userId))
        firestore.collection("users").document(userId)
            .collection("wishlist").document(item.productId)
            .set(item.toFirestoreMap()).await()
    }
    wishlistItemDao.deleteAllForUser("")
}
```

### Koin module additions (DataModule.kt)
```kotlin
// Follows existing repositoryModule pattern: app/di/DataModule.kt
val repositoryModule = module {
    // ...existing entries...
    singleOf(::CartRepositoryImpl).bind<CartRepository>()
    singleOf(::WishlistRepositoryImpl).bind<WishlistRepository>()
}

val databaseModule = module {
    // ...existing entries...
    single { get<WenuCommerceDatabase>().cartItemDao() }
    single { get<WenuCommerceDatabase>().wishlistItemDao() }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SwipeToDismiss (Material) | SwipeToDismissBox (Material3) | Material3 adoption | Use SwipeToDismissBox — old API deprecated |
| TabRow for navigation | NavigationBar for primary nav | Material3 | Project currently uses TabRow; should migrate to NavigationBar for bottom nav |
| Pager-based tab switching | NavigationBar + direct screen swap or Pager | Ongoing | Keep Pager if swipe-between-tabs is desired; no mandatory change |

**Deprecated/outdated:**
- `SwipeToDismiss` (Material): replaced by `SwipeToDismissBox` in Material3 — project uses Material3 exclusively.
- `rememberDismissState`: replaced by `rememberSwipeToDismissBoxState` in Material3.

---

## Open Questions

1. **NavigationBar vs TabRow migration**
   - What we know: Current CustomerTabScreen uses HorizontalPager + TabRow; context decision adds Cart + Wishlist as "dedicated bottom navigation tabs"
   - What's unclear: Whether to keep Pager (allows swipe navigation) or switch to NavigationBar-only with NavHost routing (standard for primary nav)
   - Recommendation: Migrate CustomerTabScreen to use `NavigationBar` + `NavigationBarItem` with existing `HorizontalPager` (disable user swipe = `userScrollEnabled = false`), or keep swipe enabled. NavigationBar is the Material3 standard for bottom navigation. This is Claude's discretion.

2. **Cart badge source of truth for CartBadgeViewModel scope**
   - What we know: Badge count needs to stay live even when cart screen is not active; `WhileSubscribed(5000)` may let the Flow lapse
   - What's unclear: Whether to use activity-scoped ViewModel or collect in CustomerTabScreen's own scope
   - Recommendation: Collect `cartItemDao.observeUniqueProductCount()` in CustomerTabScreen directly via `collectAsStateWithLifecycle()`, which stays active as long as the tab screen is in composition — which it always is when the customer is logged in.

3. **Purchase.quantity fix scope**
   - What we know: STATE.md flags this as a blocker before CartItemEntity design; it's `Double` in `Purchase.kt`
   - What's unclear: Where Purchase.quantity is used in serialization/Firestore writes that might break
   - Recommendation: Search all usages of `Purchase.quantity` before changing type. Likely only in UserEntity's purchasesJson JSON column — the mapper roundtrip already uses `runCatching` so safe. Fix in the first cart plan task.

4. **Wishlist anonymous sync conflict resolution**
   - What we know: On login, local userId="" items are synced to Firestore under the real uid
   - What's unclear: What happens if the user already had wishlist items in Firestore from a previous login on another device (items exist for both "" and real uid)
   - Recommendation: On login sync, upsert all "" items to Firestore (Firestore will merge if doc exists). Then fetch the Firestore wishlist and upsert all to Room under the real uid. Delete "" rows last. This is a merge-on-login strategy.

---

## Sources

### Primary (HIGH confidence)
- Project codebase — ProductEntity.kt, ProductDao.kt, ProductRepositoryImpl.kt, WenuCommerceDatabase.kt, SyncWorker.kt, CustomerHomeViewModel.kt — direct observation of established patterns
- [Android official: Badges (developer.android.com)](https://developer.android.com/develop/ui/compose/components/badges) — BadgedBox / Badge API confirmed
- [Android official: Swipe to dismiss (developer.android.com)](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss) — SwipeToDismissBox API confirmed
- [Android official: Animation quick guide (developer.android.com)](https://developer.android.com/develop/ui/compose/animation/quick-guide) — animateFloatAsState, Animatable, AnimatedContent patterns confirmed
- [Android official: Room migrations (developer.android.com)](https://developer.android.com/training/data-storage/room/migrating-db-versions) — Migration object pattern confirmed

### Secondary (MEDIUM confidence)
- STATE.md and CONTEXT.md — project-specific decisions directly applicable
- WebSearch results for BadgedBox NavigationBar — confirmed Material3 pattern; official docs provide authoritative source

### Tertiary (LOW confidence)
- None — all critical claims verified against official docs or project codebase

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project, versions verified in libs.versions.toml
- Architecture: HIGH — directly follows established Phase 1/2 patterns (ProductEntity, ProductDao, ProductRepositoryImpl, CustomerHomeViewModel)
- Pitfalls: HIGH for composite PK / quantity / navigation sync (observed from codebase); MEDIUM for wishlist anonymous sync edge cases (logic reasoning)
- UI patterns: HIGH — BadgedBox, SwipeToDismissBox, AnimatedContent verified against official docs

**Research date:** 2026-02-20
**Valid until:** 2026-04-20 (stable APIs; Material3 BOM version may update but patterns are stable)
