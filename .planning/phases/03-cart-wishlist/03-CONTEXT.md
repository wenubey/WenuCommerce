# Phase 3: Cart & Wishlist - Context

**Gathered:** 2026-02-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Customers can build a cart and wishlist that persist across app restarts and work offline, giving them full control over what they intend to buy. Cart requires login; wishlist works without login and syncs on login. Both are accessible via dedicated bottom navigation tabs. Checkout flow, payments, and order creation are Phase 4.

</domain>

<decisions>
## Implementation Decisions

### Cart screen layout
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

### Add-to-cart flow
- Quantity selector (stepper) on product detail screen before adding to cart
- After adding: snackbar with "View Cart" action + badge animation on nav tab
- If product already in cart: button transforms to "In Cart (x2)" with stepper controls on detail page
- Adding same product again increments quantity by selected amount
- Maximum quantity per item limited by available stock
- Cart requires login — tapping Add to Cart when not logged in prompts login

### Stock warnings & edge cases
- Out-of-stock items: row grayed out with red/orange "Out of Stock" banner overlay, quantity controls disabled
- Deleted products by seller: same treatment as out-of-stock, labeled "No longer available"
- Over-stock (cart qty > available): auto-reduce quantity to available stock with snackbar notice
- Out-of-stock items excluded from subtotal calculation
- Checkout button disabled until all out-of-stock/unavailable items are removed from cart
- Low stock indicator: "Only X left" shown when stock is 3 or fewer
- Product detail: Add to Cart button disabled with "Out of Stock" label when stock is 0

### Wishlist heart interaction
- Heart icon: outlined when not wishlisted, filled red when wishlisted
- Scale bounce animation on toggle
- On product cards (browse/search): heart in action row below the card (not overlaid on image)
- On product detail: heart icon in top app bar
- No snackbar when toggling wishlist from browse/detail — heart animation is the only feedback
- Unwishlisting from wishlist screen: instant removal with undo snackbar
- Wishlist works without login; stored locally, synced to Firestore on login
- Wishlist items stay permanently until user manually removes (not affected by purchase)

### Wishlist screen
- Product display: grid layout matching browse/search screen style
- "Add All to Cart" button for bulk action
- Long-press multi-select: select specific items, then "Add Selected to Cart"
- Unavailable/deleted products show inline warning (same treatment as cart)
- Wishlist is a dedicated bottom navigation tab (no badge count)

### Empty states
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

</decisions>

<specifics>
## Specific Ideas

- Cart badge shows unique product count, not total quantity — "2 different products" not "5 total items"
- When product is already in cart, the detail page button transforms to show current quantity with in-place stepper — no need to go to cart to adjust
- Wishlist doesn't need a badge — it's a "save for later" feature, not urgent like cart
- Both cart and wishlist get their own bottom nav tabs — first-class features
- Playful empty state messaging but no instructional hints — keep it clean
- Wishlist is the only feature that works pre-login; cart requires authentication

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-cart-wishlist*
*Context gathered: 2026-02-20*
