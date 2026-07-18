# Phase 9: Seller Storefronts & Favorite Sellers - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-18
**Phase:** 09-seller-storefronts-favorite-sellers
**Areas discussed:** Storefront entry point, Storefront photo, Follow authentication, Followed-sellers list placement

---

## Storefront entry point (FAVS-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Tap seller name | Seller name on product cards + product detail clickable → storefront; reuses existing sellerName field, low friction | ✓ |
| 'Visit store' button | Explicit button on product detail; more obvious but only from detail | |
| Tap seller logo/avatar | Avatar chip on card/detail; nice branding but smaller tap target | |

**User's choice:** Tap seller name
**Notes:** Single canonical entry across card / search result / product detail. Requires adding an `onSellerClick(sellerId)` callback to `CustomerProductCard`.

---

## Storefront photo (FAVS-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse profile photo | Use existing `User.profilePhotoUri`; no new field/upload; smallest scope | ✓ |
| Dedicated shop logo | New `businessLogoUri` + seller upload flow; more branding but expands scope | |

**User's choice:** Reuse profile photo
**Notes:** Dedicated shop logo deferred to a future phase.

---

## Follow authentication (FAVS-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Require sign-in | Account-bound social action; prompt sign-in if logged out; clean counts, simpler sync | ✓ |
| Allow anonymous | Mirror wishlist (userId='' + migrate on login); consistent but muddies counts + adds migration | |

**User's choice:** Require sign-in
**Notes:** Deliberately diverges from the wishlist's anonymous support — removes the anon→auth migration path, simplifying the repository impl.

---

## Followed-sellers list placement (FAVS-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Profile menu item | Row in customer Profile like Order History / Notifications; no bottom-nav change | ✓ |
| New bottom-nav tab | Dedicated tab; most prominent but crowds the existing bar | |
| From storefront only | Link after following; minimal but low discoverability | |

**User's choice:** Profile menu item
**Notes:** Standalone `FollowedSellers` route registered like `NotificationHistory`; reached from the Profile screen.

---

## Claude's Discretion

Recorded as CD-01..CD-05 in CONTEXT.md (grounded in the codebase scout, not asked of the user):
- CD-01 Follow storage clones the wishlist offline-first pattern (`FollowedSellersRepository`, Room `followed_sellers` + Firestore subcollection, snapshot model), minus the anonymous branch (per the sign-in decision).
- CD-02 Fire-and-forget sync (no PendingOperation queue), matching the wishlist.
- CD-03 `followerCount` maintained server-side atomically (trigger preferred over callable); seller sees count only.
- CD-04 Seller aggregate rating live-derived across ACTIVE products (no denormalization unless perf demands).
- CD-05 Business-header card with the Follow button as the storefront's first item; wrap the stub screen in Scaffold + TopAppBar + state machine.

## Deferred Ideas

- Dedicated shop logo / branded banner (`businessLogoUri` + upload) — future phase.
- Seller analytics dashboard — out of scope per PROJECT.md; FAVS-04 is a count only.
- Notify customers when a followed seller posts a new product/discount — future, ties into Phase 8 notifications.
- Seller-side follower list / messaging — contradicts FAVS-04 privacy; not planned.
