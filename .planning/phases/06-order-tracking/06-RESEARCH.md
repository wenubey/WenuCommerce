# Phase 6: Order Tracking & Management — Research

**Researched:** 2026-06-15
**Domain:** Multi-seller order lifecycle, Firestore security-rule state machines, Stripe partial refunds, FCM-driven sync + deep-linking, Room one-to-many caching
**Confidence:** HIGH on existing-repo patterns and Firestore/Stripe contracts; MEDIUM on the "right" Room shape (justified below); LOW only on the Material 3 stepper component (none official — must build).

---

## 1. Executive Summary

- **Locked architecture is sound and implementable as written.** Parent `Order` + N `SellerOrder` Firestore docs, single Stripe PaymentIntent, status-rule + trigger split, FCM-driven sync — all match the existing codebase idioms (`getHttpsCallable` callable functions, Room-first reads via Flow, Firestore writes for offline support). No alternative architecture is being proposed.
- **The hardest concrete change is the fan-out in `createPaymentIntent`** (`functions/src/index.ts:354-392`). It currently writes ONE `orders/{id}` doc with all items. It must instead: (a) `db.getAll(...productRefs)` to look up `sellerId` per cart item (the cart payload has no sellerId today — see `PaymentRepositoryImpl.createPaymentIntent:39-46`); (b) group items by sellerId; (c) compute per-seller subtotal + proportional shipping/discount allocation; (d) write parent + N children in one `WriteBatch`.
- **The "forward-only" Firestore rule is expressible without enum ordinals** by encoding the allowed transition map directly in the rule (a small CEL-friendly literal map of `"PENDING" -> ["CONFIRMED", "CANCELLED"]` etc.). Storing `statusOrdinal` parallel-field is unnecessary complexity.
- **Aggregate status MUST be denormalized server-side onto the parent `Order` doc** by the same `onOrderStatusChange` trigger. Computing client-side requires the customer list to JOIN N sub-orders per row in Room, which is expensive and contradicts the "list = single flat list of `OrderEntity`" requirement in ORDR-02.
- **Storing `statusHistory` and `items` as JSON columns on `SellerOrderEntity` is the right choice** — matches the established `OrderEntity.itemsJson` / `shippingAddressJson` pattern (data/local/entity/OrderEntity.kt:16-17), no `@Relation` needed. The bounded size (≤ 5 status entries, ≤ ~20 items per sub-order) makes JSON columns lossless and fast.

**Primary recommendation:** Implement the four plans exactly as scoped in ROADMAP. Wave order: 06-01 (data foundation + rules + fan-out) → 06-02 (customer UI) + 06-03 (seller UI) in parallel → 06-04 (FCM trigger + deep-link wiring).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|---|---|---|---|
| Order fan-out at payment success | Cloud Function (`createPaymentIntent`) | — | Server-authoritative; sellerId lookup needs admin SDK |
| Status transition authorization | Firestore Security Rules | Cloud Function (`cancelSellerOrder`) | Direct rule for forward-step (offline-friendly); callable for cancel-with-refund (atomic) |
| Aggregate status computation | Cloud Function (`onOrderStatusChange` trigger) | — | Denormalized to parent `orders/{id}.aggregateStatus` for cheap list rendering |
| Push notification dispatch | Cloud Function (`onOrderStatusChange` trigger) | — | Uses FCM Admin SDK + customer's `users/{uid}.fcmToken` lookup |
| Stripe partial refund | Cloud Function (`cancelSellerOrder`) | — | Requires server-side Stripe secret + atomic Firestore update |
| Order list / detail reads | Room (Flow) | Firestore (sync only) | Single source of truth per ARCHITECTURE.md; no live Firestore listener |
| Sync trigger | FCM message receipt | Pull-to-refresh + screen open | Per CONTEXT.md decision 4 — no persistent listener |
| Deep-link routing | `MessagingService.onMessageReceived` → `MainActivity` Intent extras → `NavController.navigate` | — | Single-Activity pattern already in `MainActivity` |
| Tracking number storage | Firestore field on `SellerOrder` + duplicated on SHIPPED `StatusEntry` | Room mirror | Free text, no carrier integration in v1 |
| Cancellation eligibility check | Cloud Function + UI gating | Firestore rules (block direct write of CANCELLED post-SHIPPED) | Defense-in-depth |

---

## 2. Per-Area Findings

### 2.1 Firestore security rules — forward-only state machine + append-only array

**Key Firestore rules semantics** (verified against Firebase docs and Codelab patterns):
- `resource.data.*` = the document **before** the write (existing).
- `request.resource.data.*` = the document **after** the proposed write.
- Lists support `.size()`, indexing (`[i]`), and `.hasAll()`. `request.time` is available in rules.
- CEL `in` operator works on maps and lists: `request.resource.data.status in ['CONFIRMED','CANCELLED']`.
- **No enum ordinal type** — use literal allowed-transition maps.

**Canonical pattern for forward-only + append-only `statusHistory`** (recommended for `sellerOrders/{id}`):

```javascript
// firestore.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Allowed forward transitions. CANCELLED only from pre-SHIPPED.
    function allowedNext(current) {
      return current == 'PENDING'   ? ['CONFIRMED', 'CANCELLED']
           : current == 'CONFIRMED' ? ['SHIPPED', 'CANCELLED']
           : current == 'SHIPPED'   ? ['DELIVERED']
           : [];
    }

    function isSeller(sellerOrder) {
      return request.auth != null
          && request.auth.uid == sellerOrder.sellerId;
    }

    function isCustomerOfParent(sellerOrder) {
      return request.auth != null
          && get(/databases/$(database)/documents/orders/$(sellerOrder.parentOrderId))
               .data.userId == request.auth.uid;
    }

    function immutableFieldsUnchanged() {
      return request.resource.data.sellerId      == resource.data.sellerId
          && request.resource.data.parentOrderId == resource.data.parentOrderId
          && request.resource.data.items         == resource.data.items
          && request.resource.data.subtotal      == resource.data.subtotal
          && request.resource.data.shippingShare == resource.data.shippingShare
          && request.resource.data.discountShare == resource.data.discountShare;
    }

    function appendedExactlyOneStatusEntry() {
      return request.resource.data.statusHistory.size()
           == resource.data.statusHistory.size() + 1
          && request.resource.data.statusHistory[resource.data.statusHistory.size()].status
           == request.resource.data.status;
    }

    match /sellerOrders/{sellerOrderId} {
      allow read: if isSeller(resource.data) || isCustomerOfParent(resource.data);

      // Create blocked — only Cloud Function (admin SDK bypasses rules) creates these.
      allow create: if false;

      // Status update: seller-only, forward-only, append-only history,
      // immutable structural fields.
      allow update: if isSeller(resource.data)
                    && request.resource.data.status in allowedNext(resource.data.status)
                    && immutableFieldsUnchanged()
                    && appendedExactlyOneStatusEntry();

      allow delete: if false;
    }

    match /orders/{orderId} {
      allow read: if request.auth != null
                   && resource.data.userId == request.auth.uid;
      allow create, update, delete: if false; // server-only
    }
  }
}
```

**Notes:**
- `get()` from inside a rule is a billed read (1 per evaluation) but is the only way to check the parent `userId` for customer reads. Cache via `getAfter()` not needed here.
- The list-equality check `request.resource.data.items == resource.data.items` works for list-of-map equality in CEL.
- Tracking number updates (when status moves to SHIPPED) are allowed because `trackingNumber` is **not** in the immutable list — it can move freely.
- **CANCELLED from SHIPPED is explicitly blocked** by the `allowedNext` map (SHIPPED → DELIVERED only).
- Sources verified: Firebase Security Rules language reference (officially: `firebase.google.com/docs/firestore/security/rules-conditions`) plus existing patterns from Codelabs `firebase.google.com/codelabs/firestore-android`. `[CITED: firebase.google.com/docs/firestore/security/rules-conditions]`

**Important:** Storing `statusOrdinal: int` as a parallel field is **NOT recommended** — adds two write fields, breaks the immutable-fields check (need a special exception), and the literal-map approach is clearer. `[VERIFIED: hand-tested locally with emulator pattern]`

---

### 2.2 Stripe partial refund on a single PaymentIntent

**API**: `stripe.refunds.create({ payment_intent, amount, metadata, reason })`.
- `amount` in cents; **omit** for full refund of remaining capturable. Specify for partial.
- Multiple partial refunds on one PaymentIntent are supported up to the captured total. Stripe tracks remaining-refundable internally.
- Idempotency: pass `{ idempotencyKey: 'cancel-sellerOrder-' + sellerOrderId }` as second arg to `stripe.refunds.create(params, { idempotencyKey })`. Critical here — if the Firestore write succeeds but Stripe call retries, we'd double-refund.
- Edge cases:
  - **PaymentIntent not yet captured** (`requires_capture`): refund still works but is reflected as a cancellation/void on the PI. Our flow auto-captures so `succeeded` is the expected state by the time cancel-window is open.
  - **Over-refund**: Stripe returns `400` with `code: 'charge_already_refunded'` if total refunded would exceed captured. We surface as `HttpsError("failed-precondition", "Refund exceeds available amount")`.
  - **PaymentIntent of zero / Stripe-minimum**: if a single sub-order is below 50¢, the cancel-refund flow must still work — refund whatever's left, do not error on "below minimum" (the minimum applies to charges, not refunds). `[CITED: stripe.com/docs/api/refunds/create]`

**Recommended `cancelSellerOrder` skeleton** (extends `functions/src/index.ts` after line 402):

```typescript
export const cancelSellerOrder = onCall(
  { secrets: [stripeSecretKey] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required");
    const { sellerOrderId } = request.data as { sellerOrderId: string };
    if (!sellerOrderId) throw new HttpsError("invalid-argument", "sellerOrderId required");

    const db = admin.firestore();
    const subRef = db.collection("sellerOrders").doc(sellerOrderId);

    // Read sub-order
    const subSnap = await subRef.get();
    if (!subSnap.exists) throw new HttpsError("not-found", "Sub-order not found");
    const sub = subSnap.data()!;

    if (sub.sellerId !== request.auth.uid)
      throw new HttpsError("permission-denied", "Only the seller can cancel");
    if (["SHIPPED", "DELIVERED", "CANCELLED"].includes(sub.status))
      throw new HttpsError("failed-precondition", "Cannot cancel post-shipping");

    // Read parent for PaymentIntent
    const parentSnap = await db.collection("orders").doc(sub.parentOrderId).get();
    const pi = parentSnap.data()!.stripePaymentIntentId as string;

    const refundCents = Math.round(
      ((sub.subtotal ?? 0) + (sub.shippingShare ?? 0) - (sub.discountShare ?? 0)) * 100
    );

    const stripe = new Stripe(stripeSecretKey.value(), { apiVersion: "2025-02-24.acacia" });
    const refund = await stripe.refunds.create(
      { payment_intent: pi, amount: refundCents, metadata: { sellerOrderId } },
      { idempotencyKey: `cancel-${sellerOrderId}` }
    );

    // Atomic Firestore update — admin SDK bypasses rules.
    const now = admin.firestore.FieldValue.serverTimestamp();
    await subRef.update({
      status: "CANCELLED",
      statusHistory: admin.firestore.FieldValue.arrayUnion({
        status: "CANCELLED",
        timestamp: admin.firestore.Timestamp.now(),
        note: "Cancelled by seller",
        trackingNumber: null,
      }),
      refundId: refund.id,
      refundedAmount: refundCents / 100,
      updatedAt: now,
    });

    return { success: true, refundId: refund.id, refundedCents: refundCents };
  },
);
```

`[VERIFIED: matches existing callable signature pattern in functions/src/index.ts:148-192,196-221]`

---

### 2.3 FCM v1 from `onDocumentWritten` trigger

**Trigger setup** (`firebase-functions/v2/firestore`):

```typescript
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getMessaging } from "firebase-admin/messaging";

export const onOrderStatusChange = onDocumentWritten(
  "sellerOrders/{sellerOrderId}",
  async (event) => {
    const before = event.data?.before.data();
    const after  = event.data?.after.data();
    if (!after) return;  // delete — ignored, we never delete
    if (before && before.status === after.status) return; // not a status change

    // Look up parent for customer uid + push token
    const db = admin.firestore();
    const parent = await db.collection("orders").doc(after.parentOrderId).get();
    const customerUid = parent.data()?.userId as string;
    if (!customerUid) return;

    const userSnap = await db.collection("USERS").doc(customerUid).get(); // see note below
    const fcmToken = userSnap.data()?.fcmToken as string | undefined;

    // Compute & write parent.aggregateStatus (see §2.8)
    await recomputeAggregate(parent.ref, after.parentOrderId);

    if (!fcmToken) return;
    await getMessaging().send({
      token: fcmToken,
      // DATA-ONLY message — gives us control over display + tap behavior on foreground/background.
      // For background-display we also include `notification` so the system tray renders it.
      notification: {
        title: titleFor(after.status),
        body: `Your order from ${after.sellerName ?? "the seller"} is ${after.status.toLowerCase()}.`,
      },
      data: {
        type: "order_status",
        orderId: after.parentOrderId,
        sellerOrderId: event.params.sellerOrderId,
        newStatus: after.status,
      },
      android: {
        priority: "high",
        notification: { channelId: "order_status_channel", clickAction: "OPEN_ORDER_DETAIL" },
      },
    });
  },
);
```

**Notes:**
- **Collection name for users:** `data/util/Constants.kt` should be checked but the existing `FirestoreRepositoryImpl:100` writes to `USER_COLLECTION` (constant) which is `"USERS"` in the existing repo. Confirmed pattern. `[VERIFIED: data/src/main/java/com/wenubey/data/repository/FirestoreRepositoryImpl.kt:97-111]`
- **Token shape:** `users/{uid}.fcmToken` is a **single string**, NOT an array. (`Device.kt:12` and `FirestoreRepositoryImpl:102`.) For multi-device this is a known limitation but is out of scope for Phase 6.
- `getMessaging().send(message)` is the **FCM v1 API** via Admin SDK. The legacy `send(token, payload)` overload is deprecated. `[CITED: firebase.google.com/docs/cloud-messaging/send-message#node.js]`
- `notification` field at the top level causes Android system tray display in background. The `data` payload arrives in `MessagingService.onMessageReceived` only when the app is in foreground OR when only the data block is sent. Including BOTH gives us both behaviors — but `onMessageReceived` will **not** fire when app is in background (system tray handles display). Tap-routing for background notifications must therefore happen via the launch Intent extras path (handled by FCM SDK auto-populating `intent.extras` with the data block). `[CITED: firebase.google.com/docs/cloud-messaging/android/receive#handling_messages]`

---

### 2.4 Android deep-link from FCM tap to a Compose screen

**The repo is single-Activity** (`MainActivity` + `RootNavigationGraph`) and uses **type-safe Navigation with `@Serializable` route classes** (`navigation/AppNavigationObjects.kt`). The existing `MessagingService.showNotification()` (lines 53-69) already builds a launch Intent via `packageManager.getLaunchIntentForPackage(...)` and pushes extras — this is the correct pattern, just needs new extras.

**Recommended approach** — extend the existing pattern:

1. **In `MessagingService.onMessageReceived(message: RemoteMessage)`** (foreground case):
   - Read `message.data["type"]`. If `"order_status"`, read `orderId` and `sellerOrderId`.
   - Build a launch Intent with extras: `EXTRA_NAV_TARGET = "order_detail"`, `EXTRA_ORDER_ID = orderId`.
   - Build `NotificationCompat.Builder(this, "order_status_channel")` — channel ID is hardcoded for now (Phase 8 promotes to typed channels).
2. **Background case** (notification tap when app is killed/background): FCM SDK auto-includes the data payload in `MainActivity.intent.extras`. No code on the service side needed.
3. **In `MainActivity.onCreate` and `onNewIntent`** (need to override the latter):
   - Read `intent.extras?.getString(EXTRA_NAV_TARGET)`.
   - If `"order_detail"`, capture `orderId`, then once `NavController` is ready, `navController.navigate(OrderDetail(orderId = orderId))` (`OrderDetail` route already exists in `AppNavigationObjects.kt:98`).
   - **Important**: `MainActivity` must declare `android:launchMode="singleTop"` in `AndroidManifest.xml` so re-launching from a notification while the activity exists fires `onNewIntent` rather than recreating. Verify current launch mode and add if missing.
4. **PendingIntent flags**: `FLAG_IMMUTABLE` is required on Android 12+. Existing code already uses `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` (line 68) — keep.

**Compose-side wiring** — pattern that works inside `MainActivity`'s `setContent { ... }`:

```kotlin
val navController = rememberNavController()
val deepLinkOrderId by remember {
  mutableStateOf(intent.getStringExtra(EXTRA_ORDER_ID))
}
LaunchedEffect(deepLinkOrderId) {
  deepLinkOrderId?.let { id ->
    navController.navigate(OrderDetail(id))
    intent.removeExtra(EXTRA_ORDER_ID)   // prevent re-navigation on recompose
  }
}
```

`[CITED: developer.android.com/develop/ui/compose/navigation; PendingIntent flag policy: developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability]`

---

### 2.5 Room one-to-many: JSON columns vs `@Relation`

**Recommendation: JSON columns. Two entities only: `OrderEntity` (parent, with `sellerOrderIdsJson`) and `SellerOrderEntity` (child, with `itemsJson` and `statusHistoryJson`). No `@Relation`, no junction tables.**

**Why JSON columns:**
1. **Matches established pattern.** `OrderEntity.itemsJson` (line 17) and `OrderEntity.shippingAddressJson` (line 16) already exist and have passed code review. Mappers (`OrderMapper.kt:25-30`) use `kotlinx.serialization` with `runCatching` fallback. Re-using this pattern is zero new infra.
2. **Bounded sizes.** `statusHistory.size ≤ 5` (PENDING → CONFIRMED → SHIPPED → DELIVERED + maybe CANCELLED). `items.size` typically < 20. JSON encoding is microseconds.
3. **`@Relation` requires either:**
   - A `@Transaction` query method on the DAO returning a wrapper class (more boilerplate), OR
   - Separate DAO calls and manual stitching.
   For a list screen that needs the parent + a tiny child list, the JSON read is 1 row vs the relation's 1+N rows. JSON wins.
4. **No filtering by inner-list fields.** We don't query "orders where statusHistory[i].status == X" — the parent has a denormalized `aggregateStatus` field. Relation tables justify themselves when you need indexed joins, which we don't.

**Trade-off accepted:** Cannot use Room migrations to ALTER schema of statusHistory entries. If we add a new field to `StatusEntry`, the JSON deserializer must use `ignoreUnknownKeys = true` (already configured — `OrderMapper.kt:11-14`).

**Migration v5 → v6** (Phase 6):
- ALTER TABLE `orders`: ADD COLUMN `sellerOrderIdsJson TEXT NOT NULL DEFAULT '[]'`, `aggregateStatus TEXT NOT NULL DEFAULT 'PENDING'`.
- CREATE TABLE `seller_orders` (id PK, parentOrderId, sellerId, sellerName, sellerLogoUrl, subtotal, shippingShare, discountShare, status, trackingNumber, refundId, refundedAmount, itemsJson, statusHistoryJson, createdAt, updatedAt).
- Add index `CREATE INDEX idx_seller_orders_parent ON seller_orders(parentOrderId)`.
- Add index `CREATE INDEX idx_seller_orders_seller ON seller_orders(sellerId)` (for seller-list query).

---

### 2.6 Multi-seller fan-out in `createPaymentIntent`

**Today** (`functions/src/index.ts:354-392`): one Stripe call, one `orders/{id}` doc with all items inlined. No per-seller anything.

**Required change:**

1. **Cart payload lacks `sellerId`.** `CartItem` (`domain/model/CartItem.kt`) has no sellerId; `PaymentRepositoryImpl:39-46` sends only `productId, productTitle, quantity, price`. **Two options:**
   - **(A) Lookup in Cloud Function** — for each cartItem, the function already reads the product doc (line 278-282) to validate stock and shipping cost. Add `sellerId` to the destructure: `const sellerId = productData?.sellerId ?? ""`. **No client change needed.** **Recommended.**
   - (B) Add sellerId to the cart payload from client. More change surface; rejected.

2. **Group + allocate**:
   ```typescript
   // After the existing loop builds enriched items with sellerId:
   const itemsBySeller = new Map<string, EnrichedItem[]>();
   for (const it of enrichedItems) {
     const list = itemsBySeller.get(it.sellerId) ?? [];
     list.push(it);
     itemsBySeller.set(it.sellerId, list);
   }

   // Total shipping is computed today as a single number. Allocate it pro-rata
   // by per-seller subtotal — simplest defensible rule.
   const totalSubtotalCents = subtotalCents;
   const totalShippingCents = shippingCents;
   const totalDiscountCents = discountCents;
   ```

3. **Discount allocation rule** (CONTEXT.md says single-seller coupon → all to that seller; cart-wide → proportional):
   ```typescript
   function allocateDiscountForSeller(
     sellerId: string,
     sellerSubtotal: number,
     totalSubtotal: number,
     couponTargetProductIds: string[],
     couponDoc: any,
     totalDiscount: number,
   ): number {
     // Coupon scoped to specific products: discount applies only to sub-orders containing them.
     // For simplicity v1: if any of the seller's items appear in targetProductIds, the
     // discount allocation is proportional to that seller's eligible-subtotal share.
     // For cart-wide coupons: pro-rata by sellerSubtotal / totalSubtotal.
     // Last seller gets rounding remainder to ensure sum == totalDiscount.
   }
   ```
   Round-on-last-seller is critical to avoid 1¢ drift between parent total and sum-of-children. Tested pattern.

4. **Atomic write** — use a single `WriteBatch`:
   ```typescript
   const batch = db.batch();
   const orderRef = db.collection("orders").doc(orderId);
   const sellerOrderIds: string[] = [];
   for (const [sellerId, items] of itemsBySeller) {
     const subId = db.collection("sellerOrders").doc().id;
     sellerOrderIds.push(subId);
     batch.set(db.collection("sellerOrders").doc(subId), {
       parentOrderId: orderId,
       sellerId,
       sellerName: items[0].sellerName, // denormalized
       items, // array of { productId, productTitle, quantity, snapshotPrice, lineTotal }
       subtotal: /* sellerSubtotalCents */ / 100,
       shippingShare: /* alloc */ / 100,
       discountShare: /* alloc */ / 100,
       status: "PENDING",
       statusHistory: [{ status: "PENDING", timestamp: admin.firestore.Timestamp.now(), note: null, trackingNumber: null }],
       trackingNumber: null,
       refundId: null,
       refundedAmount: 0,
       createdAt: admin.firestore.FieldValue.serverTimestamp(),
       updatedAt: admin.firestore.FieldValue.serverTimestamp(),
     });
   }
   batch.set(orderRef, { /* existing parent fields */, sellerOrderIds, aggregateStatus: "PENDING" });
   await batch.commit();
   ```
   `WriteBatch.set/update` are all-or-nothing — perfect for fan-out atomicity. `[CITED: firebase.google.com/docs/firestore/manage-data/transactions#batched-writes]`

5. **`sellerName` denormalization:** the seller's `name` and `logoUrl` should be copied to `sellerOrders/{id}` at creation. Customers shouldn't need a separate `get(/sellers/{sellerId})` per row. Lookup once during fan-out from the product's `sellerName` field already in `Product.kt:13`.

---

### 2.7 Forward-only enum comparison without ordinals

**Confirmed**: literal map in the rule (`allowedNext` function in §2.1) is the cleanest path. **No need to add `statusOrdinal: int`** — it duplicates state and breaks the immutable-fields check unless explicitly exempted.

---

### 2.8 Aggregate status — server-side denormalization

**Decision: compute server-side in `onOrderStatusChange`, write to `orders/{id}.aggregateStatus`.**

Reasoning:
- ORDR-02 requires the list row to show "status badge." If computed client-side, every list render must JOIN with `sellerOrders` — either via a `@Relation` (rejected per §2.5) or by an in-memory map per row. Both contradict the "fast list" goal.
- The trigger already fires on every status change. Adding a `recomputeAggregate(parentRef, parentId)` step is free.
- Customer-side Room sync: parent `orders/{id}.aggregateStatus` is mirrored to `OrderEntity.aggregateStatus`. List view shows just this column.

**Computation** (TypeScript):
```typescript
async function recomputeAggregate(parentRef: FirebaseFirestore.DocumentReference, parentId: string) {
  const subs = await admin.firestore()
    .collection("sellerOrders")
    .where("parentOrderId", "==", parentId)
    .get();
  const statuses = subs.docs.map(d => d.data().status as string);

  const order = ["PENDING","CONFIRMED","SHIPPED","DELIVERED"];
  const hasCancelled = statuses.includes("CANCELLED");
  const nonCancelled = statuses.filter(s => s !== "CANCELLED");

  let agg: string;
  if (nonCancelled.length === 0) agg = "CANCELLED";
  else if (hasCancelled) agg = "PARTIALLY_CANCELLED";
  else agg = nonCancelled.reduce((min, s) =>
    order.indexOf(s) < order.indexOf(min) ? s : min, "DELIVERED");

  await parentRef.update({ aggregateStatus: agg, updatedAt: admin.firestore.FieldValue.serverTimestamp() });
}
```

Add `PARTIALLY_CANCELLED` to a new enum `AggregateOrderStatus` in `:domain` (separate from `OrderStatus` so the per-seller stepper still uses the 5-state enum).

---

### 2.9 Testing patterns — what's in this repo + what to add

**Existing patterns observed:**
- **Domain unit tests**: `domain/src/test/.../DiscountCalculationTest.kt` — pure JUnit, no Android. Pattern: parameterized tables.
- **Data layer tests**:
  - `data/src/test/.../DiscountRepositoryTest.kt` — fake Firestore via MockK.
  - `data/src/androidTest/.../PaymentRepositoryImplEmulatorTest.kt` — Firebase emulator-suite for integration tests.
- **App layer tests**: `app/src/test/.../CheckoutViewModelDiscountTest.kt` — `StandardTestDispatcher` + `MainDispatcherRule` + Turbine + MockK for ViewModel state machine assertions.
- **Compose UI tests**: Wave 4 backfill commits show ~56 tests across 18 screens (see recent commits `9a8f77a`, `0bb541e`). Pattern: `createAndroidComposeRule<ComponentActivity>()`, semantic node finders, no `Thread.sleep`.

**For Phase 6 specifically:**

1. **Firestore Security Rules tests** — use `@firebase/rules-unit-testing` (Node, run from `functions/test/`). Pattern from Firebase docs:
   ```javascript
   const { initializeTestEnvironment, assertSucceeds, assertFails } = require("@firebase/rules-unit-testing");
   // setup env loaded from firestore.rules
   it("seller can advance PENDING → CONFIRMED", async () => {
     const ctx = env.authenticatedContext("seller-1");
     await assertSucceeds(ctx.firestore().doc("sellerOrders/abc").update({...}));
   });
   it("seller cannot revert SHIPPED → CONFIRMED", async () => {
     await assertFails(ctx.firestore().doc("sellerOrders/abc").update({...}));
   });
   ```
   Spin up emulator with `firebase emulators:exec --only firestore "npm test"`. `[CITED: firebase.google.com/docs/rules/unit-tests]`

2. **Cloud Function tests for `cancelSellerOrder` and `onOrderStatusChange`** — use `firebase-functions-test` (already in `functions/package.json:devDependencies`). Mock Stripe via `jest.mock('stripe')` or sinon. Test the trigger via `wrapV2(myFunction)` and synthesize a `DocumentSnapshot` change. `[CITED: firebase.google.com/docs/functions/unit-testing]`

3. **Repository tests** — fake DAOs (in-memory implementations of `OrderDao`, `SellerOrderDao`), mocked `FirebaseFunctions`. Mirror `DiscountRepositoryTest` shape.

4. **ViewModel tests** — `StandardTestDispatcher`, Turbine for `StateFlow` assertions, fake `OrderRepository`. Mirror `CheckoutViewModelDiscountTest` shape.

5. **Compose UI tests** — for each new screen (4 of them: `CustomerOrderHistoryScreen`, `CustomerOrderDetailScreen`, `SellerOrdersScreen`, plus a stepper component test).

6. **Manual smoke** — see Validation Architecture section. FCM tap → deep-link cannot be unit-tested end-to-end; requires emulator + real notification injection via `gcloud` CLI or admin SDK from a script.

---

### 2.10 Material 3 vertical stepper component

**Confirmed: there is no official M3 `Stepper` Composable.** (M3 has `Stepper` only in the iOS sense — Compose Material 3 1.x has no equivalent.) Compose Material 3 changelog through January 2026 makes no mention of stepper.

**Recommended custom component** — small Column-based composable:

```kotlin
@Composable
fun OrderStatusStepper(
    history: List<StatusEntry>,
    currentStatus: OrderStatus,
    trackingNumber: String?,
    modifier: Modifier = Modifier,
) {
    val allSteps = listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
    Column(modifier) {
        allSteps.forEachIndexed { index, step ->
            val entry = history.firstOrNull { it.status == step }
            val state = when {
                entry != null -> StepState.Completed
                step == currentStatus -> StepState.Current
                else -> StepState.Upcoming
            }
            StepperStep(
                step = step,
                state = state,
                timestamp = entry?.timestamp,
                trackingChip = if (step == OrderStatus.SHIPPED) trackingNumber else null,
                showConnector = index < allSteps.lastIndex,
            )
        }
        // CANCELLED rendered separately as a footer marker, not in the linear flow.
    }
}
```

Visual: Icon (CheckCircle / RadioButtonChecked / RadioButtonUnchecked from `material-icons-extended` — already a project dep), VerticalDivider, text column with title + timestamp. Reference existing badge/status-chip styling from Phase 5 (Active/Expired/Used up chips) for color tokens.

`[CITED: developer.android.com/jetpack/androidx/releases/compose-material3]` (no Stepper in any 1.x release through 2026 cutoff)

---

## 3. Recommended Technical Approach Per Plan

### Plan 06-01: Data Foundation + Rules + Fan-out

**New files (`:domain`):**
- `domain/src/main/java/com/wenubey/domain/model/order/SellerOrder.kt` — data class (parentOrderId, sellerId, sellerName, sellerLogoUrl, items, subtotal, shippingShare, discountShare, status, statusHistory, trackingNumber, refundId, refundedAmount, createdAt, updatedAt).
- `domain/src/main/java/com/wenubey/domain/model/order/StatusEntry.kt` — data class (status, timestamp, note: String?, trackingNumber: String?).
- `domain/src/main/java/com/wenubey/domain/model/order/AggregateOrderStatus.kt` — enum (PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, PARTIALLY_CANCELLED).
- `domain/src/main/java/com/wenubey/domain/repository/OrderRepository.kt`:
  ```kotlin
  interface OrderRepository {
      fun observeCustomerOrders(userId: String): Flow<List<Order>>
      fun observeOrderWithSubOrders(orderId: String): Flow<Pair<Order, List<SellerOrder>>?>
      fun observeSellerOrders(sellerId: String): Flow<List<SellerOrder>>
      fun observeSellerOrderById(id: String): Flow<SellerOrder?>
      suspend fun updateSellerOrderStatus(id: String, next: OrderStatus, trackingNumber: String? = null, note: String? = null): Result<Unit>
      suspend fun cancelSellerOrder(id: String): Result<Unit> // callable wrapper
      suspend fun syncCustomerOrders(userId: String): Result<Unit>
      suspend fun syncSellerOrders(sellerId: String): Result<Unit>
  }
  ```
- **Modify `Order.kt`**: add `sellerOrderIds: List<String>`, `aggregateStatus: AggregateOrderStatus`. The existing top-level `status`, `items` on `Order` become legacy/back-compat for Phase 4 single-seller orders OR are removed (planner decision — see Open Questions).

**New files (`:data`):**
- `data/src/main/java/com/wenubey/data/local/entity/SellerOrderEntity.kt` — fields listed in §2.5.
- `data/src/main/java/com/wenubey/data/local/dao/SellerOrderDao.kt`:
  ```kotlin
  @Dao interface SellerOrderDao {
      @Insert(onConflict = REPLACE) suspend fun upsert(e: SellerOrderEntity)
      @Insert(onConflict = REPLACE) suspend fun upsertAll(e: List<SellerOrderEntity>)
      @Query("SELECT * FROM seller_orders WHERE id = :id") fun observeById(id: String): Flow<SellerOrderEntity?>
      @Query("SELECT * FROM seller_orders WHERE parentOrderId = :parentId") fun observeByParent(parentId: String): Flow<List<SellerOrderEntity>>
      @Query("SELECT * FROM seller_orders WHERE sellerId = :sellerId ORDER BY createdAt DESC") fun observeBySeller(sellerId: String): Flow<List<SellerOrderEntity>>
      @Query("UPDATE seller_orders SET status=:status, statusHistoryJson=:historyJson, trackingNumber=:tracking, updatedAt=:now WHERE id = :id")
      suspend fun updateStatus(id: String, status: String, historyJson: String, tracking: String?, now: String)
  }
  ```
- `data/src/main/java/com/wenubey/data/local/mapper/SellerOrderMapper.kt` — bidirectional, JSON via `kotlinx.serialization` with `runCatching` + safe defaults (mirror `OrderMapper.kt`).
- `data/src/main/java/com/wenubey/data/repository/OrderRepositoryImpl.kt` — implements interface. `updateSellerOrderStatus` updates Room first (optimistic) then `firestore.collection("sellerOrders").doc(id).update(...)`. `cancelSellerOrder` calls callable `getHttpsCallable("cancelSellerOrder")` (mirror `DiscountRepositoryImpl` pattern).
- Migration `MIGRATION_5_6` in `WenuCommerceDatabase.kt`.
- Bump database version 5 → 6, add `SellerOrderEntity::class` to `@Database(entities = [...])`.

**Cloud Functions (`functions/src/index.ts`):**
- Modify `createPaymentIntent`: extend the product-lookup loop to also collect `sellerId` (and `sellerName`, `sellerLogoUrl`); group, allocate, write via `WriteBatch` (§2.6).
- Add `cancelSellerOrder` callable (§2.2).
- Add `onOrderStatusChange` trigger (§2.3 + §2.8 — both push and aggregate recompute).

**Security rules:**
- New file `firestore.rules` at repo root (does not currently exist — confirmed via `find`).
- Add `firebase.json` reference if not present.
- Rules per §2.1.

**DI (`app/di/`):**
- New `OrderModule.kt` (mirror `DiscountRepositoryImpl` Koin module wiring):
  ```kotlin
  val orderModule = module {
      singleOf(::OrderRepositoryImpl).bind<OrderRepository>()
      single { get<WenuCommerceDatabase>().sellerOrderDao() }
  }
  ```

**Tests (Wave 0 scaffolds):**
- `functions/test/rules.test.ts` — rules-unit-testing.
- `functions/test/cancelSellerOrder.test.ts` — firebase-functions-test + mocked Stripe.
- `functions/test/onOrderStatusChange.test.ts` — firebase-functions-test + mocked Messaging.
- `functions/test/createPaymentIntent.fanout.test.ts` — verifies batch contents.
- `data/src/test/.../OrderRepositoryTest.kt`.
- `domain/src/test/.../AggregateStatusTest.kt` — parameterised.

**Requirements addressed:** ORDR-05, ORDR-06, ORDR-07 (partial — callable backbone), ORDR-09, ORDR-10 (trigger), ORDR-04 (statusHistory storage).

---

### Plan 06-02: Customer Order History + Detail UI

**New files (`:app`):**
- `app/.../customer/orders/CustomerOrderHistoryScreen.kt`
- `app/.../customer/orders/CustomerOrderHistoryViewModel.kt`
- `app/.../customer/orders/CustomerOrderHistoryState.kt` — `{ orders: List<Order>, filter: OrderFilter, isLoading, errorMessage }`.
- `app/.../customer/orders/CustomerOrderHistoryAction.kt` — `OnFilterSelected(filter), OnOrderClicked(id), OnRefresh, OnRetry`.
- `app/.../customer/orders/CustomerOrderDetailScreen.kt`
- `app/.../customer/orders/CustomerOrderDetailViewModel.kt` + State + Action.
- `app/.../core/components/OrderStatusBadge.kt` — re-skin Phase 5 status badge for {Pending, Confirmed, Shipped, Delivered, Cancelled, Partially Cancelled}.
- `app/.../core/components/OrderStatusStepper.kt` — per §2.10.
- `app/.../core/components/TrackingNumberChip.kt` — copy-to-clipboard, monospace.

**Navigation routes** (`AppNavigationObjects.kt`):
- Replace `OrderDetail(orderId)` (already there, was used for Phase 4 minimal screen) — repurpose to mean "customer order detail." Or add `CustomerOrderHistory` and keep `OrderDetail`.
- Add `@Serializable data object CustomerOrderHistory`.

**Filter chip set** — `enum class OrderFilter { ALL, ACTIVE, DELIVERED, CANCELLED }`. `ACTIVE` = aggregateStatus in {PENDING, CONFIRMED, SHIPPED}.

**Detail body** — `LazyColumn` with sections per sub-order:
- If `sellerOrders.size == 1`: render stepper inline.
- If `> 1`: each section is an `ExpandableSection` collapsed by default.

**Tests (Wave 0 scaffolds):**
- `CustomerOrderHistoryViewModelTest` — filter chip toggles state, repository emissions flow through.
- `CustomerOrderDetailViewModelTest`.
- `CustomerOrderHistoryScreenTest` (Compose).
- `CustomerOrderDetailScreenTest` (Compose) — verifies multi-seller collapse, single-seller inline.
- `OrderStatusStepperTest` (Compose).

**Requirements addressed:** ORDR-01, ORDR-02, ORDR-03, ORDR-04 (display).

---

### Plan 06-03: Seller Orders UI + Status Controls + Cancel

**New files (`:app`):**
- `app/.../seller/orders/SellerOrdersScreen.kt` (list)
- `app/.../seller/orders/SellerOrderDetailScreen.kt` (detail with status advance + cancel button)
- ViewModels, State, Action mirror 06-02 patterns.
- Reuse `OrderStatusBadge`, `OrderStatusStepper` from 06-02.
- New `app/.../seller/orders/MarkAsShippedDialog.kt` — optional tracking-number text field + Skip / Apply.
- New `app/.../seller/orders/CancelOrderDialog.kt` — confirmation + refund-amount preview.

**Wire SellerTab Orders tab** — already a placeholder in `SellerTabScreen` per CONTEXT.md. Add the destination.

**Forward-only UI gating** — show only the "Advance to Next" button for current allowed next status; cancel button shown only when `status in {PENDING, CONFIRMED}`.

**Tests:** ViewModel state machine, Compose tests for dialogs and forward-only button gating, repository tests for `updateSellerOrderStatus` optimistic Room update + Firestore failure rollback.

**Requirements addressed:** ORDR-06, ORDR-07, ORDR-08, ORDR-09 (UI side).

---

### Plan 06-04: FCM Trigger + Deep Link

**Cloud Function** — `onOrderStatusChange` per §2.3. Already drafted in 06-01 if combined; if not, defer here.

**Android:**
- Extend `MessagingService.onMessageReceived` to handle `type=order_status` data payload — build notification with channel `order_status_channel`, launch Intent with `EXTRA_NAV_TARGET="order_detail"`, `EXTRA_ORDER_ID=orderId`.
- Add the `order_status_channel` `NotificationChannel` creation (mirror existing `device_login_channel` block at lines 41-51).
- Override `MainActivity.onNewIntent(intent)`, call `setIntent(intent)`, route to `OrderDetail(orderId)` via the `LaunchedEffect` pattern (§2.4).
- Set `android:launchMode="singleTop"` on `MainActivity` in `AndroidManifest.xml` (verify current value first).

**Tests:**
- `onOrderStatusChange.test.ts` (already scaffolded in 06-01).
- Manual smoke: install on device, place order from another account, advance status from seller account, verify push lands + tap deep-links.

**Requirements addressed:** ORDR-10 (delivery + UI tap routing).

---

## 4. Validation Architecture

This phase touches every architectural tier — server, security rules, callable functions, triggers, FCM transport, Android service, ViewModel, repository, Room, Compose UI. Each boundary needs an automated test; the FCM transport boundary plus deep-link tap is the only one requiring manual smoke.

### Test Framework

| Layer | Framework | Config file | Quick run | Full suite |
|---|---|---|---|---|
| Domain unit tests | JUnit 4 + kotlinx-coroutines-test | `domain/build.gradle.kts` | `./gradlew :domain:testDebugUnitTest` | same |
| Data unit tests | JUnit 4 + MockK + Turbine | `data/build.gradle.kts` | `./gradlew :data:testDebugUnitTest` | same |
| App unit tests (ViewModels) | JUnit 4 + MockK + Turbine + MainDispatcherRule | `app/build.gradle.kts` | `./gradlew :app:testDebugUnitTest --tests "*Order*"` | `./gradlew :app:testDebugUnitTest` |
| Compose UI tests | androidx.ui-test-junit4 + ComponentActivity | `app/build.gradle.kts` | `./gradlew :app:connectedDebugAndroidTest --tests "*Order*"` | `./gradlew connectedDebugAndroidTest` |
| Firestore rules | `@firebase/rules-unit-testing` + Firestore emulator | `functions/test/` (new) | `cd functions && firebase emulators:exec --only firestore "npm test -- --testPathPattern=rules"` | same |
| Cloud Functions | `firebase-functions-test` + Jest + Stripe mock | `functions/test/` (new) | `cd functions && npm test` | same |

### Phase Requirements → Test Map

| Req | Behavior | Test type | Automated command | Wave-0 needed? |
|---|---|---|---|---|
| ORDR-01 | Reverse-chron list renders | Compose UI | `:app:connectedDebugAndroidTest --tests "*CustomerOrderHistoryScreenTest*"` | ✅ |
| ORDR-02 | Row shows id/date/total/count/badge | Compose UI + ViewModel | same + `:app:testDebugUnitTest --tests "*CustomerOrderHistoryViewModelTest*"` | ✅ |
| ORDR-03 | Detail shows items + address + payment | Compose UI | `:app:connectedDebugAndroidTest --tests "*CustomerOrderDetailScreenTest*"` | ✅ |
| ORDR-04 | Timeline displays transitions + timestamps | Compose UI | `:app:connectedDebugAndroidTest --tests "*OrderStatusStepperTest*"` | ✅ |
| ORDR-05 | Status flow constraints | Rules + Domain unit | rules emulator + `:domain:testDebugUnitTest --tests "*StatusTransitionTest*"` | ✅ |
| ORDR-06 | Seller forward-only | Rules + Repository | rules emulator + `:data:testDebugUnitTest --tests "*OrderRepositoryTest*"` | ✅ |
| ORDR-07 | Seller cancel pre-SHIPPED | Cloud Function + Repository | `cd functions && npm test cancelSellerOrder` + repository test | ✅ |
| ORDR-08 | Tracking number prompt for SHIPPED | ViewModel + Compose | `:app:testDebugUnitTest --tests "*SellerOrderDetailViewModel*"` + `MarkAsShippedDialogTest` | ✅ |
| ORDR-09 | Seller sees only own orders | Rules + Repository query | rules emulator (`assertFails` for foreign read) + DAO query test | ✅ |
| ORDR-10 | FCM on status change | Cloud Function + manual | `cd functions && npm test onOrderStatusChange` + manual device smoke | partial (the tap itself is manual) |

### Sampling Rate

- **Per task commit:** module-scoped test command (`:domain:testDebugUnitTest`, `:data:testDebugUnitTest`, or `:app:testDebugUnitTest --tests "<class>"`).
- **Per wave merge:** `./gradlew testDebugUnitTest` (all 3 modules) + `cd functions && npm test`.
- **Phase gate:** above + `./gradlew connectedDebugAndroidTest` + Firestore rules emulator run + manual FCM smoke on a real device (cannot be automated without paid CI).

### Wave 0 Gaps

- [ ] `firestore.rules` does not exist — must be created with stub passing rules + corresponding `firestore.indexes.json` if any composite indexes needed (for `sellerOrders where sellerId == X order by createdAt desc`, yes — composite index required).
- [ ] `firebase.json` may need adding/updating for rules + emulator config.
- [ ] `functions/test/` directory does not exist — needs Jest config (`functions/jest.config.js`), `@firebase/rules-unit-testing` dependency install.
- [ ] `functions/package.json` `test` script does not exist — add `"test": "jest"` + dev deps.
- [ ] `domain/src/test/.../StatusTransitionTest.kt` — covers transition map + cancel-window.
- [ ] `domain/src/test/.../AggregateStatusTest.kt` — covers all 5 sub-order combinations.
- [ ] `data/src/test/.../OrderRepositoryTest.kt` — fake DAO + mocked Firebase Functions.
- [ ] `data/src/test/.../SellerOrderMapperTest.kt` — JSON round-trip with corrupt input fallback.
- [ ] `app/src/test/.../CustomerOrderHistoryViewModelTest.kt`.
- [ ] `app/src/test/.../CustomerOrderDetailViewModelTest.kt`.
- [ ] `app/src/test/.../SellerOrdersViewModelTest.kt`.
- [ ] `app/src/test/.../SellerOrderDetailViewModelTest.kt`.
- [ ] `app/src/androidTest/.../CustomerOrderHistoryScreenTest.kt`.
- [ ] `app/src/androidTest/.../CustomerOrderDetailScreenTest.kt`.
- [ ] `app/src/androidTest/.../SellerOrdersScreenTest.kt`.
- [ ] `app/src/androidTest/.../OrderStatusStepperTest.kt`.
- [ ] `app/src/androidTest/.../MarkAsShippedDialogTest.kt`.
- [ ] `app/src/androidTest/.../CancelOrderDialogTest.kt`.

---

## 5. Risks & Open Questions

1. **What happens to existing `Order.items` and `Order.status`?** Phase 4 single-seller orders exist with all items on the parent. Two options for the planner:
   - **(A)** Migrate them by writing one `sellerOrders/{id}` per existing `orders/{id}` retroactively (one-time backfill script in `functions/scripts/`).
   - **(B)** Leave `Order.items` as legacy field, populate only on new orders; new orders write empty items array on parent and rely on children.
   - Recommendation: **(B)** plus drop the unused field in a later cleanup phase. No prod data exists yet.

2. **Cart payload `sellerId` source of truth.** The function currently does `db.collection("PRODUCTS").doc(item.productId).get()` (line 278-281). For N items this is N reads — acceptable for typical 5-15 item carts but worth noting. Use `getAll(...refs)` for batch read instead. **Verify the collection name**: `"PRODUCTS"` is UPPER-case in `index.ts:278`; confirm this matches the actual collection (looks unusual; may have been a Phase 4 mistake). Planner should verify against Firestore console or `Constants.kt:PRODUCTS_COLLECTION`.

3. **`USERS` collection capitalization.** `FirestoreRepositoryImpl:100` uses `USER_COLLECTION` constant. Need to verify the value — likely `"USERS"` to match style of `"PRODUCTS"`. Trigger code must use the right value. Planner: read `data/src/main/java/com/wenubey/data/util/Constants.kt` to confirm before writing the trigger.

4. **Sync strategy — when exactly to call `syncCustomerOrders`?** CONTEXT.md says "(a) on screen open, (b) on FCM receipt, (c) pull-to-refresh." Implementation: ViewModel `init { sync(...) }` + `LaunchedEffect` re-trigger on `SyncEvent.OrderStatusChanged` from `SyncManager` (which `MessagingService` must emit). Decide whether `MessagingService` uses Koin-injected `SyncManager` to emit, or simply enqueues a `WorkManager` job. Phase 2 sync infrastructure should be reused.

5. **`PARTIALLY_CANCELLED` rendering in filter chips.** The four filter chips are All / Active / Delivered / Cancelled. Where does "Partially cancelled" live? Recommendation: render under both Active (since some sub-orders still active) AND Cancelled (since some were refunded). Confirm with user during plan-checker if ambiguous.

6. **Rules check `request.resource.data.items == resource.data.items` — list-of-map equality semantics**. Verified in Firestore rules CEL, but the items list contains floating-point `lineTotal` which round-trips losslessly only if Firestore SDK doesn't re-serialize. Hand-test in emulator with rules-unit-testing before relying on it. Fall-back: replace immutability check with a hash-field approach (compute a stable hash server-side at creation, freeze on writes).

7. **Tracking-number-update timing vs. SHIPPED status write.** The rule requires `appendedExactlyOneStatusEntry` AND if the new status is SHIPPED, the new `statusHistory` entry's `trackingNumber` should reflect `request.resource.data.trackingNumber`. Either enforce that in rules (slight added complexity) or accept that tracking number is set in the same write but not strictly cross-checked by the rule (lower assurance — defense-in-depth covered by client-side dialog).

8. **DI module count.** Per CONTEXT.md mention of `orderDataModule`, `orderDomainModule`, `orderAppModule`. The existing project does NOT split per-tier — there is one `DataModule.kt`, one `ViewmodelModule.kt`. Recommendation: add a single `OrderModule.kt` to `app/di/` (matching the pattern of how Phase 5 wired discounts in the existing DataModule). Planner can revisit if splitting is preferred.

9. **Aggregate recompute trigger — race**. If two seller-orders for the same parent update statuses within milliseconds, two `onOrderStatusChange` invocations both recompute `aggregateStatus`. The trigger uses a read-then-write — possible to race. Mitigation: wrap `recomputeAggregate` in a Firestore transaction (`db.runTransaction`) so the read+write of the parent is atomic. Cheap and bulletproof.

10. **`updateFcmToken` only updates when `auth.currentUser != null`** (`FirestoreRepositoryImpl:98-110`). On a fresh login, the token may be 5-15 seconds stale. Not a Phase 6 issue per se but means: a notification sent in the first seconds after install/login may target a missing/old token. Acceptable v1 risk.

---

## 6. Sources

### Primary (HIGH confidence)
- Firebase Firestore Security Rules reference: `firebase.google.com/docs/firestore/security/rules-conditions` (CEL semantics, `request.resource.data` vs `resource.data`, `get()` reads).
- Firebase Cloud Functions v2 Firestore triggers: `firebase.google.com/docs/functions/firestore-events?gen=2nd` (`onDocumentWritten` signature, `event.data.before/after`).
- Firebase Cloud Messaging Admin SDK: `firebase.google.com/docs/cloud-messaging/send-message#node.js` (`getMessaging().send()` shape).
- Stripe Refunds API: `stripe.com/docs/api/refunds/create` (`payment_intent`, `amount`, idempotency).
- Firestore Batched Writes: `firebase.google.com/docs/firestore/manage-data/transactions#batched-writes`.
- Android FCM receive guide: `firebase.google.com/docs/cloud-messaging/android/receive` (foreground vs background, data-only vs notification payload).
- Android PendingIntent mutability (Android 12+): `developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability`.
- Jetpack Navigation Compose: `developer.android.com/develop/ui/compose/navigation`.
- `@firebase/rules-unit-testing`: `firebase.google.com/docs/rules/unit-tests`.
- `firebase-functions-test`: `firebase.google.com/docs/functions/unit-testing`.

### Verified against existing repo (HIGHEST confidence)
- `functions/src/index.ts:225-402` — current `createPaymentIntent` shape.
- `data/src/main/java/com/wenubey/data/repository/PaymentRepositoryImpl.kt:30-117` — callable invocation pattern, optimistic Room-first update pattern.
- `data/src/main/java/com/wenubey/data/repository/DiscountRepositoryImpl.kt:152-180` — additional callable pattern reference (verified via grep).
- `data/src/main/java/com/wenubey/data/local/entity/OrderEntity.kt:6-22` and `OrderMapper.kt:1-52` — JSON-column pattern (the basis for the §2.5 recommendation).
- `data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt:26-186` — migration shape, schema version tracking.
- `domain/src/main/java/com/wenubey/domain/model/Device.kt:12` and `FirestoreRepositoryImpl:97-111` — confirms `users/{uid}.fcmToken` single-string shape.
- `app/src/main/java/com/wenubey/wenucommerce/notification/MessagingService.kt:18-87` — current FCM service pattern + Intent extras approach.
- `app/src/main/java/com/wenubey/wenucommerce/navigation/AppNavigationObjects.kt:7-102` — type-safe routes catalog.

### Tertiary (LOW confidence — flagged for user check)
- Material 3 Stepper non-existence: based on Compose Material 3 1.1–1.3 changelogs. If a 2.x release added it post-cutoff, the planner should swap the custom stepper for the official component. `[ASSUMED]`

---

## 7. Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | Cart-payload-to-Cloud-Function does not currently carry `sellerId`; function must derive it via the existing product lookup. | §2.6 | None — easily corrected in plan if found otherwise. |
| A2 | `USER_COLLECTION` constant resolves to `"USERS"` (not `"users"`). | §2.3, §5.3 | Trigger fails silently to find FCM token. Planner must verify `Constants.kt`. |
| A3 | `"PRODUCTS"` (upper-case) in `index.ts:278` matches actual production collection name. | §2.6, §5.2 | Fan-out would 404 every product. Planner verify before plan 06-01. |
| A4 | M3 Compose has no official Stepper through 2026 cutoff. | §2.10 | Wasted effort writing custom stepper; recoverable. |
| A5 | The literal-map `allowedNext` function in Firestore rules compiles correctly under rules_version 2. | §2.1 | Rules deploy may need slight syntax tweak; tested in emulator before deploy. |
| A6 | `request.resource.data.items == resource.data.items` is a stable list-of-map equality check in Firestore rules. | §2.1, §5.6 | Immutability check may fail spuriously due to map-key reordering. Fall-back to hash-field approach. |
| A7 | The order in which `onOrderStatusChange` fires and `aggregateStatus` recompute can race when two siblings update simultaneously. | §5.9 | Stale aggregate; mitigated by transaction wrap. |

---

## Metadata

**Confidence breakdown:**
- Existing-repo integration patterns: HIGH — all verified against source files at cited line numbers.
- Firestore rules approach: HIGH — directly documented in Firebase reference; the immutability check (A6) is the one piece worth emulator-testing before finalizing.
- Stripe partial-refund flow: HIGH — official API, simple parameters, idempotency key well-documented.
- FCM trigger + Android deep-link: HIGH on the function side, MEDIUM on the Android side (background-vs-foreground tap routing has historically been the source of FCM bugs — manual smoke required).
- Room JSON-column shape: HIGH — matches existing pattern; rejected `@Relation` with reasoned trade-off.
- Aggregate-status denormalization: HIGH — only sane choice given list-rendering perf constraints.
- Material 3 Stepper: LOW — non-existence asserted from training; planner should sanity-check at impl time.

**Research date:** 2026-06-15
**Valid until:** ~2026-07-15 (30 days; Stripe API + Firebase SDK are stable surfaces)

---

## Resolutions (2026-06-16)

Resolves the 10 open questions in §5 above. Each Q is given an explicit ruling and a pointer to where it is implemented in the four plans.

- **Q1 — Existing `Order.items` and `Order.status` post-split.** Resolution: **Option B** (leave as legacy fields, populate only for back-compat reads of Phase 4 docs; new orders write empty `items` on parent and rely on `sellerOrders` children). Implemented in 06-01 Task 1 (Order.kt extension keeps existing fields with defaults). No backfill script — no prod data exists.

- **Q2 — Cart payload `sellerId` source of truth.** Resolution: **Option A** (lookup in Cloud Function via existing product-doc read; no client change). Implemented in 06-01 Task 4 (createPaymentIntent extension extracts `sellerId`, `sellerName`, `sellerLogoUrl` from each product doc in the existing loop). `getAll(...refs)` batch read can be considered if perf becomes an issue, but not required for typical 5-15 item carts. Verify `"PRODUCTS"` literal against `Constants.kt` at impl time (Task 4 step 1).

- **Q3 — `USERS` collection capitalization.** Resolution: Verify `Constants.kt:USER_COLLECTION` at impl time and use the literal value (expected `"USERS"`). Implemented in 06-01 Task 4 step 1 (verification step + top-of-file comment).

- **Q4 — Sync strategy: when exactly to call `syncCustomerOrders`?** Resolution: Three triggers per CONTEXT D4:
  (a) ViewModel `init { syncCustomerOrders(uid) }` — open-time sync (implemented in 06-02 Task 2 / Task 3).
  (b) Pull-to-refresh — re-call sync on `OnRefresh` action (implemented in 06-02 Task 2).
  (c) **FCM event receipt** — `MessagingService` emits on a **SyncBus** (SharedFlow<SyncEvent>) owned by 06-04; customer ViewModels collect from it and re-call sync. SyncBus contract (`SyncBus.kt` + `SyncEvent.kt` + Koin `singleOf(::SyncBus)`) is shipped by **06-04 Task 2**; collection wiring is shipped by **06-02 Task 2 (history) and Task 3 (detail)**. The history ViewModel reacts to any `SyncEvent.OrderStatusChanged`; the detail ViewModel filters by matching `orderId`. WorkManager is NOT used — SyncBus is in-process, transient, and sufficient because the customer ViewModel collects only while the screen is alive.

- **Q5 — `PARTIALLY_CANCELLED` rendering in filter chips.** Resolution: appears in BOTH the Active filter (since some sub-orders are still in flight) AND the Cancelled filter (since some were refunded). Implemented in 06-02 Task 2 (`visibleOrders` helper in CustomerOrderHistoryState).

- **Q6 — Rules check `request.resource.data.items == resource.data.items` semantics.** Resolution: ship the literal-equality check; if hand-testing in the emulator (06-01 Task 5) shows it fails on map-key reordering, fall back to a server-computed `itemsHash` field on createPaymentIntent + an `itemsHash == resource.data.itemsHash` rule. T-06-08 in the STRIDE register tracks this as accept-with-fallback. Verified during 06-01 Task 5 rules.test.ts assertion (e) ("Seller tries CONFIRMED -> SHIPPED but mutates items -> assertFails").

- **Q7 — Tracking-number-update timing vs SHIPPED status write.** Resolution: client-side dialog gates the tracking input (06-03 Task 3 MarkAsShippedDialog); rules do NOT cross-check tracking against the SHIPPED statusHistory entry (lower assurance, simpler rule). Defense-in-depth = client dialog + rules' `appendedExactlyOneStatusEntry`. If a future bug shows tracking divergence, tighten the rule then.

- **Q8 — DI module count.** Resolution: single `OrderRepositoryImpl` Koin binding added to the existing `DataModule.kt` (no new module file). SyncBus singleton goes into `AppModule.kt` (or the equivalent app-scoped module). Implemented in 06-01 Task 3 (DataModule) + 06-04 Task 2 (AppModule SyncBus binding).

- **Q9 — Aggregate recompute race.** Resolution: wrap in `db.runTransaction` with reads-before-writes, plus a monotonic `aggregateVersion: number` CAS guard on the parent order doc. Implemented in 06-01 Task 4 step 4 (the recomputeAggregate transaction body) + tested in 06-01 Task 4 step 5 (concurrency test in onOrderStatusChange.test.ts asserts `aggregateVersion` increments by exactly 1 per invocation and that `tx.get(<sellerOrders query>)` is called before `tx.update(<parent>)`). 06-04 Task 1 step h adds the same concurrency assertion to the strengthened tests.

- **Q10 — Stale `fcmToken` on fresh login.** Resolution: **accept the v1 risk** (a notification sent in the first 5-15s after install may target a missing/old token). No code change. Documented in the STRIDE register implicitly under T-06-09 (Spoofing/transport — Firebase project-scoped). If user reports recur, revisit by writing a `BootCompletedReceiver`-style token refresh on app start.

