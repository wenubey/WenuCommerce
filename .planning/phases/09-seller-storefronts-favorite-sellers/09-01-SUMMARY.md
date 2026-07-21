---
phase: 09-seller-storefronts-favorite-sellers
plan: 01
subsystem: data-foundation
tags: [room, migration, firestore, repository, di, followed-sellers]
requires: []
provides:
  - FollowedSeller domain model
  - FollowedSellersRepository interface (no anonymous branch)
  - Room followed_sellers table + MIGRATION_10_11
  - FollowedSellersRepositoryImpl (Room-first, fire-and-forget Firestore)
  - User.followerCount surfaced via FirestoreRepositoryImpl.getUser()
  - Koin DI wiring for FollowedSellerDao + FollowedSellersRepository
affects:
  - data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt (schema v11)
  - data/src/main/java/com/wenubey/data/repository/FirestoreRepositoryImpl.kt (getUser reads followerCount)
  - domain/src/main/java/com/wenubey/domain/model/user/User.kt (+ followerCount)
  - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt (DAO + repo + migration)
tech-stack:
  added: []
  patterns:
    - Room-first + fire-and-forget Firestore (cloned from WishlistRepositoryImpl minus anonymous branch)
    - Composite primary key (userId, sellerId) for user-scoped local rows
    - Scalar Firestore field read (getLong) + data-class copy to inject aggregate into a @Serializable model
key-files:
  created:
    - domain/src/main/java/com/wenubey/domain/model/FollowedSeller.kt
    - domain/src/main/java/com/wenubey/domain/repository/FollowedSellersRepository.kt
    - data/src/main/java/com/wenubey/data/local/entity/FollowedSellerEntity.kt
    - data/src/main/java/com/wenubey/data/local/dao/FollowedSellerDao.kt
    - data/src/main/java/com/wenubey/data/local/mapper/FollowedSellerMapper.kt
    - data/src/main/java/com/wenubey/data/repository/FollowedSellersRepositoryImpl.kt
    - data/src/test/java/com/wenubey/data/repository/FollowedSellersRepositoryTest.kt
    - data/schemas/com.wenubey.data.local.WenuCommerceDatabase/11.json
  modified:
    - domain/src/main/java/com/wenubey/domain/model/user/User.kt
    - data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt
    - data/src/main/java/com/wenubey/data/repository/FirestoreRepositoryImpl.kt
    - data/src/androidTest/java/com/wenubey/data/local/WenuCommerceMigrationTest.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt
decisions:
  - "D-03 honoured: no anonymous branch in FollowedSellersRepository; auth guard lives in the ViewModel (09-03)."
  - "followerCount surfaced as a scalar getLong read in FirestoreRepositoryImpl.getUser (not on the @Serializable User) so client code cannot write the aggregate — enforces T-09-01 mitigation."
  - "No UserEntity change: followerCount is not cached in Room (RESEARCH A4)."
metrics:
  duration: ~15m
  completed: 2026-07-21
---

# Phase 09 Plan 01: Followed-Sellers Data Foundation Summary

Offline-first follow relationship data layer: `FollowedSeller` snapshot model, `FollowedSellersRepository` contract (no anonymous branch per D-03), Room `followed_sellers` table with composite PK (userId, sellerId), `MIGRATION_10_11`, `FollowedSellersRepositoryImpl` cloning the wishlist Room-first / fire-and-forget Firestore pattern, `User.followerCount` (default 0) plus `FirestoreRepositoryImpl.getUser` scalar read, DI bindings, unit + migration tests.

## What Was Built

### Task 1 — Domain layer (commit `906118f`)
- `FollowedSeller(sellerId, sellerName, sellerLogoUrl, followedAt)` — plain data class, not `@Serializable` (Firestore doc is written as a `Map`).
- `FollowedSellersRepository` — 4 methods only: `observeFollowedSellers`, `isFollowing`, `followSeller`, `unfollowSeller`. `syncAnonymousOnLogin` intentionally omitted.
- `User.followerCount: Int = 0` — appended field with default so every existing constructor / test compiles unchanged.

### Task 2 — Data layer, DI, tests (commit `07a0406`)
- `FollowedSellerEntity(@Entity tableName = "followed_sellers", primaryKeys = ["userId", "sellerId"])`.
- `FollowedSellerDao`: `observeFollowedSellers` (ORDER BY followedAt DESC), `getFollowedSeller`, `@Upsert upsert`, `deleteItem`, `isFollowing` (SELECT EXISTS Flow). `getItemsForUser` / `deleteAllForUser` deliberately omitted (D-03).
- `FollowedSellerMapper.toDomain` / `toEntity(userId)`.
- `WenuCommerceDatabase` bumped 10 → 11; entities list gained `FollowedSellerEntity`; `followedSellerDao()` accessor added; `MIGRATION_10_11` creates the table + `index_followed_sellers_userId`.
- `FollowedSellersRepositoryImpl` — Room-first upsert/delete, Firestore `set`/`delete` wrapped in try/catch and logged via Timber. Writes ONLY the follow doc body — never `followerCount` (T-09-01).
- `FirestoreRepositoryImpl.getUser` — after `toObject(User)`, reads `userDoc.getLong("followerCount") ?: 0L` and returns `user.copy(followerCount = ...)`.
- `DataModule` — `FollowedSellerDao` single, `singleOf(::FollowedSellersRepositoryImpl).bind<FollowedSellersRepository>()`, `MIGRATION_10_11` registered.
- Tests:
  - `FollowedSellersRepositoryTest` (JVM unit): fake in-memory DAO + MockK Firestore (success + throwing variants). 5 tests: upsert shape, fire-and-forget on Firestore failure, unfollow deletes row, `observeFollowedSellers` mapping, `isFollowing` flow transitions.
  - `WenuCommerceMigrationTest`: `migrate10To11_createsFollowedSellersTable` + `migrate6To11_chainsCleanly` added.
- Room schema `data/schemas/.../11.json` generated by KSP and committed.

## Verification Results

| Command | Result |
| ------- | ------ |
| `./gradlew :domain:testDebugUnitTest` | GREEN |
| `./gradlew :data:testDebugUnitTest --tests "*FollowedSellersRepositoryTest"` | GREEN |
| `./gradlew :app:assembleDebug` | GREEN (pre-existing deprecation warnings unrelated to this plan) |
| `./gradlew :data:connectedDebugAndroidTest --tests "*WenuCommerceMigrationTest"` | NOT RUN — no emulator/device available in this worktree. Migration test authored and validated at compile time; device run deferred per plan's acceptance criteria (device-gated). |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocker] Restored build config files missing from the fresh worktree**
- **Found during:** first Gradle run after Task 1.
- **Issue:** the worktree lacked `local.properties` and `app/google-services.json`, so Gradle failed configuration.
- **Fix:** copied both from the main checkout (`/Users/wenubey/AndroidStudioProjects/WenuCommerce`). Both files are `.gitignore`d, so they were not staged.
- **Commit:** N/A (uncommitted infra files).

**2. [Rule 1 - Bug] Kotlin does not allow nested `typealias`**
- **Found during:** first compile of `FollowedSellersRepositoryTest`.
- **Issue:** wrote `private typealias CapturingSlot<T> = io.mockk.CapturingSlot<T>` inside the test class — compile error "Nested and local type aliases are not supported."
- **Fix:** removed the alias, used the fully-qualified `io.mockk.CapturingSlot<Map<String, Any>>?` parameter type on the one helper that needed it.
- **Commit:** rolled into `07a0406`.

Otherwise the plan executed as written.

## Auth / Approval Gates

Plan called out two approval-required items (Room schema change + domain model change). Both were pre-approved by the plan text ("Writing the code + tests is autonomous"), so execution proceeded without additional prompts. No live backend writes occurred — Firestore was mocked in all tests.

## Threat Mitigation Notes

- **T-09-01 (Tampering — client writing `followerCount`)**: `FollowedSellersRepositoryImpl` write payload has exactly four keys: `sellerId`, `sellerName`, `sellerLogoUrl`, `followedAt`. `grep -n followerCount data/src/main/java/com/wenubey/data/repository/FollowedSellersRepositoryImpl.kt` returns no matches — confirmed.
- **T-09-02 (Elevation — anonymous follow)**: no anonymous branch exists in the repo impl; `userId: String` is non-nullable at the interface. Auth guard is the ViewModel's responsibility (09-03).
- **T-09-03 (Info disclosure)**: follow docs are written under `USERS/{userId}/followed_sellers/{sellerId}` — the customer's own subtree — never under a seller path.
- **T-09-04 (Firestore write failure)**: accepted; Timber log confirms best-effort behaviour; validated by the "Firestore write throws" unit test.

## Deferred Issues

- Device-gated migration test (`connectedDebugAndroidTest`). Deferred by design — plan explicitly marks the device run as deferred pending emulator availability. Schema JSON export at v11 was auto-generated by KSP, which means Room's own schema validator will confirm the migration on the first device run.

## Known Stubs

None — every field on `FollowedSeller` / `FollowedSellerEntity` is written from real data at the callsite (09-03 ViewModel will supply seller name + logo from the loaded storefront).

## Self-Check: PASSED

- Files created — verified via `git log --stat` on commits `906118f` and `07a0406`; all seven created files present at their spec paths.
- Commits present — `git log --oneline` shows `906118f` and `07a0406` on this worktree branch.
- Domain unit tests GREEN; repository unit tests GREEN; app assembles.
