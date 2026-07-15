# Phase 7: Reviews & Ratings - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-16
**Phase:** 7-reviews-ratings
**Areas discussed:** Verified-purchase enforcement, Aggregate rating + count freshness, Helpful votes scope, Edit + sort UX

---

## Verified-purchase enforcement (REVW-02/03)

| Option | Description | Selected |
|--------|-------------|----------|
| Server-verified Cloud Function + rules | submitReview Function verifies DELIVERED sellerOrder + one-per-product, writes review, updates aggregate; reviews server-only in rules | ✓ (after refinement) |
| Client-side + rules verify | Keep client write, rules get() the referenced purchaseId | |

**User's choice:** Initially "client-side + rules", then switched to the minimal Cloud Function after the rules-limitation heads-up.
**Notes:** Flagged two concrete walls with the pure client+rules path — (1) Firestore rules cannot verify product-in-order membership (no array predicate) nor one-per-product atomically; (2) letting customers write the seller-owned product aggregate is not safely rule-constrainable. User then chose the "Minimal submitReview Cloud Function" refinement, which verifies DELIVERED + product membership + one-per-product and updates the aggregate in the same server transaction. Mirrors the Phase 6 order-hardening we shipped this session.

---

## Aggregate rating + count freshness (REVW-04/07)

| Option | Description | Selected |
|--------|-------------|----------|
| Same submitReview transaction + denormalize onto product | avg/count recomputed server-side in the submit transaction, written to product doc | ✓ |
| Separate onReviewWrite Firestore trigger | dedicated trigger recomputes/denormalizes | |
| Compute on read (no denormalization) | avg/count computed when reading; cards would need subcollection counts | |

**User's choice:** Same submitReview transaction + denormalize onto the product doc.
**Notes:** Product cards read ratingCount from the product doc — cheap browse/search (REVW-07). Consistent with the single-Cloud-Function trust decision.

---

## Helpful votes scope

| Option | Description | Selected |
|--------|-------------|----------|
| Defer — leave dormant | helpfulCount/markReviewHelpful exist but no UI this phase | |
| Complete this phase | Helpful button + count + prevent double-vote | ✓ |

**User's choice:** Complete this phase.
**Notes:** Scope addition beyond REVW-01..07, explicitly approved by the owner. Because review docs become server-only, the helpful increment also needs a server path (Function or scoped rule) — left to research.

---

## Edit + sort UX (REVW-03/06)

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-filled edit + sort toggle (recent default / highest), tie-break recency | Edit reopens the form pre-filled and replaces in place; segmented sort toggle | ✓ |
| A different approach | Custom edit/sort design | |

**User's choice:** Pre-filled edit + sort toggle, default Most recent, option Highest rated, ties by recency.
**Notes:** None.

---

## Claude's Discretion

- Exact aggregate field names, the helpful-vote server mechanism (Function vs scoped rule), aggregate-recompute concurrency handling, and Room caching of reviews.

## Deferred Ideas

- Seller replies to reviews, review photos/media, reporting/flagging + moderation queue — all future phases.
