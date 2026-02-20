---
phase: 03-cart-wishlist
verified: 2026-02-20T21:30:00Z
status: passed
score: 3/3 gap-closure truths verified; 13/13 original truths confirmed by regression
re_verification:
  previous_status: passed (initial) + UAT gaps found
  previous_score: 13/13
  gaps_closed:
    - "Add-to-cart from product detail shows only the feature snackbar, no 'saved locally' overlap"
    - "Incrementing or decrementing cart quantity does not flash the offline banner when online"
    - "Wishlist add-to-cart does not trigger false offline banner or saved locally snackbar when online"
  gaps_remaining: []
  regressions: []
gaps: []
human_verification:
  - test: "Offline path: 'Saved locally' snackbar still fires when actually offline"
    expected: "Exactly one snackbar appears when adding to cart in airplane mode. No second snackbar overlaps."
    why_human: "Fix uses isOnline.first() — offline path must still emit. Requires device with network toggled off."
  - test: "Offline banner appears and disappears correctly with connectivity"
    expected: "PendingSyncBanner appears when offline, disappears when network restored"
    why_human: "shouldShowBanner is now !online only; offline show / online hide behavior needs device confirmation."
  - test: "Cart badge live increment after add-to-cart"
    expected: "Badge on Cart nav tab increments within 1-2 seconds without manual refresh"
    why_human: "collectAsStateWithLifecycle wiring verified statically; live recomposition reactivity requires device runtime."
  - test: "Decrement-to-zero removes item with undo snackbar"
    expected: "Item disappears from cart list and snackbar appears with Undo action; tapping Undo restores item"
    why_human: "LaunchedEffect(undoItem) and SnackbarResult chain verified statically; runtime timing needs device."
  - test: "Heart scale bounce animation on toggle"
    expected: "Heart icon visibly bounces and switches between outlined and filled red"
    why_human: "Animatable + LaunchedEffect(isWishlisted) animation verified in code; visual rendering needs device."
  - test: "Anonymous-to-user wishlist migration on login"
    expected: "Wishlist items added while logged out appear in Wishlist tab after sign-in"
    why_human: "syncAnonymousOnLogin wiring verified; end-to-end migration with real Firestore requires device."
  - test: "Offline cart add — persistence and sync-on-reconnect"
    expected: "Item appears in cart immediately (Room); SyncWorker dispatches ADD_TO_CART to Firestore when network returns"
    why_human: "PendingOperationEntity insert and WorkManager enqueue verified in code; requires device + Firestore."
---

# Phase 03: Cart and Wishlist Verification Report

**Phase Goal:** Customers can build a cart and wishlist that persist across app restarts and work offline, giving them full control over what they intend to buy
**Verified:** 2026-02-20T21:30:00Z
**Status:** passed
**Re-verification:** Yes — after UAT gap closure (Plans 03-01 through 03-05 complete)

---

## Re-verification Summary

The initial automated verification (2026-02-20T18:30:00Z) returned 13/13 passed. UAT (commit 15b7efa) then revealed 5 runtime issues — all tracing to two root causes:

1. `PendingSyncViewModel.shouldShowBanner` fired when online with pending items, causing false offline banner flashes on cart quantity changes (UAT tests 4, 5, 11, 12)
2. `SyncManager.emitOfflineWriteQueued()` emitted unconditionally regardless of connectivity, causing a duplicate "Saved locally" snackbar on every add-to-cart while online (UAT tests 2, 11, 12)

Plan 03-05 was created (commit 86321bc) and executed in two commits:
- `5ea9535` — `shouldShowBanner` lambda body simplified from `!online || (pending > 0 && pending > dismissed)` to `!online`
- `c10c482` — `emitOfflineWriteQueued()` made `suspend fun` with `isOnline.first()` connectivity check; `ConnectivityObserver` injected as 5th SyncManager constructor parameter; DataModule updated to 5 `get()` calls

This re-verification confirms both fixes are correctly implemented and no regressions were introduced.

---

## Gap Closure Verification (Plan 03-05 Must-Haves)

### Gap-Closure Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| G1 | Add-to-cart from product detail shows only the feature snackbar, no 'saved locally' overlap | VERIFIED | `SyncManager.emitOfflineWriteQueued()` (lines 98-103) is `suspend fun` that calls `connectivityObserver.isOnline.first()` (line 99) and only emits `SyncEvent.OfflineWriteQueued` when `!isOnline` (line 100-102). `import kotlinx.coroutines.flow.first` present at line 22. |
| G2 | Incrementing or decrementing cart quantity does not flash the offline banner when online | VERIFIED | `PendingSyncViewModel.shouldShowBanner` combine lambda at lines 67-78: destructured as `{ online, _, _ -> !online }`. Parameters `pending` and `dismissed` received as underscored (ignored). Banner shows only when `!online`. |
| G3 | Wishlist add-to-cart does not trigger false offline banner or saved locally snackbar when online | VERIFIED | Root causes fixed by G1 and G2. `WishlistViewModel.addItemToCart` calls `cartRepository.addToCart`, which calls `syncManager.emitOfflineWriteQueued()` (now offline-gated). Banner gated by `!online` in `PendingSyncViewModel`. |

**Gap-closure score:** 3/3 truths verified

### Gap-Closure Artifacts

| Artifact | Provides | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncViewModel.kt` | Connectivity-gated banner visibility | VERIFIED | `shouldShowBanner` lambda at lines 71-73: `{ online, _, _ -> !online }`. Commit `5ea9535` confirmed. |
| `data/src/main/java/com/wenubey/data/local/SyncManager.kt` | Connectivity-aware offline write event emission | VERIFIED | 5-parameter constructor at lines 33-39 includes `private val connectivityObserver: ConnectivityObserver`; `emitOfflineWriteQueued()` is `suspend fun` (line 98); `isOnline.first()` at line 99; `import kotlinx.coroutines.flow.first` at line 22. Commit `c10c482` confirmed. |
| `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` | Koin wiring for 5-arg SyncManager | VERIFIED | `syncModule` at line 137: `single { SyncManager(get(), get(), get(), get(), get()) }` — 5 `get()` calls confirmed. Commit `c10c482` confirmed. |

### Gap-Closure Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SyncManager.emitOfflineWriteQueued()` | `ConnectivityObserver.isOnline` | `isOnline.first()` check before tryEmit | VERIFIED | `isOnline.first()` at SyncManager.kt line 99; `tryEmit` called only in `if (!isOnline)` branch at line 101 |
| `PendingSyncViewModel.shouldShowBanner` | `ConnectivityObserver.isOnline` | combine gating on `!online` | VERIFIED | `{ online, _, _ -> !online }` at PendingSyncViewModel.kt lines 71-73; combines isOnline, pendingCount, dismissedCount |
| `SyncManager` | `ConnectivityObserver` (Koin) | 5th constructor parameter resolved by Koin | VERIFIED | `DataModule.syncModule` line 137 has 5 `get()` calls; `ConnectivityObserver` registered as singleton in `connectivityModule` line 141 |

---

## Regression Check on Original 13 Truths

Only the three files in Plan 03-05's `files_modified` were changed. All other Phase 03 artifacts are unmodified.

| # | Truth | Regression Status | Evidence |
|---|-------|-------------------|----------|
| 1 | CartItemEntity Room persistence | NO REGRESSION | `cart_items` entity file unmodified; schema v3 JSON at `data/schemas/.../3.json` confirmed present |
| 2 | Offline queue to PendingOperationEntity and SyncWorker dispatch | NO REGRESSION | `CartRepositoryImpl.queueCartOperation()` unmodified; `SyncWorker` dispatch methods (3 matches: syncAddToCart, syncUpdateQuantity, syncRemoveFromCart) confirmed |
| 3 | CartRepository Flow scoped to user | NO REGRESSION | `observeCartItems(userId)` in `CartRepositoryImpl` unmodified |
| 4 | Duplicate add increments quantity | NO REGRESSION | `addToCart()` upsert logic in `CartRepositoryImpl` unmodified |
| 5 | Purchase.quantity is Int | NO REGRESSION | `Purchase.kt` not in Plan 03-05 modified files |
| 6 | Add-to-cart from product detail with badge increment | NO REGRESSION | `CustomerProductDetailScreen`, `CustomerTabScreen` unmodified; `BadgedBox`/`observeUniqueProductCount`/`NavigationBar` confirmed (9 matches in CustomerTabScreen) |
| 7 | Increment/decrement/remove with live subtotal | NO REGRESSION | `CartViewModel` unmodified |
| 8 | Out-of-stock/deleted inline warnings | NO REGRESSION | `CartItemRow` unmodified |
| 9 | Cart badge shows unique count, disappears when empty | NO REGRESSION | `CustomerTabScreen` unmodified; `BadgedBox` wiring confirmed |
| 10 | Empty cart with crossfade and CTA | NO REGRESSION | `CustomerCartScreen` unmodified |
| 11 | WishlistRepository offline persistence with anonymous support | NO REGRESSION | `WishlistRepositoryImpl` unmodified; 14 matches confirmed for syncAnonymousOnLogin/wishlistItemDao/toggleWishlist |
| 12 | Heart toggle with scale bounce animation | NO REGRESSION | `WishlistHeartButton` unmodified; Animatable/LaunchedEffect/isWishlisted confirmed (10 matches) |
| 13 | Wishlist screen grid, per-item/bulk/multi-select add-to-cart | NO REGRESSION | `CustomerWishlistScreen`, `WishlistViewModel` unmodified |

---

## Requirements Coverage

All 13 requirements accounted for and marked `[x]` complete in REQUIREMENTS.md. No orphaned requirements.

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| CART-01 | Customer can add product to cart from product detail with quantity selector | SATISFIED | `CartActionSection` with stepper and Add to Cart button; `CustomerProductDetailAction.AddToCart` dispatch verified |
| CART-02 | Cart persists across app restarts via Room | SATISFIED | Room `cart_items` entity, composite PK, `MIGRATION_2_3`, schema v3 JSON exported |
| CART-03 | Cart syncs to Firestore when online | SATISFIED | `SyncWorker` dispatches `ADD_TO_CART`, `UPDATE_CART_QUANTITY`, `REMOVE_FROM_CART` to Firestore |
| CART-04 | Customer can update quantity (increment/decrement) or remove items | SATISFIED | `CartViewModel.onAction` handles `IncrementQuantity`, `DecrementQuantity`, `RemoveItem` |
| CART-05 | Cart shows subtotal, item count, and per-line totals | SATISFIED | `CartState.subtotal` computed val; `CartBottomBar` renders formatted total |
| CART-06 | Out-of-stock or deleted products show warning inline | SATISFIED | `CartItemRow` renders "No longer available" / "Out of Stock"; row alpha 0.5f; stepper disabled |
| CART-07 | Cart badge/count visible on navigation tab | SATISFIED | `BadgedBox` in `CustomerTabScreen`; `observeUniqueProductCount` Flow; hidden when count == 0 |
| CART-08 | Empty cart shows illustration with CTA to browse products | SATISFIED | `AnimatedContent` crossfade to `CartEmptyState`; "Start Shopping" Button navigating to home tab |
| WISH-01 | Customer can add/remove products from wishlist via heart icon on product cards and detail | SATISFIED | `WishlistHeartButton` on `CustomerHomeScreen` cards and `CustomerProductDetailScreen` title row |
| WISH-02 | Wishlist persists offline via Room, syncs to Firestore | SATISFIED | `wishlist_items` Room table; Room-first writes; Firestore write for logged-in users |
| WISH-03 | Dedicated wishlist screen showing saved products | SATISFIED | `CustomerWishlistScreen` with `LazyVerticalGrid(GridCells.Fixed(2))` wired on tab page 2 |
| WISH-04 | Customer can add to cart directly from wishlist | SATISFIED | Per-item "Add" button, "Add All to Cart" button, multi-select "Add to Cart" in selection TopAppBar |
| WISH-05 | Deleted or unavailable products show inline warning in wishlist | SATISFIED | `WishlistItemCard` overlays "No longer available" / "Out of Stock"; "Add" button hidden for unavailable items |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `data/worker/SyncWorker.kt` | 130-131 | `TODO("Wire UPDATE_PROFILE...")` and `TODO("Wire SUBMIT_REVIEW...")` | Info | Intentionally deferred to future phases — explicitly out of scope for Phase 03. Not a gap. |

No anti-patterns found in any Plan 03-05 modified files.

---

## Human Verification Required

### 1. Offline path — "Saved locally" snackbar still fires when actually offline

**Test:** Enable airplane mode. Open a product detail. Tap "Add to Cart."
**Expected:** Exactly one snackbar appears: "Saved locally, will sync when online" (or equivalent). No overlapping second snackbar.
**Why human:** The fix uses `isOnline.first()` — the offline path must still emit. Requires device with network disabled to confirm the positive case was not broken by the fix.

### 2. Offline banner appears and disappears correctly with connectivity

**Test:** Enable airplane mode. Navigate to Home, Cart, or Wishlist screen.
**Expected:** `PendingSyncBanner` appears at the top of the screen. Re-enable network — banner disappears.
**Why human:** `shouldShowBanner` is now `!online` only. Online state with pending items must not show the banner. Requires device with airplane mode toggle.

### 3. Cart badge live increment after add-to-cart

**Test:** Open any in-stock product detail. Tap "Add to Cart." Immediately observe the Cart tab in the bottom navigation bar.
**Expected:** The badge number on the Cart tab increments within 1-2 seconds without any manual refresh.
**Why human:** `collectAsStateWithLifecycle` from `observeUniqueProductCount` Flow wiring verified statically; live recomposition reactivity requires device runtime.

### 4. Decrement-to-zero removes item with undo snackbar

**Test:** Add one unit of a product to the cart. On the Cart screen, tap the "-" stepper button.
**Expected:** Item disappears from the list. A snackbar appears with an "Undo" action button. Tapping Undo restores the item.
**Why human:** `LaunchedEffect(undoItem)` and `SnackbarResult.ActionPerformed` chain verified statically; runtime coroutine timing and snackbar display need device confirmation.

### 5. Heart scale bounce animation on toggle

**Test:** On the Home screen product cards, tap the heart icon on any product card.
**Expected:** The heart icon visibly scales up (bounces) before settling, and switches between outlined (not wishlisted) and filled red (wishlisted).
**Why human:** `Animatable` + `LaunchedEffect(isWishlisted)` spring animations verified in code; animation smoothness and visual correctness requires device rendering.

### 6. Anonymous-to-user wishlist migration on login

**Test:** While logged out, add 2+ products to the wishlist. Then sign in with an existing account.
**Expected:** Previously saved wishlist items appear in the Wishlist tab after login, merged with any Firestore wishlist items for that user.
**Why human:** `syncAnonymousOnLogin` implementation and `AuthViewModel` wiring verified. End-to-end migration with real Firestore requires device testing with network.

### 7. Offline cart add — persistence and sync-on-reconnect

**Test:** Enable airplane mode. Add a product to the cart. Verify it appears in cart. Re-enable network.
**Expected:** Cart item is immediately visible (Room write). When network reconnects, SyncWorker fires and item syncs to Firestore (`users/{uid}/cart/{productId}` document created).
**Why human:** `PendingOperationEntity` insert and `WorkManager.enqueueUniqueWork` verified in code. WorkManager scheduling and Firestore write execution require device + Firestore observation.

---

## Gaps Summary

No gaps. All 5 UAT issues (tests 2, 4, 5, 11, 12) were closed by Plan 03-05 in two commits:

- `5ea9535`: `PendingSyncViewModel.shouldShowBanner` simplified to `!online` — eliminates false offline banner on cart quantity changes (UAT tests 4, 5) and wishlist add-to-cart (UAT tests 11, 12)
- `c10c482`: `SyncManager.emitOfflineWriteQueued()` made connectivity-aware via `isOnline.first()` — eliminates duplicate "Saved locally" snackbar when online (UAT tests 2, 11, 12)

All 13 original phase truths pass regression checks. All 13 requirements (CART-01 through CART-08, WISH-01 through WISH-05) satisfied. Phase 03 goal is fully achieved.

---

*Verified: 2026-02-20T21:30:00Z*
*Verifier: Claude (gsd-verifier)*
*Re-verification after UAT gap closure — Plans 03-01 through 03-05 complete*
