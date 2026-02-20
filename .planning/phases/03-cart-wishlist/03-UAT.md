---
status: diagnosed
phase: 03-cart-wishlist
source: [03-01-SUMMARY.md, 03-02-SUMMARY.md, 03-03-SUMMARY.md, 03-04-SUMMARY.md]
started: 2026-02-20T19:00:00Z
updated: 2026-02-20T19:25:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Bottom Navigation Bar
expected: Bottom of the screen shows a Material3 NavigationBar with 4 tabs: Home, Cart, Wishlist, Profile. Each has an icon. Tapping each tab switches the displayed content.
result: pass

### 2. Add to Cart from Product Detail
expected: Open any product detail. You see a quantity stepper (defaults to 1) and an "Add to Cart" button. Tap "Add to Cart". Button changes to show "In Cart (x1)" as an outlined button. A snackbar appears saying "Added to cart" with a "View Cart" action.
result: issue
reported: "pass but I found issue on when I tap add to cart button 2 snackbar shown one for added to cart and the other one for saved locally will sync... snackbar also shown they overlap each other"
severity: minor

### 3. Cart Badge on Navigation
expected: After adding an item to cart, the Cart tab in the bottom NavigationBar shows a badge with the number of unique products in cart. Adding more distinct products increases the badge count. When cart is emptied, the badge disappears.
result: pass

### 4. Cart Screen Items and Subtotal
expected: Navigate to Cart tab. Each cart item shows product image, title, price, and a quantity stepper (+/-). A sticky bottom bar shows the subtotal (sum of price x quantity for all items) and a Checkout button.
result: issue
reported: "pass but when I increment or decrement quantity, you are offline banner shows and disappear"
severity: minor

### 5. Cart Quantity Stepper
expected: On the Cart screen, tap + on an item to increase quantity. Tap - to decrease. Subtotal in the bottom bar updates immediately. Decreasing to 0 removes the item (or minimum is 1 with separate remove).
result: issue
reported: "pass but same offline banner issue when changing quantity"
severity: minor

### 6. Swipe to Remove and Undo
expected: On the Cart screen, swipe a cart item from right to left. A red delete background is revealed. The item is removed. An undo snackbar appears. Tapping "Undo" restores the item to the cart.
result: pass

### 7. Cart Empty State
expected: Remove all items from cart. The cart list crossfades (AnimatedContent) to an empty state view (illustration or message indicating the cart is empty, possibly with a "Continue Shopping" button).
result: pass

### 8. Auth Gate on Add to Cart
expected: While logged out, open a product detail and tap "Add to Cart". An AlertDialog appears prompting you to log in. The item is NOT added to cart until authentication.
result: skipped
reason: No logout functionality exists yet — cannot test unauthenticated state

### 9. Heart Toggle on Product Cards (Browse)
expected: On the Home screen product cards, each card shows a heart icon. Tapping the heart fills it with a scale bounce animation (grows then settles). The product is added to the wishlist. Tapping again un-fills the heart and removes from wishlist.
result: pass

### 10. Heart Toggle on Product Detail
expected: On the product detail screen, next to the product title there is a heart icon. Tapping it toggles wishlisted state with the same scale bounce animation. Fills when wishlisted, outlines when not.
result: pass

### 11. Wishlist Screen
expected: Navigate to the Wishlist tab. If items have been wishlisted, they appear in a 2-column grid showing product thumbnail, price, a filled heart (to remove), and an add-to-cart button. If no items are wishlisted, an empty state is shown with a crossfade transition.
result: issue
reported: "pass but as I mentioned before when I click add to cart button offline banner show and snackbar show item saved locally"
severity: minor

### 12. Wishlist Add to Cart
expected: On the Wishlist screen, tap the add-to-cart button on a wishlist item. The item is added to the cart (cart badge updates). The item remains in the wishlist.
result: issue
reported: "pass but same offline banner and saved locally snackbar issue"
severity: minor

### 13. Wishlist Multi-Select
expected: On the Wishlist screen, long-press an item. Selection mode activates (item shows selected state). Tap additional items to select them. A top bar or button shows "Add Selected to Cart". Tapping it adds all selected items to cart.
result: pass

## Summary

total: 13
passed: 7
issues: 5
pending: 0
skipped: 1

## Gaps

- truth: "Add to Cart snackbar appears without overlap"
  status: failed
  reason: "User reported: pass but I found issue on when I tap add to cart button 2 snackbar shown one for added to cart and the other one for saved locally will sync... snackbar also shown they overlap each other"
  severity: minor
  test: 2
  root_cause: "SyncManager.emitOfflineWriteQueued() called unconditionally in CartRepositoryImpl.addToCart() (line 141) — emits SyncEvent.OfflineWriteQueued regardless of connectivity. MainActivity.kt (lines 84-88) observes this event and shows 'Saved locally' snackbar unconditionally, overlapping with the feature's own 'Added to cart' snackbar."
  artifacts:
    - path: "data/src/main/java/com/wenubey/data/repository/CartRepositoryImpl.kt"
      issue: "emitOfflineWriteQueued() called unconditionally at line 141"
    - path: "app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt"
      issue: "SyncEvent.OfflineWriteQueued handler at lines 84-88 shows snackbar without connectivity check"
    - path: "data/src/main/java/com/wenubey/data/local/SyncManager.kt"
      issue: "emitOfflineWriteQueued() at line 96 has no connectivity awareness"
  missing:
    - "Add connectivity check to SyncManager.emitOfflineWriteQueued() — only emit when device is actually offline"
    - "Or filter at MainActivity: only show 'Saved locally' snackbar when !isOnline"

- truth: "Cart operations do not trigger false offline banner"
  status: failed
  reason: "User reported: pass but when I increment or decrement quantity, you are offline banner shows and disappear"
  severity: minor
  test: 4
  root_cause: "PendingSyncViewModel.shouldShowBanner (line 76) shows banner when pending > 0 && pending > dismissed, even when online. Every cart write inserts a PendingOperationEntity, briefly spiking pendingCount before SyncWorker processes it."
  artifacts:
    - path: "app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncViewModel.kt"
      issue: "shouldShowBanner logic at line 76 triggers on any pending count increase even when online"
  missing:
    - "PendingSyncViewModel.shouldShowBanner should only show when device is offline (!online), not when online with pending items"

- truth: "Cart quantity changes do not trigger false offline banner"
  status: failed
  reason: "User reported: pass but same offline banner issue when changing quantity"
  severity: minor
  test: 5
  root_cause: "Same as test 4 — PendingSyncViewModel.shouldShowBanner triggers on pending count increase from updateQuantity queueCartOperation()"
  artifacts:
    - path: "app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncViewModel.kt"
      issue: "shouldShowBanner logic at line 76 triggers on any pending count increase even when online"
  missing:
    - "PendingSyncViewModel.shouldShowBanner should only show when device is offline"

- truth: "Wishlist add-to-cart does not trigger false offline banner or saved locally snackbar"
  status: failed
  reason: "User reported: pass but as I mentioned before when I click add to cart button offline banner show and snackbar show item saved locally"
  severity: minor
  test: 11
  root_cause: "Same root cause as test 2 and 4 — WishlistViewModel.addItemToCart calls CartRepository.addToCart which triggers both emitOfflineWriteQueued() (snackbar) and PendingOperation insert (banner)"
  artifacts:
    - path: "data/src/main/java/com/wenubey/data/repository/CartRepositoryImpl.kt"
      issue: "addToCart() emits OfflineWriteQueued unconditionally"
    - path: "app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncViewModel.kt"
      issue: "shouldShowBanner triggers on pending count increase when online"
  missing:
    - "Same fixes as tests 2 and 4"

- truth: "Wishlist add-to-cart does not trigger false offline banner or saved locally snackbar"
  status: failed
  reason: "User reported: pass but same offline banner and saved locally snackbar issue"
  severity: minor
  test: 12
  root_cause: "Same root cause as tests 2, 4, 11"
  artifacts:
    - path: "data/src/main/java/com/wenubey/data/repository/CartRepositoryImpl.kt"
      issue: "addToCart() emits OfflineWriteQueued unconditionally"
  missing:
    - "Same fixes as tests 2 and 4"
