# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-19)

**Core value:** Customers can browse, search, and purchase products with a seamless offline-capable experience
**Current focus:** Phase 1 — Room Foundation

## Current Position

Phase: 1 of 11 (Room Foundation)
Plan: 0 of 3 in current phase
Status: Ready to plan
Last activity: 2026-02-19 — Roadmap created; 11 phases derived from 77 v1 requirements

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: none yet
- Trend: -

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Research]: Room must live in `data` module only — it is currently declared in both `app/build.gradle.kts` and `data/build.gradle.kts`; move it before writing any entities
- [Research]: Domain models must NOT become Room entities — create separate `*Entity` classes in `data/local/entity/` with mappers
- [Research]: All Stripe payment logic must go through Firebase Cloud Functions; client only receives `clientSecret`
- [Research]: Conflict resolution policy is defined per entity: Products = server wins; Cart = local wins for adds / server for stock; Orders = server-authoritative / forward-only; Profile = last-write-wins with `updatedAt` guard; Payments = Firestore is source of truth / no optimistic Room writes

### Pending Todos

None yet.

### Blockers/Concerns

- [Pre-Phase 1]: All library versions marked [VERIFY] in research (WorkManager, Stripe SDK, Turbine, MockK, Koin test artifacts) must be confirmed at Maven Central / GitHub before adding to `libs.versions.toml`
- [Pre-Phase 1]: `Purchase.quantity` is currently `Double` in domain model — must fix to `Int` before CartItemEntity design in Phase 3
- [Pre-Phase 4]: Stripe Cloud Function contract (createPaymentIntent API shape, handlePaymentSuccess webhook) must be designed before Phase 4 begins — research-phase recommended
- [Pre-Phase 4]: Stripe webhook signing secret must be stored in Cloud Function environment variables before Phase 4 can be tested end-to-end
- [Ongoing]: Firestore security rules for new collections (cart, orders, reviews, notifications) must be written per phase as new data types are introduced

## Session Continuity

Last session: 2026-02-19
Stopped at: Roadmap and STATE.md created; ready to begin Phase 1 planning
Resume file: None
