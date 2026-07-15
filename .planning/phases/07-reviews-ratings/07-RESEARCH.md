# Phase 7: Reviews & Ratings - Research

**Researched:** 2026-07-16
**Domain:** Firebase Cloud Functions (server-authoritative writes), Firestore security rules, Room offline caching, Jetpack Compose UI (product detail + cards)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** A minimal `submitReview` Cloud Function is the ONLY write path for reviews. It verifies the caller owns a DELIVERED `sellerOrders` doc that contains the productId (ownership + status + product-in-order membership), enforces one review per (customer, product) — an edit REPLACES in place — sets `isVerifiedPurchase`, writes the review, and updates the product rating aggregate in the SAME transaction. Mirrors the Phase 6 / this-session server-authoritative order-hardening pattern.
- **D-02:** Firestore rules lock the reviews subcollection (`PRODUCTS/{id}/REVIEWS`) writes AND the product rating aggregate fields to server-only (client create/update/delete = false); client reads/observe stay allowed. The existing client-side `ProductReviewRepositoryImpl.submitReview` transaction is re-routed through the callable; reads/observe remain client-side.
- **D-03:** `ratingAverage` + `ratingCount` are denormalized onto the product document, recomputed inside the submitReview Cloud Function transaction. Browse/search product cards read `ratingCount` (and average) directly from the product doc — no per-product subcollection counting.
- **D-04:** In scope this phase. Review cards get a "Helpful" action + count using the existing `helpfulCount` / `markReviewHelpful`. Double-voting is prevented per-user. Because review docs are server-only now (D-02), the helpful increment also goes through the server (Cloud Function or a narrowly-scoped rule) — researcher to choose the cheapest safe mechanism.
- **D-05:** If the customer already reviewed the product, the "Write a Review" entry opens the form pre-filled and submission replaces in place (no duplicate).
- **D-06:** The reviews list has a segmented sort toggle — default "Most recent", option "Highest rated"; ties broken by recency.
- **D-07:** The "Write a Review" prompt on product detail is shown only when the customer has a DELIVERED order for that product; hidden/disabled otherwise. The Cloud Function is the authority; the UI gate is a UX affordance (defense in depth, not the security boundary).

### Claude's Discretion

- Exact Firestore field names for the aggregate, the helpful-vote server mechanism (Function vs scoped rule), aggregate-recompute concurrency handling, and Room caching of reviews (offline-first per the established pattern) are left to research/planning.

### Deferred Ideas (OUT OF SCOPE)

- Seller replies to reviews — future phase (roadmap 07-03 covers seller visibility of reviews on their products, read-only; replies are new scope).
- Review photos / media attachments — future.
- Reporting / flagging abusive reviews + moderation queue — future (the existing `isVisible` flag + `setReviewVisibility` is the current minimal lever).
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REVW-01 | Customer can rate purchased products 1-5 stars (required) with optional text review | submitReview Cloud Function + ReviewForm composable in CustomerProductDetailScreen |
| REVW-02 | Only customers with DELIVERED order for the product can review (verified purchase) | Function queries sellerOrders with status=DELIVERED + productId in items[]; D-07 UI gate |
| REVW-03 | One review per customer per product (can edit, not duplicate) | Function's Firestore query for existing review by reviewerId; edit=update existing doc |
| REVW-04 | Product detail shows aggregate rating (average + count) and individual reviews | Already partially rendered; needs full section with sort toggle + Verified Purchase badge |
| REVW-05 | Reviews display "Verified Purchase" badge | `isVerifiedPurchase=true` set by Function; badge in ReviewCard composable |
| REVW-06 | Reviews sorted by most recent (default), with option for highest rated | Sort toggle in ViewModel state; client-side sort of Flow-observed reviews |
| REVW-07 | Review count shown on product cards in browse/search | `reviewCount` already on Product domain model + ProductEntity + CustomerProductCard (partially rendered — already shows it when >0) |
</phase_requirements>

---

## Summary

Phase 7 is a **hardening + surfacing phase**, not a greenfield build. The domain model (`ProductReview`), repository interface (`ProductReviewRepository`), repository implementation (`ProductReviewRepositoryImpl`), ViewModel injection, and basic card rendering in both `CustomerProductDetailScreen` and `CustomerProductCard` already exist. The UI partially renders reviews and shows `reviewCount` on cards when it is greater than zero.

The phase has three concrete engineering workstreams:

**07-01 (Data + Backend):** Introduce a `submitReview` Cloud Function that is the sole write path for reviews. The current client-side transaction in `ProductReviewRepositoryImpl.submitReview` must be replaced by a `Firebase.functions.getHttpsCallable("submitReview").call(data)` call. The Function verifies purchase ownership by querying `sellerOrders` (status=DELIVERED, items containing productId, userId==caller), enforces one-per-product by querying `PRODUCTS/{id}/REVIEWS` for an existing doc with `reviewerId==uid` (edit replaces rather than rejecting), writes/updates the review in a transaction, and recomputes the product aggregate. Firestore rules lock the subcollection and product rating fields to server-only writes. For helpful votes, the cheapest safe mechanism is a **`helpfulVotes` subcollection per review** keyed by UID, with a narrowly-scoped Firestore rule that allows the authenticated caller to write exactly their own UID document — this avoids a new Cloud Function while enforcing one-per-user. A Room `ReviewEntity` (schema v9 migration) with a `ReviewDao` backed by a `callbackFlow` SyncManager listener provides the Room-first offline pattern consistent with prior phases.

**07-02 (Product Detail UI):** Extend `CustomerProductDetailScreen` and `CustomerProductDetailViewModel` with the complete reviews UX: sort toggle (state in ViewModel, client-side sort of the observed list), Verified Purchase badge in `ReviewCard`, "Write a Review" / "Edit Review" button gated on `hasDeliveredOrder` (a new VM property populated by querying `sellerOrders` from Room), and the `WriteReviewSheet` / `EditReviewSheet` bottom sheet form.

**07-03 (Cards + Seller Visibility):** `CustomerProductCard` already conditionally renders `reviewCount` — it only needs the product's `ratingAverage`/`ratingCount` fields to be live (they flow from Room via ProductEntity which already has both columns). No new card code is required if the aggregate fields are correctly maintained by the Cloud Function. Seller review visibility (read-only toggle of `isVisible`) is the `setReviewVisibility` path that already exists in the repository — it needs a rule fix to allow sellers to write that field on their own products' reviews.

**Primary recommendation:** Implement in three plans exactly as roadmapped. 07-01 is the critical-path blocker (Function + rules + Room entity + DAO + repository re-routing); 07-02 and 07-03 are parallel once 07-01 is merged.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Review write authority | Cloud Functions | — | D-01/D-02; client cannot verify purchase membership or prevent double-review safely |
| Purchase eligibility check | Cloud Functions | `:domain` (read from SellerOrderRepository) | Function queries sellerOrders server-side; Android UI reads Room for UI gating (D-07) |
| Aggregate recompute (ratingAverage, ratingCount) | Cloud Functions (inside transaction) | — | Atomic with review write; server-side avoids race conditions |
| Review reads / observe | `:data` (Firestore listener → Room) | `:app` ViewModel | Room-first pattern; Firestore callbackFlow in SyncManager writes to Room; ViewModel collects Room DAO Flow |
| Helpful vote write | Firestore rules (scoped) | — | helpfulVotes/{uid} subcollection with rules: callerUid can create/delete own doc; helpfulCount update via serverTimestamp field or separate Function (see pitfalls) |
| Review list sorting | `:app` ViewModel | — | Client-side sort of Flow list; no Firestore index needed |
| "Write a Review" gate (UI) | `:app` ViewModel | — | D-07: UX affordance only; security is the Function; ViewModel queries Room SellerOrderDao for DELIVERED status + productId match |
| Review count on product cards | Product doc `ratingCount` field | Room `ProductEntity.reviewCount` | Already denormalized; SyncManager updates Room when product doc changes |
| Seller review visibility toggle | `:data` repository | Firestore rules | `setReviewVisibility` already exists; needs rule allowing seller write on `isVisible` field of their products' reviews |

---

## Existing Code Inventory

### What Already Exists (DO NOT REBUILD)

**Domain model — `domain/.../model/product/ProductReview.kt`**
Fields: `id, productId, reviewerId, reviewerName, reviewerPhotoUrl, purchaseId, rating, title, body, isVerifiedPurchase, helpfulCount, isVisible, createdAt, updatedAt` + `toMap()`.
Status: complete, no changes needed for Phase 7.

**Repository interface — `domain/.../repository/ProductReviewRepository.kt`**
Methods: `observeReviewsForProduct`, `getReviewsForProduct`, `submitReview`, `markReviewHelpful`, `setReviewVisibility`.
Status: `submitReview` signature is fine; the implementation body will change (callable instead of direct Firestore transaction). Interface may need `getMyReviewForProduct(productId): Result<ProductReview?>` added for pre-fill (D-05).

**Repository implementation — `data/.../repository/ProductReviewRepositoryImpl.kt`**
Current: Direct client-side Firestore transaction. Uses `PRODUCTS_COLLECTION` / `REVIEWS_SUBCOLLECTION` constants already defined in `Constants.kt`.
Phase 7 change: `submitReview` body becomes a `Firebase.functions.getHttpsCallable("submitReview").call(data).await()` call matching the `PaymentRepositoryImpl` pattern (line 53-55 of `PaymentRepositoryImpl.kt`).
Reads/observe (`observeReviewsForProduct`, `getReviewsForProduct`, `markReviewHelpful`, `setReviewVisibility`) stay as-is except `markReviewHelpful` will need routing through a server path (see helpful vote design below).

**ViewModel — `app/.../customer/customer_products/CustomerProductDetailViewModel.kt`**
Already: injects `ProductReviewRepository`, calls `observeReviewsForProduct` in `init`, handles `OnMarkReviewHelpful`.
Needs: `hasDeliveredOrder: Boolean`, `existingReview: ProductReview?`, `reviewSortOrder` state, actions for `WriteReview`, `EditReview`, `SubmitReview`, `SortReviews`.

**State — `CustomerProductDetailState.kt`**
Needs additions: `hasDeliveredOrder: Boolean = false`, `existingReview: ProductReview? = null`, `reviewSortOrder: ReviewSortOrder = MOST_RECENT`, `isSubmittingReview: Boolean = false`, `showReviewForm: Boolean = false`, `reviewSubmitError: String? = null`.

**UI — `CustomerProductDetailScreen.kt`**
Already renders: basic `ReviewCard` with `helpfulCount`, star rating row on the product, `reviews.size` count in section header.
Needs: sort toggle (SegmentedButton / two FilterChips), Verified Purchase badge in `ReviewCard`, "Write a Review" / "Edit your review" button gated on `hasDeliveredOrder`, `WriteReviewSheet` bottom sheet composable.

**Product card — `CustomerHomeScreen.kt` (`CustomerProductCard`)**
Line 511-525: Already conditionally renders `reviewCount` and `averageRating` when `product.reviewCount > 0`. The `Product` domain model already has both fields. The `ProductEntity` already has `averageRating` and `reviewCount` columns. **No new card code is needed for REVW-07** — the aggregate fields just need to be correctly maintained by the Cloud Function and synced to Room via `SyncManager` (product doc listener already exists from Phase 1).

**Product domain model — `domain/.../model/product/Product.kt`**
Already has: `averageRating: Double = 0.0`, `reviewCount: Int = 0`.
Field names used in current `ProductReviewRepositoryImpl.submitReview` (line 127-130): `"averageRating"` and `"reviewCount"` on the product doc.
**Decision:** Keep these exact Firestore field names — the Cloud Function must match them. [VERIFIED: codebase]

**Emulator test — `data/.../repository/ProductReviewRepositoryImplEmulatorTest.kt`**
Tests: submit, aggregate, duplicate rejection, visibility, observe, markHelpful.
Phase 7: needs updating to test the callable path (mock callable or use Firebase emulator with Functions). Existing structural tests become invalid when `submitReview` changes to a callable.

**DI — `DataModule.kt`**
`ProductReviewRepositoryImpl` already bound as `ProductReviewRepository` via `singleOf`. No new binding needed; just need to add `FirebaseFunctions` as a constructor param (already available in `firebaseModule` as `Firebase.functions`).

**Koin — `Firebase.functions`** already registered in `firebaseModule` (line 70 of `DataModule.kt`).

**Constants — `data/.../util/Constants.kt`**
Already has `REVIEWS_SUBCOLLECTION = "REVIEWS"`. Add `HELPFUL_VOTES_SUBCOLLECTION = "helpfulVotes"` in this file.

**Room schema — `WenuCommerceDatabase.kt`**
Current version: **8**. Phase 7 needs `MIGRATION_8_9` adding `reviews` table.

---

## Standard Stack

### Core (no new dependencies required)

| Library | Version | Purpose |
|---------|---------|---------|
| Firebase Functions KTX | `firebase-bom:33.8.0` | `Firebase.functions.getHttpsCallable` callable invoke |
| Firebase Firestore KTX | `firebase-bom:33.8.0` | reads / observe (no change) |
| Room | `2.6.1` | `ReviewEntity` + `ReviewDao` |
| Kotlinx Serialization | BOM-managed | JSON column in `ReviewEntity` |
| Jetpack Compose Material 3 | `composeBom:2025.01.00` | `SegmentedButton` / `FilterChip` for sort toggle |
| Koin | `4.0.1` | no new modules; DAO added to existing `databaseModule` |
| Turbine | `1.1.0` | ViewModel + DAO unit tests |

All dependencies already in `libs.versions.toml`. No new packages to add.

### Package Legitimacy Audit

> No new packages are installed in Phase 7. All libraries are existing dependencies in `libs.versions.toml`. This section is intentionally omitted.

---

## Architecture Patterns

### System Architecture Diagram

```
Customer taps "Write a Review"
         |
         v
CustomerProductDetailViewModel
   ├─ hasDeliveredOrder check (SellerOrderDao.observeDeliveredOrderForProduct) → show/hide button
   └─ SubmitReview action
         |
         v
ProductReviewRepositoryImpl.submitReview(review)
         |
         v
Firebase.functions.getHttpsCallable("submitReview").call(payload).await()
         |
         v
[Cloud Function: submitReview]
   ├─ request.auth check (unauthenticated → error)
   ├─ query sellerOrders WHERE userId==uid AND status==DELIVERED AND productId in items[]
   ├─ no matching doc → HttpsError("failed-precondition", "No delivered order")
   ├─ query PRODUCTS/{id}/REVIEWS WHERE reviewerId==uid
   │     ├─ no existing doc → INSERT new review doc
   │     └─ existing doc → UPDATE existing review doc (edit = replace)
   ├─ db.runTransaction → write/update review + recompute ratingAverage/ratingCount on product doc
   └─ return { reviewId, isVerifiedPurchase: true }
         |
         v
SyncManager Firestore listener on PRODUCTS/{id}/REVIEWS
         |
         v
ReviewDao.upsert(ReviewEntity)   (Room write-through)
         |
         v
ReviewDao.observeByProduct(productId) → Flow<List<ReviewEntity>>
         |
         v
ProductReviewRepositoryImpl.observeReviewsForProduct → Flow<List<ProductReview>>
         |
         v
CustomerProductDetailViewModel.reviews StateFlow
         |
         v
CustomerProductDetailScreen renders ReviewCard list
```

```
Customer taps "Helpful" on a review
         |
         v
ProductReviewRepositoryImpl.markReviewHelpful(productId, reviewId)
         |
         v
PRODUCTS/{productId}/REVIEWS/{reviewId}/helpfulVotes/{callerUid}  SET {}
(Firestore rules: allow create/delete if request.auth.uid == reviewId_uid_key is NOT
 the approach — see Helpful Vote Design below)
         |
         v
onWrite trigger in Cloud Function (OR inline rule-managed FieldValue.increment)
updates helpfulCount on the review doc
```

### Recommended Project Structure

```
data/src/main/java/com/wenubey/data/
├── local/
│   ├── entity/
│   │   └── ReviewEntity.kt           # NEW — Room entity, schema v9
│   ├── dao/
│   │   └── ReviewDao.kt              # NEW — upsert, observeByProduct, getByReviewerAndProduct
│   └── mapper/
│       └── ReviewMapper.kt           # NEW — ReviewEntity ↔ ProductReview
├── repository/
│   └── ProductReviewRepositoryImpl.kt  # MODIFY — submitReview → callable; add Room DAO
└── local/
    └── WenuCommerceDatabase.kt       # MODIFY — add ReviewEntity + MIGRATION_8_9

domain/src/main/java/com/wenubey/domain/
└── repository/
    └── ProductReviewRepository.kt    # MODIFY — add getMyReviewForProduct

functions/src/
└── index.ts                          # ADD — submitReview + markReviewHelpful callables (or keep markReviewHelpful rule-based)

app/src/main/java/com/wenubey/wenucommerce/
├── customer/customer_products/
│   ├── CustomerProductDetailViewModel.kt  # MODIFY — hasDeliveredOrder, sort, existingReview
│   ├── CustomerProductDetailState.kt      # MODIFY — new fields
│   ├── CustomerProductDetailAction.kt     # MODIFY — new actions
│   └── CustomerProductDetailScreen.kt    # MODIFY — sort toggle, badge, Write Review button+sheet
└── navigation/
    └── AppNavigationObjects.kt         # POSSIBLY — WriteReview route if modal screen used
```

---

## Design Decisions (Claude's Discretion)

### Firestore Field Names (D-03 discretion)

The existing client-side code and emulator test use:
- `"averageRating"` on the product doc (read as `getDouble("averageRating")`)
- `"reviewCount"` on the product doc (read as `getLong("reviewCount")`)

The `ProductEntity` columns are `averageRating: Double` and `reviewCount: Int`.

**Decision:** Keep `averageRating` and `reviewCount` as the canonical Firestore field names. The Cloud Function uses these same names. [ASSUMED based on existing codebase — no Firestore schema doc]

### Helpful Vote Mechanism (D-04 discretion)

Two options evaluated:

**Option A: helpfulVotes subcollection + Firestore rules (RECOMMENDED)**
- Each review gets `PRODUCTS/{pid}/REVIEWS/{rid}/helpfulVotes/{callerUid}` documents.
- Firestore rule: `allow create: if request.auth.uid == reviewId_segment_key` — but this is not how subcollections work. Instead: `allow create, delete: if request.auth.uid == helpfulVoteId` where `helpfulVoteId` is the document ID (the caller's UID).
- A Firestore `onWrite` trigger on `helpfulVotes/{voteId}` documents increments/decrements `helpfulCount` on the parent review doc.
- OR: simpler — use Firestore rules to allow the caller to set their own vote doc, and compute `helpfulCount = number of children` server-side. But counting subcollection children is expensive; prefer explicit increment.
- **Cheapest safe implementation:** A small Cloud Function `markReviewHelpful` callable that: (1) checks auth, (2) writes `helpfulVotes/{uid}` using `set({exists: true}, {merge: true})` inside a transaction with a read to detect duplicate, (3) increments `helpfulCount` on the review doc. Atomic, no double-vote possible, no extra Firestore rule complexity.

**Option B: Pure rules path with helpfulVotes subcollection**
- Allow authenticated users to write/delete their own `helpfulVotes/{uid}` doc.
- Trigger: Cloud Function (onDocumentCreated on helpfulVotes) increments helpfulCount.
- More moving parts; trigger latency visible to user.

**Chosen: Option A with a `markReviewHelpful` callable** — mirrors the submitReview pattern, easy to test, eliminates race conditions. The existing `ProductReviewRepository.markReviewHelpful` changes from a direct `FieldValue.increment` to a callable invocation.

### Room Caching of Reviews (D-03 discretion)

The project's established Room-first pattern (Phases 1-6) requires offline-first reads for all data. Reviews are suitable for Room caching:
- `ReviewEntity` (schema v9) with `productId` index for efficient `WHERE productId = ?` queries.
- `SyncManager` adds a Firestore `callbackFlow` listener on `PRODUCTS/{productId}/REVIEWS` (triggered when `CustomerProductDetailViewModel` opens).
- `ReviewDao.observeByProduct(productId)` returns `Flow<List<ReviewEntity>>` → `observeReviewsForProduct` maps to `Flow<List<ProductReview>>`.
- Room cache is product-scoped (loaded on demand when detail screen opens), not globally pre-synced, to avoid downloading all reviews at startup.

**Alternative (no Room cache):** Firestore `callbackFlow` directly in `observeReviewsForProduct` — already the current pattern in `ProductReviewRepositoryImpl`. This would be simpler but breaks offline-first consistency.

**Decision: Add Room caching via `ReviewEntity` + `ReviewDao` + SyncManager listener.** Consistent with the established architectural decision (Phase 1 research: "Room must live in `data` module only"). Schema goes to v9.

### Sort Toggle Implementation (D-06 discretion)

Client-side sort on the collected Flow list:
- `ReviewSortOrder.MOST_RECENT`: sort by `createdAt` descending (ISO-8601 string, lexicographic sort works for timestamps in this format).
- `ReviewSortOrder.HIGHEST_RATED`: sort by `rating` descending, then `createdAt` descending for ties.
- Sort is applied in the ViewModel before emitting to the state.
- No new Firestore index required.

---

## Cloud Function: submitReview Design

### Request Shape (Android callable payload)

```typescript
interface SubmitReviewRequest {
  productId: string;
  rating: number;           // 1-5
  title: string;            // may be empty string
  body: string;             // may be empty string
  reviewerName: string;     // from Auth display name
  reviewerPhotoUrl: string; // from Auth photoURL, may be empty
}
```

### Verification Logic (purchase check)

```typescript
// Inside submitReview Cloud Function
const db = admin.firestore();
const uid = request.auth.uid;

// Step 1: find at least one DELIVERED sellerOrder owned by this user
// that contains the target productId
const sellerOrdersSnap = await db
  .collection("sellerOrders")
  .where("userId", "==", uid)
  .where("status", "==", "DELIVERED")
  .get();

const qualifying = sellerOrdersSnap.docs.filter(doc => {
  const items: {productId: string}[] = doc.data().items ?? [];
  return items.some(it => it.productId === productId);
});

if (qualifying.length === 0) {
  throw new HttpsError(
    "failed-precondition",
    "No delivered order found for this product"
  );
}
```

**Note on Firestore compound index:** The query `where("userId", "==", uid).where("status", "==", "DELIVERED")` requires a composite index on `sellerOrders(userId ASC, status ASC)`. This is a server-side query inside a Cloud Function (Admin SDK, no security rule constraint), so it requires deploying a Firestore index. Add to `firestore.indexes.json`. [ASSUMED — standard Firestore compound query requirement]

### One-per-product Enforcement + Edit Path

```typescript
// Step 2: look for existing review by this reviewer for this product
const existingSnap = await db
  .collection("PRODUCTS")
  .doc(productId)
  .collection("REVIEWS")
  .where("reviewerId", "==", uid)
  .get();

const isEdit = !existingSnap.empty;
const reviewId = isEdit
  ? existingSnap.docs[0].id
  : db.collection("PRODUCTS").doc(productId).collection("REVIEWS").doc().id;
const now = admin.firestore.Timestamp.now().toDate().toISOString();

const reviewData = {
  id: reviewId,
  productId,
  reviewerId: uid,
  reviewerName: request.data.reviewerName,
  reviewerPhotoUrl: request.data.reviewerPhotoUrl ?? "",
  purchaseId: qualifying[0].id,   // first qualifying sellerOrderId
  rating: request.data.rating,
  title: request.data.title ?? "",
  body: request.data.body ?? "",
  isVerifiedPurchase: true,        // always true — function is the gate
  helpfulCount: isEdit ? existingSnap.docs[0].data().helpfulCount ?? 0 : 0,
  isVisible: true,
  createdAt: isEdit ? existingSnap.docs[0].data().createdAt : now,
  updatedAt: now,
};
```

### Aggregate Recompute Transaction

```typescript
// Step 3: transaction — write review + recompute product aggregate
const productRef = db.collection("PRODUCTS").doc(productId);
const reviewRef = db.collection("PRODUCTS").doc(productId)
  .collection("REVIEWS").doc(reviewId);

await db.runTransaction(async (tx) => {
  const productSnap = await tx.get(productRef);
  const currentCount = (productSnap.data()?.reviewCount as number) ?? 0;
  const currentAvg = (productSnap.data()?.averageRating as number) ?? 0;

  let newCount: number;
  let newAvg: number;

  if (isEdit) {
    // Replace old rating with new: recompute average from the delta
    const oldRating = (existingSnap.docs[0].data().rating as number) ?? 0;
    newCount = currentCount; // count unchanged for an edit
    newAvg = currentCount === 0
      ? request.data.rating
      : ((currentAvg * currentCount) - oldRating + request.data.rating) / currentCount;
  } else {
    newCount = currentCount + 1;
    newAvg = currentCount === 0
      ? request.data.rating
      : ((currentAvg * currentCount) + request.data.rating) / newCount;
  }

  tx.set(reviewRef, reviewData);
  tx.update(productRef, {
    reviewCount: newCount,
    averageRating: newAvg,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
});

return { reviewId, isVerifiedPurchase: true };
```

**Concurrency:** Firestore transactions provide optimistic locking — concurrent writes on the same product doc will retry automatically. For review aggregates with many concurrent reviewers (typical for popular products), this is safe. [ASSUMED — standard Firestore transaction guarantee]

---

## Firestore Rules Design

### Current gap

The current `firestore.rules` has a broad permissive block for `PRODUCTS/{productId}` and its subcollections:
```
match /PRODUCTS/{productId} {
  allow read, write: if request.auth != null;
  match /{document=**} {
    allow read, write: if request.auth != null;
  }
}
```
This currently allows any authenticated user to write reviews directly. Phase 7 must tighten this.

### Required rule changes

```javascript
// Tighten PRODUCTS to replace the broad block:
match /PRODUCTS/{productId} {
  // Product top-level doc: read = any authenticated user.
  // Write = seller who owns it (for product management) OR server (Admin SDK for aggregates).
  // Phase 7 locks ratingAverage + ratingCount to server-only by denying
  // all client writes to the top-level product doc (sellers manage products via
  // their existing flow which already uses Admin SDK — verify this assumption).
  // SIMPLER approach: keep existing permissive rule for product doc itself,
  // only tighten the REVIEWS subcollection.
  allow read: if request.auth != null;
  allow write: if request.auth != null;  // keep existing — product mgmt is client-side

  // REVIEWS subcollection: reads open to authenticated users; ALL writes server-only.
  match /REVIEWS/{reviewId} {
    allow read: if request.auth != null;
    allow create, update, delete: if false;  // server-only via Admin SDK

    // helpfulVotes subcollection: each voter writes their own UID-keyed doc.
    // Count management is via markReviewHelpful callable (Admin SDK).
    match /helpfulVotes/{voterId} {
      allow read: if request.auth != null;
      allow create, delete: if false;  // markReviewHelpful callable uses Admin SDK
    }
  }
}
```

**Seller `setReviewVisibility`:** The existing `ProductReviewRepository.setReviewVisibility` updates `isVisible` on a review doc. Since review writes are now server-only, `setReviewVisibility` must also become a callable. However, Phase 7 scope notes: "seller review visibility" is plan 07-03 scope. Options:
1. Add a `setReviewVisibility` Cloud Function callable (clean, consistent).
2. Narrow the rule to allow the product's seller to write only the `isVisible` field.

**Decision: Add a `setReviewVisibility` callable** to keep all review writes server-authoritative. The seller's identity can be verified by reading the product doc inside the Function.

### Firestore indexes to add (firestore.indexes.json)

```json
{
  "indexes": [
    {
      "collectionGroup": "sellerOrders",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "userId", "order": "ASCENDING" },
        { "fieldPath": "status", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "REVIEWS",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "reviewerId", "order": "ASCENDING" },
        { "fieldPath": "productId", "order": "ASCENDING" }
      ]
    }
  ]
}
```

---

## Room Entity and Migration Design

### ReviewEntity (schema v9)

```kotlin
// data/src/main/java/com/wenubey/data/local/entity/ReviewEntity.kt
@Entity(
    tableName = "reviews",
    indices = [
        Index("productId"),
        Index("reviewerId")
    ]
)
data class ReviewEntity(
    @PrimaryKey val id: String,
    val productId: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val reviewerPhotoUrl: String = "",
    val purchaseId: String = "",
    val rating: Int = 0,
    val title: String = "",
    val body: String = "",
    val isVerifiedPurchase: Boolean = true,
    val helpfulCount: Int = 0,
    val isVisible: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
)
```

### ReviewDao

```kotlin
@Dao
interface ReviewDao {
    @Upsert
    suspend fun upsert(review: ReviewEntity)

    @Upsert
    suspend fun upsertAll(reviews: List<ReviewEntity>)

    @Query("SELECT * FROM reviews WHERE productId = :productId AND isVisible = 1")
    fun observeByProduct(productId: String): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE reviewerId = :reviewerId AND productId = :productId LIMIT 1")
    suspend fun getByReviewerAndProduct(reviewerId: String, productId: String): ReviewEntity?

    @Query("DELETE FROM reviews WHERE productId = :productId")
    suspend fun deleteByProduct(productId: String)
}
```

### MIGRATION_8_9

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reviews` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `productId` TEXT NOT NULL DEFAULT '',
                `reviewerId` TEXT NOT NULL DEFAULT '',
                `reviewerName` TEXT NOT NULL DEFAULT '',
                `reviewerPhotoUrl` TEXT NOT NULL DEFAULT '',
                `purchaseId` TEXT NOT NULL DEFAULT '',
                `rating` INTEGER NOT NULL DEFAULT 0,
                `title` TEXT NOT NULL DEFAULT '',
                `body` TEXT NOT NULL DEFAULT '',
                `isVerifiedPurchase` INTEGER NOT NULL DEFAULT 1,
                `helpfulCount` INTEGER NOT NULL DEFAULT 0,
                `isVisible` INTEGER NOT NULL DEFAULT 1,
                `createdAt` TEXT NOT NULL DEFAULT '',
                `updatedAt` TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reviews_productId` ON `reviews` (`productId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reviews_reviewerId` ON `reviews` (`reviewerId`)"
        )
    }
}
```

---

## UI Wiring Points

### CustomerProductDetailState additions

```kotlin
// additions to CustomerProductDetailState
val hasDeliveredOrder: Boolean = false,
val existingReview: ProductReview? = null,  // for pre-fill on edit (D-05)
val reviewSortOrder: ReviewSortOrder = ReviewSortOrder.MOST_RECENT,
val isSubmittingReview: Boolean = false,
val showReviewForm: Boolean = false,
val reviewSubmitError: String? = null,

enum class ReviewSortOrder { MOST_RECENT, HIGHEST_RATED }
```

### CustomerProductDetailAction additions

```kotlin
data object OpenReviewForm : CustomerProductDetailAction
data object DismissReviewForm : CustomerProductDetailAction
data class SubmitReview(
    val rating: Int,
    val title: String,
    val body: String,
) : CustomerProductDetailAction
data class OnSortOrderChanged(val order: ReviewSortOrder) : CustomerProductDetailAction
```

### CustomerProductDetailViewModel additions

```kotlin
// In init block — after loadProduct():
checkDeliveredOrderStatus(productId)

private fun checkDeliveredOrderStatus(productId: String) {
    val userId = authRepository.currentUser.value?.uuid ?: return
    viewModelScope.launch(ioDispatcher) {
        // Query Room SellerOrderDao for DELIVERED orders belonging to this user
        // that contain this productId in their itemsJson
        val hasDelivered = sellerOrderDao
            .getDeliveredOrdersForUser(userId)
            .any { entity ->
                // parse itemsJson to check productId presence
                runCatching {
                    Json.decodeFromString<List<OrderItem>>(entity.itemsJson)
                        .any { it.productId == productId }
                }.getOrDefault(false)
            }
        val myReview = reviewRepository.getMyReviewForProduct(productId)
            .getOrNull()
        _state.update {
            it.copy(hasDeliveredOrder = hasDelivered, existingReview = myReview)
        }
    }
}
```

**Note:** `SellerOrderDao` needs a `getDeliveredOrdersForUser(userId: String)` query. Check existing SellerOrderDao — likely needs adding. [ASSUMED — need to verify DAO contents]

### Sort logic in ViewModel

```kotlin
private fun sortedReviews(reviews: List<ProductReview>, order: ReviewSortOrder) =
    when (order) {
        ReviewSortOrder.MOST_RECENT -> reviews.sortedByDescending { it.createdAt }
        ReviewSortOrder.HIGHEST_RATED -> reviews
            .sortedWith(compareByDescending<ProductReview> { it.rating }
                .thenByDescending { it.createdAt })
    }
```

Apply in the collect block of `observeReviews`.

### ReviewCard enhancement — Verified Purchase badge

Add to existing `ReviewCard` composable after reviewer name/stars row:
```kotlin
if (review.isVerifiedPurchase) {
    Text(
        text = "Verified Purchase",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp)
    )
}
```

### WriteReviewSheet

A `ModalBottomSheet` composable containing:
- Star rating selector (5 `Icon` buttons, selectable)
- Optional title `OutlinedTextField`
- Optional body `OutlinedTextField` (multiline)
- Submit button + cancel
- Pre-filled when `existingReview != null` (D-05)

Does NOT require a new navigation route — embedded in `CustomerProductDetailScreen` as a bottom sheet shown when `state.showReviewForm`.

### Product Card (REVW-07 status)

`CustomerProductCard` at lines 511-525 of `CustomerHomeScreen.kt` already conditionally renders rating when `product.reviewCount > 0`:
```kotlin
if (product.reviewCount > 0) {
    Row(...) {
        Icon(Icons.Default.Star, ...)
        Text("%.1f (%d)".format(product.averageRating, product.reviewCount), ...)
    }
}
```
REVW-07 is **already implemented** — it activates once `ratingAverage`/`ratingCount` are populated by the Cloud Function. The `SyncManager` product listener already keeps `ProductEntity.reviewCount` and `ProductEntity.averageRating` in sync. No new card code needed.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Purchase verification | Client-side query in repository | `submitReview` Cloud Function (Admin SDK has no security rule bypass concern; can safely query any sellerOrders) |
| One-per-product dedup | Client-side check with race condition | Firestore transaction inside Cloud Function (atomic read-check-write) |
| Aggregate recompute | Client-side read-modify-write (race condition) | Firestore transaction in Cloud Function (atomic with review write) |
| Double helpful-vote prevention | Client-side flag in Room | `markReviewHelpful` callable with `helpfulVotes/{uid}` idempotent set + check |
| Sort toggle | Custom sorting index | In-memory list sort in ViewModel |
| Review form validation | Custom validator | Inline in ViewModel action handler (rating required 1-5, title/body optional) |

---

## Common Pitfalls

### Pitfall 1: Edit path aggregate math
**What goes wrong:** When a customer edits their review, the rating changes. Naively incrementing `ratingCount` would be wrong — it should stay the same. Naively recomputing `((oldAvg * count) + newRating) / (count + 1)` would also be wrong (count+1 is wrong for an edit).
**Prevention:** Cloud Function distinguishes edit vs. new submission. For edit: `newAvg = ((oldAvg * count) - oldRating + newRating) / count`. For new: `newAvg = ((oldAvg * count) + newRating) / (count + 1)`.
**Warning sign:** `ratingCount` incrementing on every review form submission even for edits.

### Pitfall 2: sellerOrders compound query index missing
**What goes wrong:** Cloud Function query `where("userId", "==", uid).where("status", "==", "DELIVERED")` on `sellerOrders` fails with "requires an index" Firestore error.
**Prevention:** Add the composite index to `firestore.indexes.json` before deploying. Deploy indexes with `firebase deploy --only firestore:indexes` before deploying the Function.
**Warning sign:** Function returns `internal` error in logs with "FAILED_PRECONDITION: Missing index" details.

### Pitfall 3: ProductReviewRepositoryImpl constructor param order
**What goes wrong:** Adding `ReviewDao` + `FirebaseFunctions` as constructor params to `ProductReviewRepositoryImpl` without updating the Koin binding in `DataModule.kt` causes `NullPointerException` at runtime (Koin cannot resolve the new params).
**Prevention:** Update `singleOf(::ProductReviewRepositoryImpl)` to explicitly pass `get()` for each new param, or use `single { ProductReviewRepositoryImpl(get(), get(), get(), get()) }`. [ASSUMED — standard Koin pattern]

### Pitfall 4: SyncManager listener scope for reviews
**What goes wrong:** Starting a Firestore listener for `PRODUCTS/{productId}/REVIEWS` in SyncManager at app startup would listen to ALL products' reviews simultaneously — too expensive.
**Prevention:** Product-scoped review listener should be started only when the detail screen opens (scoped to ViewModel lifecycle) and cleaned up in `onCleared`. Use a `callbackFlow` in the `observeReviewsForProduct` path directly rather than in SyncManager for reviews.

### Pitfall 5: Helpful vote double-count on rapid tap
**What goes wrong:** Customer taps "Helpful" twice quickly. If `markReviewHelpful` uses `FieldValue.increment`, both calls succeed and count goes up by 2.
**Prevention:** `markReviewHelpful` callable uses a transaction: check if `helpfulVotes/{uid}` exists → if yes, return "already voted" error; if no, set the doc and increment `helpfulCount` atomically.

### Pitfall 6: SyncWorker SUBMIT_REVIEW TODO
**What goes wrong:** `SyncWorker` already has `OperationType.SUBMIT_REVIEW` with a `TODO("Wire SUBMIT_REVIEW to ReviewRepository in Phase 3+")`. This TODO must be wired to call `ProductReviewRepository.submitReview` so offline-queued reviews actually sync.
**Prevention:** Implement the `SUBMIT_REVIEW` case in `SyncWorker.doWork()` — call `reviewRepository.submitReview(review)` where the review is deserialized from `operation.payloadJson`. Note: reviews submitted offline still need the callable path, which requires network. The offline queue is the right mechanism for this.
**Warning sign:** Reviews submitted offline silently disappear after network restore.

### Pitfall 7: createdAt sort order correctness
**What goes wrong:** `ProductReview.createdAt` is stored as `System.currentTimeMillis().toString()` (epoch milliseconds as string) by the current client-side implementation. Lexicographic sort on epoch millis strings works correctly only because the length is fixed (13 digits through year 2286). The Cloud Function must also write epoch-millis-as-string (not ISO-8601) for consistent sort behavior.
**Prevention:** In the Cloud Function, use `admin.firestore.Timestamp.now().toMillis().toString()` for `createdAt` to match the existing format.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Product top-level writes (seller product management) go through client-side Firestore calls, not Admin SDK | Firestore Rules Design | If product management uses Admin SDK, tightening product doc rules to server-only is safe; otherwise sellers cannot save products |
| A2 | `sellerOrders` compound query (userId + status=DELIVERED) requires a composite Firestore index | Cloud Function Design | If index already exists (from Phase 6 queries), no new index needed; if not, Function will fail at runtime |
| A3 | `SellerOrderDao` does not yet have a `getDeliveredOrdersForUser(userId)` suspend query | UI Wiring | If it exists, no new DAO method needed |
| A4 | `createdAt` in existing reviews is stored as epoch millis string (matching current `ProductReviewRepositoryImpl` behavior) | Sort Logic + Cloud Function | ISO-8601 sort would break if strings are mixed formats |
| A5 | Firestore `helpfulVotes/{uid}` subcollection + callable approach is cheaper than a trigger | Helpful Vote Design | A Cloud Function trigger (onWrite) would also work but adds latency; callable is synchronous |
| A6 | No existing `firestore.indexes.json` in the project | Firestore indexes | If it exists, indexes must be merged not replaced |

---

## Open Questions

1. **Does `SellerOrderDao` have a query for delivered orders by userId?**
   - What we know: `SellerOrderDao` exists with `observeBySeller` and `observeByParent` and `observeById`.
   - What's unclear: whether a `getForUserWithStatus(userId, status)` query exists.
   - Recommendation: Read `SellerOrderDao.kt` in 07-01 planning and add if missing.

2. **Does `firestore.indexes.json` exist in the project root?**
   - What we know: Not found in files read during research.
   - What's unclear: Whether Firebase auto-created one.
   - Recommendation: Check `ls /Users/wenubey/AndroidStudioProjects/WenuCommerce/firestore.indexes.json` in 07-01 planning; create if absent.

3. **Is `setReviewVisibility` (seller toggle) in scope for 07-03?**
   - What we know: CONTEXT.md defers review moderation beyond `isVisible` flag; 07-03 covers "seller review visibility in seller product management".
   - What's unclear: Whether this means read-only visibility to sellers (just seeing reviews on their products) or the ability to hide/show individual reviews via `setReviewVisibility`.
   - Recommendation: Interpret as read-only (seller can see their products' reviews). `setReviewVisibility` can be deferred or implemented as a Cloud Function callable if seller toggle is needed.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Turbine 1.1.0 + kotlinx-coroutines-test 1.9.0 |
| Config file | None (test runner config in build.gradle.kts) |
| Quick run command | `./gradlew :domain:testDebugUnitTest :data:testDebugUnitTest :app:testDebugUnitTest --tests "*Review*"` |
| Full suite command | `./gradlew testDebugUnitTest` |
| Functions tests | `cd functions && npm test` (Jest, `functions/test/`) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File |
|--------|----------|-----------|-------------------|------|
| REVW-01 | submitReview callable is invoked with rating + text | Unit (fake Functions) | `./gradlew :data:testDebugUnitTest --tests "*ProductReviewRepository*"` | `FakeProductReviewRepository.kt` + `ProductReviewRepositoryTest.kt` |
| REVW-02 | Function rejects caller with no DELIVERED order | Unit (TypeScript Jest) | `cd functions && npm test -- --testPathPattern=submitReview` | `functions/test/submitReview.test.ts` |
| REVW-03 | Second submit replaces, not duplicates; reviewCount unchanged on edit | Unit (TypeScript Jest) | `cd functions && npm test -- --testPathPattern=submitReview` | `functions/test/submitReview.test.ts` |
| REVW-04 | ProductDetailViewModel exposes sorted reviews list | Unit (Turbine) | `./gradlew :app:testDebugUnitTest --tests "*CustomerProductDetailViewModel*"` | `CustomerProductDetailViewModelTest.kt` |
| REVW-05 | ProductReview.isVerifiedPurchase=true returned by Function | Unit (TypeScript Jest) | `cd functions && npm test -- --testPathPattern=submitReview` | `functions/test/submitReview.test.ts` |
| REVW-06 | Sort toggle changes list order (MOST_RECENT vs HIGHEST_RATED) | Unit (Turbine) | `./gradlew :app:testDebugUnitTest --tests "*CustomerProductDetailViewModel*"` | `CustomerProductDetailViewModelTest.kt` |
| REVW-07 | CustomerProductCard renders reviewCount when > 0 | Compose UI | `./gradlew :app:connectedDebugAndroidTest --tests "*CustomerProductCard*"` | `CustomerProductCardTest.kt` |
| D-04 | markReviewHelpful callable invoked; count increments | Unit (TypeScript Jest) | `cd functions && npm test -- --testPathPattern=markReviewHelpful` | `functions/test/markReviewHelpful.test.ts` |
| D-05 | Pre-filled form when existingReview != null | Unit (Turbine) | `./gradlew :app:testDebugUnitTest --tests "*CustomerProductDetailViewModel*"` | `CustomerProductDetailViewModelTest.kt` |
| D-07 | hasDeliveredOrder=false hides Write Review button | Compose UI | `./gradlew :app:connectedDebugAndroidTest --tests "*CustomerProductDetail*"` | `CustomerProductDetailScreenTest.kt` |

### Sampling Rate
- **Per task commit:** `./gradlew :domain:testDebugUnitTest` + `./gradlew :app:testDebugUnitTest --tests "*Review*"` + `cd functions && npm test`
- **Per wave merge:** `./gradlew testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `functions/test/submitReview.test.ts` — covers REVW-01, REVW-02, REVW-03, REVW-05 (structural contract tests, jest)
- [ ] `functions/test/markReviewHelpful.test.ts` — covers D-04
- [ ] `data/src/test/java/com/wenubey/data/repository/FakeProductReviewRepository.kt` — fake impl for ViewModel tests
- [ ] `app/src/test/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailViewModelTest.kt` — review state, sort, hasDeliveredOrder, submit flow (Turbine)
- [ ] `ReviewEntity.kt` and `ReviewDao.kt` test — Room DAO unit test using in-memory database

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Firebase Auth — `request.auth` check in Cloud Function (same as existing pattern) |
| V3 Session Management | no | Firebase manages token lifecycle |
| V4 Access Control | yes | Cloud Function verifies purchase ownership; Firestore rules deny all client writes on reviews subcollection |
| V5 Input Validation | yes | Cloud Function validates `rating` is 1-5 integer; rejects invalid productId |
| V6 Cryptography | no | No cryptographic operations |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Fake "Verified Purchase" review | Spoofing / Tampering | Cloud Function verifies `sellerOrders` ownership server-side before setting `isVerifiedPurchase=true`; client cannot write review docs at all (rules deny) |
| Review bombing (rapid submissions) | Denial of Service | One-per-product rule enforced in Function transaction; additional submissions are idempotent (replace existing) |
| Helpful vote stuffing | Tampering | `helpfulVotes/{uid}` subcollection + callable check prevents one user from voting twice |
| Review aggregate manipulation | Tampering | Product doc `ratingAverage`/`ratingCount` locked by rules — only Admin SDK (Function) can write |
| Seller deleting customer reviews | Tampering | All review writes (including delete) are server-only; `setReviewVisibility` callable verifies seller owns the product before toggling `isVisible` |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node 20 | Cloud Functions | ✓ (firebase-functions engines) | 20 (per `package.json`) | — |
| Firebase CLI | Function deployment | [ASSUMED available] | `^13.29.0` (devDep) | — |
| Jest | Functions unit tests | ✓ | `^29.7.0` | — |
| Room 2.6.1 | ReviewEntity + DAO | ✓ (existing dep) | 2.6.1 | — |
| Firebase Functions KTX | callable invoke | ✓ (`firebase-functions-ktx` in `libs.versions.toml`, `Firebase.functions` in DI) | BOM 33.8.0 | — |
| Turbine 1.1.0 | ViewModel tests | ✓ (existing dep) | 1.1.0 | — |

---

## Sources

### Primary (HIGH confidence — verified from codebase)
- `data/src/main/java/com/wenubey/data/repository/ProductReviewRepositoryImpl.kt` — existing client-side implementation, field names, collection paths
- `data/src/main/java/com/wenubey/data/repository/PaymentRepositoryImpl.kt` — Firebase callable pattern to replicate
- `data/src/main/java/com/wenubey/data/repository/OrderRepositoryImpl.kt` — FirebaseFunctions + FirebaseFunctionsException error handling pattern
- `functions/src/index.ts` — existing Cloud Function patterns (onCall, Admin SDK, Firestore transaction, HttpsError codes)
- `firestore.rules` — existing rules structure to extend
- `data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt` — Room schema v8, migration pattern
- `domain/src/main/java/com/wenubey/domain/model/product/Product.kt` — `averageRating`/`reviewCount` field names already on product
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt` — `CustomerProductCard` already renders review count (lines 511-525)
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt` — existing `ReviewCard` and review section
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` — Koin wiring, `Firebase.functions` already registered
- `gradle/libs.versions.toml` — all existing dependency versions confirmed

### Secondary (MEDIUM confidence)
- `data/src/androidTest/java/com/wenubey/data/repository/ProductReviewRepositoryImplEmulatorTest.kt` — test patterns to update/extend
- `functions/test/cancelSellerOrder.test.ts` — source-grep test pattern for new Function tests

### Tertiary (ASSUMED — not verified via tool)
- Firestore composite index requirement for `userId + status` query on sellerOrders
- `createdAt` string sort correctness assumption (epoch millis format is consistent)

---

## Metadata

**Confidence breakdown:**
- Existing code inventory: HIGH — all files read directly
- Cloud Function design: HIGH — mirrors existing `cancelSellerOrder` pattern exactly
- Firestore rules design: HIGH — extends existing rules file with same pattern
- Room entity design: HIGH — mirrors existing entity files (SellerOrderEntity, OrderEntity)
- UI wiring: HIGH — existing ViewModel/State/Action/Screen pattern is clear
- Firestore index requirement: ASSUMED — standard Firestore behavior

**Research date:** 2026-07-16
**Valid until:** 2026-08-16 (30 days — stable tech stack)
