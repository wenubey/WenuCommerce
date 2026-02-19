---
phase: 01-room-foundation
plan: 01
subsystem: database
tags: [room, koin, kotlin-serialization, android, dao, entity, type-converters]

# Dependency graph
requires: []
provides:
  - WenuCommerceDatabase v1 (Room, schema exported to data/schemas/)
  - ProductEntity, CategoryEntity, UserEntity with JSON-column defaults
  - RoomTypeConverters for all nested serializable domain types
  - ProductDao, CategoryDao, UserDao with Flow queries and @Upsert methods
  - ProductMapper, CategoryMapper, UserMapper for bidirectional entity/domain conversion
  - Koin databaseModule providing WenuCommerceDatabase + all three DAOs
affects:
  - 02-repository-migration
  - 03-cart
  - all phases that read/write local data

# Tech tracking
tech-stack:
  added:
    - androidx.room:room-runtime 2.6.1
    - androidx.room:room-ktx 2.6.1
    - Room KSP annotation processing (in :data module only)
  patterns:
    - Entities are separate from domain models; mappers convert bidirectionally
    - JSON columns use kotlinx.serialization with runCatching crash protection and safe defaults
    - Room KSP runs only in :data module; :app module gets runtime-only deps
    - fallbackToDestructiveMigration() guarded by BuildConfig.DEBUG

key-files:
  created:
    - data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt
    - data/src/main/java/com/wenubey/data/local/entity/ProductEntity.kt
    - data/src/main/java/com/wenubey/data/local/entity/CategoryEntity.kt
    - data/src/main/java/com/wenubey/data/local/entity/UserEntity.kt
    - data/src/main/java/com/wenubey/data/local/dao/ProductDao.kt
    - data/src/main/java/com/wenubey/data/local/dao/CategoryDao.kt
    - data/src/main/java/com/wenubey/data/local/dao/UserDao.kt
    - data/src/main/java/com/wenubey/data/local/converter/RoomTypeConverters.kt
    - data/src/main/java/com/wenubey/data/local/mapper/ProductMapper.kt
    - data/src/main/java/com/wenubey/data/local/mapper/CategoryMapper.kt
    - data/src/main/java/com/wenubey/data/local/mapper/UserMapper.kt
    - data/schemas/com.wenubey.data.local.WenuCommerceDatabase/1.json
  modified:
    - app/build.gradle.kts
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/AppModules.kt

key-decisions:
  - "Room KSP runs only in :data module; :app gets room.runtime + room.ktx for Room.databaseBuilder API access"
  - "JSON columns in entities use kotlinx.serialization with runCatching + safe defaults to prevent crashes on corrupt data"
  - "fallbackToDestructiveMigration() is guarded by BuildConfig.DEBUG — release builds will throw IllegalStateException on missing migration"
  - "Enum fields stored as String name (e.g., ProductStatus.ACTIVE) with valueOf() + runCatching fallback in mappers"

patterns-established:
  - "Entity pattern: separate *Entity class from domain model, JSON columns have String type with default empty arrays/objects"
  - "Mapper pattern: file-level private val json = Json{...}, extension functions toDomain()/toEntity() with runCatching on all deserialization"
  - "DAO pattern: Flow-returning queries for reactive UI, suspend functions for one-shot writes, @Upsert for insert-or-update"
  - "Koin module pattern: databaseModule provides singleton DB + individual DAO singles via get<WenuCommerceDatabase>().daoMethod()"

requirements-completed:
  - SYNC-01

# Metrics
duration: 6min
completed: 2026-02-19
---

# Phase 1 Plan 01: Room Foundation Summary

**Room database v1 with ProductEntity/CategoryEntity/UserEntity, three DAOs with Flow queries, JSON TypeConverters with crash protection, bidirectional domain mappers, and Koin databaseModule singleton**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-19T12:04:13Z
- **Completed:** 2026-02-19T12:10:35Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments

- WenuCommerceDatabase (schema v1) compiles; schema JSON exported to `data/schemas/`
- Three entities (products/categories/users tables) with JSON-column defaults and three DAOs with Flow queries and @Upsert
- RoomTypeConverters covers all nested serializable types with runCatching crash protection
- ProductMapper, CategoryMapper, UserMapper provide safe bidirectional conversion including enum fallbacks
- Koin databaseModule provides WenuCommerceDatabase singleton + all three DAOs; registered before repositoryModule in appModules
- Room KSP annotation processing runs only in `:data` module; `:app` gets runtime-only deps

## Task Commits

Each task was committed atomically:

1. **Task 1: Remove duplicate Room deps and create entities, DAOs, TypeConverters, Database** - `e2a9e48` (feat)
2. **Task 2: Create domain mappers and wire Koin databaseModule** - `b1d5e63` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified

- `data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt` - Room @Database v1 with abstract DAO accessors
- `data/src/main/java/com/wenubey/data/local/entity/ProductEntity.kt` - @Entity(tableName="products") with JSON column defaults
- `data/src/main/java/com/wenubey/data/local/entity/CategoryEntity.kt` - @Entity(tableName="categories")
- `data/src/main/java/com/wenubey/data/local/entity/UserEntity.kt` - @Entity(tableName="users")
- `data/src/main/java/com/wenubey/data/local/dao/ProductDao.kt` - Flow queries + suspend upsert/delete + text search
- `data/src/main/java/com/wenubey/data/local/dao/CategoryDao.kt` - Flow queries + suspend upsert/delete
- `data/src/main/java/com/wenubey/data/local/dao/UserDao.kt` - observeCurrentUser Flow + suspend upsert/clearAll
- `data/src/main/java/com/wenubey/data/local/converter/RoomTypeConverters.kt` - @TypeConverter for all serializable nested types
- `data/src/main/java/com/wenubey/data/local/mapper/ProductMapper.kt` - ProductEntity.toDomain() / Product.toEntity()
- `data/src/main/java/com/wenubey/data/local/mapper/CategoryMapper.kt` - CategoryEntity.toDomain() / Category.toEntity()
- `data/src/main/java/com/wenubey/data/local/mapper/UserMapper.kt` - UserEntity.toDomain() / User.toEntity()
- `data/schemas/com.wenubey.data.local.WenuCommerceDatabase/1.json` - Room schema v1 export
- `app/build.gradle.kts` - Removed ksp(room.compiler); kept room.runtime + room.ktx for Koin module API access
- `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` - Added databaseModule with Room.databaseBuilder
- `app/src/main/java/com/wenubey/wenucommerce/di/AppModules.kt` - Added databaseModule before repositoryModule

## Decisions Made

- Room KSP runs only in `:data` module; `:app` gets `room.runtime` + `room.ktx` for `Room.databaseBuilder` API access
- `fallbackToDestructiveMigration()` guarded by `BuildConfig.DEBUG` — release builds throw on missing migration
- Enum fields stored as their String name with `valueOf() + runCatching` fallback in mappers for forward compatibility
- JSON columns use `kotlinx.serialization.json.Json` (already a transitive dep) — no new serialization library needed

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Room runtime dependency needed in :app module for Koin databaseModule**
- **Found during:** Task 2 (wire Koin databaseModule) — `:app:compileDebugKotlin` failed with "Unresolved reference: Room"
- **Issue:** The plan specified removing all Room deps from `app/build.gradle.kts`, but `databaseModule` in DataModule.kt (in `:app`) calls `Room.databaseBuilder()` which requires `room.runtime`. Without it the Koin module cannot compile.
- **Fix:** Added `implementation(libs.room.runtime)` and `implementation(libs.room.ktx)` back to `app/build.gradle.kts`. The plan requirement "Room KSP runs only in data module" is satisfied because `ksp(libs.room.compiler)` is NOT present in app — only the runtime library is.
- **Files modified:** `app/build.gradle.kts`
- **Verification:** `./gradlew :app:compileDebugKotlin` succeeded; `./gradlew assembleDebug` succeeded
- **Committed in:** `b1d5e63` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 3 - blocking)
**Impact on plan:** The fix correctly interprets the plan intent (KSP runs only in :data). No scope creep.

## Issues Encountered

None beyond the auto-fixed blocking issue above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Room infrastructure is complete; Plan 02 (CategoryRepository migration) and Plan 03 (ProductRepository migration) can proceed
- All three DAOs are available via Koin injection
- Schema v1 JSON is tracked; future migrations will need explicit migration objects in release builds

---
*Phase: 01-room-foundation*
*Completed: 2026-02-19*
