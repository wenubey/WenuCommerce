# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-19)

**Core value:** Customers can browse, search, and purchase products with a seamless offline-capable experience
**Current focus:** Phase 3 — Cart & Wishlist

## Current Position

Phase: 3 of 11 (Cart & Wishlist)
Plan: 1 of ? in current phase — COMPLETE
Status: Active — Phase 3 plan 01 complete (Room data layer done), ready for Phase 3 plan 02
Last activity: 2026-02-20 - Completed plan 03-01: Room data layer for Cart and Wishlist

Progress: [███░░░░░░░] 15%

## Performance Metrics

**Velocity:**
- Total plans completed: 8
- Average duration: 4.6 min
- Total execution time: 0.59 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-room-foundation | 4/4 complete | 17 min | 4.25 min |
| 02-offline-write-queue | 3/3 complete | 15 min | 5.0 min |
| 03-cart-wishlist | 1/? complete | 5 min | 5.0 min |

**Recent Trend:**
- Last 5 plans: 02-01 (6 min), 02-02 (6 min), 02-03 (3 min), 03-01 (5 min)
- Trend: stable

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Research]: Room must live in `data` module only — it is currently declared in both `app/build.gradle.kts` and `data/build.gradle.kts`; move it before writing any entities
- [Research]: Domain models must NOT become Room entities — create separate `*Entity` classes in `data/local/entity/` with mappers
- [Research]: All Stripe payment logic must go through Firebase Cloud Functions; client only receives `clientSecret`
- [Research]: Conflict resolution policy is defined per entity: Products = server wins; Cart = local wins for adds / server for stock; Orders = server-authoritative / forward-only; Profile = last-write-wins with `updatedAt` guard; Payments = Firestore is source of truth / no optimistic Room writes
- [01-01]: Room KSP annotation processing runs only in :data module; :app gets room.runtime + room.ktx for Room.databaseBuilder API access — removing ksp(room.compiler) from app satisfies the "KSP only in data" requirement
- [01-01]: fallbackToDestructiveMigration() guarded by BuildConfig.DEBUG — release builds throw IllegalStateException on missing migration, forcing explicit migration objects
- [01-01]: JSON columns in entities use kotlinx.serialization with runCatching + safe defaults to prevent crashes on corrupt/missing data
- [01-01]: Enum fields stored as String name in entities; valueOf() with runCatching fallback in mappers handles unknown values forward-compatibly
- [01-02]: SyncManager uses SupervisorJob so a product listener failure does not cancel the category listener
- [01-02]: Observe methods return DAO Flows directly — Firestore callbackFlow is gone from repositories; Firestore lives only in SyncManager and one-shot fetch methods
- [01-02]: deleteCategory uses categoryDao.deleteById() rather than upsert with isActive=false — simpler since observeActiveCategories filters by isActive=1
- [01-02]: approveProduct and suspendProduct update Room cache immediately after Firestore transaction for instant UI status reflection
- [01-02]: syncModule placed before repositoryModule in appModules to ensure DAOs resolve before SyncManager is created
- [Phase 01-room-foundation]: ConnectivityViewModel initialValue=true prevents false-offline flash on startup before callbackFlow emits
- [Phase 01-room-foundation]: Initial state emitted inside callbackFlow before registerDefaultNetworkCallback — required for correct airplane-mode cold launch
- [Phase 01-room-foundation]: Box overlay in MainActivity wraps full navigation graph so offline banner appears on every screen without per-screen wiring
- [Phase 01-room-foundation]: Room user cache cleared in logOut, deleteAccount, and authStateListener null — redundant paths ensure cache is always wiped on sign-out
- [01-04]: shimmerEffect() modifier extension lives in ShimmerProductCard.kt and shared via import — no separate ShimmerModifier.kt file needed for a small package
- [01-04]: PullToRefreshBox wraps only the main content area; search bar stays above so it remains accessible during refresh
- [01-04]: isOnline StateFlow exposed directly on CustomerHomeViewModel (SharingStarted.WhileSubscribed 5000) — not passed as composable parameter
- [01-04]: Scaffold added to MainActivity solely for SnackbarHost; padding lambda uses _ (ignored) to prevent layout shift to existing Box overlay
- [01-04]: SyncEvent sealed interface is top-level in SyncManager.kt — imported as com.wenubey.data.local.SyncEvent (not SyncManager.SyncEvent)
- [quick-1]: Source.SERVER used in manualSync() one-shot fetches only — startSync() real-time listeners unchanged; emit() replaces tryEmit() in manualSync() catch for guaranteed SharedFlow delivery in suspend context
- [02-01]: OperationType enum defines initial types (ADD_TO_CART, UPDATE_CART_QUANTITY, REMOVE_FROM_CART, UPDATE_PROFILE, SUBMIT_REVIEW); more will be added in future phases
- [02-01]: OperationStatus enum tracks lifecycle: PENDING → IN_PROGRESS → (deleted on success) or FAILED — successful operations deleted rather than marked to keep table lean
- [02-01]: MAX_RETRIES = 3 hardcoded in SyncWorker; three retries with exponential backoff (30s initial) balances reliability vs. battery/network cost
- [02-01]: Sequential queue draining (getNextPending() + re-enqueue after success) prevents race conditions and simplifies error handling
- [02-01]: SyncWorker repository wiring deferred to Phase 3+ — when() block contains TODOs for actual Firestore write dispatching per operation type
- [02-01]: WorkManagerInitializer removed via AndroidManifest.xml to prevent factory conflict — CRITICAL for Koin WorkerFactory dependency injection
- [02-02]: PendingSyncBanner merges offline-only, pending-only, and combined states with conditional text/icons rather than separate composables
- [02-02]: Banner dismiss stores current pending count; reappears when count increases (not on app restart)
- [02-02]: isSyncing state derived from WorkManager.getWorkInfosForUniqueWorkFlow() Flow API
- [02-02]: Retry/discard actions use IconButtons instead of swipe-to-dismiss gestures
- [02-02]: OperationType enum mapped to user-friendly strings (e.g., ADD_TO_CART -> 'Cart update')
- [02-02]: SyncEvent sealed interface extended with OfflineWriteQueued and SyncPartialFailure for future snackbar triggers
- [02-03]: with(NavDestination) scope required to call hasRoute(KClass) — the KClass overload is declared @JvmStatic inside NavDestination.Companion; plain call resolves to String overload
- [02-03]: isOnQueueManagementScreen derived from currentBackStackEntryAsState() in setContent — reactive to back-stack changes, no ViewModel needed
- [02-03]: AnimatedVisibility visible = shouldShowBanner && !isOnQueueManagementScreen — banner slides out on navigate-to-queue, slides back in on back-navigation
- [03-01]: CartRepositoryImpl takes Application Context as constructor param for SyncWorker.enqueue() — injected via Koin androidContext()
- [03-01]: AddToCartPayload and UpdateCartQuantityPayload declared in CartRepositoryImpl.kt (co-located with repo) — no separate package for small DTOs
- [03-01]: SyncManager.emitOfflineWriteQueued() added as public method — syncEvents is read-only SharedFlow externally; _syncEvents.tryEmit is private
- [03-01]: REMOVE_FROM_CART stores productId directly as payloadJson string — single value needs no wrapper class
- [03-01]: clearCart() does not queue PendingOperation — used post-checkout when Firestore already reflects cleared state
- [03-01]: Purchase.quantity changed Double -> Int; backward-compatible via runCatching in UserMapper purchaseHistoryJson deserialization

### Pending Todos

None.

### Blockers/Concerns

- [Pre-Phase 1]: All library versions marked [VERIFY] in research (WorkManager, Stripe SDK, Turbine, MockK, Koin test artifacts) must be confirmed at Maven Central / GitHub before adding to `libs.versions.toml`
- [Pre-Phase 4]: Stripe Cloud Function contract (createPaymentIntent API shape, handlePaymentSuccess webhook) must be designed before Phase 4 begins — research-phase recommended
- [Pre-Phase 4]: Stripe webhook signing secret must be stored in Cloud Function environment variables before Phase 4 can be tested end-to-end
- [Ongoing]: Firestore security rules for new collections (cart, orders, reviews, notifications) must be written per phase as new data types are introduced

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 1 | Fix sync failure snackbar not showing on pull-to-refresh with no network | 2026-02-19 | 692f26b | [1-fix-sync-failure-snackbar-not-showing-on](./quick/1-fix-sync-failure-snackbar-not-showing-on/) |

## Session Continuity

Last session: 2026-02-20
Stopped at: Completed 03-01-PLAN.md (Phase 3 plan 01 — Room data layer for Cart and Wishlist)
Resume file: .planning/phases/03-cart-wishlist/03-02-PLAN.md (next plan in Phase 3)
