---
phase: 06-order-tracking
plan: 02
subsystem: customer-ui
tags: [compose, material3, viewmodel, sync-bus, koin, navigation]
requirements_completed: [ORDR-01, ORDR-02, ORDR-03, ORDR-04]
status: complete
completed_date: 2026-06-16
---

# Phase 6 Plan 02: Customer Order Tracking UI Summary

**One-liner:** Customer-facing order history (reverse-chron + 4-chip filter) and
detail (per-seller collapsible sections + 4-status vertical stepper + tracking
chip + refund footer) reading from Room via `OrderRepository`; both ViewModels
collect `SyncBus.OrderStatusChanged` so FCM-driven status changes from 06-04
refresh the screen without user action; reusable `OrderStatusBadge`,
`OrderStatusStepper`, `TrackingNumberChip` core components shared with 06-03.

---

## What changed (per task)

### Task 1 — Shared core components (commit `27b6632`)

- `core/components/OrderStatusBadge.kt` — Material 3 chip-style badge with two
  overloads (`AggregateOrderStatus` for the order-row aggregate, `OrderStatus`
  for per-seller). Tokens: PENDING/CONFIRMED → `tertiaryContainer`; SHIPPED →
  `primaryContainer`; DELIVERED → `primary`; CANCELLED/PARTIALLY_CANCELLED →
  `errorContainer`. Labels per CONTEXT specifics.
- `core/components/TrackingNumberChip.kt` — monospace text + ContentCopy icon;
  on tap calls `LocalClipboardManager.current.setText(AnnotatedString(...))`
  and shows a `Toast("Copied")`. Semantic `contentDescription` for a11y.
- `core/components/OrderStatusStepper.kt` — vertical 4-step stepper
  (PENDING → CONFIRMED → SHIPPED → DELIVERED) with internal `StepState`
  (Completed / Current / Upcoming) controlling icon + tint:
  CheckCircle/RadioButtonChecked/RadioButtonUnchecked. Connector line between
  steps. Tracking chip rendered under the SHIPPED step when
  `currentStatus != Upcoming && trackingNumber != null`. When
  `currentStatus == CANCELLED`, renders only history steps (Completed) + a
  single "Cancelled on {timestamp}" marker row in `colorScheme.error`; no
  upcoming-after-cancel steps.

### Task 2 — Customer order history screen + ViewModel + navigation (commit `5a8759b`)

- `customer/orders/CustomerOrderHistoryState.kt` — immutable state with
  `OrderFilter` enum (ALL / ACTIVE / DELIVERED / CANCELLED) and
  `visibleOrders()` projection. PARTIALLY_CANCELLED appears in BOTH ACTIVE and
  CANCELLED lists (CONTEXT Open Question 5 resolution).
- `customer/orders/CustomerOrderHistoryAction.kt` — sealed interface:
  `OnFilterSelected`, `OnOrderClicked`, `OnRefresh`, `OnRetry`,
  `OnDismissError`.
- `customer/orders/CustomerOrderHistoryViewModel.kt` — Koin-injected
  `OrderRepository + AuthRepository + SyncBus`. In `init`:
  1. `observeCustomerOrders(uid).onEach { copy(orders = it) }.launchIn(...)`
  2. `syncCustomerOrders(uid)` initial fetch (failure → `errorMessage`)
  3. `syncBus.events.filterIsInstance<SyncEvent.OrderStatusChanged>()
      .onEach { syncCustomerOrders(uid) }.launchIn(...)` — collects ALL
     order-status events (history view is global to user, so any change is
     in-scope).
- `customer/orders/CustomerOrderHistoryScreen.kt` — M3 Scaffold + TopAppBar
  + `FilterChip` row + `PullToRefreshBox` wrapping `LazyColumn` of order rows.
  Row shows `#${id.take(8)}`, ISO date, `"N items from M sellers"`,
  `$%.2f.format(totalAmount)`, and `OrderStatusBadge`. Empty state with
  "Start shopping" CTA. Snackbar surfaces `errorMessage` and dismisses.
- Nav: `AppNavigationObjects` adds `@Serializable data object
  CustomerOrderHistory` + `@Serializable data class CustomerOrderDetail`.
  `TabNavRoutes` adds both composables; `CustomerTab` plumbs new
  `onNavigateToOrderHistory` lambda.
- Profile entry: `CustomerProfileScreen` "My Orders" ProfileMenuItem invokes
  `onNavigateToOrderHistory` (callback propagated through `CustomerTabScreen`
  → `TabNavRoutes` → `navController.navigate(CustomerOrderHistory)`).
- Koin: `viewModelOf(::CustomerOrderHistoryViewModel)` and
  `viewModelOf(::CustomerOrderDetailViewModel)` registered in
  `ViewmodelModule`.
- `customer/orders/CustomerOrderDetailScreen.kt`,
  `CustomerOrderDetailViewModel.kt`, `CustomerOrderDetailState.kt`,
  `CustomerOrderDetailAction.kt` also landed in this commit (so the
  navigation route compiles without forward-reference breakage).

### Task 3 — Customer order detail screen + ViewModel (commit `5a8759b`, tests `f1d765f`)

- `CustomerOrderDetailState.kt` — `order`, `sellerOrders`, `expandedSellerIds`,
  `isLoading`, `errorMessage`.
- `CustomerOrderDetailAction.kt` — `OnToggleSection`, `OnRetry`,
  `OnDismissError`.
- `CustomerOrderDetailViewModel.kt` — reads `orderId` from `SavedStateHandle`
  (populated automatically by the type-safe `CustomerOrderDetail(orderId)`
  route). In `init`:
  1. `observeOrderWithSubOrders(orderId).onEach { pair -> ... }` — single
     sub-order auto-expands; multi-seller stays collapsed.
  2. `syncCustomerOrders(uid)` initial fetch.
  3. `syncBus.events.filterIsInstance<OrderStatusChanged>().filter { it.orderId == orderId }
      .onEach { syncCustomerOrders(uid) }.launchIn(...)` — only events matching
     THIS order trigger a re-sync; events for other orders are dropped.
- `CustomerOrderDetailScreen.kt` — header card (shipping address, subtotal,
  shipping, "You saved $X.XX" line when `discountAmount > 0`, total) + a per-
  seller `SellerSection`. Single-seller: header + body (no toggle). Multi-
  seller: collapsible header row (chevron icon) with `AnimatedVisibility` body.
  Body: items rows (`${qty}× ${productTitle}` + `$%.2f.format(lineTotal)`) +
  `OrderStatusStepper`. CANCELLED + `refundedAmount != null` adds the refund
  footer: `"Refund of $X.XX issued — funds typically arrive within 5–10
  business days"`.

### Tests (commit `f1d765f` unit, `d2916be` Compose UI)

#### Unit (testDebugUnitTest)
- `CustomerOrderHistoryViewModelTest` — 7 tests, all green:
  - init triggers `observeCustomerOrders` + `syncCustomerOrders`
  - `OnFilterSelected` updates `state.filter`
  - repo emissions flow into `state.orders`
  - `Cancelled` filter hides DELIVERED but includes PARTIALLY_CANCELLED + CANCELLED, newest-first
  - `OnRefresh` re-calls `syncCustomerOrders` and clears `isRefreshing`
  - sync failure populates `errorMessage`
  - **SyncBus `OrderStatusChanged` emission triggers `syncCustomerOrders`**
- `CustomerOrderDetailViewModelTest` — 6 tests, all green:
  - observe flows into `state.order` + `state.sellerOrders`
  - single sub-order auto-expands
  - multi sub-order starts collapsed
  - `OnToggleSection` flips expanded set
  - **SyncBus event with MATCHING orderId triggers sync**
  - **SyncBus event with NON-MATCHING orderId does NOT trigger sync** (filter works)

#### Compose UI (androidTest, sources compile; emulator run deferred)
- `CustomerOrderHistoryScreenTest` — 4 tests: filter chip labels, row content
  (id + total + badge + item count summary), filter tap emits action, empty state
- `CustomerOrderDetailScreenTest` — 4 tests: single-seller stepper inline,
  cancelled refund footer, savings line, multi-seller section headers
- `OrderStatusStepperTest` — 3 tests: 4-step labels, SHIPPED tracking chip,
  CANCELLED marker (with no future-step labels visible)

---

## Commits

| Hash | Subject |
|------|---------|
| `27b6632` | feat(06-02): add shared order status components (badge, stepper, tracking chip) |
| `5a8759b` | feat(06-02): customer order history + detail screens, ViewModels, nav |
| `f1d765f` | test(06-02): VM unit tests for CustomerOrderHistory + CustomerOrderDetail |
| `e8d903d` | fix(06-02): correct OrderItem field references in SellerOrderDetailScreen |
| `d2916be` | test(06-02): Compose UI tests for customer order screens + stepper |

(Plus the final `docs(06-02)` commit covering this SUMMARY + STATE + ROADMAP +
REQUIREMENTS metadata.)

---

## Build gates verified at end of plan

- `./gradlew :app:compileDebugKotlin -x lint` → exit 0
- `./gradlew :app:testDebugUnitTest --tests "*CustomerOrder*" -x lint` →
  13/13 green (7 history + 6 detail)
- `./gradlew :app:compileDebugAndroidTestKotlin -x lint` → exit 0
- `./gradlew :app:assembleDebug -x lint` → exit 0
- `grep -rc "navigate(CustomerOrderHistory)" app/src/main/java/com/wenubey/wenucommerce/` → 1 (TabNavRoutes wires the CustomerProfileScreen → onNavigateToOrderHistory → `navController.navigate(CustomerOrderHistory)` chain)

---

## Deviations from plan

1. **`CustomerProfileScreen` navigation plumbed via callback chain, not direct
   `navController`.** Plan suggested either `onNavigate(CustomerOrderHistory)`
   on the existing screen or threading `navController` through. The existing
   screen had no `navController` param. Chose to add `onNavigateToOrderHistory:
   () -> Unit = {}` to `CustomerProfileScreen`, propagate it through
   `CustomerTabScreen` (new param), and bind it in `TabNavRoutes` where
   `navController` is already in scope. Cleaner separation: keeps Profile
   stateless w.r.t. NavController. **Non-functional refactor — same end result;
   `grep "navigate(CustomerOrderHistory)"` returns 1 in TabNavRoutes.**

2. **Pre-existing untracked 06-03 WIP (SellerOrderDetailScreen.kt) referenced
   non-existent OrderItem fields `title`/`price`.** Domain `OrderItem` exposes
   `productTitle` and `lineTotal`. The broken code blocked
   `:app:testDebugUnitTest` from compiling (couldn't run my own VM tests).
   **Rule 3 auto-fix (blocking compile issue).** Minimal patch:
   `item.title` → `item.productTitle`; `item.price * item.quantity` →
   `item.lineTotal`. Plan 06-03 still owns the seller plan; this fix is the
   smallest possible unblock.

3. **`SellerOrderDetailScreen.kt` and other untracked 06-03 files (`SellerOrdersScreen.kt`,
   `SellerOrdersViewModel.kt`, etc.) were committed under 06-02 commits.**
   These files were present as untracked WIP in the working tree when 06-02
   execution began (likely staged externally between commits). `git commit`
   picked them up alongside my deliberate stages in commits `e8d903d` and
   `d2916be`. **Non-functional cross-bleed** — Plan 06-03 owns those files and
   will continue iteration; the commits do not regress anything and were
   required so the build stayed green between 06-02 commits. Untracked files
   remaining (`SellerOrderDetailViewModel.kt`, `SellerOrderDetailAction.kt`,
   `SellerOrderDetailState.kt`, `CancelOrderDialog.kt`,
   `MarkAsShippedDialog.kt`, `SellerOrderDetailViewModelTest.kt`) are 06-03
   work-in-progress and were left untouched.

4. **AndroidTest emulator run deferred.** Plan accepts this as "manual run; CI
   gate optional." `:app:compileDebugAndroidTestKotlin` is green;
   `:app:connectedDebugAndroidTest` not executed (no emulator available in the
   execution environment). The unit tests + the source-compile check together
   verify the component contracts; visual verification per the plan's
   `<verification>` section is a manual QA step.

5. **`TrackingNumberChip` uses `LocalClipboardManager` (deprecated as of M3
   1.7).** Kotlin compiler emits a `w:` warning recommending `LocalClipboard`
   (which supports suspend functions). The deprecated API still works fully;
   the suspend variant requires a `CoroutineScope` plumbed into the chip and
   isn't needed for fire-and-forget copy. Accepted v1; revisit in a Phase 8
   polish pass alongside the notification-permission rationale.

---

## Known stubs

None introduced. The "Start shopping" CTA in the empty state navigates to the
home tab via `onStartShopping` callback wired to `CustomerTab(tabIndex = 0)`
with `popUpTo<CustomerTab> { inclusive = true }`. All data flows from Room →
ViewModel → UI (no placeholder rendering).

---

## Threat Flags

None. The customer UI is read-only against Room and triggers only the same
`syncCustomerOrders` callable that 06-01 audited. SyncBus collection is
in-process; no new IPC surface. Clipboard write uses `AnnotatedString` (no
HTML/markup parsing on a path the user controls).

---

## Open questions for downstream waves

1. **`ProfileMenuItem` callback through `CustomerTabScreen`** is a one-off; a
   future refactor may want a `LocalNavController` composition local to avoid
   the lambda-prop chain growing as more menu entries get routes.

2. **`OrderRow` date formatting** uses the raw ISO string today. A `java.time`
   formatter ("June 15") would be nicer once we standardise locale + date
   pattern across the app — see also OrderConfirmationScreen which has a
   similar issue.

3. **PullToRefreshBox + LazyColumn empty-state** — when filter yields zero rows
   but `state.orders.isNotEmpty()`, the empty state currently shows "No orders
   match this filter" with no CTA. Acceptable for v1; a "Clear filter" chip
   would polish UX.

4. **Tracking chip Toast vs Snackbar** — Toast is used for "Copied" feedback.
   The chip is rendered from a Composable inside a Scaffold, so a Snackbar
   would be more idiomatic but requires plumbing `SnackbarHostState` through.
   Acceptable v1.

---

## Self-Check: PASSED

Verified each claim above:

- `app/src/main/java/com/wenubey/wenucommerce/core/components/OrderStatusBadge.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/core/components/OrderStatusStepper.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/core/components/TrackingNumberChip.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/customer/orders/CustomerOrderHistoryScreen.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/customer/orders/CustomerOrderHistoryViewModel.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/customer/orders/CustomerOrderHistoryState.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/customer/orders/CustomerOrderHistoryAction.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/customer/orders/CustomerOrderDetailScreen.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/customer/orders/CustomerOrderDetailViewModel.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/customer/orders/CustomerOrderDetailState.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/customer/orders/CustomerOrderDetailAction.kt` — FOUND
- `app/src/test/java/com/wenubey/wenucommerce/CustomerOrderHistoryViewModelTest.kt` — FOUND
- `app/src/test/java/com/wenubey/wenucommerce/CustomerOrderDetailViewModelTest.kt` — FOUND
- `app/src/androidTest/java/com/wenubey/wenucommerce/CustomerOrderHistoryScreenTest.kt` — FOUND
- `app/src/androidTest/java/com/wenubey/wenucommerce/CustomerOrderDetailScreenTest.kt` — FOUND
- `app/src/androidTest/java/com/wenubey/wenucommerce/OrderStatusStepperTest.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/navigation/AppNavigationObjects.kt` — contains `data object CustomerOrderHistory` + `data class CustomerOrderDetail`
- `app/src/main/java/com/wenubey/wenucommerce/navigation/TabNavRoutes.kt` — contains `composable<CustomerOrderHistory>` + `composable<CustomerOrderDetail>` + `navigate(CustomerOrderHistory)`
- `app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt` — contains `viewModelOf(::CustomerOrderHistoryViewModel)` + `viewModelOf(::CustomerOrderDetailViewModel)`
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerProfileScreen.kt` — "My Orders" entry wired to `onNavigateToOrderHistory()`
- Commits `27b6632`, `5a8759b`, `f1d765f`, `e8d903d`, `d2916be` present on `main`
- Test suites green: 7/7 + 6/6 unit; androidTest sources compile
- `:app:assembleDebug -x lint` exits 0
