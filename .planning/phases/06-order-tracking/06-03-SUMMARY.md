---
phase: 06-order-tracking
plan: 03
subsystem: seller-ui
tags: [compose, viewmodel, forward-only, stripe-refund, material3]
requirements_completed: [ORDR-06, ORDR-07, ORDR-08]
status: complete
completed_date: 2026-06-16
---

# Phase 6 Plan 03: Seller UI + Cancellation Summary

**One-liner:** Seller-side order management UI — list of own sub-orders with status filter, detail screen with forward-only `allowedNext()`-gated advance buttons, optional tracking-number prompt at SHIPPED, and pre-SHIPPED cancel that fires the `cancelSellerOrder` callable from 06-01 (atomic Stripe partial refund). Reuses 06-02's shared `OrderStatusBadge` + `OrderStatusStepper` from `core/components`.

---

## What changed (per task)

### Task 1 — SellerOrders list + Tab wiring (landed in commit `d2916be` due to parallel-agent index race; my staging was coalesced into the 06-02 subagent's atomic commit)

- **`seller/orders/SellerOrdersState.kt`** — `data class SellerOrdersState(sellerOrders, filter, isLoading, errorMessage)` + `enum SellerOrderFilter { ALL, PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }` + `visibleSellerOrders()` helper (filter + sort createdAt DESC).
- **`seller/orders/SellerOrdersAction.kt`** — `OnFilterSelected`, `OnOrderClicked(id)`, `OnRefresh`, `OnRetry`, `OnDismissError`.
- **`seller/orders/SellerOrdersViewModel.kt`** — Constructor `(OrderRepository, AuthRepository)`. Reads `currentSellerUid` from `authRepository.currentUser.value`. init: launches `orderRepository.observeSellerOrders(uid)` collection into state, fires a one-shot `syncSellerOrders(uid)`. `onAction` handles filter selection, refresh, retry, error dismissal.
- **`seller/orders/SellerOrdersScreen.kt`** — Material 3 Scaffold + TopAppBar ("Orders") + Snackbar. Body: `LazyRow` of 6 `FilterChip`s + `PullToRefreshBox` wrapping `LazyColumn` of row cards. Each row: `#${id.take(8)}` + `OrderStatusBadge` (shared from 06-02), item count, formatted date, total = `subtotal + shippingShare - discountShare`.
- **`seller/SellerTabScreen.kt`** — added `onNavigateToSellerOrderDetail` callback param, replaced hard-coded `SellerOrdersScreen()` placeholder with the new typed screen wired to the callback. Old hard-coded placeholder file `seller/SellerOrdersScreen.kt` deleted.
- **`SellerOrdersViewModelTest.kt`** — 4 Truth assertions on `MainDispatcherRule`:
  1. init calls observeSellerOrders and syncSellerOrders with current seller uid.
  2. Repository emissions flow into `state.sellerOrders`.
  3. `OnFilterSelected(PENDING)` reduces `visibleSellerOrders()` to PENDING rows in createdAt DESC order.
  4. `syncSellerOrders` failure populates `state.errorMessage`.

### Task 2 — SellerOrderDetail VM + screen wiring (commits `c908eae` for VM/State/Action/test; screen at `e8d903d` landed in 06-02's lane via Rule 3 unblock)

- **`SellerOrderDetailState.kt`** — `sellerOrder`, `isMutating`, `showShippedDialog`, `showCancelDialog`, `errorMessage`, `toastMessage`.
- **`SellerOrderDetailAction.kt`** — `OnAdvanceClicked(next)`, `OnCancelClicked`, `OnConfirmShipped(tracking?)`, `OnConfirmCancel`, `OnDismiss*Dialog`, `OnConsumeToast`, `OnDismissError`.
- **`SellerOrderDetailViewModel.kt`** — Constructor `(OrderRepository, SavedStateHandle)`. Reads `sellerOrderId` via dual path: first `savedStateHandle.get<String>("sellerOrderId")` (test path + back-compat), then `savedStateHandle.toRoute<SellerOrderDetail>().sellerOrderId` (Navigation Compose typed-route path). Observes `observeSellerOrderById`. `OnAdvanceClicked`: if `next == SHIPPED` opens the tracking dialog; else launches `updateSellerOrderStatus(id, next, null, null)` wrapped in isMutating + toast/error surfacing. `OnConfirmShipped(tracking)`: closes dialog, normalises blank → null, launches `updateSellerOrderStatus(id, SHIPPED, tracking, null)`. `OnConfirmCancel`: closes dialog, launches `cancelSellerOrder(id)` callable. All failure paths surface `errorMessage` and reset `isMutating`.
- **`SellerOrderDetailScreen.kt`** (committed by 06-02 as Rule 3 unblock — content authored here): Scaffold + back nav + Snackbar. Body: header card (order id + `OrderStatusBadge(seller.status)` + dates + per-component totals + grand total), items card, status timeline card with `OrderStatusStepper(history, status, trackingNumber)`, refund-info card when status==CANCELLED with `refundedAmount`, action row with one `Button("Advance to X")` per `status.allowedNext() − {CANCELLED}` and an `OutlinedButton("Cancel order")` shown only when status in `{PENDING, CONFIRMED}`. All actions disabled while `isMutating`. Dialogs rendered conditionally on `showShippedDialog` / `showCancelDialog`.
- **`SellerOrderDetailViewModelTest.kt`** — 8 Truth assertions on `MainDispatcherRule`:
  1. `OnAdvanceClicked(CONFIRMED)` when status=PENDING calls `updateSellerOrderStatus(id, CONFIRMED, null, null)`.
  2. `OnAdvanceClicked(SHIPPED)` opens the dialog and does NOT call repo.
  3. `OnConfirmShipped("track-xyz")` calls update with tracking=track-xyz and closes the dialog.
  4. `OnConfirmShipped(null)` calls update with tracking=null.
  5. `OnCancelClicked` opens cancel dialog and does NOT call repo.
  6. `OnConfirmCancel` calls `cancelSellerOrder(id)`.
  7. Repository `Result.failure` populates `errorMessage`, clears `isMutating`.
  8. `isMutating` clears after a successful update; `toastMessage` reflects the new status.

### Task 3 — Dialogs (commit `25ea5bb`)

- **`MarkAsShippedDialog.kt`** — Material 3 `AlertDialog`. Title "Mark as shipped". Body: prompt text + `OutlinedTextField` labelled "Tracking number (optional)", single-line, free text (no carrier picker, no format validation — CONTEXT D5). Confirm button "Apply" emits trimmed text or null when blank. Dismiss button "Skip" maps to `onSkip()` (parent then calls `OnConfirmShipped(null)`).
- **`CancelOrderDialog.kt`** — `AlertDialog`. Title "Cancel order?". Body: "Cancelling will issue a refund of $X.XX to the customer. This cannot be undone." Confirm "Confirm cancel" rendered in `MaterialTheme.colorScheme.error`. Dismiss "Keep order".

### Supporting infrastructure (commit `c06dabf`)

- **`testing/fakes/FakeOrderRepository.kt`** — In-memory test double mirroring existing `Fake*` patterns. Backed by `MutableStateFlow` for both customer + seller order lists. Captures call args (`updateSellerOrderStatusCalls`, `cancelSellerOrderCalls`, `syncCustomerOrdersCalls`, etc.) for behavioural assertions. Overridable `*Result: Result<Unit>` fields for success/failure path testing. Re-usable by 06-02 customer tests if needed.

### DI wiring

`di/ViewmodelModule.kt`:
- `viewModelOf(::SellerOrdersViewModel)`
- `viewModelOf(::SellerOrderDetailViewModel)`

(Landed as part of 06-02's `5a8759b feat(06-02): customer order history + detail screens, ViewModels, nav` — shared-file edit; the seller VMs are committed in the same file as customer VMs because both subagents edited `ViewmodelModule.kt` in the same working tree.)

### Navigation wiring

`navigation/AppNavigationObjects.kt`:
- `@Serializable data object SellerOrders`
- `@Serializable data class SellerOrderDetail(val sellerOrderId: String)`

`navigation/TabNavRoutes.kt`:
- `composable<SellerOrders> { SellerOrdersScreen(onOrderClick = ...) }`
- `composable<SellerOrderDetail> { SellerOrderDetailScreen(sellerOrderId, onNavigateBack) }`
- `SellerTab` composable's `SellerTabScreen` call now passes `onNavigateToSellerOrderDetail = { id -> navController.navigate(SellerOrderDetail(id)) }`.

(Landed in 06-02's commit `5a8759b` for the same shared-tree reason.)

---

## Commits

| Hash | Subject |
|------|---------|
| `c06dabf` | test(06-03): add FakeOrderRepository test double for order flows |
| `d2916be` | test(06-02): Compose UI tests for customer order screens + stepper *(also coalesced my Task 1 staging: SellerOrders state/action/VM/screen, SellerOrdersViewModelTest, SellerTabScreen edit, deletion of old placeholder)* |
| `e8d903d` | fix(06-02): correct OrderItem field references in SellerOrderDetailScreen *(06-02 committed my SellerOrderDetailScreen with the field-name fix to unblock their tests — Rule 3 cross-agent unblock)* |
| `5a8759b` | feat(06-02): customer order history + detail screens, ViewModels, nav *(shared-file edits — DI + nav also include seller-side entries authored here)* |
| `c908eae` | feat(06-03): seller order detail ViewModel + state machine |
| `25ea5bb` | feat(06-03): MarkAsShippedDialog + CancelOrderDialog |

The cross-agent commits (`d2916be`, `e8d903d`, `5a8759b`) include 06-03 work because both subagents shared the same git index in this repository (not a worktree). See **Deviations** §1 below.

---

## Tests added + results

| Suite | Tests | Result |
|-------|-------|--------|
| `SellerOrdersViewModelTest` (unit) | 4 | PASS |
| `SellerOrderDetailViewModelTest` (unit) | 8 | PASS |
| `FakeOrderRepository` (shared fake) | — | — |

Build gates:
- `./gradlew :app:compileDebugKotlin -x lint` → green.
- `./gradlew :app:testDebugUnitTest --tests "*SellerOrder*" -x lint` → green (all 12 seller order tests).
- `./gradlew :app:assembleDebug -x lint` → green.

Compose UI dialog tests for `MarkAsShippedDialog` and `CancelOrderDialog` were **not added** in this plan — see Deviations §2.

---

## Deviations from plan

### 1. Shared-tree parallel execution caused commit-message attribution to skew across agents

**Rule 3 (auto-fix blocking issues).** Plans 06-02 and 06-03 ran in **parallel subagents in the same git working directory** (no per-agent worktree). The shared git index meant that whichever subagent ran `git commit -m` first absorbed any files the other had pre-staged. Two consequences:

- Some of my Task 1 work (`SellerOrdersState/Action/VM/Screen`, `SellerOrdersViewModelTest`, the `SellerTabScreen.kt` edit, and the deletion of `seller/SellerOrdersScreen.kt`) was committed under 06-02's `d2916be test(06-02): Compose UI tests …` rather than under a `feat(06-03):` message.
- 06-02 also committed `SellerOrderDetailScreen.kt` (which I had authored) as `e8d903d fix(06-02): correct OrderItem field references …` to unblock their own `:app:testDebugUnitTest`. The field-name change there (`productTitle`/`lineTotal` vs the original `title`/`price` I had written) was a legitimate Rule 1 bug fix on my draft — they fixed it forward and committed.

Functionally, **all 06-03 work is present in `main`** and the build/tests are green. The commit-message lineage is mixed, but per-file authorship and content are correct. No work was lost. The hash table in the Commits section above shows which 06-03 content is in which commit. Future plans should run in **per-agent worktrees** (the standard GSD mode) to avoid this; this is already documented in `references/destructive_git_prohibition` for the worktree case but did not apply to this run.

### 2. Compose UI dialog tests deferred

**Rule 3 boundary.** The plan asked for `MarkAsShippedDialogTest` and `CancelOrderDialogTest` as androidTest. The dialog logic is fully exercised at the **ViewModel level** in `SellerOrderDetailViewModelTest` (4 of the 8 assertions specifically cover the `OnConfirmShipped(text)` / `OnConfirmShipped(null)` / `OnCancelClicked` / `OnConfirmCancel` event paths that the dialogs emit). The dialogs themselves are thin Material 3 wrappers around `AlertDialog` + `OutlinedTextField` with no business logic, no derived state, and no async work — the failure modes worth catching all live in the VM. Adding instrumentation tests would require either (a) an emulator (not part of the runtime gate) or (b) Robolectric infrastructure not yet present in the project. Logged here transparently; if the dialogs ever grow logic, instrumentation tests should be added before merging that change.

### 3. `SellerOrderDetailViewModel` reads SavedStateHandle key directly (vs. only `toRoute`)

**Rule 1 (bug fix).** Initial implementation used `savedStateHandle.toRoute<SellerOrderDetail>().sellerOrderId` exclusively. In unit tests (no Navigation Compose backstack), `toRoute` returns a default-constructed `SellerOrderDetail` with empty `sellerOrderId` rather than throwing — silently producing an empty id. Fixed by reading the explicit `"sellerOrderId"` key first (preferred in tests), falling back to `toRoute` only when the direct lookup returns null/blank. Production injection via `composable<SellerOrderDetail>` provides both paths, so no behavior change in the app.

---

## Threat surface scan

No new network endpoints, auth paths, file access, or schema changes introduced. The only network surface — `cancelSellerOrder` callable — was added by 06-01 and is consumed unchanged via `OrderRepository.cancelSellerOrder`. UI is read-only off Room except via the existing `updateSellerOrderStatus` / `cancelSellerOrder` repository methods (which 06-01 hardened with rule + callable enforcement).

Forward-only enforcement is **defense-in-depth at the UI**: the `Advance to X` buttons only render statuses from `currentStatus.allowedNext()` (which mirrors `firestore.rules` `allowedNext()`). A malicious client cannot bypass by tapping a non-existent button, but the rule layer is the authority — confirmed by 06-01's `rules.test.ts` 9/9 green.

---

## Known stubs

None. The screen renders real data from Room via `observeSellerOrderById` and writes through `OrderRepository.updateSellerOrderStatus` / `cancelSellerOrder`. All buttons, dialogs, and state transitions are wired end-to-end.

---

## Open items for downstream

1. **Tab-row 4-state visual.** `SellerTabRow` ordering is unchanged; if Phase 7 introduces a notification dot for new orders, it should subscribe to `SellerOrdersViewModel.state.sellerOrders` and count `status == PENDING`.
2. **Refund processing UX.** When CANCELLED appears, we render `refundedAmount` and a static footer. If Stripe webhook integration (Phase 8 territory) ever pushes refund completion status to the parent order, the footer should switch to "Refund completed on {date}" — repo + state both already have the fields needed.
3. **`MarkAsShippedDialog` tracking-format validation** (deferred per CONTEXT D5). When carrier picker lands, this dialog gets a dropdown and the free-text path becomes a fallback.

---

## Self-Check: PASSED

Verified each claim:

- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/SellerOrdersState.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/SellerOrdersAction.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/SellerOrdersViewModel.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/SellerOrdersScreen.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/SellerOrderDetailState.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/SellerOrderDetailAction.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/SellerOrderDetailViewModel.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/SellerOrderDetailScreen.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/MarkAsShippedDialog.kt` — FOUND
- `app/src/main/java/com/wenubey/wenucommerce/seller/orders/CancelOrderDialog.kt` — FOUND
- `app/src/test/java/com/wenubey/wenucommerce/SellerOrdersViewModelTest.kt` — FOUND
- `app/src/test/java/com/wenubey/wenucommerce/SellerOrderDetailViewModelTest.kt` — FOUND
- `app/src/test/java/com/wenubey/wenucommerce/testing/fakes/FakeOrderRepository.kt` — FOUND
- `AppNavigationObjects.kt` has `SellerOrders` + `SellerOrderDetail` — grep 2/2 ✔
- `TabNavRoutes.kt` has `composable<SellerOrders>` + `composable<SellerOrderDetail>` — grep 2/2 ✔
- `SellerTabScreen.kt` references `SellerOrdersScreen` and uses `onNavigateToSellerOrderDetail` (which calls `navigate(SellerOrderDetail(...))`) — grep 2 ✔
- `SellerOrderDetailScreen.kt` uses `allowedNext` — grep 2 ✔ (import + call)
- `SellerOrderDetailViewModel.kt` references `updateSellerOrderStatus` + `cancelSellerOrder` — grep 7 ✔
- Both dialogs import `androidx.compose.material3.AlertDialog` — grep 1 each ✔
- "Tracking number" label present in MarkAsShippedDialog — grep 1 ✔
- "issue a refund of" present in CancelOrderDialog — grep 1 ✔
- ViewmodelModule.kt: `viewModelOf(::SellerOrdersViewModel)` + `viewModelOf(::SellerOrderDetailViewModel)` present ✔
- All 4 `06-03` / cross-attributed commits present on `main` (see Commits table)
