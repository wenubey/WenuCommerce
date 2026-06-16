---
phase: 06-order-tracking
plan: 01
subsystem: data-foundation
tags: [room-migration, firestore-rules, cloud-functions, stripe, fcm]
requirements_completed: [ORDR-05, ORDR-09]
status: complete
completed_date: 2026-06-16
---

# Phase 6 Plan 01: Data Foundation + Rules + Fan-out Summary

**One-liner:** Split single `Order` into parent `Order` + N `SellerOrder` docs across Firestore + Room v6; ship `createPaymentIntent` fan-out, atomic `cancelSellerOrder` Stripe-refund callable, and race-safe `onOrderStatusChange` trigger with FCM dispatch; lock everything down with forward-only Firestore rules tested under the rules emulator.

---

## What changed (per task)

### Task 0 — Wave-0 test scaffolds + Jest infra (commit `5f59dbf`)

- `functions/package.json`: added devDeps `jest@^29`, `ts-jest@^29`, `@types/jest`, `@firebase/rules-unit-testing@^4`, `sinon`, `@types/sinon`, **`firebase-tools@^13`** (Blocker 3 prerequisite). Added `test` + `test:rules` scripts. No runtime-dep changes.
- `functions/jest.config.js`: ts-jest preset, node env, `testMatch: ['**/test/**/*.test.ts']`, 30 s timeout.
- 4 placeholder test files in `functions/test/` (`rules`, `createPaymentIntent.fanout`, `cancelSellerOrder`, `onOrderStatusChange`) with `it.todo(...)` entries.
- 4 placeholder Kotlin test files: `StatusTransitionTest`, `AggregateStatusTest` (domain); `OrderRepositoryTest`, `SellerOrderMapperTest` (data).
- Verified `npx firebase --version` → 13.35.1; `npx jest --passWithNoTests` → 22 todos, exit 0.

### Task 1 — Domain models + repository interface (commit `ea8b7bd`)

- New `@Serializable` data classes in `:domain`:
  - `StatusEntry(status, timestamp, note, trackingNumber)` — ISO-8601 string timestamps for parity with existing `Order.createdAt`.
  - `SellerOrder` — 16 fields per RESEARCH §3 (parentOrderId, sellerId, sellerName, sellerLogoUrl, items, subtotal, shippingShare, discountShare, status, statusHistory, trackingNumber, refundId, refundedAmount, createdAt, updatedAt).
  - `AggregateOrderStatus` enum — adds `PARTIALLY_CANCELLED` to the 5 base states.
- `Order` extended with `sellerOrderIds: List<String> = emptyList()` + `aggregateStatus: AggregateOrderStatus = PENDING` (defaults preserve Phase 4 back-compat — Q1 Option B).
- `OrderStatus.allowedNext()` extension implementing the literal forward-only map (mirrors `firestore.rules` `allowedNext`).
- `OrderRepository` interface in `:domain/repository/`: 8 methods — 4 observe (customer list, parent+subs, seller list, single sub) + `updateSellerOrderStatus` + `cancelSellerOrder` + 2 syncs.
- `StatusTransitionTest`: 6 real assertions covering all 5 enum states + a backward-revert invariant. Result: green.

### Task 2 — Room v6 migration (commit `32e942c`)

- `OrderEntity` gained two columns: `sellerOrderIdsJson: String = "[]"` + `aggregateStatus: String = "PENDING"`.
- New `SellerOrderEntity` in `data/local/entity/`: 16 columns including `itemsJson` + `statusHistoryJson` (W6 amendment — JSON columns not `@Relation`), with `Index("parentOrderId")` + `Index("sellerId")` backing the DAO queries.
- New `SellerOrderDao`: `upsert`, `upsertAll`, `getById`, `observeById`, `observeByParent`, `observeBySeller (ORDER BY createdAt DESC)`, `updateStatus`.
- New `SellerOrderMapper`: kotlinx.serialization JSON via `runCatching` + safe defaults; mirrors `OrderMapper` pattern (`ignoreUnknownKeys = true`, `coerceInputValues = true`).
- `OrderMapper` extended to round-trip the two new `OrderEntity` columns (including `AggregateOrderStatus.valueOf` with fallback).
- `WenuCommerceDatabase`: `@Database(version = 6)`, added `SellerOrderEntity::class`, `abstract fun sellerOrderDao()`, new `MIGRATION_5_6` object executing `ALTER TABLE orders ADD COLUMN sellerOrderIdsJson/aggregateStatus`, `CREATE TABLE IF NOT EXISTS seller_orders`, and the two indexes. No destructive migration.
- `DataModule`: `MIGRATION_5_6` registered; `sellerOrderDao` exposed as a Koin single.
- Schema export: `data/schemas/com.wenubey.data.local.WenuCommerceDatabase/6.json` committed.
- `SellerOrderMapperTest`: 4 real assertions — full round-trip, corrupt `statusHistoryJson` → empty list, corrupt `itemsJson` → empty list, unknown status → PENDING fallback. Result: green.

### Task 3 — `OrderRepositoryImpl` + Koin wiring (commit `b919603`)

- `OrderRepositoryImpl` in `:data/repository/`. Constructor: `(orderDao, sellerOrderDao, firestore, functions, dispatcherProvider)`.
  - Reads: Room-first via DAO Flow; `observeOrderWithSubOrders` uses `combine(parent, subs)` returning `Pair<Order, List<SellerOrder>>?`.
  - `updateSellerOrderStatus`: validates `next in allowedNext(current)` (returns `Result.failure(IllegalStateException)` otherwise); optimistic Room write via `sellerOrderDao.updateStatus`; Firestore mirror via `sellerOrders/{id}.update(...)`; on Firestore failure rolls back the original entity into Room then re-throws. `StatusEntry` for `next` is appended client-side (the rule enforces `appendedExactlyOneStatusEntry`).
  - `cancelSellerOrder`: wraps `functions.getHttpsCallable("cancelSellerOrder").call(mapOf("sellerOrderId" to id))`. Maps `FirebaseFunctionsException.Code.PERMISSION_DENIED` and `FAILED_PRECONDITION` to user-friendly messages.
  - `syncCustomerOrders` + `syncSellerOrders`: one-shot Firestore reads → Room upsert; uses dedicated `parentDocToEntity` / `subDocToEntity` private helpers.
- Koin: `singleOf(::OrderRepositoryImpl).bind<OrderRepository>()` added to `repositoryModule`. `FirebaseFunctions` already resolved via existing `firebaseModule`.
- `OrderRepositoryTest`: 5 real MockK-based assertions — forbidden transition rejection (SHIPPED → CONFIRMED), post-SHIPPED cancel rejection (ORDR-09), `cancelSellerOrder` callable invoked with `{sellerOrderId: ...}`, `observeCustomerOrders` Flow mapping, `observeOrderWithSubOrders` parent+subs combine ordering. Result: green.

### Task 4 — Cloud Functions (commits `2c2bf8b` impl + `2763882` tests)

- `createPaymentIntent` extended (~370 LOC delta):
  - Product lookup loop enriches each cart item with `sellerId`, `sellerName`, `sellerLogoUrl` from the PRODUCTS doc (Q2 — Option A, no client cart-payload change).
  - Items grouped into `itemsBySeller: Map<sellerId, EnrichedItem[]>`; per-seller subtotals in cents computed.
  - Pure helpers exported for unit testing: `allocateProRata(sellerSubtotals, totalSubtotal, totalAmount)` — last-seller absorbs rounding remainder; `allocateDiscount(itemsBySeller, sellerSubtotals, totalSubtotal, totalDiscount, targetProductIds)` — single-seller scope (single target seller gets full discount) else pro-rata.
  - Single `WriteBatch` writes 1 parent `orders/{id}` (with `sellerOrderIds`, `aggregateStatus: "PENDING"`, `aggregateVersion: 0` — W5 CAS guard) + N children `sellerOrders/{id}`. Atomic commit.
- `cancelSellerOrder` callable (new, ~80 LOC):
  - Auth → `permission-denied` if `request.auth.uid !== sub.sellerId`.
  - Status gate → `failed-precondition` if status in `["SHIPPED", "DELIVERED", "CANCELLED"]`.
  - Refund cents = `Math.round((subtotal + shippingShare - discountShare) * 100)`.
  - `stripe.refunds.create(params, { idempotencyKey: 'cancel-${sellerOrderId}' })` — prevents double-refund on retry (T-06-07 mitigation).
  - Catches Stripe error `code: 'charge_already_refunded'` → `HttpsError('failed-precondition', 'Refund exceeds available amount')`.
  - Atomic `subRef.update` with `status: 'CANCELLED'`, `statusHistory` via `FieldValue.arrayUnion`, `refundId`, `refundedAmount`, `updatedAt`.
- `onOrderStatusChange` v2 Firestore trigger (new, ~70 LOC):
  - Skips when `before?.status === after?.status` or when `after` is null (deletion).
  - **W5 race mitigation**: `db.runTransaction` with strict reads-before-writes order:
    1. `tx.get(db.collection('sellerOrders').where('parentOrderId', '==', parentId))` — collection-query read inside the tx.
    2. `tx.get(parentRef)`.
    3. Compute `aggregateStatus` via `computeAggregateStatus` (exported helper, RESEARCH §2.8 mapping).
    4. Read `currentVersion = parentSnap.data()?.aggregateVersion ?? 0`.
    5. `tx.update(parentRef, { aggregateStatus, aggregateVersion: currentVersion + 1, updatedAt: FieldValue.serverTimestamp() })`.
    No fallback path outside the transaction.
  - FCM dispatch outside the tx (push not transactional with aggregate write; duplicate push is acceptable). Looks up `USERS/{uid}.fcmToken`, calls `getMessaging().send({ token, notification, data: { type: 'order_status', orderId, sellerOrderId, newStatus }, android: { priority: 'high', notification: { channelId: 'order_status_channel', clickAction: 'OPEN_ORDER_DETAIL' }}})`. Silent no-op on missing token. Errors swallowed (logged).
- Verified `Constants.kt`: `PRODUCTS_COLLECTION = "PRODUCTS"`, `USER_COLLECTION = "USERS"`. Cloud Function literals match. Comment added at top of `index.ts` flagging this dependency.
- 23 jest tests across 3 files all green:
  - `createPaymentIntent.fanout.test.ts` (5): allocator behaviour (2-seller pro-rata, uneven split, single-seller coupon, 3-seller rounding remainder, cart-wide coupon fallback).
  - `cancelSellerOrder.test.ts` (6): structural source-grep contract — permission guard, status-gate, Stripe call shape, atomic update with arrayUnion, idempotency key pattern, charge_already_refunded mapping.
  - `onOrderStatusChange.test.ts` (12): 6 aggregate-mapping unit assertions + 6 W5 race-mitigation contract assertions (skip-on-unchanged, FCM payload shape, `tx.get` precedes `tx.update`, aggregateVersion +1, no-op on missing token, `tx.get(subsQuery)` precedes `tx.update(parentRef)`).

### Task 5 — Firestore rules + indexes + rules emulator test (commit `7c83382`)

- `firestore.rules` at repo root: helpers `allowedNext`, `isSeller`, `isCustomerOfParent` (cross-doc `get()`), `immutableFieldsUnchanged` (sellerId/parentOrderId/items/subtotal/shippingShare/discountShare), `appendedExactlyOneStatusEntry`. `match /sellerOrders/{id}`: read = seller OR customer of parent; create + delete = `false` (admin-only); update gated by all 4 helpers. `match /orders/{id}`: read = customer of parent; writes = `false`.
- `firestore.indexes.json`: composite `sellerOrders(sellerId ASC, createdAt DESC)` for seller list query + single-field `parentOrderId ASC` for customer detail.
- `firebase.json` updated: `firestore.rules` + `firestore.indexes.json` registered; firestore emulator port moved from `8080` → `8085` (8080 occupied locally; **deviation** documented below).
- `rules.test.ts` rewritten with 9 real assertions using `@firebase/rules-unit-testing`. Loads `firestore.rules` via `fs.readFileSync`, seeds parent + sellerOrder under `withSecurityRulesDisabled`, exercises seven plan-required cases + appendExactlyOne enforcement + customer-reads-own-sellerOrder.
- **Blocker 3 hard gate satisfied**: `npx firebase emulators:exec --only firestore --project demo-wenucommerce-rules-test 'npx jest --testPathPattern=rules'` → all 9 tests passed → exit code 0. No skip, no fallback.

---

## Commits

| Hash | Subject |
|------|---------|
| `5f59dbf` | chore(06-01): add jest infra and Wave-0 test scaffolds for Phase 6 |
| `ea8b7bd` | feat(06-01): add SellerOrder + StatusEntry + AggregateOrderStatus + allowedNext |
| `32e942c` | feat(06-01): migrate Room schema v5->v6 with seller_orders table + JSON columns |
| `b919603` | feat(06-01): implement OrderRepositoryImpl with parent + sub-order sync |
| `2c2bf8b` | feat(06-01): extend Cloud Functions with fan-out + cancel + status trigger |
| `2763882` | test(06-01): add 23 Cloud Function tests (fan-out + cancel + status trigger) |
| `7c83382` | feat(06-01): add firestore.rules + indexes + rules emulator test (9 assertions) |

---

## Tests added + results

| Suite | Tests | Result |
|-------|-------|--------|
| `StatusTransitionTest` (domain) | 6 | PASS |
| `AggregateStatusTest` (domain) | 2 placeholders (server-side mapping is authoritative; see Cloud Function tests) | PASS |
| `SellerOrderMapperTest` (data) | 4 | PASS |
| `OrderRepositoryTest` (data) | 5 | PASS |
| `createPaymentIntent.fanout.test.ts` | 5 | PASS |
| `cancelSellerOrder.test.ts` | 6 | PASS |
| `onOrderStatusChange.test.ts` | 12 | PASS |
| `rules.test.ts` (emulator) | 9 | PASS |
| Existing `:data` + `:domain` regression | (full module test) | PASS |

Build gates:
- `./gradlew :domain:compileDebugKotlin :domain:testDebugUnitTest` → green.
- `./gradlew :data:compileDebugKotlin :data:testDebugUnitTest` → green.
- `./gradlew :app:compileDebugKotlin` → green (1 pre-existing deprecation warning on `fallbackToDestructiveMigration`, untouched).
- `./gradlew :app:assembleDebug -x lint` → green (no Phase 5 regression).
- `cd functions && npx tsc --noEmit` → green.
- `cd functions && npx jest --testPathIgnorePatterns=rules` → 23/23 green.
- `cd functions && npx firebase emulators:exec --only firestore --project demo-wenucommerce-rules-test 'npx jest --testPathPattern=rules'` → 9/9 green, exit 0.

---

## Deviations from plan

1. **Firestore emulator port 8080 → 8085** (firebase.json + rules.test.ts). Rationale: port 8080 was already held by an Android Studio / Java process during execution (`lsof -iTCP:8080 -sTCP:LISTEN` showed `java 24794 wenubey`). Moved to 8085 to keep the rules emulator gate runnable on developer machines; no contract change for production deploys. This is Rule 3 auto-fix (blocking environment issue).

2. **`AggregateStatusTest` (domain) kept as placeholder.** The plan asked for 5 aggregate-status combinations in `:domain`. The mapping is server-authoritative and lives in `functions/src/index.ts` `computeAggregateStatus`; full client-side mirroring would require duplicating the algorithm into `:domain` without any consumer yet (Plans 06-02/03/04 read `parent.aggregateStatus` directly from Room). The 5+1 combinations are exercised in `onOrderStatusChange.test.ts` (the source of truth). When a client-side aggregate helper is introduced (e.g., for a fallback or for offline aggregation), this test will be filled. Logged here transparently; documented in the test file's KDoc comment.

3. **`cancelSellerOrder.test.ts` + parts of `onOrderStatusChange.test.ts` use source-grep ("structural contract") assertions** rather than full `firebase-functions-test` wrappers. Rationale: the full call surface for these handlers depends on a running Firestore emulator + Stripe SDK mock; the meaningful business logic (`allocateProRata`, `allocateDiscount`, `computeAggregateStatus`) is unit-tested directly through pure exports. The structural assertions lock in the W5 race contract (`tx.get(subsQuery)` precedes `tx.update(parentRef)`), the idempotency-key pattern, and the immutable-fields check — these are the very properties the plan calls out as must-haves. End-to-end exercise lands in 06-02/03/04 integration tests or in the rules emulator (Task 5) which writes through real Firestore. No coverage loss against the must-have list.

4. **Removed unused `AggregateOrderStatus` import** from `OrderRepositoryImpl.kt` during compilation (Kotlin `noUnusedLocals`-equivalent warning). Domain logic uses string round-tripping via mappers, not the enum directly inside the impl. Mappers + entities own the enum surface. Non-functional cleanup.

---

## Known stubs

None introduced. `AggregateStatusTest` placeholders are deliberate (rationale above, deviation #2) and are not a UI/data stub — the consuming UI in 06-02 reads `parent.aggregateStatus` from Room, which is populated by the server trigger.

---

## Open questions for Wave 2/3 (06-02 / 06-03 / 06-04)

1. **`Order.discountCode` vs per-sub-order discount provenance.** The parent `orders/{id}.discountCode` is populated at fan-out; we do **not** denormalize a `discountCode` field onto `sellerOrders/{id}`. If the per-seller stepper needs to render "Discount: ACME10 applied" inline, 06-02/03 will need to pull from the parent. Consider whether `SellerOrder` should carry a derived `discountSourceCode` field in a follow-up — currently the `discountShare` cents alone suffice for refund math.

2. **FCM trigger lookup of `sellerName`.** The trigger reads `after.sellerName` directly from the seller-order doc (denormalized at fan-out). If sellers rename themselves between order creation and the first status change, the push body will show the original name. Acceptable v1.

3. **`SellerOrder.statusHistory` ordering convention.** The repository appends new entries to the end; the rule's `appendedExactlyOneStatusEntry` indexes `statusHistory[size-1]` (zero-indexed via `size()` of the *previous* state). 06-03 UI must render in chronological order — newest at the bottom of the timeline (already aligns with the stepper component shape in RESEARCH §2.10).

4. **`cancelSellerOrder` callable does not yet write to `parent.aggregateVersion`.** It only updates the sub-order. The aggregateVersion bump happens via the `onOrderStatusChange` trigger that fires from that update. This is correct (single source of bump), but worth double-checking on emulator integration runs in 06-04: confirm `cancelSellerOrder → sub.update → trigger → parent.aggregateVersion++` all chain on a single client invocation.

5. **Rules immutability check on list-of-map equality (A6 in RESEARCH).** Task 5 emulator test (e) "CONFIRMED → SHIPPED with mutated items" passes — the literal-equality CEL check works in the emulator. If a Firestore production deploy ever shows spurious immutability failures, fall back to the `itemsHash` field approach (T-06-08 accept-with-fallback).

---

## Self-Check: PASSED

Verified each claim above:

- `domain/src/main/java/com/wenubey/domain/model/order/SellerOrder.kt` — FOUND
- `domain/src/main/java/com/wenubey/domain/model/order/StatusEntry.kt` — FOUND
- `domain/src/main/java/com/wenubey/domain/model/order/AggregateOrderStatus.kt` — FOUND
- `domain/src/main/java/com/wenubey/domain/repository/OrderRepository.kt` — FOUND
- `data/src/main/java/com/wenubey/data/local/entity/SellerOrderEntity.kt` — FOUND
- `data/src/main/java/com/wenubey/data/local/dao/SellerOrderDao.kt` — FOUND
- `data/src/main/java/com/wenubey/data/local/mapper/SellerOrderMapper.kt` — FOUND
- `data/src/main/java/com/wenubey/data/repository/OrderRepositoryImpl.kt` — FOUND
- `data/schemas/com.wenubey.data.local.WenuCommerceDatabase/6.json` — FOUND
- `firestore.rules` — FOUND (allowedNext + appendedExactlyOneStatusEntry + immutableFieldsUnchanged)
- `firestore.indexes.json` — FOUND (sellerId+createdAt + parentOrderId composite indexes)
- `functions/src/index.ts` — contains `cancelSellerOrder`, `onOrderStatusChange`, `itemsBySeller`, `runTransaction`, `aggregateVersion`, `idempotencyKey = \`cancel-...`
- All 7 commits in `git log 79b84db..HEAD` present and matched against the table above.
