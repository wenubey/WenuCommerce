---
phase: 03-cart-wishlist
plan: 03
subsystem: database
tags: [room, firestore, wishlist, anonymous, koin, coroutines]

# Dependency graph
requires:
  - phase: 03-01
    provides: WishlistItemDao, WishlistItemEntity, WishlistItemMapper Room data layer

provides:
  - WishlistRepository domain interface with observeWishlistItems, isWishlisted, toggleWishlist, removeFromWishlist, syncAnonymousOnLogin
  - WishlistRepositoryImpl with Room-first storage, anonymous userId="" support, and Firestore sync
  - Anonymous-to-real-user migration on login via AuthViewModel
  - WishlistRepository injectable via Koin (repositoryModule)

affects: [03-04-wishlist-ui, any phase using wishlist data]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Room-first write pattern with async Firestore sync (non-blocking, Room is source of truth)
    - Anonymous user support via userId="" sentinel in Room rows
    - Login-triggered migration: anonymous items cloned to real userId, anonymous rows deleted
    - Koin singleOf with bind for repository registration

key-files:
  created:
    - domain/src/main/java/com/wenubey/domain/repository/WishlistRepository.kt
    - data/src/main/java/com/wenubey/data/repository/WishlistRepositoryImpl.kt
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt
    - app/src/main/java/com/wenubey/wenucommerce/AuthViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerCartScreen.kt

key-decisions:
  - "WishlistRepositoryImpl takes WishlistItemDao + FirebaseFirestore + DispatcherProvider (no ProductDao needed — product snapshot data written at toggle time from Product domain model)"
  - "toggleWishlist uses effectiveUserId = userId ?: '' — anonymous users store in Room with empty string userId"
  - "Firestore write on toggle wrapped in try/catch — Room write always completes first; Firestore failure only logs, not surfaced to UI"
  - "syncAnonymousOnLogin: migrates anonymous items to real userId in Room, writes to Firestore, deletes anonymous rows, then merges Firestore items from other devices"
  - "Login sync wired in AuthViewModel: tracks previousUserId to detect null->non-null transition; runs in background coroutine, failure does not block login flow"
  - "CustomerCartScreen.kt pre-existing call-site mismatch fixed (Rule 3 - blocking): added onNavigateToHome and onNavigateToProduct params with defaults"

patterns-established:
  - "Anonymous support pattern: use '' as userId sentinel in Room; check isNotEmpty() before any Firestore write"
  - "Sync-on-login pattern: compare previousUserId with current to detect login transitions in AuthViewModel"

requirements-completed: [WISH-01, WISH-02]

# Metrics
duration: 3min
completed: 2026-02-20
---

# Phase 03 Plan 03: WishlistRepository Summary

**WishlistRepository interface and Room-first implementation with anonymous userId="" support, Firestore sync on toggle, and login-triggered anonymous-to-user migration**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-20T17:57:37Z
- **Completed:** 2026-02-20T18:00:44Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- WishlistRepository domain interface defines all wishlist operations including anonymous support
- WishlistRepositoryImpl provides Room-first reactive Flow observation with Firestore sync for logged-in users
- Anonymous users can toggle wishlist items (stored with userId="") without authentication
- Login triggers syncAnonymousOnLogin in AuthViewModel, migrating anonymous items and merging Firestore state

## Task Commits

Each task was committed atomically:

1. **Task 1: Create WishlistRepository interface and implementation** - `6b5cf87` (feat)
2. **Task 2: Register WishlistRepository in Koin and wire login sync in AuthViewModel** - `d1b4759` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `domain/src/main/java/com/wenubey/domain/repository/WishlistRepository.kt` - Domain interface with 5 operations
- `data/src/main/java/com/wenubey/data/repository/WishlistRepositoryImpl.kt` - Room-first impl with Firestore sync and anonymous migration
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` - Added WishlistRepositoryImpl Koin binding
- `app/src/main/java/com/wenubey/wenucommerce/AuthViewModel.kt` - Added WishlistRepository, wired syncAnonymousOnLogin on login transition
- `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerCartScreen.kt` - Fixed call-site parameter mismatch (Rule 3 auto-fix)

## Decisions Made

- WishlistRepositoryImpl constructor: WishlistItemDao + FirebaseFirestore + DispatcherProvider (no ProductDao — product snapshot data passed in from Product domain model at toggle time)
- Firestore write wrapped in try/catch — Room is source of truth; Firestore failure logs but does not fail the operation
- Anonymous support via userId="" sentinel — check `isNotEmpty()` before any Firestore write
- Login sync in AuthViewModel tracks `previousUserId` to detect null->non-null transition; runs in background coroutine so sync failure never blocks login navigation

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed user field reference: user.id -> user.uuid**
- **Found during:** Task 2 (AuthViewModel wiring)
- **Issue:** Plan referred to `user.id` but User domain model uses `uuid` as the identifier field
- **Fix:** Used `user.uuid` throughout AuthViewModel sync logic with null safety check
- **Files modified:** app/src/main/java/com/wenubey/wenucommerce/AuthViewModel.kt
- **Verification:** `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- **Committed in:** d1b4759 (Task 2 commit)

**2. [Rule 3 - Blocking] Fixed CustomerCartScreen parameter mismatch**
- **Found during:** Task 2 (`assembleDebug` verification)
- **Issue:** `CustomerTabScreen.kt` (modified by plan 03-02 partial work) calls `CustomerCartScreen(onNavigateToHome=..., onNavigateToProduct=...)` but the existing `CustomerCartScreen` only accepted `modifier`. This caused a compilation failure blocking `assembleDebug`.
- **Fix:** Added `onNavigateToHome: () -> Unit = {}` and `onNavigateToProduct: (String) -> Unit = {}` parameters to `CustomerCartScreen`, wired `onClick = onNavigateToHome` for the Continue Shopping button
- **Files modified:** app/src/main/java/com/wenubey/wenucommerce/customer/CustomerCartScreen.kt
- **Verification:** `./gradlew assembleDebug` — BUILD SUCCESSFUL
- **Committed in:** d1b4759 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both auto-fixes necessary for correctness and compilability. No scope creep.

## Issues Encountered

None beyond the two auto-fixed deviations above.

## Next Phase Readiness

- WishlistRepository injectable via Koin, ready for plan 03-04 (wishlist UI)
- Anonymous wishlist toggle works without authentication
- Login triggers automatic migration of anonymous items to user account
- observeWishlistItems returns reactive Flow — ready for UI binding

---
*Phase: 03-cart-wishlist*
*Completed: 2026-02-20*

## Self-Check: PASSED

- FOUND: domain/src/main/java/com/wenubey/domain/repository/WishlistRepository.kt
- FOUND: data/src/main/java/com/wenubey/data/repository/WishlistRepositoryImpl.kt
- FOUND: .planning/phases/03-cart-wishlist/03-03-SUMMARY.md
- FOUND: commit 6b5cf87 (Task 1)
- FOUND: commit d1b4759 (Task 2)
