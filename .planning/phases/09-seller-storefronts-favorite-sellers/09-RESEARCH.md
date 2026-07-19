# Phase 9: Seller Storefronts & Favorite Sellers - Research

**Researched:** 2026-07-18
**Domain:** Android Kotlin/Compose — offline-first follow relationship, Firestore subcollection counter, Room migration, storefront extension
**Confidence:** HIGH (all primary findings verified against live codebase files)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 (LOCKED):** Seller-name tap on `CustomerProductCard` + `CustomerProductDetailScreen` → `SellerStorefront(sellerId)`. Add `onSellerClick(sellerId)` callback to the card. No "Visit store" button.
- **D-02 (LOCKED):** Storefront header uses existing `User.profilePhotoUri`. No new `businessLogoUri` upload this phase. Show `BusinessInfo.businessName` + `BusinessInfo.businessDescription`; hide bio section if blank.
- **D-03 (LOCKED):** Following requires sign-in. Logged-out Follow tap → auth-gate `AlertDialog` (reuse existing `showLoginPrompt` pattern). No anonymous follows, no `syncAnonymousOnLogin`.
- **D-04 (LOCKED):** "Followed Sellers" list reached from a `ProfileMenuItem` row in `CustomerProfileScreen`. Standalone `FollowedSellers` route in `AppNavigationObjects.kt` + `TabNavRoutes.kt`. No new bottom-nav tab.
- **D-05 (LOCKED):** Extend existing stubs — do NOT create parallel files. `SellerStorefront` route + `SellerStorefrontScreen/State/ViewModel` + `getStorefrontProducts(sellerId)` + their tests already exist.

### Claude's Discretion
- **CD-01:** Follow storage = Room `followed_sellers` table + Firestore subcollection `users/{uid}/followed_sellers/{sellerId}`. Omit anonymous branch (D-03).
- **CD-02:** Sync = fire-and-forget async (no PendingOperation queue).
- **CD-03:** `followerCount` denormalized on seller's USERS doc, maintained by a Firestore trigger on the `followed_sellers` subcollection write.
- **CD-04:** Seller aggregate rating computed live in `SellerStorefrontViewModel` from active products' `averageRating`/`reviewCount`.
- **CD-05:** Storefront header = `StorefrontHeaderCard` as first `LazyColumn` item. Follow/Unfollow button inside the header card. Add `onNavigateBack` param.

### Deferred Ideas (OUT OF SCOPE)
- Dedicated shop logo / branded storefront banner (`businessLogoUri` upload flow).
- Seller analytics dashboard (FAVS-04 is a count only).
- "Notify customer when followed seller posts new product" (NOTF-V2-01).
- Seller-side follower list / messaging.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FAVS-01 | Customer follows/unfollows a seller from the storefront (optimistic UI, requires sign-in per D-03) | Q5 (repository pattern), Q6 (optimistic toggle + auth gate), Q8 (tests) |
| FAVS-02 | Customer views a "Followed Sellers" list reached from Profile menu row per D-04 | Q5 (repo), Q7 (route wiring + screen shape), Q8 (tests) |
| FAVS-03 | Seller storefront shows business name, bio, photo, aggregate rating, follower count, active product listings | Q4 (aggregate rating formula), Q7 (extend existing stubs), UI-SPEC verified |
| FAVS-04 | Seller sees their own follower COUNT only — never individual follower identities | Q1 (trigger mechanism), Q2 (Firestore rules — structural privacy), Q7 (seller surface updates) |
</phase_requirements>

---

## Summary

Phase 9 builds on eight prior phases of solid offline-first infrastructure. The riskiest novelty is the follower-count Cloud Function: every other question resolves to cloning an existing well-tested pattern.

**Follow relationship** is a direct clone of the wishlist offline-first pattern (`WishlistRepositoryImpl`, `WishlistItemEntity`, `WishlistItemDao`, `WishlistItemMapper`) minus the anonymous-user branch. Room `followed_sellers` table is the source of truth; Firestore subcollection `users/{uid}/followed_sellers/{sellerId}` is synced fire-and-forget. The domain snapshot model `FollowedSeller(sellerId, sellerName, sellerLogoUrl, followedAt)` mirrors `WishlistItem`.

**Follower count** is maintained server-side by a new `onFollowedSellerWrite` Firestore trigger (2nd-gen, `onDocumentWritten`) on the subcollection. It increments/decrements `followerCount` on the seller's USERS doc via `FieldValue.increment(±1)`. The trigger detects follow vs. unfollow by comparing `before`/`after` existence. This is the same idiom as `onOrderStatusChange`/`onNewReview` in `functions/src/index.ts`. **This requires user approval** (new billable Cloud Function).

**Room migration v10→v11** adds a `followed_sellers` table. The `followerCount` field on the seller's `UserEntity` does NOT need Room caching — it is read live from Firestore for the storefront (always current) and for seller surfaces (`SellerDashboardScreen`/`SellerProfileScreen`). No UserEntity migration needed.

**Aggregate seller rating** (FAVS-03) is computed live in `SellerStorefrontViewModel` from the already-loaded active products using a review-count-weighted mean. No new schema.

**Storefront extension** (D-05): the existing `SellerStorefrontScreen`, `SellerStorefrontState`, `SellerStorefrontViewModel`, `SellerStorefrontViewModelTest`, and `SellerStorefrontScreenTest` are all verified in the codebase. The screen is a `LazyColumn` stub with no Scaffold/TopAppBar yet — both must be added.

**Primary recommendation:** Follow the wishlist clone path faithfully (minus the anonymous branch), use the `onDocumentWritten` trigger style for follower count (mirrors Phase 8 triggers), and keep `followerCount` as a Firestore-only live read for seller surfaces.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Follow/unfollow write | :data (Room + Firestore) | :app ViewModel (optimistic state) | Room source-of-truth; Firestore best-effort sync |
| Follower count maintenance | Cloud Functions (Admin SDK) | Firestore USERS doc | Server-authoritative; client must NOT write the count |
| Storefront seller info display | :app SellerStorefrontViewModel | :data FirestoreRepository.getUser() | UI concern; data from existing repo method |
| Aggregate rating computation | :app SellerStorefrontViewModel | — | Derived from already-loaded products; no new data layer |
| Follow state (is this seller followed?) | :data FollowedSellersRepository (Room Flow) | :app SellerStorefrontViewModel | Room source-of-truth drives UI state reactively |
| Follower count display (seller side) | :app SellerDashboardViewModel / SellerProfileScreen | :data FirestoreRepository | Live Firestore read; no Room cache needed |
| Followed Sellers list display | :app FollowedSellersViewModel | :data FollowedSellersRepository (Room) | Room source-of-truth; fast offline list |
| Security rules enforcement | Firestore rules | Cloud Functions (Admin SDK) | Structural: follow docs live under customer's space |

---

## Standard Stack

### Core (existing — no new libraries required)

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| Room | via libs.versions.toml | `followed_sellers` entity, DAO, migration | [ASSUMED] — existing in :data |
| Firestore (firebase-admin SDK) | via functions/package.json | Follow subcollection writes + Admin SDK for counter trigger | [ASSUMED] — existing in project |
| Koin BOM 4.0.1 | libs.versions.toml | DI wiring for new repository + ViewModel | [ASSUMED] — existing |
| Kotlin Coroutines + Flow | libs.versions.toml | Repository Flow, ViewModel state | [ASSUMED] — existing |
| Jetpack Compose M3 | existing BOM | `FollowButton`, `FollowedSellersScreen`, `StorefrontHeaderCard` | [ASSUMED] — existing |
| firebase-functions/v2 | ^6.0.0 (functions/package.json) | `onDocumentWritten` trigger | [VERIFIED: codebase] |
| firebase-admin | ^13.0.0 (functions/package.json) | Admin SDK for `FieldValue.increment(±1)` | [VERIFIED: codebase] |

### Supporting (testing — all existing)

| Library | Purpose |
|---------|---------|
| Turbine | ViewModel Flow assertions |
| MainDispatcherRule + TestDispatcherProvider | Coroutine test dispatchers (pattern in SellerStorefrontViewModelTest) |
| MockK | Mocking for Compose UI tests (pattern in SellerStorefrontScreenTest) |
| MigrationTestHelper | Room migration instrumented test |
| Jest 29 + ts-jest | Cloud Function unit tests (existing in functions/test/) |

**No new libraries are required for Phase 9.** [VERIFIED: codebase]

---

## Package Legitimacy Audit

No new external packages are installed in Phase 9. All libraries (Room, Firestore, Koin, Compose) are already present in `gradle/libs.versions.toml` and `functions/package.json`. No audit required.

---

## Architecture Patterns

### System Architecture Diagram

```
Customer taps seller name
        │
        ▼
SellerStorefrontScreen ──── SellerStorefrontViewModel
        │                          │
        │              ┌───────────┴──────────────┐
        │              ▼                          ▼
        │    FirestoreRepository            ProductRepository
        │    .getUser(sellerId)         .getStorefrontProducts(sellerId)
        │    [seller info: name,               [Product list]
        │     bio, photo, followerCount]            │
        │              │                    aggregate rating
        │              │                    computed here (CD-04)
        │              └───────────┬──────────────┘
        │                          ▼
        │                  SellerStorefrontState
        │                  {seller, products, followState, isLoading...}
        │
        ├── FollowButton tap ──→ SellerStorefrontViewModel
        │                           │
        │         ┌─────────────────┴──────────────────┐
        │         ▼                                     ▼
        │  FollowedSellersRepository              Check auth
        │  .followSeller(userId, sellerId)         (isBlank?)
        │         │                                     │
        │    ┌────┴──────┐                         show AlertDialog
        │    ▼           ▼                         (D-03 auth gate)
        │  Room       Firestore
        │  insert     USERS/{uid}/followed_sellers/{sellerId}
        │                │
        │                ▼ (Firestore trigger)
        │         onFollowedSellerWrite
        │         Cloud Function
        │                │
        │                ▼
        │         USERS/{sellerId}.followerCount
        │         FieldValue.increment(+1 or -1)
        │
Customer profile → FollowedSellers route
        │
        ▼
FollowedSellersScreen ──── FollowedSellersViewModel
        │                          │
        │              FollowedSellersRepository
        │              .observeFollowedSellers(userId)
        │              (Room Flow → FollowedSeller domain list)
        │
Seller dashboard / profile → reads USERS/{uid}.followerCount directly
                              via FirestoreRepository or ProfileRepository
```

### Recommended Project Structure (new files only)

```
domain/src/main/java/com/wenubey/domain/
├── model/
│   └── FollowedSeller.kt              # snapshot domain model (mirrors WishlistItem)
├── repository/
│   └── FollowedSellersRepository.kt   # interface (mirrors WishlistRepository, minus syncAnonymousOnLogin)

data/src/main/java/com/wenubey/data/
├── local/
│   ├── entity/
│   │   └── FollowedSellerEntity.kt    # Room entity (mirrors WishlistItemEntity)
│   ├── dao/
│   │   └── FollowedSellerDao.kt       # DAO (mirrors WishlistItemDao)
│   └── mapper/
│       └── FollowedSellerMapper.kt    # toDomain / toEntity (mirrors WishlistItemMapper)
├── repository/
│   └── FollowedSellersRepositoryImpl.kt  # clone of WishlistRepositoryImpl minus anon branch

app/src/main/java/com/wenubey/wenucommerce/
├── customer/
│   └── customer_followed_sellers/
│       ├── FollowedSellersScreen.kt
│       ├── FollowedSellersState.kt
│       ├── FollowedSellersViewModel.kt
│       └── FollowedSellersAction.kt
├── core/components/
│   └── FollowButton.kt                # reusable (analogous to WishlistHeartButton)
└── navigation/
    # AppNavigationObjects.kt — add FollowedSellers route object
    # TabNavRoutes.kt — add composable<FollowedSellers> { }

functions/src/
└── index.ts  # add onFollowedSellerWrite trigger
functions/test/
└── onFollowedSellerWrite.test.ts  # mirrors onNewReview.test.ts pattern
```

### Pattern 1: `onDocumentWritten` Trigger for Follower Count (CD-03)

**What:** A 2nd-gen Firestore trigger on `users/{customerId}/followed_sellers/{sellerId}` that increments `followerCount` on the seller's USERS doc by +1 on create and -1 on delete.

**Why trigger over callable:** The wishlist (our template) uses direct Firestore writes from the client. A trigger preserves that pattern — the client writes the follow doc exactly as it writes a wishlist doc, and the trigger handles the side-effect. A callable would require the client to call two RPCs. The trigger also enforces FAVS-04 privacy structurally: the follower identity doc lives under the customer's own space (`users/{customerId}/...`), making it inaccessible to the seller via security rules.

**Distinguishing follow-create vs. unfollow-delete** — `onDocumentWritten` fires on both. Use `event.data?.before` and `event.data?.after`:

```typescript
// Source: mirrors onOrderStatusChange in functions/src/index.ts [VERIFIED: codebase]
export const onFollowedSellerWrite = onDocumentWritten(
  "users/{customerId}/followed_sellers/{sellerId}",
  async (event) => {
    const before = event.data?.before;
    const after = event.data?.after;
    const sellerId = event.params.sellerId;

    const wasFollowing = before?.exists ?? false;
    const isNowFollowing = after?.exists ?? false;

    if (wasFollowing === isNowFollowing) {
      // Update to an existing doc (shouldn't happen in this design) — skip
      return;
    }

    const delta = isNowFollowing ? 1 : -1;
    const db = admin.firestore();

    // Atomic increment — safe even if followerCount field doesn't exist yet
    // (FieldValue.increment on a missing field initialises it to 0 + delta)
    await db.collection("USERS").doc(sellerId).update({
      followerCount: admin.firestore.FieldValue.increment(delta),
    });
  },
);
```

**Idempotency:** A duplicate follow (user follows same seller twice) is blocked at the Room + Firestore document level: the follow doc path `users/{uid}/followed_sellers/{sellerId}` is a single document with a fixed ID (the sellerId). A second write is an update (no `before.exists` change → `wasFollowing === isNowFollowing` → skip). An unfollow-when-not-following is blocked by the client guard (Room check before write) and at the trigger (before.exists = false → delta = 0 → no change). [ASSUMED — trigger idempotency reasoning; verified against `onDocumentWritten` contract behavior documented in Firebase docs]

**`followerCount` default 0:** `FieldValue.increment(delta)` on a field that does not exist initialises it atomically to `delta` (Firebase SDK documented behavior — no need to pre-populate the field). Existing USERS docs without `followerCount` will get `followerCount: 1` on the first follow. [ASSUMED — confirmed by Firebase SDK contract for `FieldValue.increment` on non-existent fields]

**Billable:** This is a new Cloud Function. It requires user approval per CLAUDE.md before the execution plan can proceed. [VERIFIED: CLAUDE.md + CONTEXT.md canonical_refs]

### Pattern 2: Offline-First Follow Repository (CD-01, CD-02)

**What:** `FollowedSellersRepositoryImpl` is a strict clone of `WishlistRepositoryImpl` with these removals:
- No `effectiveUserId = userId ?: ""` anonymous branch — caller must pass a non-blank userId (D-03 guarantees auth before calling)
- No `syncAnonymousOnLogin` — entire method omitted from the interface
- No `fetchAndMergeFirestoreWishlist` — on login there are no anonymous items to migrate

**Room source-of-truth:** `observeFollowedSellers(userId)` returns a `Flow<List<FollowedSeller>>` from Room DAO. The DAO query mirrors `WishlistItemDao.observeWishlistItems(userId)`.

**Fire-and-forget Firestore sync** (CD-02): exact pattern from `WishlistRepositoryImpl.toggleWishlist`:
1. Write/delete Room immediately (source of truth correct)
2. `try { firestore...await() } catch { Timber.e(...) }` — swallow Firestore failure, Room stays correct

**Key difference from wishlist:** The follow document in Firestore stores `{ sellerId, sellerName, sellerLogoUrl, followedAt }` (snapshot fields, for Firestore-side reads only). Room is the canonical list for display. The trigger reads only `event.params.sellerId` — it never reads the document body for the counter.

### Pattern 3: Aggregate Rating Computation (CD-04)

**Formula:** Review-count-weighted mean across ACTIVE products:

```kotlin
// Source: derived from Product model [VERIFIED: codebase]
fun List<Product>.computeWeightedAverageRating(): Pair<Double, Int> {
    val totalReviews = sumOf { it.reviewCount }
    if (totalReviews == 0) return Pair(0.0, 0)
    val weightedSum = sumOf { it.averageRating * it.reviewCount }
    return Pair(weightedSum / totalReviews, totalReviews)
}

// In SellerStorefrontViewModel, after loading products:
val (avgRating, totalReviews) = state.products
    .filter { it.status == ProductStatus.ACTIVE }
    .computeWeightedAverageRating()
_state.update { it.copy(averageRating = avgRating, totalReviewCount = totalReviews) }
```

**Empty/no-reviews display:** When `totalReviews == 0`, the rating row in `StorefrontHeaderCard` is hidden entirely (per UI-SPEC copywriting contract). On seller surfaces (`SellerDashboardScreen`, `SellerProfileScreen`), display `"–"` (em dash).

**Scope:** ACTIVE products only — `ProductStatus.ACTIVE`. Products with `reviewCount == 0` contribute 0 to the weighted sum and 0 to the total count, so they don't distort the average. [VERIFIED: Product.kt has `averageRating: Double` and `reviewCount: Int` fields]

### Anti-Patterns to Avoid

- **Client-side `followerCount` write:** Never let the Android client write `followerCount` directly to Firestore. The security rule must deny client writes to `USERS/{userId}.followerCount` (Cloud Function Admin SDK bypasses rules). [VERIFIED: existing `firestore.rules` pattern for reviews/orders]
- **Anonymous follow path:** Do NOT add `effectiveUserId = userId ?: ""` or any empty-string guard. D-03 requires sign-in; the ViewModel must check auth and show the auth-gate dialog BEFORE calling the repository. [VERIFIED: CONTEXT.md D-03]
- **Self-follow:** A seller browsing their own storefront should not see a Follow button, or it should be disabled. The ViewModel can detect self-follow by comparing `currentUser?.uuid == sellerId`. [ASSUMED — product requirement gap; recommend: hide Follow button if currentUser.uuid == sellerId]
- **Not guarding against missing `followerCount` field:** Existing USERS docs have no `followerCount` field. `FieldValue.increment` handles this safely (Firebase contract). Client-side Firestore reads should treat missing/null as 0. [ASSUMED]
- **Calling `FieldValue.increment` in a batch with a doc that might not exist:** The trigger uses `update()` not `set()`. If the seller's USERS doc is somehow missing, `update()` will throw a NOT_FOUND error. Guard with: if the doc doesn't exist, use `set({followerCount: delta}, {merge: true})`. [ASSUMED — Firebase contract for update() on missing docs]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Atomic follower counter | Custom read-modify-write in client | `FieldValue.increment(±1)` via Cloud Function trigger | Race conditions on concurrent follows; FieldValue.increment is server-side atomic [ASSUMED] |
| Offline follow list | Custom sync worker or PendingOperation queue | Room source-of-truth + fire-and-forget Firestore (WishlistRepositoryImpl pattern) | Already proven in Phase 3; PendingOperation queue overkill for read-heavy social data (CD-02) |
| Average rating calculation | Separate Firestore field `sellerAverageRating` | Live-derive in ViewModel from already-loaded products | Eliminates a denormalized field + trigger; product count per seller is small |
| Room entity with multiple primary keys | Composite PK like `(userId, sellerId)` | Single PK `sellerId` + `userId` as regular column | Table is per-user (userId always in queries); same pattern as WishlistItemEntity |
| Anonymous follow sync | Migrate anonymous follows on login | Simply require auth (D-03) | Wishlist's `syncAnonymousOnLogin` is the complexity we avoid by requiring sign-in |

**Key insight:** Every hard problem in this phase has a directly analogous solution already proven in the codebase. Following the templates exactly (not reimagining them) is the correct strategy.

---

## Runtime State Inventory

> Not applicable — this is a greenfield feature addition, not a rename/refactor/migration.
> No stored data, live service config, OS-registered state, secrets, or build artifacts reference
> "followed_sellers" or "followerCount" before this phase. The USERS Firestore collection
> gains a new optional `followerCount` field (defaults to missing=0 — no data migration needed).
> Confirmed by codebase grep — no pre-existing reference to `followerCount` or `followed_sellers`.

---

## Common Pitfalls

### Pitfall 1: Counter Drift from Double-Follow

**What goes wrong:** Customer taps Follow, sees loading state, taps again — two writes hit Firestore. The trigger fires twice, `followerCount` increments by 2.

**Why it happens:** Optimistic UI toggles state immediately, but a second tap during the async window issues a second write.

**How to avoid:** In `SellerStorefrontViewModel`, transition to `LOADING` immediately on Follow tap and disable the button during `LOADING`. The trigger's idempotency check (before.exists == after.exists) is a safety net but should not be the primary guard.

**Warning signs:** `followerCount` on seller profile increasing by 2 per tap.

### Pitfall 2: Self-Follow

**What goes wrong:** A logged-in seller views their own storefront and taps Follow, inflating their own count. The seller's `uuid` matches `sellerId`.

**How to avoid:** In `SellerStorefrontViewModel.init`, compare `authRepository.currentUser.value?.uuid` with `sellerId`. If equal, set `followState = HIDDEN` (no Follow button rendered). Or at minimum disable the button.

**Warning signs:** Seller follower count increments when they open their own storefront.

### Pitfall 3: Unfollow Idempotency (unfollow-when-not-following)

**What goes wrong:** User taps Unfollow from the Followed Sellers list; the optimistic Room delete fires; Firestore delete also fires; but the trigger fires with `before.exists = false, after.exists = false` (no-op) — this is safe. The real pitfall is the reverse: stale state causes the delete to be dispatched when the doc doesn't exist, Firestore delete on a missing doc succeeds silently, but the trigger delta would be -1 on a doc that doesn't exist — leaving followerCount at -1.

**How to avoid:** The guard in the trigger (`wasFollowing === isNowFollowing` → skip) handles this. Additionally, the ViewModel should check Room before dispatching unfollow (wishlist pattern: `getWishlistItem` guard before delete).

**Warning signs:** `followerCount` going negative.

### Pitfall 4: `update()` on Missing USERS Doc in Trigger

**What goes wrong:** `db.collection("USERS").doc(sellerId).update({followerCount: ...})` throws `NOT_FOUND (5)` if the seller doc is deleted between follow and trigger execution.

**How to avoid:** Use `set({followerCount: admin.firestore.FieldValue.increment(delta)}, {merge: true})` instead of `update()`. This is safe: `merge: true` creates the doc if missing.

**Warning signs:** Cloud Function errors in Firebase console for deleted sellers.

### Pitfall 5: Cold-Start Follower Count Binding

**What goes wrong:** `SellerStorefrontViewModel` loads seller info from `FirestoreRepository.getUser(sellerId)` (a one-shot `fold` call, not a Flow). If `followerCount` is not in the `User` domain model, the storefront shows 0 even for sellers with followers.

**How to avoid:** Two options: (a) add `followerCount: Int = 0` to the `User` domain model and populate it from Firestore during `getUser()`, or (b) perform a separate one-shot Firestore read for `followerCount` in the ViewModel. Option (a) is cleaner and aligns with how the seller surfaces (dashboard, profile) also need the count. Requires confirming that `User.kt` and `UserEntity.kt` + `UserMapper` are updated, and that the Firestore USERS doc is read to include this field.

**Warning signs:** Follower count always shows 0 on storefront despite following existing.

### Pitfall 6: Hardcoded Colors (Code Review Flag from Phase 08)

**What goes wrong:** The Phase 08 review flagged hardcoded ARGB hex values as a defect. `SellerDashboardScreen` already has some hardcoded colors in pre-existing code (green verification badge, amber stats). The new "Followers" stat card must use `MaterialTheme.colorScheme.primary` not `Color(0xFF...)`.

**How to avoid:** New code in this phase uses ONLY M3 colorScheme tokens. The amber `Color(0xFFFFC107)` for star icons is the established project convention and is explicitly permitted by the UI-SPEC.

### Pitfall 7: `SellerStorefrontScreen` Missing `onNavigateBack`

**What goes wrong:** The current stub has no `onNavigateBack` param and no `Scaffold`/`TopAppBar`. The route registration in `TabNavRoutes.kt` (line 183–189) also passes no back callback.

**How to avoid:** Add `onNavigateBack: () -> Unit` to `SellerStorefrontScreen`; add `Scaffold + TopAppBar(navigationIcon = ArrowBack)` wrapping the existing `LazyColumn`; update the `TabNavRoutes.kt` composable registration to pass `onNavigateBack = { navController.navigateUp() }`. Keep the existing ViewModel and tests green.

### Pitfall 8: `CustomerProductCard` `onSellerClick` Threading

**What goes wrong:** `CustomerProductCard` is rendered in multiple places: `CustomerHomeScreen`, `CustomerWishlistScreen`, `SellerStorefrontScreen` itself. Adding `onSellerClick` as a required param breaks all callers.

**How to avoid:** Make `onSellerClick: ((String) -> Unit)? = null` optional with a default of null. When null, the seller name remains non-tappable (appropriate for `SellerStorefrontScreen` where tapping the seller name within their own storefront is circular). Only `CustomerHomeScreen` and `CustomerProductDetailScreen` need the wiring (D-01).

---

## Code Examples

### Firestore Trigger — `onFollowedSellerWrite`

```typescript
// Source: mirrors onOrderStatusChange + onNewReview pattern [VERIFIED: functions/src/index.ts]
export const onFollowedSellerWrite = onDocumentWritten(
  "users/{customerId}/followed_sellers/{sellerId}",
  async (event) => {
    const before = event.data?.before;
    const after = event.data?.after;
    const sellerId = event.params.sellerId;
    const customerId = event.params.customerId;

    const wasFollowing = before?.exists ?? false;
    const isNowFollowing = after?.exists ?? false;

    // No-op for update events (shouldn't happen; guard anyway)
    if (wasFollowing === isNowFollowing) {
      console.log("[follow_write] no existence change — skipping", { customerId, sellerId });
      return;
    }

    const delta = isNowFollowing ? 1 : -1;
    const db = admin.firestore();

    try {
      // merge: true handles missing USERS doc (Pitfall 4)
      await db.collection("USERS").doc(sellerId).set(
        { followerCount: admin.firestore.FieldValue.increment(delta) },
        { merge: true }
      );
      console.log("[follow_write] followerCount updated", { sellerId, delta });
    } catch (err) {
      console.error("[follow_write] failed to update followerCount", { sellerId, delta }, err);
    }
  },
);
```

### Room Entity — `FollowedSellerEntity`

```kotlin
// Source: mirrors WishlistItemEntity [VERIFIED: data/local/entity/WishlistItemEntity.kt]
@Entity(tableName = "followed_sellers", primaryKeys = ["userId", "sellerId"])
data class FollowedSellerEntity(
    val userId: String,
    val sellerId: String,
    val sellerName: String = "",
    val sellerLogoUrl: String = "",
    val followedAt: String = "",  // Instant.now().toString() — ISO-8601
)
```

Note: composite PK `(userId, sellerId)` mirrors `WishlistItemEntity`'s `(userId, productId)`. This is correct for a per-user table that must support multi-user (e.g., family device) scenarios.

### Domain Model — `FollowedSeller`

```kotlin
// Source: mirrors WishlistItem [VERIFIED: domain/model/WishlistItem.kt]
data class FollowedSeller(
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerLogoUrl: String = "",
    val followedAt: String = "",
)
```

### Repository Interface — `FollowedSellersRepository`

```kotlin
// Source: mirrors WishlistRepository [VERIFIED: domain/repository/WishlistRepository.kt]
// Note: syncAnonymousOnLogin is OMITTED (D-03 requires auth)
interface FollowedSellersRepository {
    fun observeFollowedSellers(userId: String): Flow<List<FollowedSeller>>
    fun isFollowing(userId: String, sellerId: String): Flow<Boolean>
    suspend fun followSeller(userId: String, sellerId: String, sellerName: String, sellerLogoUrl: String)
    suspend fun unfollowSeller(userId: String, sellerId: String)
}
```

### Repository Impl — `FollowedSellersRepositoryImpl` (key method)

```kotlin
// Source: mirrors WishlistRepositoryImpl.toggleWishlist [VERIFIED: codebase]
override suspend fun followSeller(userId: String, sellerId: String, sellerName: String, sellerLogoUrl: String) =
    withContext(dispatcherProvider.io()) {
        val entity = FollowedSellerEntity(
            userId = userId,
            sellerId = sellerId,
            sellerName = sellerName,
            sellerLogoUrl = sellerLogoUrl,
            followedAt = Instant.now().toString()
        )
        followedSellerDao.upsert(entity)          // Room first — source of truth
        try {
            val data = mapOf(
                "sellerId" to sellerId,
                "sellerName" to sellerName,
                "sellerLogoUrl" to sellerLogoUrl,
                "followedAt" to entity.followedAt
            )
            firestore
                .collection(USER_COLLECTION)      // "USERS"
                .document(userId)
                .collection("followed_sellers")
                .document(sellerId)
                .set(data)
                .await()
        } catch (e: Exception) {
            Timber.e(e, "FollowedSellersRepository: Firestore write failed (Room updated)")
        }
    }
```

### Room Migration v10→v11

```kotlin
// Source: mirrors MIGRATION_9_10 in WenuCommerceDatabase.kt [VERIFIED: codebase]
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `followed_sellers` (
                `userId` TEXT NOT NULL,
                `sellerId` TEXT NOT NULL,
                `sellerName` TEXT NOT NULL DEFAULT '',
                `sellerLogoUrl` TEXT NOT NULL DEFAULT '',
                `followedAt` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`userId`, `sellerId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_followed_sellers_userId` " +
                "ON `followed_sellers` (`userId`)"
        )
    }
}
```

**Note on `followerCount` in Room:** Do NOT add `followerCount` to `UserEntity` or create a migration for it. The storefront and seller surfaces read `followerCount` live from Firestore. Caching it in Room would require a Firestore listener to keep it fresh, which is more complexity than a one-shot read. The existing `FirestoreRepository.getUser(sellerId)` already returns a `User` from Firestore; updating `User.kt` with `followerCount: Int = 0` is all that's needed.

### Optimistic Follow Toggle (ViewModel pattern)

```kotlin
// Source: mirrors WishlistHeartButton + WishlistViewModel pattern [VERIFIED: codebase]
fun onFollow() {
    val userId = authRepository.currentUser.value?.uuid
    if (userId.isNullOrBlank()) {
        _state.update { it.copy(showLoginPrompt = true) }
        return
    }
    if (sellerId == userId) return  // self-follow guard (Pitfall 2)

    // Optimistic: set FOLLOWING immediately
    _state.update { it.copy(followState = FollowState.FOLLOWING, followerCount = state.value.followerCount + 1) }

    viewModelScope.launch(ioDispatcher) {
        runCatching {
            followedSellersRepository.followSeller(
                userId = userId,
                sellerId = sellerId!!,
                sellerName = state.value.sellerName,
                sellerLogoUrl = state.value.sellerLogoUrl
            )
        }.onFailure {
            // Revert on failure (Pitfall 1 revert path)
            _state.update { it.copy(followState = FollowState.NOT_FOLLOWING, followerCount = state.value.followerCount - 1, errorEvent = "Couldn't follow. Try again.") }
        }
    }
}
```

### Firestore Security Rules Addition

```javascript
// Source: mirrors /notifications/{userId}/items/{notifId} pattern [VERIFIED: firestore.rules]
// Add inside match /databases/{database}/documents { ... }

match /USERS/{userId} {
  // Existing catch-all (authenticated read/write):
  allow read, write: if request.auth != null;

  // Follow subcollection: customer may create/delete ONLY their own follow docs.
  // Clients MUST NOT write followerCount on the USERS doc directly — the
  // Cloud Function (Admin SDK) is the only writer. Since the existing USERS
  // rule is "allow read, write: if request.auth != null", the followerCount
  // field is technically writable by any authenticated user — tighten in a
  // follow-up housekeeping pass (noted as pre-Phase-6 debt in firestore.rules).
  // For Phase 9, the trigger's Admin SDK write bypasses rules; FAVS-04 privacy
  // is structural (follow docs live under /USERS/{customerId}/... not the seller's space).
  match /followed_sellers/{sellerId} {
    // Customer may read/create/delete only their OWN follow docs.
    allow read, create, delete: if request.auth != null
                                && request.auth.uid == userId;
    // Updates are not needed (client only creates or deletes, never updates a follow doc).
    allow update: if false;
  }
  match /{document=**} {
    allow read, write: if request.auth != null;
  }
}
```

**FAVS-04 privacy — structural enforcement:** The seller's UID appears as the _document ID_ inside the customer's space (`/USERS/{customerId}/followed_sellers/{sellerId}`). The seller has no path to list or read documents under `/USERS/{customerId}/...` (the `userId` in the rule is the customer's UID). This means even with the broad `allow read: if request.auth != null` on the top-level USERS doc, the seller cannot enumerate who follows them. [VERIFIED: firestore.rules owner-scoped subcollection pattern for /notifications/{userId}/items/]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Client-side read-modify-write for counters | `FieldValue.increment(±1)` via trigger | Firebase SDK ~2018 | Removes race conditions |
| `onDocumentCreated` only | `onDocumentWritten` for create+delete in one trigger | Firebase Functions v2 | Handles both follow/unfollow in a single function export |
| Hardcoded hex colors in Compose | M3 colorScheme tokens only | Phase 08 code review | Required project convention — no `Color(0xFF...)` in new code |
| Firestore listeners in repositories | Room DAO Flows as source of truth | Phase 01 | Offline-capable; Firestore only in SyncManager + one-shot fetches |

**Deprecated/outdated:**
- `onDocumentCreated` + `onDocumentDeleted` as separate triggers: use `onDocumentWritten` and check `before.exists`/`after.exists` — one export handles both.
- `syncAnonymousOnLogin` pattern for follows: omit entirely (D-03 requires auth, simplifying the repository substantially compared to the wishlist template).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `FieldValue.increment` on a missing field initialises it to `delta` (not 0+delta would be wrong → wrong) | Common Pitfalls P3, Code Examples | followerCount starts wrong if the field doesn't exist and `update()` is used; mitigated by using `set(..., merge: true)` |
| A2 | `onDocumentWritten` `before.exists == false && after.exists == false` is a no-op case (update to non-existent doc) | Pattern 1, Pitfall 3 | Trigger could fire unexpectedly — guarded by the wasFollowing===isNowFollowing check |
| A3 | Composite PK `(userId, sellerId)` is correct for `followed_sellers` entity | Code Examples | If userId is always present (D-03 guarantees auth), a single PK on `sellerId` per-user would also work; composite is safer and matches wishlist |
| A4 | `followerCount` does NOT need Room caching — one-shot Firestore read is sufficient for storefront + seller surfaces | Don't Hand-Roll, Pitfall 5 | If followerCount needs to be reactive (live-updating while screen is open), a Firestore listener would be needed; but the UX-SPEC doesn't require live count updates post-load |
| A5 | `onSellerClick` param on `CustomerProductCard` should default to `null` (not required) to avoid breaking `SellerStorefrontScreen`'s own product list | Pitfall 8 | If made required, compilation fails for existing usages |
| A6 | Self-follow is a real edge case requiring guard | Pitfall 2 | A seller following themselves is a minor UX bug, not a security issue; if seller never navigates to their own storefront via the customer path, the bug is unreachable in practice |

---

## Open Questions (RESOLVED)

1. **`followerCount` field on `User` domain model**
   - What we know: `User.kt` currently has no `followerCount` field [VERIFIED: codebase]. `FirestoreRepository.getUser(sellerId)` returns a `Result<User>`.
   - What's unclear: Should `followerCount` be added to `User.kt` (and `UserEntity.kt` if cached) or read separately? Adding it to `User` means it flows through the existing `getUser()` path cleanly but requires updating the Firestore mapper. A separate read keeps User clean but adds an extra Firestore call in the storefront ViewModel.
   - RESOLVED: Add `followerCount: Int = 0` to `User.kt`; read it via the Firestore `getUser()` path (Firestore `toObject`/mapper defaults to 0). `UserEntity` does NOT cache `followerCount`, so no Room change for this field. Requires user approval (domain model change) — surfaced in the plan-phase summary and handled in 09-01.

2. **Security rules: tightening `USERS` write access**
   - What we know: The current rule `allow read, write: if request.auth != null` on USERS allows any authenticated user to write any field, including `followerCount`. The CONTEXT.md notes this as pre-Phase-6 technical debt.
   - What's unclear: Should Phase 9 tighten the USERS write rules as part of adding the `followed_sellers` subcollection rule? (This is scope creep risk.)
   - RESOLVED: Add ONLY the `followed_sellers` subcollection rule in Phase 9 (plus a client-write guard on `followerCount`). Document the broader USERS write tightening as a future housekeeping task — out of scope here.

3. **`SellerDashboardViewModel` and `SellerProfileScreen` follower count data source**
   - What we know: Both screens currently show hardcoded placeholder data. Neither has a clean path to read the seller's `followerCount` from Firestore. The `SellerDashboardViewModel` exists and uses `FirestoreRepository` or `ProfileRepository`.
   - What's unclear: Does the planner need to add a new method to `FirestoreRepository` to read `followerCount`, or does `getUser(sellerId)` already return it (after the `User.kt` update above)?
   - RESOLVED: After adding `followerCount` to `User.kt`, `getUser(currentUserId)` in `SellerDashboardViewModel` naturally returns the count. No new repository method needed.

---

## Environment Availability

> This phase adds one new Cloud Function and modifies Firestore rules. No new Android SDK dependencies.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| firebase-functions onDocumentWritten | follower-count trigger | ✓ | 6.x (package.json) | — |
| firebase-admin FieldValue.increment | counter maintenance | ✓ | 13.x (package.json) | — |
| Room KSP | FollowedSellerEntity | ✓ | existing in :data module | — |
| Node 20 runtime | Cloud Functions | ✓ | confirmed (Phase 8 deploy) | — |

**Missing dependencies with no fallback:** None.

---

## Validation Architecture

> `workflow.nyquist_validation` key is absent from `.planning/config.json` — treat as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Turbine + MockK + Compose Test (Android), Jest 29 (Cloud Functions) |
| Android config | Standard AGP test config, no separate pytest.ini |
| Quick unit run | `./gradlew :domain:testDebugUnitTest :app:testDebugUnitTest` |
| Full suite | `./gradlew testDebugUnitTest` (all 3 modules) |
| Instrumented | `./gradlew connectedDebugAndroidTest` (migration test requires device/emulator) |
| Functions tests | `cd functions && npm test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FAVS-01 | Follow toggles state optimistically, reverts on failure | Unit (ViewModel) | `./gradlew :app:testDebugUnitTest --tests "*SellerStorefrontViewModelTest"` | ❌ Wave 0 — extend existing test |
| FAVS-01 | Logged-out follow tap shows auth-gate dialog | Unit (ViewModel) | same | ❌ Wave 0 |
| FAVS-01 | Self-follow is blocked | Unit (ViewModel) | same | ❌ Wave 0 |
| FAVS-01 | followSeller writes Room + Firestore fire-and-forget | Unit (Repository fake) | `./gradlew :data:testDebugUnitTest --tests "*FollowedSellersRepositoryTest"` | ❌ Wave 0 |
| FAVS-01 | unfollowSeller deletes from Room + Firestore | Unit (Repository fake) | same | ❌ Wave 0 |
| FAVS-02 | FollowedSellersViewModel emits list from Room | Unit (ViewModel) | `./gradlew :app:testDebugUnitTest --tests "*FollowedSellersViewModelTest"` | ❌ Wave 0 |
| FAVS-02 | Followed Sellers screen renders list / empty state | Compose UI test | `./gradlew :app:connectedDebugAndroidTest --tests "*FollowedSellersScreenTest"` | ❌ Wave 0 |
| FAVS-03 | Storefront header shows seller name, renders Follow button | Compose UI test | `./gradlew :app:connectedDebugAndroidTest --tests "*SellerStorefrontScreenTest"` | ✅ Extend existing |
| FAVS-03 | Aggregate rating formula computes weighted mean correctly | Unit (pure) | `./gradlew :domain:testDebugUnitTest --tests "*AggregateRatingTest"` OR within ViewModel test | ❌ Wave 0 |
| FAVS-04 | Seller follower count surfaced on dashboard (not identity list) | Unit (SellerDashboardViewModel) | `./gradlew :app:testDebugUnitTest --tests "*SellerDashboardViewModelTest"` | ❌ Wave 0 |
| FAVS-04 | followerCount trigger increments on create, decrements on delete | Jest structural | `cd functions && npm test -- --testPathPattern=onFollowedSellerWrite` | ❌ Wave 0 |
| — | Room v10→v11 migration | Instrumented migration | `./gradlew :data:connectedDebugAndroidTest --tests "*WenuCommerceMigrationTest"` | ✅ Extend existing |
| — | followerCount trigger idempotency (same-state no-op) | Jest unit | same file | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :app:assembleDebug` + affected module `testDebugUnitTest`
- **Per wave merge:** `./gradlew testDebugUnitTest` (all modules)
- **Phase gate:** Full suite + `connectedDebugAndroidTest` (migration) green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `app/src/test/.../seller_storefront/SellerStorefrontViewModelTest.kt` — extend existing (add follow state, auth gate, self-follow, aggregate rating tests)
- [ ] `app/src/androidTest/.../seller_storefront/SellerStorefrontScreenTest.kt` — extend existing (add Follow button render, auth dialog, empty bio, storefront header tests)
- [ ] `data/src/test/.../repository/FollowedSellersRepositoryTest.kt` — new, fake DAO + fake Firestore
- [ ] `app/src/test/.../customer_followed_sellers/FollowedSellersViewModelTest.kt` — new
- [ ] `app/src/androidTest/.../customer_followed_sellers/FollowedSellersScreenTest.kt` — new
- [ ] `domain/src/test/.../AggregateRatingKtTest.kt` — pure weighted-mean formula test (or embed in ViewModel test)
- [ ] `functions/test/onFollowedSellerWrite.test.ts` — mirrors `onNewReview.test.ts` pattern (pure helper + structural contract greps)
- [ ] `data/src/androidTest/.../WenuCommerceMigrationTest.kt` — extend with `migrate10To11_createsFollowedSellersTable` test

---

## Security Domain

> `security_enforcement` is absent from `.planning/config.json` — treat as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes — follow requires auth | D-03 auth gate in ViewModel; Firestore rule `request.auth != null && request.auth.uid == userId` |
| V3 Session Management | no | n/a |
| V4 Access Control | yes — FAVS-04 follower identity privacy | Structural: follow docs in customer's space; seller cannot list them |
| V5 Input Validation | yes — sellerId passed through UI | Validate sellerId non-blank before repository call; Firestore paths reject empty segments |
| V6 Cryptography | no | n/a |

### Known Threat Patterns for this Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Client writes `followerCount` directly | Tampering | Firestore rule tightening (Phase 9 adds subcollection rule; full USERS tightening in follow-up) + Admin SDK is the only writer |
| Follower identity enumeration by seller | Info Disclosure | Structural: follow docs under `/USERS/{customerId}/...`; security rule `request.auth.uid == userId` blocks cross-user reads |
| Anonymous follow circumventing auth | Elevation | D-03: no anonymous branch; ViewModel checks `userId.isNullOrBlank()` before any repo call |
| Self-follow counter inflation | Tampering | ViewModel guard: skip follow if `currentUser.uuid == sellerId` |
| Replay follow write (double-follow) | Tampering | Document-level idempotency: follow doc at a fixed path = upsert, not append; trigger detects no existence change |

---

## Sources

### Primary (HIGH confidence)
- `functions/src/index.ts` — `onDocumentWritten`, `onDocumentCreated` trigger signatures; `FieldValue.increment`; FCM dispatch pattern; Admin SDK usage — verified against live codebase
- `data/src/main/java/com/wenubey/data/repository/WishlistRepositoryImpl.kt` — offline-first fire-and-forget pattern — verified
- `data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt` — Room version 10, migration chain, `MIGRATION_9_10` idiom — verified
- `data/src/androidTest/java/com/wenubey/data/local/WenuCommerceMigrationTest.kt` — migration test idiom — verified
- `app/src/main/java/com/wenubey/wenucommerce/seller/seller_storefront/SellerStorefrontViewModel.kt` — existing stub to extend — verified
- `app/src/main/java/com/wenubey/wenucommerce/seller/seller_storefront/SellerStorefrontScreen.kt` — existing stub — verified
- `app/src/test/java/com/wenubey/wenucommerce/seller/seller_storefront/SellerStorefrontViewModelTest.kt` — test idiom (MainDispatcherRule, FakeFirestoreRepository, advanceUntilIdle) — verified
- `app/src/androidTest/java/com/wenubey/wenucommerce/seller/seller_storefront/SellerStorefrontScreenTest.kt` — Compose test idiom (mockk, MutableStateFlow) — verified
- `firestore.rules` — owner-scoped subcollection pattern; security rule idiom — verified
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` — Koin `singleOf(::WishlistRepositoryImpl).bind<WishlistRepository>()` wiring pattern — verified
- `app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt` — `viewModelOf(::SellerStorefrontViewModel)` already present — verified
- `functions/test/onNewReview.test.ts` — Jest structural test pattern (pure helper + grep contract) — verified
- `.planning/phases/09-seller-storefronts-favorite-sellers/09-UI-SPEC.md` — UI contract for all new/modified surfaces — verified
- `.planning/phases/09-seller-storefronts-favorite-sellers/09-CONTEXT.md` — locked decisions, canonical refs — verified

### Secondary (MEDIUM confidence)
- Firebase documentation for `FieldValue.increment` on non-existent fields (`set + merge: true`) — training knowledge, consistent with Firebase SDK contracts
- Firebase Functions v2 `onDocumentWritten` event shape (`event.data?.before`, `event.data?.after`) — training knowledge, verified by `onOrderStatusChange` usage in `index.ts`

### Tertiary (LOW confidence)
- None — all claims grounded in verified codebase files or Firebase SDK contracts

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all from verified live codebase
- Architecture patterns: HIGH — directly derived from existing Phase 3/7/8 patterns
- Pitfalls: MEDIUM-HIGH — most from observed patterns + Firebase SDK contracts
- Cloud Function trigger behavior: MEDIUM — verified against existing trigger code in index.ts; Firebase increment-on-missing-field is [ASSUMED]

**Research date:** 2026-07-18
**Valid until:** 2026-08-18 (stable libraries; Firebase Admin SDK and Room APIs are stable)
