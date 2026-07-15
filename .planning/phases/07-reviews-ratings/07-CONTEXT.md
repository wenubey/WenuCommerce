# Phase 7: Reviews & Ratings - Context

**Gathered:** 2026-07-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Customers who received a product (have a DELIVERED order for it) can leave a 1–5
star rating with optional text, editing their existing review in place rather
than creating duplicates. Product detail shows the aggregate rating (average +
count), the individual reviews with a "Verified Purchase" badge and a
recent/highest sort toggle, and product cards in browse/search show the review
count. Delivers REVW-01 through REVW-07 (plus an owner-approved "helpful votes"
addition, see D-04).

Not in this phase: seller replies to reviews, review photos, reporting/flagging,
review moderation beyond the existing visibility flag.
</domain>

<decisions>
## Implementation Decisions

### Review submission & trust (REVW-01/02/03)
- **D-01:** A minimal **`submitReview` Cloud Function is the ONLY write path**
  for reviews. It verifies the caller owns a DELIVERED `sellerOrders` doc that
  contains the productId (ownership + status + product-in-order membership),
  enforces one review per (customer, product) — an edit REPLACES in place —
  sets `isVerifiedPurchase`, writes the review, and updates the product rating
  aggregate in the SAME transaction. Mirrors the Phase 6 / this-session
  server-authoritative order-hardening pattern.
- **D-02:** Firestore rules lock the reviews subcollection
  (`PRODUCTS/{id}/REVIEWS`) writes AND the product rating aggregate fields to
  **server-only** (client create/update/delete = false); client reads/observe
  stay allowed. The existing client-side `ProductReviewRepositoryImpl.submitReview`
  transaction is re-routed through the callable; reads/observe remain client-side.
  (Chosen over a pure client+rules path because rules cannot verify
  product-in-order membership or one-per-product, and cannot safely grant
  customers write access to seller-owned product docs.)

### Aggregate rating & review count (REVW-04/07)
- **D-03:** `ratingAverage` + `ratingCount` are **denormalized onto the product
  document**, recomputed inside the submitReview Cloud Function transaction.
  Browse/search product cards read `ratingCount` (and average) directly from the
  product doc — no per-product subcollection counting.

### Helpful votes — owner-approved addition beyond REVW-01..07
- **D-04:** **In scope this phase.** Review cards get a "Helpful" action + count
  using the existing `helpfulCount` / `markReviewHelpful`. Double-voting is
  prevented per-user. Because review docs are server-only now (D-02), the helpful
  increment also goes through the server (Cloud Function or a narrowly-scoped
  rule) — researcher to choose the cheapest safe mechanism. NOTE: this extends
  beyond REVW-01..07; explicitly approved by the owner.

### Edit & sort UX (REVW-03/06)
- **D-05:** If the customer already reviewed the product, the "Write a Review"
  entry opens the form **pre-filled** and submission replaces in place (no
  duplicate).
- **D-06:** The reviews list has a **segmented sort toggle** — default "Most
  recent", option "Highest rated"; ties broken by recency.

### Review gating UI (REVW-02)
- **D-07:** The "Write a Review" prompt on product detail is shown only when the
  customer has a DELIVERED order for that product; hidden/disabled otherwise. The
  Cloud Function is the authority; the UI gate is a UX affordance (defense in
  depth, not the security boundary).

### Claude's Discretion
- Exact Firestore field names for the aggregate, the helpful-vote server
  mechanism (Function vs scoped rule), aggregate-recompute concurrency handling,
  and Room caching of reviews (offline-first per the established pattern) are left
  to research/planning.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 7 requirements & roadmap
- `.planning/REQUIREMENTS.md` §REVW-01..07 — the seven review requirements
- `.planning/ROADMAP.md` §"Phase 7: Reviews & Ratings" — goal, success criteria, plan outline (07-01/02/03)

### Server-authoritative write pattern (carried from Phase 6 + this session's hardening)
- `.planning/phases/06-order-tracking/06-CONTEXT.md` — server-authoritative writes, Firestore rules, `sellerOrders`/DELIVERED status model
- `.planning/phases/04-checkout-payments/04-CONTEXT.md` — `createPaymentIntent` Cloud Function pattern
- `functions/src/index.ts` — existing Cloud Functions (`createPaymentIntent`, `stripeWebhook`, `onOrderStatusChange`) — copy the server-authoritative + idempotency + verification patterns
- `firestore.rules` — existing rules (orders/sellerOrders server-only writes, owner-scoped reads) to EXTEND for reviews + product-rating fields

### Room-first + architecture
- `.planning/phases/01-room-foundation/01-CONTEXT.md` — Room-first read, schema export, Flow observation
- `.planning/codebase/ARCHITECTURE.md` — multi-module MVVM + UDF rules
- `.planning/codebase/STACK.md` — Koin / Room / Firebase / Stripe versions
- `.planning/codebase/CONVENTIONS.md` — naming + structure conventions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets (already built — this phase EXTENDS + hardens these)
- `domain/src/main/java/com/wenubey/domain/model/product/ProductReview.kt` — review model (id, productId, reviewerId, reviewerName, reviewerPhotoUrl, purchaseId, rating, title, body, isVerifiedPurchase, helpfulCount, isVisible, createdAt, updatedAt) + `toMap()`
- `domain/src/main/java/com/wenubey/domain/repository/ProductReviewRepository.kt` — observeReviewsForProduct, getReviewsForProduct, submitReview, markReviewHelpful, setReviewVisibility
- `data/src/main/java/com/wenubey/data/repository/ProductReviewRepositoryImpl.kt` — CURRENT client-side Firestore transaction (PRODUCTS/{id}/REVIEWS subcollection, client one-per-purchase dedup + client product-aggregate update). `submitReview` to be re-routed through the new Cloud Function; observe/read stay.
- `data/src/androidTest/java/com/wenubey/data/repository/ProductReviewRepositoryImplEmulatorTest.kt` — existing emulator test to update
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt` + `CustomerProductDetailViewModel.kt` + `CustomerProductDetailState.kt` — where reviews surface today
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt` + product-card composables — where the review count (REVW-07) must appear
- `SellerOrder` (domain + `SellerOrderEntity`) — DELIVERED status, `userId` (denormalized this session), `items[].productId` → the verification source for the Cloud Function

### Established Patterns
- Server-authoritative writes via Cloud Functions + Firestore rules server-only (Phase 6 + this session)
- Room-first read/observe, synced from Firestore; schema export + explicit migrations
- StateFlow UDF ViewModels, type-safe Navigation Compose, Material 3, Koin DI

### Integration Points
- New `submitReview` (and possibly helpful-vote) callable in `functions/src/index.ts`
- Reviews + product-rating rules block in `firestore.rules`
- Product-doc aggregate fields (ratingAverage / ratingCount) read by product-card composables in browse/search
</code_context>

<specifics>
## Specific Ideas

- Reuse the Phase-6 order-hardening shape verbatim: verify-in-a-Cloud-Function,
  idempotent server transaction, Firestore rules server-only for the sensitive
  writes, owner/eligibility-scoped client reads.
- Editing = the same "Write a Review" affordance, pre-filled, replacing the
  existing review (one per customer per product).
</specifics>

<deferred>
## Deferred Ideas

- Seller replies to reviews — future phase (roadmap 07-03 covers seller
  *visibility* of reviews on their products, read-only; replies are new scope).
- Review photos / media attachments — future.
- Reporting / flagging abusive reviews + moderation queue — future (the existing
  `isVisible` flag + `setReviewVisibility` is the current minimal lever).

None of the above blocks REVW-01..07.

</deferred>

---

*Phase: 7-reviews-ratings*
*Context gathered: 2026-07-16*
