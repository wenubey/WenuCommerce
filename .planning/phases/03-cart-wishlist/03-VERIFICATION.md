---
phase: 03-cart-wishlist
verified: 2026-02-20T18:30:00Z
status: passed
score: 13/13 must-haves verified
re_verification: false
gaps: []
human_verification:
  - test: "Add product to cart from product detail — verify cart badge increments live"
    expected: "Badge on Cart nav tab shows correct unique product count immediately after add"
    why_human: "Flow wiring verified in code, but live badge reactivity requires runtime observation"
  - test: "Decrement quantity to 1 and press minus — verify item is removed with undo snackbar"
    expected: "Item disappears from cart list and snackbar appears with Undo action"
    why_human: "ViewModel logic and SnackbarResult handling verified statically, but flow through LaunchedEffect needs runtime confirmation"
  - test: "Heart button on product card — verify scale bounce animation fires on toggle"
    expected: "Heart scales up then settles, switching between outlined and filled red"
    why_human: "Animatable + LaunchedEffect(isWishlisted) pattern is correct in code; animation rendering needs device verification"
  - test: "Wishlist without login — add a product to wishlist anonymously, then log in"
    expected: "Anonymous wishlist items appear in the authenticated wishlist after login"
    why_human: "syncAnonymousOnLogin wired in AuthViewModel and Room userId migration logic is correct, but end-to-end migration needs runtime verification with real Firestore"
  - test: "Offline cart add — disable network, add product to cart, re-enable network"
    expected: "Item appears in cart immediately; SyncWorker dispatches ADD_TO_CART to Firestore when network returns"
    why_human: "Offline queue, PendingOperationEntity insertion, and SyncWorker dispatch all verified in code but WorkManager scheduling requires device testing"
---

# Phase 03: Cart and Wishlist Verification Report

**Phase Goal:** Customers can build a cart and wishlist that persist across app restarts and work offline, giving them full control over what they intend to buy
**Verified:** 2026-02-20T18:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | CartItemEntity rows persist across app restarts via Room | VERIFIED | `CartItemEntity` uses `@Entity(tableName = "cart_items", primaryKeys = ["userId", "productId"])`. DB v3 with `MIGRATION_2_3` exists. Schema JSON exported at `data/schemas/.../3.json`. |
| 2 | Cart operations queue to PendingOperationEntity when offline and SyncWorker dispatches when online | VERIFIED | `CartRepositoryImpl.queueCartOperation()` inserts `PendingOperationEntity` with correct `OperationType`. `SyncWorker` deserializes payloads and calls `cartRepository.syncAddToCart/syncUpdateQuantity/syncRemoveFromCart`. |
| 3 | CartRepository provides reactive Flow of cart items scoped to current user | VERIFIED | `CartRepositoryImpl.observeCartItems(userId)` maps `cartItemDao.observeCartItems(userId)` Flow to domain `CartItem`. `CartViewModel.init` collects this Flow. |
| 4 | Adding the same product again increments quantity instead of creating duplicate rows | VERIFIED | `CartRepositoryImpl.addToCart()` calls `cartItemDao.getCartItem()` first; if existing, calls `updateQuantity(existing.quantity + quantity)`; else `upsert()`. |
| 5 | Purchase.quantity is Int, not Double | VERIFIED | `Purchase.kt` declares `val quantity: Int = 0`. No `Double` variant found. |
| 6 | Customer adds product to cart from product detail with quantity selector; cart badge increments | VERIFIED | `CustomerProductDetailScreen` has `CartActionSection` composable with 3 button states (Out of Stock / In Cart / Add to Cart) dispatching `CustomerProductDetailAction.AddToCart`. Badge in `CustomerTabScreen` collects `observeUniqueProductCount` Flow via `koinInject()`. |
| 7 | Customer increments, decrements, and removes items on cart screen; subtotal updates in real time | VERIFIED | `CartViewModel.onAction` handles `IncrementQuantity`, `DecrementQuantity`, `RemoveItem`. `CartState.subtotal` is a computed `val` on `cartItems`. `CustomerCartScreen` renders stepper controls (`IconButton +/-`) and `CartBottomBar` showing `"$%.2f".format(subtotal)`. |
| 8 | Out-of-stock items show grayed-out warning; deleted products show "No longer available" | VERIFIED | `CartItemRow` checks `item.isProductDeleted` and `item.availableStock <= 0`. Renders "No longer available" / "Out of Stock" text in error color. Row alpha set to 0.5f when unavailable. Stepper disabled for unavailable items. |
| 9 | Cart badge on nav tab shows unique product count and disappears when cart is empty | VERIFIED | `CustomerTabScreen` renders `BadgedBox` with `Badge { Text("$cartCount") }` only when `cartCount > 0` on Cart tab. Count from `cartRepository.observeUniqueProductCount(userId)` Flow. |
| 10 | Empty cart shows illustrated empty state with CTA | VERIFIED | `AnimatedContent(targetState = state.cartItems.isEmpty())` crossfades to `CartEmptyState` composable with icon, text "Your cart feels lonely!", and "Start Shopping" Button calling `onNavigateToHome`. |
| 11 | WishlistRepository provides reactive Flow, persists offline, supports anonymous users | VERIFIED | `WishlistRepositoryImpl` uses `userId ?: ""` sentinel, `wishlistItemDao.observeWishlistItems(userId)`, and Room-first writes. `syncAnonymousOnLogin` migrates anonymous rows to real userId. `AuthViewModel` calls `wishlistRepository.syncAnonymousOnLogin(uid)` on login. |
| 12 | Heart toggle works on product cards and detail screen with scale bounce animation | VERIFIED | `WishlistHeartButton` composable uses `Animatable` + `LaunchedEffect(isWishlisted)` (1.3f DampingRatioLowBouncy then 1f MediumBouncy). Wired on `CustomerHomeScreen` product cards (`wishlistedProductIds` Set from `CustomerHomeViewModel`) and `CustomerProductDetailScreen` title row (`isWishlisted` StateFlow from `CustomerProductDetailViewModel`). |
| 13 | Wishlist screen shows grid, add-to-cart from wishlist (per item / bulk / multi-select) | VERIFIED | `CustomerWishlistScreen` uses `LazyVerticalGrid(GridCells.Fixed(2))` with `AnimatedContent` empty state. `WishlistItemCard` has "Add" button per item. "Add All to Cart" button shown when available items exist. Multi-select via long-press (`combinedClickable`) with "Add to Cart" in selection `TopAppBar`. `WishlistViewModel.addItemToCart/addAllToCart/addSelectedToCart` all call `cartRepository.addToCart`. |

**Score:** 13/13 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `data/.../entity/CartItemEntity.kt` | cart_items Room table | VERIFIED | `@Entity(tableName = "cart_items", primaryKeys = ["userId","productId"])`, all fields correct |
| `data/.../dao/CartItemDao.kt` | Cart CRUD with Flow | VERIFIED | All 7 methods present: `observeCartItems`, `observeUniqueProductCount`, `getCartItem`, `upsert`, `deleteItem`, `clearCart`, `updateQuantity`, `updateProductStatus` |
| `data/.../entity/WishlistItemEntity.kt` | wishlist_items Room table | VERIFIED | `@Entity(tableName = "wishlist_items", primaryKeys = ["userId","productId"])`, userId="" for anonymous |
| `data/.../dao/WishlistItemDao.kt` | Wishlist CRUD with Flow | VERIFIED | All methods present: `observeWishlistItems`, `getWishlistItem`, `getItemsForUser`, `upsert`, `deleteItem`, `deleteAllForUser`, `isWishlisted` |
| `domain/.../repository/CartRepository.kt` | Cart domain contract | VERIFIED | All specified methods present + `restoreCartItem` added for undo functionality |
| `data/.../repository/CartRepositoryImpl.kt` | Cart Room-first impl with offline queue | VERIFIED | `pendingOperationDao.insert()` called in `queueCartOperation()`, `SyncWorker.enqueue(context)` called after mutations |
| `data/.../local/WenuCommerceDatabase.kt` | DB v3 with both tables | VERIFIED | `version = 3`, `CartItemEntity::class` and `WishlistItemEntity::class` in entities array, `MIGRATION_2_3` creates both tables atomically |
| `domain/.../model/CartItem.kt` | Cart domain model | VERIFIED | All fields present, no `userId` in domain model |
| `domain/.../model/WishlistItem.kt` | Wishlist domain model | VERIFIED | All fields present |
| `domain/.../repository/WishlistRepository.kt` | Wishlist domain contract | VERIFIED | `observeWishlistItems`, `isWishlisted`, `toggleWishlist`, `removeFromWishlist`, `syncAnonymousOnLogin` |
| `data/.../repository/WishlistRepositoryImpl.kt` | Wishlist Room-first impl with anonymous support | VERIFIED | `wishlistItemDao` used throughout; `effectiveUserId = userId ?: ""`; Firestore writes gated on `isNotEmpty()`; `syncAnonymousOnLogin` migrates anonymous rows and fetches Firestore state |
| `app/.../customer_cart/CartViewModel.kt` | Cart state management | VERIFIED | Observes `cartRepository.observeCartItems(userId)` Flow; `onAction` handles all 11 action types; `clearUndoItem()` public method |
| `app/.../customer_cart/CartState.kt` | Cart state with computed subtotal | VERIFIED | `subtotal`, `availableItemCount`, `hasUnavailableItems`, `canCheckout` all computed `val` properties |
| `app/.../CustomerCartScreen.kt` | Full cart UI | VERIFIED | `SwipeToDismissBox`, `AnimatedContent`, `SnackbarHost` with undo, `CartBottomBar` with subtotal and Checkout button |
| `app/.../CustomerTabScreen.kt` | NavigationBar with BadgedBox | VERIFIED | `NavigationBar` + `NavigationBarItem`, `BadgedBox` on Cart tab, 4 tabs (Home/Cart/Wishlist/Profile) |
| `app/.../customer_wishlist/WishlistViewModel.kt` | Wishlist state management | VERIFIED | `wishlistRepository.observeWishlistItems` collected; `onAction` handles all actions; `cartRepository.addToCart` called for cart actions |
| `app/.../CustomerWishlistScreen.kt` | Wishlist grid UI | VERIFIED | `LazyVerticalGrid(GridCells.Fixed(2))`, `AnimatedContent`, undo snackbar, "Add All to Cart" button, multi-select overlay |
| `app/.../core/components/WishlistHeartButton.kt` | Reusable heart with animation | VERIFIED | `Animatable` + `LaunchedEffect(isWishlisted)`, `Filled.Favorite` / `Outlined.FavoriteBorder`, `graphicsLayer { scaleX = scale.value }` |
| `data/schemas/.../3.json` | Room schema v3 export | VERIFIED | File exists; `cart_items` and `wishlist_items` tables with composite PKs confirmed |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `CartRepositoryImpl` | `CartItemDao` | Room DAO injection | VERIFIED | `cartItemDao.` called in every CartRepositoryImpl method |
| `CartRepositoryImpl` | `PendingOperationDao` | offline queue insertion | VERIFIED | `pendingOperationDao.insert(PendingOperationEntity(...))` in `queueCartOperation()` |
| `SyncWorker` | `CartRepository` | cart operation dispatch | VERIFIED | `cartRepository.syncAddToCart`, `syncUpdateQuantity`, `syncRemoveFromCart` wired in SyncWorker `when()` block; `AddToCartPayload`/`UpdateCartQuantityPayload` deserialized from JSON |
| `CartViewModel` | `CartRepository` | Koin injection | VERIFIED | `cartRepository.` used in `observeCartItems`, `updateQuantity`, `removeFromCart`, `restoreCartItem` |
| `CustomerTabScreen` | `CartViewModel` (via `CartRepository`) | badge count collection | VERIFIED | `cartRepository.observeUniqueProductCount(userId).collectAsStateWithLifecycle(initialValue = 0)` — badge count live in composition |
| `CustomerProductDetailScreen` | `CustomerProductDetailViewModel` | add-to-cart action dispatch | VERIFIED | `onAction(CustomerProductDetailAction.AddToCart)` dispatched from `CartActionSection` |
| `WishlistRepositoryImpl` | `WishlistItemDao` | Room DAO injection | VERIFIED | `wishlistItemDao.` called in all 5 interface methods |
| `WishlistRepositoryImpl` | Firestore `users/{uid}/wishlist` | Firestore write on toggle | VERIFIED | `.collection("wishlist")` appears 5 times — toggle add/remove, removeFromWishlist, syncAnonymousOnLogin migration, Firestore merge |
| `WishlistViewModel` | `WishlistRepository` | Koin injection | VERIFIED | `wishlistRepository.observeWishlistItems`, `removeFromWishlist`, `toggleWishlist` called |
| `WishlistViewModel` | `CartRepository` | add-to-cart from wishlist | VERIFIED | `cartRepository.addToCart(userId, minimalProduct, 1)` called in `addItemToCart`, `addAllToCart`, `addSelectedToCart` |
| `CustomerHomeScreen` | heart icon toggle | wishlist state observation | VERIFIED | `wishlistedProductIds` Set from `CustomerHomeViewModel` state; `isWishlisted = product.id in wishlistedProductIds` on each card |
| `AuthViewModel` | `WishlistRepository.syncAnonymousOnLogin` | login-triggered migration | VERIFIED | `wishlistRepository.syncAnonymousOnLogin(uid)` called in background coroutine on login transition |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| CART-01 | 03-01, 03-02 | Customer can add product to cart from product detail with quantity selector | SATISFIED | `CartActionSection` composable with stepper (1..totalStockQuantity) + Add to Cart button; dispatches `CustomerProductDetailAction.AddToCart` to `CustomerProductDetailViewModel.addToCart()` |
| CART-02 | 03-01 | Cart persists across app restarts via Room | SATISFIED | Room `cart_items` table with `@Entity`, composite PK, `MIGRATION_2_3`; schema v3 JSON exported |
| CART-03 | 03-01 | Cart syncs to Firestore when online | SATISFIED | `SyncWorker` dispatches `ADD_TO_CART`, `UPDATE_CART_QUANTITY`, `REMOVE_FROM_CART` to `CartRepository` sync methods that write to `users/{uid}/cart/{productId}` Firestore documents |
| CART-04 | 03-01, 03-02 | Customer can update quantity (increment/decrement) or remove items | SATISFIED | `CartViewModel.onAction` handles `IncrementQuantity`, `DecrementQuantity`, `RemoveItem`; calls `cartRepository.updateQuantity` and `removeFromCart`; decrement-to-0 triggers remove |
| CART-05 | 03-02 | Cart shows subtotal, item count, and per-line totals | SATISFIED | `CartState.subtotal` computed val; `CartBottomBar` renders `"$%.2f".format(subtotal)`; per-item `"$%.2f".format(item.snapshotPrice)` in `CartItemRow` |
| CART-06 | 03-02 | Out-of-stock or deleted products show warning inline | SATISFIED | `CartItemRow` renders "No longer available" / "Out of Stock" in error color; row alpha 0.5f; stepper disabled; swipe-dismiss background shown only for available items |
| CART-07 | 03-02 | Cart badge/count visible on navigation tab | SATISFIED | `BadgedBox` with `Badge { Text("$cartCount") }` on Cart `NavigationBarItem`; hidden when `cartCount == 0`; sourced from `observeUniqueProductCount` Room Flow |
| CART-08 | 03-02 | Empty cart shows illustration with CTA to browse products | SATISFIED | `AnimatedContent` crossfades to `CartEmptyState` with ShoppingCart icon (80dp), "Your cart feels lonely!", "Start Shopping" Button navigating to home tab |
| WISH-01 | 03-03, 03-04 | Customer can add/remove products from wishlist via heart icon on product cards and detail | SATISFIED | `WishlistHeartButton` in product cards (`CustomerHomeScreen`) and detail title row (`CustomerProductDetailScreen`); dispatches `OnToggleWishlist` / `ToggleWishlist` actions to respective ViewModels |
| WISH-02 | 03-03 | Wishlist persists offline via Room, syncs to Firestore | SATISFIED | `wishlist_items` Room table; `WishlistRepositoryImpl` writes Room-first; Firestore write on toggle for logged-in users (wrapped in try/catch so Room succeeds even if Firestore fails) |
| WISH-03 | 03-04 | Dedicated wishlist screen showing saved products | SATISFIED | `CustomerWishlistScreen` with `LazyVerticalGrid(GridCells.Fixed(2))`; wired on page 2 of `HorizontalPager` in `CustomerTabScreen` |
| WISH-04 | 03-04 | Customer can add to cart directly from wishlist | SATISFIED | Per-item "Add" button in `WishlistItemCard`; "Add All to Cart" full-width button above grid; multi-select "Add to Cart" in selection TopAppBar; all call `cartRepository.addToCart` via `WishlistViewModel` |
| WISH-05 | 03-04 | Deleted or unavailable products show inline warning in wishlist | SATISFIED | `WishlistItemCard` overlays "No longer available" / "Out of Stock" text centered on image; "Add" button hidden for unavailable items |

**All 13 requirements satisfied.**

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `data/worker/SyncWorker.kt` | 130-131 | `TODO("Wire UPDATE_PROFILE...")` and `TODO("Wire SUBMIT_REVIEW...")` | Info | These are for future phases (Phase 3+ per comment); explicitly out of scope for this phase. Not a Phase 03 gap. |

No blocker or warning anti-patterns found in Phase 03 artifacts.

---

### Human Verification Required

#### 1. Cart badge live increment after add-to-cart

**Test:** Open product detail for any in-stock product. Tap "Add to Cart". Immediately switch to or observe the Cart tab in the navigation bar.
**Expected:** The badge number on the Cart tab increments within 1-2 seconds without any manual refresh.
**Why human:** Flow collection wiring from `observeUniqueProductCount` through `collectAsStateWithLifecycle` verified in code, but live recomposition reactivity requires device runtime.

#### 2. Decrement-to-zero removes item with undo snackbar

**Test:** Add one unit of a product to the cart. On the cart screen, tap the "-" (minus) stepper button.
**Expected:** The item disappears from the list. A snackbar appears at the bottom with "Item removed" and an "Undo" action button. Tapping "Undo" restores the item.
**Why human:** `LaunchedEffect(undoItem)` and `SnackbarResult.ActionPerformed -> onAction(UndoRemove)` chain verified statically; runtime coroutine timing and snackbar display need device confirmation.

#### 3. Heart scale bounce animation on toggle

**Test:** Navigate to the product browse screen. Tap the heart icon on any product card.
**Expected:** The heart icon visibly scales up (bounces) before settling back to normal size, and switches between outlined (not wishlisted) and filled red (wishlisted).
**Why human:** `Animatable` + `LaunchedEffect(isWishlisted)` spring animations verified in code; animation smoothness and visual correctness requires device rendering.

#### 4. Anonymous-to-user wishlist migration on login

**Test:** While logged out, add 2+ products to the wishlist. Then sign in with an existing account.
**Expected:** The previously saved wishlist items appear in the Wishlist tab after login. The anonymous items are merged with any existing Firestore wishlist items.
**Why human:** `syncAnonymousOnLogin` implementation in `WishlistRepositoryImpl` is complete, and `AuthViewModel` wiring is verified. End-to-end migration with real Firestore writes and Room deletions requires device testing with network.

#### 5. Offline cart persistence and sync-on-reconnect

**Test:** Enable airplane mode. Add a product to the cart. Verify item appears in cart. Re-enable network.
**Expected:** Cart item is immediately visible (Room write). When network reconnects, SyncWorker fires and the item syncs to Firestore (`users/{uid}/cart/{productId}` document created).
**Why human:** `PendingOperationEntity` queue insertion and `WorkManager.enqueueUniqueWork` are verified in code. WorkManager scheduling and Firestore write execution require device + Firestore verification.

---

### Gaps Summary

No gaps found. All 13 must-haves across Plans 03-01 through 03-04 are verified at all three levels (exists, substantive, wired).

The two `TODO` stubs in `SyncWorker.kt` (`UPDATE_PROFILE`, `SUBMIT_REVIEW`) are intentionally deferred to future phases per the plan specification and do not affect Phase 03 goal achievement.

---

*Verified: 2026-02-20T18:30:00Z*
*Verifier: Claude (gsd-verifier)*
