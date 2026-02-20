---
phase: 03-cart-wishlist
plan: 04
subsystem: ui
tags: [compose, wishlist, animation, koin, viewmodel, room, flow]

# Dependency graph
requires:
  - phase: 03-03
    provides: WishlistRepository interface with observeWishlistItems, isWishlisted, toggleWishlist, removeFromWishlist
  - phase: 03-01
    provides: CartRepository.addToCart for add-to-cart from wishlist
  - phase: 03-02
    provides: CustomerTabScreen with wishlist tab placeholder

provides:
  - WishlistViewModel with onAction, state Flow, enterSelectionMode, clearUndoItem
  - CustomerWishlistScreen with LazyVerticalGrid (2-column), empty state, undo snackbar, multi-select
  - WishlistHeartButton reusable composable with scale bounce animation
  - Heart toggle on CustomerProductCard (browse/search) and CustomerProductDetailScreen (title row)
  - CustomerHomeViewModel wishlist observation: wishlistedProductIds StateFlow from observeWishlistItems
  - CustomerProductDetailViewModel wishlist observation: isWishlisted via isWishlisted Flow

affects: [03-05-checkout, any phase using wishlist or home screen context]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Animatable scale bounce: animateTo(1.3f, DampingRatioLowBouncy) then animateTo(1f, DampingRatioMediumBouncy) in LaunchedEffect(isWishlisted)
    - WishlistHeartButton reusable composable in core/components — passed isWishlisted + onToggle
    - Map-to-Set pattern: observeWishlistItems(...).map { items -> items.map { it.productId }.toSet() } for efficient per-card lookups
    - AnimatedContent crossfade for empty/content state (fadeIn togetherWith fadeOut)
    - Long-press selection mode: combinedClickable + enterSelectionMode in ViewModel

key-files:
  created:
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistState.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistAction.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerWishlistScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/core/components/WishlistHeartButton.kt
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeAction.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeState.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailAction.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailState.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt

key-decisions:
  - "WishlistViewModel.buildMinimalProduct uses ProductStatus.ARCHIVED for deleted wishlist items (no DELETED enum in ProductStatus)"
  - "Heart animation fires on every isWishlisted change — no snackbar from browse/detail, animation is the only feedback"
  - "CustomerHomeViewModel injects both WishlistRepository and AuthRepository to observe wishlist IDs mapped to Set<String>"
  - "CustomerProductDetailScreen: heart placed in title Row alongside product title (no separate TopAppBar on this screen)"
  - "WishlistViewModel.addItemToCart uses buildMinimalProduct to convert WishlistItem snapshot to Product for CartRepository"

patterns-established:
  - "Scale bounce pattern: Animatable + LaunchedEffect(booleanState) fires on every toggle — reusable WishlistHeartButton in core/components"
  - "Wishlist set pattern: observe items Flow, map to productId Set in ViewModel, expose via state for per-card O(1) lookup"

requirements-completed: [WISH-01, WISH-03, WISH-04, WISH-05]

# Metrics
duration: 8min
completed: 2026-02-20
---

# Phase 03 Plan 04: Wishlist UI Summary

**Wishlist screen (2-column grid, add-to-cart, multi-select, undo snackbar) and heart toggle with Animatable scale bounce on product cards and detail screen**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-20T17:50:42Z
- **Completed:** 2026-02-20T17:58:42Z
- **Tasks:** 2
- **Files modified:** 15

## Accomplishments

- CustomerWishlistScreen with LazyVerticalGrid (2-column), AnimatedContent empty/content crossfade, undo snackbar for removal, and "Add All to Cart" button
- WishlistItemCard shows product thumbnail, price, filled heart (remove), add-to-cart button, and unavailability overlays (deleted / out-of-stock)
- Long-press enters multi-select mode with "Add Selected to Cart" in top bar
- WishlistHeartButton reusable composable with Animatable scale bounce animation (DampingRatioLowBouncy -> MediumBouncy) on every toggle
- Heart toggle wired on all CustomerProductCard instances (browse and search) via wishlistedProductIds Set in CustomerHomeViewModel
- Heart toggle wired on CustomerProductDetailScreen in title row with isWishlisted Flow in CustomerProductDetailViewModel

## Task Commits

Each task was committed atomically:

1. **Task 1: Create WishlistViewModel, WishlistState, WishlistAction and CustomerWishlistScreen** - `4bd34fc` (feat)
2. **Task 2: Add heart toggle with scale bounce animation on product cards and detail screen** - `54e28e5` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistState.kt` - Wishlist state: items, loading, error, selectedItems, selectionMode, undoItem
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistAction.kt` - Sealed interface with all wishlist actions
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistViewModel.kt` - ViewModel: observes wishlist, handles add-to-cart (single/all/selected), remove with undo, multi-select
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerWishlistScreen.kt` - Wishlist screen with grid, empty state, snackbar, selection mode
- `app/src/main/java/com/wenubey/wenucommerce/core/components/WishlistHeartButton.kt` - Reusable heart button with scale bounce animation
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabScreen.kt` - Replaced WishlistPlaceholder with CustomerWishlistScreen
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt` - WishlistHeartButton added to CustomerProductCard action row
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeAction.kt` - Added OnToggleWishlist action
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeState.kt` - Added wishlistedProductIds Set<String>
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeViewModel.kt` - Injects WishlistRepository + AuthRepository, observes wishlist IDs, handles OnToggleWishlist
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailAction.kt` - Added ToggleWishlist action
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailState.kt` - Added isWishlisted: Boolean
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailViewModel.kt` - Injects WishlistRepository, observes isWishlisted Flow, handles ToggleWishlist
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt` - WishlistHeartButton in title row with ToggleWishlist action
- `app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt` - Registered WishlistViewModel with viewModelOf

## Decisions Made

- ProductStatus.ARCHIVED used for deleted wishlist items in buildMinimalProduct (no DELETED enum value exists in ProductStatus)
- Heart animation fires on every isWishlisted state change — no snackbar on toggle from browse/detail; animation is the only feedback (per plan decision)
- CustomerHomeViewModel injects both WishlistRepository and AuthRepository to map wishlist Flow to Set<String> for efficient per-card lookups
- Heart placed in title Row alongside product title on detail screen (no standalone TopAppBar to add actions to)
- WishlistViewModel.addItemToCart creates minimal Product from WishlistItem snapshot data for CartRepository.addToCart

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed ProductStatus.DELETED -> ProductStatus.ARCHIVED**
- **Found during:** Task 1 (WishlistViewModel compilation)
- **Issue:** Plan referenced `ProductStatus.DELETED` but ProductStatus enum only has DRAFT, PENDING_REVIEW, ACTIVE, SUSPENDED, ARCHIVED — no DELETED value
- **Fix:** Used `ProductStatus.ARCHIVED` as the closest semantic equivalent for items that are no longer available
- **Files modified:** `app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistViewModel.kt`
- **Verification:** `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- **Committed in:** 4bd34fc (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug — enum value mismatch)
**Impact on plan:** Minor correction required; semantically equivalent. No scope creep.

## Issues Encountered

None beyond the single auto-fixed deviation above.

## Next Phase Readiness

- Wishlist feature end-to-end complete: save, view, add-to-cart, remove with undo
- Heart toggle reactive via Room Flows on both browse and detail screens
- WishlistViewModel injectable via Koin, ready for any future phase needing wishlist operations
- Phase 03 all UI plans complete (Cart + Wishlist); ready for Phase 04 (checkout/payment) or Phase 05 (auth)

---
*Phase: 03-cart-wishlist*
*Completed: 2026-02-20*

## Self-Check: PASSED

- FOUND: app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistState.kt
- FOUND: app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistAction.kt
- FOUND: app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/WishlistViewModel.kt
- FOUND: app/src/main/java/com/wenubey/wenucommerce/customer/CustomerWishlistScreen.kt
- FOUND: app/src/main/java/com/wenubey/wenucommerce/core/components/WishlistHeartButton.kt
- FOUND: .planning/phases/03-cart-wishlist/03-04-SUMMARY.md
- FOUND: commit 4bd34fc (Task 1)
- FOUND: commit 54e28e5 (Task 2)
