# Phase 4: Checkout & Payments - Context

**Gathered:** 2026-02-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Customers complete a purchase end-to-end — from cart to payment to order confirmation — with payment logic fully server-side via Stripe. Includes checkout wizard, address selection/entry, Stripe PaymentSheet integration, order creation, and confirmation screen. Discounts/coupons are Phase 5. Full order tracking/detail is Phase 6.

</domain>

<decisions>
## Implementation Decisions

### Checkout flow structure
- 3-step wizard: Address -> Review -> Payment
- Simple dots progress indicator (three dots, current step highlighted, no labels)
- Free back-navigation: user can tap any previous step dot to revisit earlier steps
- Cart screen has a bottom sticky bar with subtotal and "Proceed to Checkout" button
- Proceed button is disabled when cart has out-of-stock items — user must fix first
- No coupon/discount UI until Phase 5
- No minimum order amount — any non-empty in-stock cart can checkout
- Login is already required app-wide; no guest checkout flow needed

### Stock validation
- Validate stock at checkout entry (client-side, immediate feedback)
- Validate again server-side when creating PaymentIntent (safety net)
- If server-side check fails during checkout, show warning on review step with affected items highlighted

### Review step
- Compact item summary: product name, quantity, line total — no images
- Shipping cost: single "Shipping" total line (sum of per-product shipping costs set by sellers)
- Breakdown: Subtotal + Shipping = Total

### Payment step
- Step 3 shows the total amount and a "Pay Now" button
- Tapping "Pay Now" launches Stripe PaymentSheet
- PaymentSheet handles all card entry — no card data passes through the app

### Address handling
- Addresses are saved to user profile (Firestore + Room) for reuse across checkouts
- When user has saved addresses: dropdown selector with address labels, plus "Add new" option
- When user has no saved addresses: "Add Address" navigates to a separate address form screen
- Required fields: full name, street line 1, street line 2 (optional), city, state/province, postal code, country

### Order confirmation
- Summary view: order ID, item count, total paid — minimal info
- Animated green checkmark on screen appearance
- Two action buttons: "Continue Shopping" (back to browsing) and "View Order"
- "View Order" navigates to a minimal order screen (order ID, status, items, total) — Phase 6 will enhance this into the full order detail

### Error handling
- Payment failure: inline error message on step 3 with "Try Again" button — user stays on payment step, cart preserved
- Back-navigation during checkout: confirmation dialog "Leave checkout? Your progress will be lost"
- Checkout requires connectivity — show "You need internet to complete checkout" if offline

### Claude's Discretion
- Exact wizard transition animations
- Dropdown component styling for address selector
- Address form validation rules and error messages
- Loading states during PaymentIntent creation
- Checkmark animation implementation (Lottie vs custom)
- Minimal order screen layout details

</decisions>

<specifics>
## Specific Ideas

- Shipping cost is per-product, set by sellers — the product model already has a shipping cost field. Checkout sums these into a single "Shipping" line.
- Review step is compact (no images) — keeps focus on totals and address confirmation before payment.
- Stripe PaymentSheet is the only payment method — no custom card forms.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-checkout-payments*
*Context gathered: 2026-02-21*
