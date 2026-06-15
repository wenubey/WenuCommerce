# Phase 6: Order Tracking & Management - Context

**Gathered:** 2026-06-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Customers can track every order from placement to delivery via a reverse-chronological order history with filter chips, and an order detail screen that visualizes the status timeline as a per-seller vertical stepper. Sellers can advance status forward only (PENDING → CONFIRMED → SHIPPED → DELIVERED) on their own sub-orders, attach an optional tracking number when shipping, and cancel pre-SHIPPED sub-orders (which issues a Stripe partial refund). Every status change triggers an FCM push to the customer via a Firestore trigger. Multi-seller carts are split into independent seller sub-orders at payment time, while remaining a single Stripe charge.

</domain>

<canonical_refs>
## Canonical References

Downstream agents MUST read these before planning/researching:

- `.planning/ROADMAP.md` — Phase 6 definition (lines 116–132), plan breakdown
- `.planning/REQUIREMENTS.md` — ORDR-01 through ORDR-10 (lines 52–61)
- `.planning/PROJECT.md` — offline-first architecture commitment
- `.planning/phases/04-checkout-payments/04-CONTEXT.md` — Stripe PaymentIntent flow, `createPaymentIntent` Cloud Function pattern
- `.planning/phases/05-discounts/05-CONTEXT.md` — discount + per-seller-coupon model, full-screen seller form pattern
- `.planning/phases/01-room-foundation/01-CONTEXT.md` — Room-first read pattern, schema export, Flow-based observation
- `.planning/codebase/ARCHITECTURE.md` — multi-module + MVVM + UDF rules
- `.planning/codebase/STACK.md` — Koin, Room, Firebase, Stripe versions
- `.planning/codebase/CONVENTIONS.md` — naming, package layout, ViewModel state pattern
- `domain/src/main/java/com/wenubey/domain/model/order/Order.kt` — existing Order model (needs `statusHistory`, `trackingNumber`, `sellerId` added; needs split into Order + SellerOrder)
- `domain/src/main/java/com/wenubey/domain/model/order/OrderStatus.kt` — enum already has all 5 states
- `functions/src/index.ts` — `createPaymentIntent` (lines 354–400) writes single `orders/{id}` doc today; needs to also write N `sellerOrders/{id}` children
- `app/src/main/java/com/wenubey/wenucommerce/notification/MessagingService.kt` — stub FCM service; needs typed routing (Phase 8 territory, but this phase delivers `onOrderStatusChange` trigger + deep-link payload)
- `CLAUDE.md` — autonomous test-driven mode; test-first non-negotiable

</canonical_refs>

<decisions>
## Implementation Decisions

### 1. Multi-seller order architecture — split into seller sub-orders

- One customer-facing parent `Order` doc + N child `SellerOrder` docs, one per seller represented in the cart.
- Firestore layout: `orders/{orderId}` (parent, holds customer-level data: shipping address, total, Stripe PaymentIntent id, list of `sellerOrderIds`); `sellerOrders/{sellerOrderId}` (each holds: `parentOrderId`, `sellerId`, that seller's items, subtotal, shippingShare, discountShare, status, `statusHistory[]`, `trackingNumber`).
- Created atomically at payment success by extending `createPaymentIntent` (or its post-success step) to fan out sub-orders.
- Single Stripe PaymentIntent for the whole cart (one charge to customer). **Money routing to individual sellers is v2 / Stripe Connect — out of scope.**
- Seller query: `sellerOrders where sellerId == request.auth.uid` — clean Firestore rule, no nested-array-contains gymnastics.
- Customer query: parent `orders where userId == uid`, then fetch each `sellerOrders` by id list.

### 2. Status timeline storage — embedded array on sub-order

- `SellerOrder.statusHistory: List<StatusEntry>` where `StatusEntry = { status: OrderStatus, timestamp: Timestamp, note: String?, trackingNumber: String? }`.
- Subcollection rejected — at most ~5 entries per sub-order, well within doc size limits, single read for timeline.
- The `trackingNumber` lives on the SHIPPED entry (and is also denormalized to top-level `SellerOrder.trackingNumber` for easy display/search).

### 3. Status transition authority — hybrid (rules + Firestore trigger)

- Seller writes status update directly to `sellerOrders/{id}` (works offline via Firestore SDK queue).
- **Firestore security rules enforce:**
  - `request.auth.uid == resource.data.sellerId`
  - New `status` ordinal >= existing ordinal (forward-only), OR new status is CANCELLED and existing is < SHIPPED.
  - `statusHistory` length increases by exactly 1, last entry's `status` equals new top-level `status`, last entry's `timestamp` == `request.time`.
  - Immutable fields (sellerId, parentOrderId, items, subtotal, shippingShare) cannot be mutated.
- **`onOrderStatusChange` Firestore trigger** (Cloud Function `onDocumentWritten` on `sellerOrders/{id}`) detects status changes and sends FCM to parent order's customer with deep-link payload `{ type: 'order_status', orderId, sellerOrderId, newStatus }`.
- Client UI hides invalid transitions as defense-in-depth, not source of truth.

### 4. Customer-facing list & detail UX

**Order history list:**
- Single reverse-chronological list (newest first).
- Filter chips at top: `All` (default) / `Active` (PENDING, CONFIRMED, SHIPPED) / `Delivered` / `Cancelled`.
- Row content: order ID (short hash), date, total, item count summary ("3 items from 2 sellers"), aggregate status badge.
- Aggregate status rule: if any sub-order is CANCELLED-but-others-active → show "Partially cancelled"; otherwise show the **minimum** (least-advanced) sub-order status. (Researcher to confirm exact mapping.)

**Order detail:**
- Header: customer info (shipping address, payment summary, parent total, savings line from Phase 5 if applicable).
- Body: per-seller section. Each section header shows seller name + sub-order's current status badge. Section is collapsed by default if multi-seller order (>1 sub-order); single-seller orders show the stepper inline without a collapsible header.
- Each section body: vertical stepper PENDING → CONFIRMED → SHIPPED → DELIVERED. Completed steps show check + timestamp; current step highlighted; future steps muted. Tracking number rendered inline at the SHIPPED step as a copy-to-clipboard chip.
- CANCELLED sub-order: stepper shown up to the cancellation point with a "Cancelled on {date}" marker; "Refund pending — will appear within 5–10 business days" footer.

**Sync behavior:**
- List + detail read from Room via Flow (single source of truth).
- Sync triggered: (a) on screen open, (b) on FCM "order updated" message receipt → enqueues a sync.
- Pull-to-refresh as fallback for sync stalls.
- Firestore listener NOT kept open while screen is visible (cost-conscious; FCM + open-time sync is sufficient).

### 5. Tracking number — free text v1

- Seller marks SHIPPED → optional text field for tracking number (skippable).
- Stored on `SellerOrder.trackingNumber` + duplicated on the SHIPPED `StatusEntry`.
- Customer side: rendered as copy-to-clipboard chip in the SHIPPED stepper step.
- **No carrier picker, no tappable URL in v1** — deferred.

### 6. Cancellation policy — seller-only + auto-refund via Stripe

- Only the seller can cancel; only allowed pre-SHIPPED.
- Cancel goes through a `cancelSellerOrder` Cloud Function callable (NOT a direct Firestore write — refund must be atomic with status change).
- Function:
  1. Validates auth (caller is the sub-order's seller).
  2. Validates current status is < SHIPPED.
  3. Computes refund amount = `subtotal + shippingShare - discountShare` (in cents) for this sub-order.
  4. Issues `stripe.refunds.create({ payment_intent, amount })` against the parent order's PaymentIntent.
  5. On Stripe success: sets `status=CANCELLED`, appends to `statusHistory`, records `refundId` + `refundedAmount` on the sub-order.
  6. Returns success/error to client.
- FCM trigger fires on the resulting CANCELLED write (same trigger as other status changes).
- Customer detail shows "Refund of $X.XX issued — will appear on your statement within 5–10 business days" once cancellation is confirmed.
- Customer self-cancel NOT supported in this phase.

### 7. SellerOrder is offline-cached, Order is too

- New Room entities: `OrderEntity` (parent), `SellerOrderEntity` (child), with a `@Relation` for one-to-many fetching.
- Sync workers fetch both for the current user (customer view) or the current seller (seller view).
- Tests use fake DAOs + in-memory state; no real Firestore in unit tests (per CLAUDE.md rules).

</decisions>

<specifics>
## Specific Ideas

- Aggregate status badge wording: "Pending", "Confirmed", "Shipped", "Delivered", "Cancelled", "Partially cancelled"
- Multi-seller order row in list: "3 items from 2 sellers • Shipped"
- Per-seller stepper section header: "[Seller logo] {Seller Name}  • [status chip]"
- Tracking number chip: monospace font, copy icon on right, toast "Copied" on tap
- Refund footer text: "Refund of $X.XX issued on {date}. Funds typically arrive within 5–10 business days."
- Aggregate status mapping recap (for researcher): CANCELLED-only-sub-orders → "Cancelled"; mix of CANCELLED + non-CANCELLED → "Partially cancelled"; all non-CANCELLED → min(status across sub-orders)

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Order` model (domain/model/order/Order.kt) — schema needs splitting; existing `discountAmount` / `discountCode` carry forward to the parent Order doc
- `OrderStatus` enum — already has all 5 states, no changes needed
- `createPaymentIntent` Cloud Function (functions/src/index.ts:280–402) — fan-out to sub-orders inserted between Stripe call and order doc write
- `MessagingService` (app/notification/MessagingService.kt) — needs type routing for `order_status` payload + deep-link, but channel/permission work is Phase 8 (this phase ships a single hard-coded `order_status_channel` to unblock)
- `PaymentRepository` callable-function pattern — `cancelSellerOrder` follows same pattern
- `SellerTabScreen` — already has Orders tab placeholder; this phase wires the destination
- Status-badge UI from Phase 5 Discounts (Active/Expired/Used up) — re-skin for order statuses
- Searchable list pattern from Phase 5 (seller product picker) — adaptable to seller orders list

### Established Patterns
- Cloud Function callable + Firestore rule layered enforcement (Phase 4/5)
- Room-first reads + Flow → ViewModel UiState (Phase 1)
- Atomic commits per plan; `feat(06-NN)` / `test(06-NN)` commit prefixes
- ViewModel: single `XxxUiState` `StateFlow` + event callbacks (UDF)

### Integration Points
- `createPaymentIntent`: after Stripe success, also fan out N `sellerOrders` docs in a Firestore batch with the order doc
- New Cloud Function `onOrderStatusChange` (Firestore `onDocumentWritten` trigger on `sellerOrders/{id}`) — sends FCM
- New Cloud Function callable `cancelSellerOrder` — Stripe partial refund + status update, atomic
- Firestore rules: add `sellerOrders` collection rules (seller can update status with the constraints above; customer can read where `parentOrderId` belongs to them)
- Room: new `OrderEntity`, `SellerOrderEntity`, DAOs, repository layer (`OrderRepository` interface in :domain, `OrderRepositoryImpl` in :data)
- Navigation: `CustomerOrdersRoute`, `CustomerOrderDetailRoute(orderId)`, `SellerOrdersRoute`, `SellerOrderDetailRoute(sellerOrderId)` — type-safe routes
- FCM deep-link handler: parses `order_status` notification payload → navigates to customer order detail
- DI: new Koin modules `orderDataModule`, `orderDomainModule`, `orderAppModule`

</code_context>

<deferred>
## Deferred Ideas

- **Stripe Connect / split payouts** — sellers receive their money directly. Whole-charge model in v1 keeps custody manual. (Future milestone.)
- **Carrier picker + tappable tracking URL** — Phase 6 ships free-text tracking; richer integration deferred.
- **Customer self-cancel pre-CONFIRMED** — common e-commerce expectation but adds policy + race-condition surface; deferred to v2.
- **In-app refund status timeline** — currently we display "issued + 5–10 business days." Pulling actual refund status from Stripe webhooks could surface "completed" later.
- **Returns / RMA flow** — post-DELIVERED returns are its own capability, not part of forward status flow.
- **Notification channels split & permission rationale** — Phase 8 territory; Phase 6 uses a single hard-coded channel to unblock testing.
- **Aggregate notifications for multi-seller orders** — debouncing "Seller A shipped" + "Seller B shipped" into a single push is a polish-pass concern.

</deferred>

---

*Phase: 06-order-tracking*
*Context gathered: 2026-06-15*
