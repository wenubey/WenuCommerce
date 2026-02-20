---
phase: 03-cart-wishlist
plan: 02
subsystem: ui
tags: [compose, material3, navigation-bar, badged-box, swipe-to-dismiss, animated-content, room, flow, koin, cart, viewmodel]

# Dependency graph
requires:
  - phase: 03-cart-wishlist
    plan: 01
    provides: CartRepository, CartItemDao, CartItem domain model, WishlistItemDao, Room v3 DB

provides:
  - CartViewModel: observes Room cart Flow, handles all cart actions (increment/decrement/remove/undo/bulk-select)
  - CartState: MutableStateFlow data class with derived subtotal, canCheckout, hasUnavailableItems
  - CartAction: sealed interface for all cart interactions
  - CustomerCartScreen: full cart UI with SwipeToDismissBox, stepper controls, AnimatedContent empty state, sticky subtotal bar, undo snackbar
  - CustomerTabScreen: refactored from TabRow to NavigationBar with 4 tabs (Home/Cart/Wishlist/Profile) and BadgedBox cart badge
  - CustomerTabs.Wishlist: new enum entry with heart icons
  - CustomerProductDetailScreen: quantity stepper + Add to Cart / In Cart (x{qty}) / Out of Stock button states
  - CustomerProductDetailViewModel: CartRepository + AuthRepository wired, add-to-cart flow with auth gate

affects:
  - 03-03 (WishlistScreen UI — tab screen already has Wishlist tab wired to placeholder)
  - 04-checkout (needs CartBottomBar Checkout button to navigate to checkout flow)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - NavigationBar + NavigationBarItem replaces TabRow for bottom navigation (Material3 standard)
    - BadgedBox wrapping nav icon for live cart count badge (disappears when count = 0)
    - userScrollEnabled=false on HorizontalPager prevents accidental swipe between Cart/Wishlist
    - koinInject() in composable for CartRepository/AuthRepository live badge count (no ViewModel needed)
    - SwipeToDismissBox end-to-start gesture to remove cart items
    - AnimatedContent for crossfade between empty state and cart list (fadeIn togetherWith fadeOut)
    - SnackbarHost inside Scaffold for undo snackbar with LaunchedEffect(undoItem)
    - Sealed interface CartAction with data object and data class variants
    - CartState derived properties (subtotal, canCheckout) as computed vals instead of stored state
    - Auth gate via showLoginPrompt flag triggering AlertDialog in product detail

key-files:
  created:
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_cart/CartState.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_cart/CartAction.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_cart/CartViewModel.kt
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabs.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerCartScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailState.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailAction.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt
    - app/src/main/res/values/strings.xml
    - domain/src/main/java/com/wenubey/domain/repository/CartRepository.kt
    - data/src/main/java/com/wenubey/data/repository/CartRepositoryImpl.kt

key-decisions:
  - "User.uuid (not .id) is the userId field — User model uses uuid: String? (nullable), so all cart operations extract userId = user?.uuid and guard against null"
  - "CartRepository.restoreCartItem(userId, CartItem) added to interface + impl for undo-remove — CartItem has all needed fields (productTitle, productImageUrl, snapshotPrice, availableStock) so no Product object required for re-insert"
  - "CartViewModel.clearUndoItem() as public method — SnackbarResult.Dismissed needs to clear undoItem without triggering restore"
  - "koinInject() for CartRepository in CustomerTabScreen composable (not a ViewModel) — badge count Flow stays live in composition without a separate ViewModel"
  - "HorizontalPager userScrollEnabled=false — prevents confusing swipe gesture between Cart(1) and Wishlist(2) tabs"
  - "CartActionSection shows three mutually exclusive states: Out of Stock (disabled button), In Cart (stepper + OutlinedButton showing current qty), Add to Cart (stepper + filled Button)"
  - "showLoginPrompt flag triggers AlertDialog in product detail — simpler than navigation event for auth gate; Phase 5 can replace with actual sign-in navigation"

patterns-established:
  - "Cart badge: koinInject() CartRepository + AuthRepository in screen composable, collect observeUniqueProductCount Flow with collectAsStateWithLifecycle"
  - "Undo snackbar: LaunchedEffect(undoItem) shows snackbar; SnackbarResult.ActionPerformed dispatches UndoRemove action; Dismissed calls clearUndoItem()"
  - "SwipeToDismissBox with enableDismissFromStartToEnd=false — only end-to-start swipe reveals red delete background"
  - "Derived state in CartState as computed vals — subtotal/canCheckout computed from cartItems on each access, no duplication"

requirements-completed: [CART-01, CART-04, CART-05, CART-06, CART-07, CART-08]

# Metrics
duration: 9min
completed: 2026-02-20
---

# Phase 03 Plan 02: Cart UI Summary

**NavigationBar with BadgedBox cart badge, full CustomerCartScreen with SwipeToDismissBox/stepper/AnimatedContent empty state/sticky subtotal, CartViewModel with all cart actions, and product detail add-to-cart flow with quantity stepper and auth gate**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-02-20T17:37:28Z
- **Completed:** 2026-02-20T17:46:28Z
- **Tasks:** 3
- **Files modified:** 14

## Accomplishments

- Refactored CustomerTabScreen from TabRow to Material3 NavigationBar with 4 tabs (Home, Cart, Wishlist, Profile); Wishlist tab added as placeholder for plan 03-03
- Cart badge implemented via BadgedBox collecting CartRepository.observeUniqueProductCount() Flow; disappears when cart is empty (0)
- Full CustomerCartScreen rewrite: SwipeToDismissBox per item, stepper controls (+/-), stock warnings ("No longer available" / "Out of Stock"), AnimatedContent crossfade between empty state and list
- CartViewModel: observes Room Flow, handles increment/decrement/remove/undo/bulk-select; CartState has computed subtotal and canCheckout
- CartRepository.restoreCartItem() added to interface + impl for undo functionality without requiring a Product object
- Product detail add-to-cart: three button states (Out of Stock/In Cart (x{qty})/Add to Cart), quantity stepper, auth gate AlertDialog for unauthenticated users, "Added to cart" snackbar with "View Cart" action

## Task Commits

Each task was committed atomically:

1. **Task 1: CustomerTabScreen NavigationBar + BadgedBox + Wishlist tab** - `b4483d1` (feat)
2. **Task 2: CartViewModel/State/Action + CustomerCartScreen rewrite** - `f6abda4` (feat)
3. **Task 3: Product detail add-to-cart flow + quantity selector** - `e96116e` (feat)

## Files Created/Modified

**Created:**
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_cart/CartState.kt` - CartState data class with derived subtotal/canCheckout/hasUnavailableItems
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_cart/CartAction.kt` - CartAction sealed interface for all cart interactions
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_cart/CartViewModel.kt` - Room-observing ViewModel with full action handling and undo support

**Modified:**
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabs.kt` - Added Wishlist enum entry with Favorite/FavoriteBorder icons
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabScreen.kt` - TabRow -> NavigationBar, BadgedBox cart badge, userScrollEnabled=false, 4-page pager
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerCartScreen.kt` - Full rewrite: SwipeToDismissBox, stepper, AnimatedContent, CartViewModel, sticky bottom bar
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt` - CartActionSection composable with 3 button states, quantity stepper, snackbar, AlertDialog auth gate
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailViewModel.kt` - Added CartRepository + AuthRepository, addToCart/setQuantity/updateCartQuantity handlers
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailState.kt` - Added cartQuantity, isInCart, selectedQuantity, isAddingToCart, cartMessage, showLoginPrompt
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailAction.kt` - Added SetQuantity, AddToCart, UpdateCartQuantity, DismissLoginPrompt, DismissCartMessage
- `app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt` - Added viewModelOf(::CartViewModel)
- `app/src/main/res/values/strings.xml` - Added wishlist string resource
- `domain/src/main/java/com/wenubey/domain/repository/CartRepository.kt` - Added restoreCartItem() method
- `data/src/main/java/com/wenubey/data/repository/CartRepositoryImpl.kt` - Implemented restoreCartItem() with CartItemEntity.upsert + offline queue

## Decisions Made

- **User.uuid vs .id:** User model uses `uuid: String?` not `id`. All places extracting userId use `?.uuid` and guard against null.
- **restoreCartItem() for undo:** Cart undo needed re-inserting a removed CartItem without a Product object. Added `CartRepository.restoreCartItem(userId, CartItem)` — CartItem already carries productTitle, productImageUrl, snapshotPrice, availableStock, so no Product needed.
- **clearUndoItem() public method:** SnackbarResult.Dismissed must clear the undoItem state without triggering restore. Separate method keeps the LaunchedEffect logic clean.
- **koinInject() for badge count:** Badge count in CustomerTabScreen collected via koinInject() CartRepository + AuthRepository — no ViewModel wrapper needed since Flow collection in composable is sufficient.
- **userScrollEnabled=false on HorizontalPager:** Prevents accidental swipe from Cart tab to Wishlist tab during in-cart interactions.
- **Auth gate via AlertDialog:** showLoginPrompt flag triggers AlertDialog instead of navigation event — Phase 5 auth flow can replace this with actual sign-in navigation when ready.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added CartRepository.restoreCartItem() for undo functionality**
- **Found during:** Task 2 (CartViewModel implementation)
- **Issue:** Plan's UndoRemove action calls `cartRepository.addToCart(userId, product, item.quantity)` but CartViewModel only has a CartItem (not a Product object) after removal
- **Fix:** Added `restoreCartItem(userId: String, cartItem: CartItem)` to CartRepository interface and CartRepositoryImpl — uses CartItemEntity.upsert() with CartItem field data and queues ADD_TO_CART offline operation
- **Files modified:** `domain/src/main/java/com/wenubey/domain/repository/CartRepository.kt`, `data/src/main/java/com/wenubey/data/repository/CartRepositoryImpl.kt`
- **Verification:** Build passes; undo flow correctly re-inserts item
- **Committed in:** `f6abda4` (Task 2 commit)

**2. [Rule 1 - Bug] User.uuid instead of User.id**
- **Found during:** Task 2 (CartViewModel) and Task 3 (CustomerProductDetailViewModel)
- **Issue:** Plan referenced `user.id` but User model has no `id` field — the correct field is `uuid: String?`
- **Fix:** All references changed to `?.uuid` with null-check; Kotlin smart-cast limitation (cross-module nullable property) required capturing as local val before use
- **Files modified:** `CustomerTabScreen.kt`, `CartViewModel.kt`, `CustomerProductDetailViewModel.kt`
- **Verification:** Compilation passes; no unresolved reference errors
- **Committed in:** `b4483d1`, `f6abda4`, `e96116e`

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 bug)
**Impact on plan:** Both required for correctness. No scope creep.

## Issues Encountered

- Smart-cast failure on `user?.uuid` across module boundary — Kotlin cannot smart-cast public API properties from different modules. Fixed by capturing as local `val userId = user?.uuid` before using in conditional branches.

## User Setup Required

None — no external service configuration required for this UI plan.

## Next Phase Readiness

- CartViewModel and CustomerCartScreen fully functional; CartRepository injectable for all cart operations
- Cart badge live on NavigationBar tab; disappears when cart empties
- Wishlist tab exists as placeholder (page 2 in HorizontalPager) — plan 03-03 will implement WishlistScreen
- Product detail add-to-cart wired; Checkout button in CartBottomBar is a stub (Phase 4 will implement checkout navigation)
- Pre-Phase 4: Firestore security rules for `users/{uid}/cart` still needed before cart sync functions in production

---
*Phase: 03-cart-wishlist*
*Completed: 2026-02-20*
