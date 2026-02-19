# Project Research Summary

**Project:** WenuCommerce — Android Multi-Role E-Commerce App
**Domain:** Mobile e-commerce (Customer / Seller / Admin), offline-first, payments, notifications
**Researched:** 2026-02-19
**Confidence:** HIGH

## Executive Summary

WenuCommerce is a multi-role Android e-commerce app (Kotlin, Jetpack Compose, Firebase) that has completed its authentication, product browsing, and category management baseline. The next milestone transforms it from a browsing app into a transactional platform by adding the commercial layer: cart, checkout via Stripe, order lifecycle management, reviews, wishlist, and push notifications. All research was conducted against the existing codebase, which uses Clean Architecture with 3 modules (domain / data / app), Koin 4.0.1, and Firestore as its only data source today.

The recommended approach is a phased delivery anchored on a Room offline-first foundation. The critical architectural shift is making Room the single source of truth for all reads, with Firestore serving as cloud sync target rather than the primary data source. This is non-negotiable: every feature in the commercial layer depends on this foundation being correct. The Room layer is already declared in `libs.versions.toml` but has zero entities, DAOs, or database classes today — this is the highest-priority prerequisite work. A write-queue pattern using WorkManager handles optimistic local writes that sync to Firestore when connectivity is restored.

The key risks are all architectural rather than feature-level. The three that can cause project-wide rewrites if not addressed upfront are: (1) placing Room entities in the domain module or using domain models directly as entities, (2) introducing two simultaneous data sources (Firestore flows and Room flows) that both feed the UI, and (3) implementing any Stripe payment logic client-side instead of through Firebase Cloud Functions. These must be resolved at the design stage, before the first line of feature code is written.

---

## Key Findings

### Recommended Stack

The existing stack requires no major additions — the project has Room, WorkManager-ready infrastructure, Firebase (including Cloud Functions), and Compose testing already declared. Key new artifacts are WorkManager (`work-runtime-ktx 2.9.1`), Stripe Android SDK (`stripe-android 20.x`), Turbine for Flow testing (`app.cash.turbine 1.2.0`), and Koin test utilities (`koin-test`, `koin-test-junit4` via existing Koin BOM 4.0.1). MockK should be upgraded from 1.12.4 to 1.13.x to fix Kotlin 2.x coroutine compatibility.

All versions marked [VERIFY] — external search was unavailable during research. Confirm at Maven Central / GitHub releases before adding to `libs.versions.toml`.

**Core technologies:**
- **Room 2.6.1** (already declared): local database, becomes the single UI data source — no new dependency needed, only implementation
- **WorkManager 2.9.1** [VERIFY]: deferrable background sync, offline write queue — survives app termination and device reboots
- **Stripe Android SDK 20.x** [VERIFY]: PaymentSheet for PCI-compliant checkout — add to `app` module only
- **Firebase Cloud Functions** (already wired): server-side PaymentIntent creation, order creation on webhook, FCM triggers — no new Android dependency
- **Turbine 1.2.0** [VERIFY]: Flow and StateFlow testing DSL — the most impactful missing test library
- **Koin test artifacts** (via existing BOM): KoinTestRule for DI in tests — replaces manual ViewModel construction

**What NOT to add:** Hilt (project decision is Koin), SQLDelight (Room already configured), Retrofit (Ktor is the HTTP client), RxJava/LiveData (project uses Flows), Stripe Terminal (physical POS), Robolectric (conflicts with Firebase), JUnit 5 (Compose testing requires JUnit 4 rules).

See `.planning/research/STACK.md` for full version matrix and `libs.versions.toml` change list.

---

### Expected Features

The commercial milestone covers 9 table-stakes features that users expect in any e-commerce app, plus 4 differentiating features for competitive advantage.

**Must have (table stakes):**
- **Cart** — Room-backed, offline-first, syncs to Firestore; badge count on nav tab; stock-aware inline warnings
- **Checkout (Stripe PaymentSheet)** — Cloud Function creates PaymentIntent server-side; app receives `clientSecret` only; no custom card form
- **Order Tracking (Customer view)** — reverse-chronological list with status timeline; FCM deep-link to order detail
- **Order Management (Seller view)** — seller-scoped orders; forward-only status advancement; FCM trigger on status change
- **Reviews and Ratings** — verified purchase required; 1-review-per-product-per-customer; aggregate rating on product cards
- **Wishlist / Save for Later** — Room-backed offline-first; toggle from product card and detail; add-to-cart from wishlist
- **FCM Push Notifications** — order status changes, new order alert to seller, seller approval; notification channels per type; POST_NOTIFICATIONS permission on Android 13+; in-app notification history (Room-backed)
- **Personalization Settings** — dark/light/system theme (DataStore), notification type toggles, orientation lock
- **Profile Management** — photo upload, password change with re-auth, seller business info

**Should have (competitive differentiators):**
- **Seller Discounts and Coupon Codes** — percentage/fixed amount, server-side coupon validation, usage limits
- **Seller Storefront** — public seller profile page accessible from product cards and search results
- **Favorite Sellers / Following** — follow from storefront; optional new-product notification
- **Offline-First Sync** — explicit conflict resolution per entity, WorkManager write queue, connectivity banner

**Defer to v2+:**
- In-app buyer-seller chat, multi-currency, email/SMS campaigns, ML recommendations, auctions, Stripe Connect marketplace payouts, loyalty points, AR preview, split payments, refunds in-app, change-email flow

The critical path to checkout is: Cloud Function for PaymentIntent (must be built first, blocks all checkout Android work) → Cart (Room + Firestore) → Stripe SDK → Order creation → Order tracking → Order management → FCM on status change.

See `.planning/research/FEATURES.md` for minimum acceptance criteria per feature.

---

### Architecture Approach

The architecture retains the existing 3-module Clean Architecture (domain / data / app) and MVVM with StateFlow. The primary change is adding a Room local database layer and a sync layer entirely inside the `data` module. Domain interfaces do not change — ViewModels continue to call the same `observeXxx()` methods, but internally repositories now emit from Room DAOs instead of Firestore `callbackFlow`. Firestore listeners become sync triggers that write into Room, not direct UI data sources.

**Major components:**
1. **Room Database Layer** (`data` module) — `WenuCommerceDatabase` with DAOs and entity classes for Product, Category, Cart, Order, Review, Notification, and PendingOperation; all mappers live here; domain layer never imports Room annotations
2. **Sync Layer** (`data` module) — `WriteQueueWorker` (WorkManager) drains a `PendingOperationEntity` queue for offline writes; Firestore snapshot listeners write to Room on every emission; `ConnectivityObserver` exposes network state as `Flow<Boolean>`; `SyncRepository` exposes pending count to UI
3. **Stripe Payment Flow** (`app` module for PaymentSheet UI; `data` module for `PaymentRepository`) — `PaymentRepository.createPaymentIntent()` calls Firebase Cloud Function; result `clientSecret` is passed to `PaymentSheet.present()` in the composable; order created on `PaymentSheetResult.Completed`
4. **FCM Notification System** (`app` module for `MessagingService`; `data` module for `NotificationRepository`) — `MessagingService` extended to route by type, check `NotificationPreferences`, persist to `NotificationEntity` in Room, show via typed notification channels
5. **Koin DI Extension** — new `databaseModule` for Room singleton and DAO bindings; `KoinWorkerFactory` in Application class for WorkManager dependency injection

**Conflict resolution policy (must be defined before sync code is written):**
- Products/catalog: server wins
- Cart items: local wins for additions; server wins for stock validation
- Orders: immutable after creation; status only advances forward (server-authoritative)
- User profile: last-write-wins with `updatedAt` timestamp guard
- Payment entities: Firestore is source of truth; never write payment data to Room optimistically

See `.planning/research/ARCHITECTURE.md` for full data flow diagrams and component file structure.

---

### Critical Pitfalls

1. **Using domain models directly as Room entities (CP-1)** — Domain models have nested objects, `@Serializable` annotations, and `Map` fields that Room cannot serialize. Putting `@Entity` on domain classes violates Clean Architecture and causes KSP compilation failures. Avoid by creating separate `*Entity` classes in `data/local/entity/` with mapper functions (`toEntity()` / `toDomain()`).

2. **Observing both Firestore and Room flows simultaneously in the UI (CP-4)** — The old `callbackFlow` Firestore pattern and Room's DAO `Flow` have different lifecycle semantics. Combining them with `combine` or collecting both in a ViewModel causes flickering, stale UI during offline periods, and unpredictable behavior. Avoid by enforcing strict single-source-of-truth: UI only observes Room; Firestore listeners write to Room.

3. **Any Stripe payment logic client-side (CP-3)** — Creating a `PaymentIntent` from the Android client requires the Stripe secret key in the APK, which Stripe considers a critical security violation leading to account termination. All payment operations (PaymentIntent creation, stock decrement, order creation, purchase history write) must flow through Firebase Cloud Functions. The client only ever receives and uses the `clientSecret`.

4. **No conflict resolution strategy before writing sync code (CP-2)** — Without explicit per-entity conflict policies, the team defaults to "last write wins," which silently corrupts stock counts, order states, and payment records. Define the conflict policy (documented above in Architecture Approach) before writing any `SyncWorker` or `PendingOperationEntity`.

5. **Room schema migrations not set up from day one (CP-5)** — Once any Room schema ships to users, every subsequent entity change requires an explicit `Migration` object. `fallbackToDestructiveMigration()` in production wipes offline cart and pending state. Avoid by: committing schema JSON files to git, using build-variant-scoped `fallbackToDestructiveMigration()` (debug-only), and planning all Phase 1 entities together so schema version 1 is stable before first ship.

Additional moderate pitfalls: FCM token updates failing silently offline (use WorkManager for token upload), `CheckoutViewModel` losing `paymentIntentId` on process death (use `SavedStateHandle`), Room declared in `app/build.gradle.kts` instead of only `data` (move it), Koin module circular deps when adding DAOs (create dedicated `databaseModule`), and timestamps stored as `String` causing ordering bugs in Room queries (use `Long` in entities, convert in mapper).

See `.planning/research/PITFALLS.md` for full pitfall catalogue with prevention strategies and phase-specific warnings.

---

## Implications for Roadmap

Based on the dependency graph in ARCHITECTURE.md and the critical path in FEATURES.md, the natural phase structure has 7 phases. Nothing in Phase 4+ is buildable until Phases 1 and 2 are solid — the sync layer is load-bearing infrastructure.

### Phase 1: Room Foundation
**Rationale:** Every subsequent feature reads from Room. No cart, order, or notification feature can be built correctly without a working Room database with write-through from Firestore. This is the prerequisite for the entire milestone.
**Delivers:** Offline product and category browsing; app works without network on first launch; Room schema version 1 established
**Addresses:** Offline-First Sync (partial), table stakes for all subsequent features
**Avoids:** CP-1 (entity design upfront), CP-4 (single-source-of-truth enforced from start), CP-5 (migration infrastructure from day one), MiP-4 (Room moved out of `app` module)
**Research flag:** Standard Android patterns — skip `/gsd:research-phase`

### Phase 2: Write Queue and Sync Infrastructure
**Rationale:** Cart, wishlist, and any offline write needs the pending operations queue. WorkManager connectivity-aware retry is the mechanism; without it, offline writes are lost.
**Delivers:** `PendingOperationEntity` queue, `WriteQueueWorker`, `ConnectivityObserver`, `SyncRepository` with pending-count UI, `KoinWorkerFactory` setup
**Uses:** WorkManager 2.9.1, `ConnectivityManager` (framework, no new dep)
**Avoids:** CP-2 (conflict policy defined per entity), MP-2 (WorkManager constraints designed upfront)
**Research flag:** Standard patterns — skip `/gsd:research-phase`

### Phase 3: Cart and Wishlist
**Rationale:** Cart is the prerequisite for Checkout. Wishlist is low-complexity and shares the same offline-first patterns established in Phases 1–2. Building together avoids two separate Room entity rounds.
**Delivers:** Cart with Room persistence, badge count, stock-aware UI, empty state; Wishlist with toggle from product card/detail and dedicated view; add-to-cart from wishlist
**Implements:** `CartRepository`, `CartRepositoryImpl`, `WishlistRepository`, Room `CartItemEntity`, `WishlistEntity`
**Avoids:** MiP-1 (fix `Purchase.quantity: Double` to `Int` before Cart entity design)
**Research flag:** Standard patterns — skip `/gsd:research-phase`

### Phase 4: Stripe Checkout and Order Creation
**Rationale:** Checkout is the highest-complexity feature and the commercial milestone's core. The Cloud Function for `createPaymentIntent` is the single biggest dependency blocker — it must be deployed before any Android checkout work can be tested end-to-end. Order model is defined here since it's created at checkout.
**Delivers:** Full checkout flow (cart → payment → order confirmation); `Order` and `OrderItem` domain models and Room entities; `PaymentRepository` calling Cloud Function; Stripe PaymentSheet integration; `CheckoutScreen` and `OrderConfirmationScreen`
**Uses:** Stripe Android SDK 20.x (add to `app` module), Firebase Cloud Functions (already wired)
**Avoids:** CP-3 (all payment logic server-side), MP-3 (`SavedStateHandle` for `paymentIntentId`), MiP-5 (Stripe PaymentSheet launched via `rememberPaymentSheet` from `ComponentActivity`)
**Research flag:** Needs `/gsd:research-phase` — Stripe PaymentSheet Compose integration specifics and Cloud Function contract design need deeper research before implementation

### Phase 5: Order Tracking and Management
**Rationale:** Once orders exist (Phase 4), both customers and sellers need to manage them. Seller order management drives FCM (sellers advance status which triggers notifications to customers).
**Delivers:** Customer order history and order detail screens with status timeline; Seller orders screen with forward-only status advancement; order-status-change FCM Cloud Function trigger
**Implements:** `OrderRepository.observeOrders()`, `OrderRepository.updateStatus()`, `SellerOrdersScreen`, `CustomerOrderHistoryScreen`, `CustomerOrderDetailScreen`
**Avoids:** Firestore security rules updated for orders (MP-5: seller can only read/update own orders; customer can only read own orders)
**Research flag:** Standard CRUD patterns — skip `/gsd:research-phase`

### Phase 6: FCM Notification System
**Rationale:** FCM depends on order events (Phase 5) for order status triggers, and on Room (Phase 1) for notification history persistence. Building after Phase 5 means all notification types can be implemented in one pass.
**Delivers:** Extended `MessagingService` with type routing and preference checking; `NotificationEntity` Room persistence; in-app notification history screen; per-type notification toggles in DataStore; notification channels for all types; deep-link tap handling; Android 13+ `POST_NOTIFICATIONS` permission flow; FCM token upload via WorkManager (fixes existing fire-and-forget bug)
**Uses:** Firebase Messaging (existing), DataStore (existing), Room (Phase 1), WorkManager (Phase 2)
**Avoids:** MP-7 (FCM token lifecycle via WorkManager), existing `NAVIGATE_TO_SETTINGS` deep-link pattern extended for order/product destinations
**Research flag:** Standard patterns — skip `/gsd:research-phase`

### Phase 7: Reviews, Seller Discounts, and Personalization
**Rationale:** Reviews require a delivered order (Phase 4+5). Seller discounts require checkout (Phase 4). Personalization settings (theme, orientation) are low-risk and can be batched here as polish. These are the last table-stakes and differentiator features.
**Delivers:** Verified-purchase review submission and display with aggregate rating; seller discount/coupon code management with server-side Cloud Function validation; dark/light/system theme via DataStore; orientation lock; profile management improvements (seller storefront, business info)
**Avoids:** Fake review problem (purchase verification enforced server-side via Cloud Function or Firestore rules, not client-side); coupon bypass (validation server-side only)
**Research flag:** Reviews purchase verification logic and Seller Storefront page need `/gsd:research-phase` for Firestore security rule design

---

### Phase Ordering Rationale

- Phases 1–2 are infrastructure-only. They produce no visible user features but are load-bearing for everything else. Attempting to build cart before Room foundation results in rebuilding cart when the offline layer is added.
- Phases 3–5 follow the commercial transaction critical path: cart → checkout → order lifecycle. This ordering mirrors the customer journey and ensures each phase is testable end-to-end.
- Phase 6 (FCM) is placed after order management because order status changes are the primary notification triggers. Building FCM before orders would require stub triggers.
- Phase 7 batches lower-dependency features that can be parallelized internally (reviews, discounts, and personalization are independent of each other).

---

### Research Flags

**Needs `/gsd:research-phase` during planning:**
- **Phase 4 (Stripe Checkout):** Stripe PaymentSheet Compose API specifics (especially `rememberPaymentSheet` vs. `ActivityResultLauncher` approach), Cloud Function contract design for `createPaymentIntent` and `handlePaymentSuccess` webhook, and Stripe webhook verification in Cloud Functions all need detailed research before implementation begins
- **Phase 7 (Reviews / Discounts):** Firestore security rule design for purchase verification (only allow review if `purchaseHistory` contains `productId`) and server-side coupon validation atomicity (Firestore transaction to decrement usage count) need research

**Standard patterns — skip `/gsd:research-phase`:**
- **Phase 1 (Room Foundation):** NiA-recommended offline-first pattern; well-documented in Android developer docs
- **Phase 2 (Write Queue):** WorkManager + PendingOperation queue is an established Android pattern
- **Phase 3 (Cart/Wishlist):** CRUD with Room and Firestore sync; straightforward application of Phase 1–2 infrastructure
- **Phase 5 (Order Tracking):** Standard Firestore real-time listener + status state machine
- **Phase 6 (FCM):** Firebase Messaging patterns are well-documented; existing `MessagingService.kt` provides the extension point

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All recommendations based on direct `libs.versions.toml` codebase inspection; all version numbers marked [VERIFY] as external search was unavailable — must confirm before adding to toml |
| Features | HIGH | Feature set directly derived from PROJECT.md requirements and standard e-commerce patterns; complexity estimates based on real codebase analysis |
| Architecture | HIGH | All architectural decisions derived from direct codebase inspection (existing patterns, module structure, Koin setup, callbackFlow usage) |
| Pitfalls | HIGH | Pitfalls identified from direct code analysis (e.g., `Purchase.quantity: Double`, Room in `app/build.gradle.kts`, fire-and-forget FCM token write, empty test scaffolding); not inferred from generic advice |

**Overall confidence: HIGH**

The research is grounded in direct codebase inspection rather than generic Android e-commerce advice. The primary uncertainty is library version numbers (all [VERIFY]) since external search was unavailable during the research session.

---

### Gaps to Address

- **Library version verification:** All [VERIFY]-marked versions (WorkManager, Stripe, Turbine, MockK, Koin test artifacts, coroutines-test) must be confirmed against official release pages before adding to `libs.versions.toml`. Handle during Phase 1 setup work.
- **Stripe Cloud Function contract:** The exact API shape for `createPaymentIntent` and `handlePaymentSuccess` webhook is not designed in this research. Must be designed before Phase 4 begins — recommend a dedicated `/gsd:research-phase` for the Stripe + Cloud Functions integration.
- **Firestore security rules:** No `firestore.rules` file was found in the repository. Security rules for new collections (orders, cart, reviews, notifications) must be written and tested in the Firebase Emulator as each phase introduces new data types. This is an ongoing per-phase concern, not a one-time task.
- **Firebase Emulator setup:** The test strategy assumes Firebase Emulator for repository integration tests. Emulator configuration (`.firebaserc`, `firebase.json` with emulator ports) must be set up before Phase 1 integration tests can be written.
- **Stripe webhook secret:** The `handlePaymentSuccess` webhook requires a Stripe webhook signing secret stored in Cloud Function environment variables. This is a deployment-configuration concern that must be resolved before Phase 4 can be tested end-to-end.

---

## Sources

### Primary (HIGH confidence — direct codebase inspection)
- `libs.versions.toml` — all version references, existing dependencies, Koin BOM version
- `data/build.gradle.kts` and `app/build.gradle.kts` — Room declared in both modules (pitfall identified)
- `domain/src/main/java/com/wenubey/domain/model/Purchase.kt` — `quantity: Double` type bug identified
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` — existing Koin module structure, Firebase.functions wired
- `app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt` — ViewModel injection pattern
- All existing repository implementations (`CategoryRepositoryImpl`, `ProductRepositoryImpl`) — callbackFlow pattern confirmed
- `CONTINUATION_NOTES.md`, `PRODUCT_BUGS_AND_GAPS.md` — existing known gaps

### Secondary (MEDIUM confidence — training data, knowledge cutoff January 2025)
- Android developer documentation patterns for Room offline-first (NiA architecture)
- WorkManager documentation for background sync patterns
- Stripe Android SDK PaymentSheet integration patterns (version 20.x)
- Firebase Cloud Functions + Stripe webhook patterns

### Tertiary (LOW confidence — versions need external verification)
- WorkManager 2.9.1 — [VERIFY] at `developer.android.com/jetpack/androidx/releases/work`
- Stripe Android SDK 20.52.0 — [VERIFY] at `github.com/stripe/stripe-android/releases`
- Turbine 1.2.0 — [VERIFY] at `github.com/cashapp/turbine/releases`
- MockK 1.13.14 — [VERIFY] at `github.com/mockk/mockk/releases`
- Koin test artifact names under BOM 4.0.1 — [VERIFY] artifact names unchanged from 3.x but confirm

---

*Research completed: 2026-02-19*
*Ready for roadmap: yes*
