# Phase 9: Seller Storefronts & Favorite Sellers - Context

**Gathered:** 2026-07-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Customers can **discover** seller profiles (a customer-facing storefront showing the seller's
business name, bio, photo, aggregate rating, follower count, and active product listings),
**follow/unfollow** sellers with optimistic UI, and **browse a list of the sellers they follow**.
Sellers see their own **follower count** (the number only — never individual follower identities).

Requirements: **FAVS-01** (follow/unfollow from storefront), **FAVS-02** (followed-sellers list),
**FAVS-03** (storefront shows business name, bio, photo, rating, follower count, product listings),
**FAVS-04** (seller sees follower count, not identities).

**In scope:** the storefront header + product grid, the follow relationship (customer side),
the followed-sellers list, and a minimal follower-count surface for the seller.

**Out of scope (do NOT expand into these):** a full seller analytics dashboard (explicitly
deferred to a future milestone in PROJECT.md — FAVS-04 is a single count stat, not analytics);
exposing follower identities to sellers; buyer↔seller chat; any new product/browse capability.
</domain>

<decisions>
## Implementation Decisions

### Storefront discovery / entry point (FAVS-03 success criterion)
- **D-01 (LOCKED):** Customers open a seller's storefront by **tapping the seller name**. Make
  the seller name clickable on the customer product card (`CustomerProductCard` in
  `CustomerHomeScreen.kt`, also used by search results) and on the product detail screen
  (`CustomerProductDetailScreen.kt`) → navigate to the existing `SellerStorefront(sellerId)`
  route. Add an `onSellerClick(sellerId)` callback threaded through the card. No separate
  "Visit store" button, no avatar-chip tap.

### Storefront branding / photo (FAVS-03)
- **D-02 (LOCKED):** The storefront header uses the seller's existing **`User.profilePhotoUri`**
  (already captured at onboarding; `Product.sellerLogoUrl` is already denormalized from it). Do
  NOT add a dedicated `businessLogoUri` / seller-logo upload flow this phase — that is a deferred
  idea. Header also shows `BusinessInfo.businessName` and `BusinessInfo.businessDescription`
  (bio); `businessDescription` is a non-null String — if empty, hide the bio section gracefully.

### Follow authentication (FAVS-01)
- **D-03 (LOCKED):** Following a seller **requires sign-in**. If a logged-out customer taps
  Follow, prompt sign-in (reuse the existing auth-gate affordance used elsewhere, e.g. the
  product-detail login prompt) — do NOT persist anonymous follows. This diverges deliberately
  from the wishlist (which allows anonymous `userId=''` + `syncAnonymousOnLogin`): follows are an
  account-bound social action, and requiring auth keeps follower counts clean and removes the
  anon→auth migration path entirely (simpler than the wishlist template).

### Followed-sellers list placement (FAVS-02)
- **D-04 (LOCKED):** The "Followed Sellers" list is reached via a **row in the customer Profile**
  (`CustomerProfileScreen.kt`), styled like the existing Order History / Notifications affordance
  rows. Register a standalone type-safe `FollowedSellers` route in `AppNavigationObjects.kt` +
  `TabNavRoutes.kt` (same pattern as `NotificationHistory`). Do NOT add a new bottom-nav tab (the
  customer bar is already Home / Cart+Wishlist / Notifications / Profile).

### Claude's Discretion (recommendations for research/planning — grounded in scout, not locked)
- **CD-01 — Follow storage = clone the wishlist offline-first pattern.** Room `followed_sellers`
  table as source of truth + Firestore subcollection `users/{uid}/followed_sellers/{sellerId}` +
  optimistic UI. Domain model `FollowedSeller(sellerId, sellerName, sellerLogoUrl, followedAt)`
  snapshotting seller name/photo at follow time (like `WishlistItem` snapshots product fields) so
  the list renders offline. Repository named `FollowedSellersRepository` (mirrors
  `WishlistRepository`). Because of D-03 (auth required), OMIT the anonymous-user branch and
  `syncAnonymousOnLogin` — the impl is simpler than `WishlistRepositoryImpl`.
- **CD-02 — Sync = fire-and-forget async (NOT the PendingOperation/SyncWorker queue).** Matches
  the wishlist rationale (read-heavy, non-critical, user-tolerant of transient sync failure);
  Room stays correct, Firestore is best-effort.
- **CD-03 — Follower count (FAVS-04) = denormalized counter maintained server-side, atomically.**
  Store `followerCount` on the seller's `USERS` doc (or nested in `BusinessInfo`). Maintain it
  with `FieldValue.increment(±1)` on follow/unfollow. Two candidate mechanisms for research to
  choose: (a) a Firestore **trigger** on the `followed_sellers` subcollection write (mirrors the
  Phase 8 `onOrderStatusChange`/`onNewReview` trigger style — keeps the client on a plain
  Firestore write like the wishlist), or (b) a **callable** (mirrors Phase 7 `submitReview`).
  Prefer (a) trigger for consistency with the wishlist direct-write pattern + it enforces
  FAVS-04 privacy naturally (the follow doc lives under the *customer's* space; the seller only
  ever reads the aggregate count). Either way the seller sees the COUNT ONLY — never a follower
  list. **This adds a Firestore-schema field + a Cloud Function + a Room migration — all require
  user approval at plan/execute per CLAUDE.md (see canonical refs).**
- **CD-04 — Seller aggregate rating (FAVS-03) = live-derived across the seller's ACTIVE products.**
  Compute in `SellerStorefrontViewModel` by averaging `Product.averageRating` (weighted by
  `Product.reviewCount`) over the seller's ACTIVE products already loaded via
  `getStorefrontProducts(sellerId)`. No new schema, no trigger, always fresh; product counts per
  seller are small. Denormalizing a `sellerAverageRating` field is the fallback only if perf
  demands it. Scope: ACTIVE products only (matches customer-visible storefront). Replace the
  hardcoded `'4.8 (127 reviews)'` placeholder in `SellerProfileScreen.kt` and the placeholder
  stats in `SellerDashboardScreen.kt` with the live value.
- **CD-05 — Storefront header = a business-header card as the first item in the storefront's
  LazyColumn/grid; Follow/Unfollow button lives in that header** (prominent), not the TopAppBar.
  Wrap `SellerStorefrontScreen` in the standard Scaffold + TopAppBar + state-machine pattern
  (analog: `CustomerOrderDetailScreen` / `NotificationHistoryScreen`) and add the missing
  `onNavigateBack` param → `navController.navigateUp()`. Final visuals can be refined by
  `/gsd:ui-phase` if run.

### Reuse the existing storefront scaffolding
- **D-05 (LOCKED):** Build ON the existing stubs — do NOT create parallel files. `SellerStorefront`
  route already exists (`AppNavigationObjects.kt` ~line 67, registered in `TabNavRoutes.kt`
  ~lines 183–189); `SellerStorefrontScreen/State/ViewModel` exist (content-only) with tests
  (`SellerStorefrontScreenTest.kt`, `SellerStorefrontViewModelTest.kt`);
  `ProductRepository.getStorefrontProducts(sellerId)` exists. Extend these, keep their tests green.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & scope (authoritative)
- `.planning/ROADMAP.md` — Phase 9 section: goal, FAVS-01..04, success criteria, tentative plans 09-01/02/03
- `.planning/REQUIREMENTS.md` — FAVS-01..04 rows (lines ~83–86, traceability ~225–228)
- `.planning/PROJECT.md` — Constraints + "Out of Scope": **seller analytics dashboard is deferred** (bounds FAVS-04 to a count only); tech-stack lock (Kotlin/Compose M3, Room source-of-truth + Firestore, Koin, no framework changes)

### Clone-me templates (the patterns this phase mirrors)
- `domain/src/main/java/com/wenubey/domain/repository/WishlistRepository.kt` — repo interface shape for `FollowedSellersRepository`
- `data/src/main/java/com/wenubey/data/repository/WishlistRepositoryImpl.kt` — Room-first + Firestore-subcollection + optimistic-UI offline pattern (drop the anonymous branch per D-03)
- `data/src/main/java/com/wenubey/data/local/entity/WishlistItemEntity.kt`, `.../dao/WishlistItemDao.kt`, `.../mapper/WishlistItemMapper.kt` — entity/DAO/mapper shape for `followed_sellers`
- `domain/src/main/java/com/wenubey/domain/model/WishlistItem.kt` — snapshot-model shape for `FollowedSeller`
- `app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/{WishlistViewModel,WishlistState,WishlistAction}.kt` + `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerWishlistScreen.kt` — VM/State/Action + list-screen shape for `FollowedSellers*`
- `app/src/main/java/com/wenubey/wenucommerce/core/components/WishlistHeartButton.kt` — optimistic toggle-button pattern for a reusable Follow button
- `functions/src/index.ts` — Phase 7 `submitReview` atomic aggregate + Phase 8 `onOrderStatusChange`/`onNewReview` trigger style (models for the follower-count counter, CD-03)

### Extend-me existing assets (storefront + models + surfaces)
- `app/src/main/java/com/wenubey/wenucommerce/seller/seller_storefront/{SellerStorefrontScreen,SellerStorefrontState,SellerStorefrontViewModel}.kt` + tests `app/src/{test,androidTest}/.../seller_storefront/*` — the stubs to build on (D-05)
- `domain/src/main/java/com/wenubey/domain/model/user/User.kt` + `.../model/onboard/BusinessInfo.kt` — seller profile fields (businessName, businessDescription, profilePhotoUri); target for `followerCount` (CD-03)
- `domain/src/main/java/com/wenubey/domain/model/product/Product.kt` — `sellerId`, `sellerName`, `sellerLogoUrl`, `averageRating`, `reviewCount` (drives CD-04 + D-01)
- `app/src/main/java/com/wenubey/wenucommerce/navigation/{AppNavigationObjects,TabNavRoutes}.kt` — add `FollowedSellers` route; seller-name-click nav (D-01, D-04)
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt` (`CustomerProductCard`) + `.../customer/customer_products/CustomerProductDetailScreen.kt` — add `onSellerClick` (D-01)
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerProfileScreen.kt` — add "Followed Sellers" affordance row (D-04)
- `app/src/main/java/com/wenubey/wenucommerce/seller/SellerProfileScreen.kt` + `.../seller/seller_dashboard/SellerDashboardScreen.kt` — replace hardcoded rating/stat placeholders with live follower count + aggregate rating (FAVS-04, CD-04)
- `data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt` + `data/src/androidTest/java/com/wenubey/data/local/WenuCommerceMigrationTest.kt` — Room v10→v11 migration for `followed_sellers` (+ any `followerCount` cache); add a migration test case
- `domain/.../repository/{FirestoreRepository,ProfileRepository}.kt` + impls — seller fetch (`getUser(sellerId)` already serves storefront) / where seller-aggregate reads may attach

### Data-model / schema / billable changes requiring user approval (surface at plan-phase)
- New Room table `followed_sellers` + migration **v10→v11** (Room migration change)
- New Firestore field `followerCount` on `USERS` docs + subcollection `users/{uid}/followed_sellers` (Firestore schema change)
- New Cloud Function (follower-count trigger or callable) + Firestore security rules for follows (billable Functions + rules change)
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Storefront is already scaffolded** (route + Screen/State/ViewModel + `getStorefrontProducts` + tests) — extend, don't recreate (D-05).
- **Wishlist feature** = the complete offline-first follow template (entity/DAO/mapper/repo/VM/state/screen/heart-button).
- **`CustomerProductCard`** (in `CustomerHomeScreen.kt`) renders the storefront product grid and is where the seller-name click originates.
- **`User` + `BusinessInfo`** already hold businessName, businessDescription, profilePhotoUri — the storefront header needs no new *display* fields, only `followerCount`.
- **Phase 7 `submitReview` / Phase 8 notification triggers** = the server-side atomic-counter template for `followerCount`.

### Established Patterns
- Offline-first: Room = source of truth, Firestore = cloud sync; wishlist uses **fire-and-forget** (no PendingOperation queue) — follows mirror this (CD-02).
- Type-safe `@Serializable` route objects only (no string routes); standalone `composable<Route>{}` destinations like `NotificationHistory`.
- Screen shape: Scaffold + TopAppBar + `StateFlow<XxxUiState>` state-machine (loading/empty/error/populated) + UDF actions; analogs `CustomerOrderDetailScreen`, `NotificationHistoryScreen`.
- Aggregates denormalized atomically server-side (product `averageRating`/`reviewCount` via `submitReview`).

### Integration Points
- Product card / product detail seller name → `SellerStorefront(sellerId)` (D-01).
- Customer Profile row → `FollowedSellers` route (D-04).
- Follow toggle → `FollowedSellersRepository` (Room + Firestore subcollection) → counter maintained server-side → seller `SellerDashboardScreen`/`SellerProfileScreen` reads the count (FAVS-04).
- Storefront header rating ← averaged over `getStorefrontProducts(sellerId)` active products (CD-04).
</code_context>

<specifics>
## Specific Ideas

- Seller-name tap is the single canonical entry to a storefront (not a button) — keep it lightweight and consistent across card, search result, and product detail.
- Seller sees only a number for followers; the follow record lives under the customer's space so identities never surface to the seller (FAVS-04 privacy is structural, not just UI).
- Reuse the seller's personal profile photo as the shop image for now — one image, no extra upload step.
</specifics>

<deferred>
## Deferred Ideas

- **Dedicated shop logo / branded storefront banner** (`businessLogoUri` on `BusinessInfo` + upload flow) — considered for D-02, deferred; revisit if sellers want brand separation from their personal photo.
- **Seller analytics dashboard** (follower trends, demographics, per-product breakdowns) — explicitly out of scope in PROJECT.md; FAVS-04 delivers only the raw count.
- **Notify a customer when a followed seller posts a new product / discount** — a natural follow-up that ties into Phase 8 notifications; its own future scope, not this phase.
- **Seller-side follower list / messaging** — contradicts FAVS-04 privacy; not planned.

### None folded from todos — no pending todos matched Phase 9.
</deferred>

---

*Phase: 09-seller-storefronts-favorite-sellers*
*Context gathered: 2026-07-18*
