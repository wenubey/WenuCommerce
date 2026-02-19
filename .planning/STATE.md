# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-19)

**Core value:** Customers can browse, search, and purchase products with a seamless offline-capable experience
**Current focus:** Phase 1 — Room Foundation

## Current Position

Phase: 1 of 11 (Room Foundation)
Plan: 1 of 3 in current phase
Status: In progress
Last activity: 2026-02-19 — Completed plan 01-01 (Room entities, DAOs, TypeConverters, mappers, Koin databaseModule)

Progress: [█░░░░░░░░░] 3%

## Performance Metrics

**Velocity:**
- Total plans completed: 1
- Average duration: 6 min
- Total execution time: 0.1 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-room-foundation | 1/3 complete | 6 min | 6 min |

**Recent Trend:**
- Last 5 plans: 01-01 (6 min)
- Trend: baseline established

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

### Pending Todos

None.

### Blockers/Concerns

- [Pre-Phase 1]: All library versions marked [VERIFY] in research (WorkManager, Stripe SDK, Turbine, MockK, Koin test artifacts) must be confirmed at Maven Central / GitHub before adding to `libs.versions.toml`
- [Pre-Phase 1]: `Purchase.quantity` is currently `Double` in domain model — must fix to `Int` before CartItemEntity design in Phase 3
- [Pre-Phase 4]: Stripe Cloud Function contract (createPaymentIntent API shape, handlePaymentSuccess webhook) must be designed before Phase 4 begins — research-phase recommended
- [Pre-Phase 4]: Stripe webhook signing secret must be stored in Cloud Function environment variables before Phase 4 can be tested end-to-end
- [Ongoing]: Firestore security rules for new collections (cart, orders, reviews, notifications) must be written per phase as new data types are introduced

## Session Continuity

Last session: 2026-02-19
Stopped at: Completed 01-01-PLAN.md (Room Foundation — entities, DAOs, mappers, Koin databaseModule)
Resume file: .planning/phases/01-room-foundation/01-02-PLAN.md
