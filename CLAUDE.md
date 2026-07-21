# CLAUDE.md — WenuCommerce (Android)

> **Project memory for Claude Code.** Project-specific decisions only.
> General Kotlin / Compose best practices come from loaded **Agent Skills** — do not repeat them here.
> Workflow: **GSD** (discuss → plan → execute → verify → ship). Atomic commits: one logical change = one commit.
> **Language:** this file (and all agent-instruction files in this repo) is English-only, regardless of chat language.

## Skills — two layers, distinct roles

Two skill collections are used together in this repo. Do not conflate them:

- **GSD skills (`gsd-*`)** = **process.** Phase planning, execute, code-review, verify, milestone. `.planning/phases/…`, atomic commit discipline, cross-session trackers. Use when starting a phase, producing a plan, or closing a phase.
- **chrisbanes/skills** (`structuring-a-compose-test`, `testing-coroutines-with-runtest`, `wenu-viewmodel-uistate`, `applying-testing-strategies`, `developing-with-compose-previews`, `finding-nodes-by-tag-text-content`, `asserting-node-state-and-text`, `injecting-touch-gestures`, `synchronizing-with-idle`, `testing-flows-with-turbine`, `testing-lazy-lists`, `printing-the-semantics-tree`, `configuring-junit4-on-android`, `picking-test-doubles`, `mocking-with-mockk`, etc.) = **tactics.** When writing code — especially tests and Compose UI — **this is the default source.**

Rules:
- Before writing a **ViewModel test**, load `wenu-viewmodel-uistate` + `testing-coroutines-with-runtest` + (if Flows are involved) `testing-flows-with-turbine`.
- Before writing a **Compose UI test**, load `structuring-a-compose-test` + `finding-nodes-by-tag-text-content` + `asserting-node-state-and-text`. Add `injecting-touch-gestures` for gestures, `testing-lazy-lists` for LazyColumn, `synchronizing-with-idle` for async recomposition.
- When building a **new Composable**, load `developing-with-compose-previews` (preview-driven).
- **Testing-strategy** questions (pyramid, hermetic, Hilt swap) → `applying-testing-strategies`.
- For a topic not covered by the exact skill name in the listing, search with ToolSearch first; fall back to GSD only when nothing matches.

Do not produce Compose test APIs from memory — load the skill and write from there. Loading a skill is cheap; debugging wrong APIs is expensive.

## Working mode (read first) — **Autonomous test-driven**

The user does NOT want to be asked "does this work?" repeatedly. So:

- **New feature / change ships with tests.** No tests = work is not done.
- **Claude writes and runs the tests.** Build + the relevant test suite **green → work is done.**
- If red: do NOT ask the user — enter **diagnose + fix + re-run** loop autonomously. If the same error repeats for 3 turns with no progress, OR an architectural decision is required, *then* ask.
- User approval is required ONLY for:
  - Adding a new library / pattern (not listed in the stack below)
  - Data model / Firestore schema / Room migration changes
  - Billable integrations (Stripe, paid-plan Firebase Functions)
  - Hard-to-reverse operations (`git reset --hard`, prod push, bulk Firestore writes)
- Otherwise: small step → write test → run → commit. Report 1–2 sentences: what changed, are tests green.

## What we're building

A multi-role **mobile e-commerce** app: customer (browse + checkout), seller (products/categories/storefront/discounts/dashboard), admin (analytics/dashboard). Firebase backend (Auth + Firestore + Functions), Stripe payments, Room as offline cache.

## Tech stack (decided — ask before adding anything new)

- Kotlin (latest stable), JDK 11 (module-defined), AGP/Compose BOM via version catalog
- Jetpack Compose + **Material 3**, single-Activity
- **Navigation Compose**, type-safe routes (no string routes)
- Coroutines + Flow; UI state is **`StateFlow<XxxUiState>`** + event callbacks (UDF)
- **Koin** DI (`koin-bom 4.0.1`, modules in `app/di/`)
- **Room** (offline cache + schema export → `data/schemas/`)
- **Firebase**: Auth, Firestore, Functions, Crashlytics
- **Stripe** Payments SDK
- minSdk 24, targetSdk 35, compileSdk 35
- All dependencies via **`gradle/libs.versions.toml`** — never raw strings in `build.gradle.kts`

## Architecture — Multi-module + MVVM + UDF

```
:app      → UI (Compose), ViewModel, navigation, DI modules
:domain   → pure Kotlin: models, use cases, repository interfaces
:data     → Room, Firebase, Stripe adapters, repository implementations
```

Rules:

- `:domain` has **no** Android or Firebase dependency (pure Kotlin / coroutines).
- `:app` never calls `:data` directly — repository interfaces from `:domain` are injected.
- Composables hold no business logic. Stateless when possible; hoist state.
- Every ViewModel emits a single immutable `XxxUiState` (`StateFlow`); user events arrive as callbacks (UDF).
- Repository returns Flow → ViewModel maps to UiState.
- Domain logic (discount calc, validation, pricing, tax, coupon rules) lives in `:domain/usecase` with **independent unit tests**.

## Package layout (feature-by-feature inside `:app`)

```
com.wenubey.wenucommerce
├── core/        (components, validators, connectivity, common UI)
├── di/          (Koin modules)
├── navigation/  (type-safe routes)
├── ui/theme/    (M3 theme)
├── customer/   seller/   admin/
│   └── <feature>/  (screen + viewmodel + uistate)
├── sign_in/ sign_up/ verify_email/ onboard/
└── notification/ queue_management/
```

## Testing & verification — **non-negotiable gate**

This is the project's backbone. "Does it work?" is answered by tests.

### Minimum per feature

- **ViewModel** → state-machine unit test (`StandardTestDispatcher` + `MainDispatcherRule` + Turbine)
- **UseCase / domain logic** → JUnit unit test (pure)
- **Repository** → fake DAO / fake remote unit test (does NOT hit real Firestore)
- **Composable** (if a screen) → at least one Compose UI test: state render + one critical interaction
- **Validator / formatter** → parametric (table) test

### Tests do NOT

- Connect to real Firebase. Use **fake / in-memory** repository implementations.
- Use `Thread.sleep` to wait — use `advanceUntilIdle()` / `IdlingResource` / Turbine.
- Leave hardcoded coordinates or `mutableStateOf` leaks in Composable tests.

### Commands (Claude runs these)

```bash
./gradlew :app:assembleDebug                       # build health
./gradlew testDebugUnitTest                        # all unit tests (3 modules)
./gradlew :domain:testDebugUnitTest                # domain only (fastest feedback)
./gradlew :app:testDebugUnitTest --tests "*DiscountCreateEditViewModelTest"
./gradlew connectedDebugAndroidTest                # UI/instrumentation (emulator required)
./gradlew lint
```

### Definition of done

- [ ] `:app:assembleDebug` green
- [ ] Affected modules' `testDebugUnitTest` green
- [ ] New/changed screens have green Compose UI tests
- [ ] Atomic commit with GSD-format message (`feat(05-02): …`)

If not green → not done. You don't ask "is this OK?" — the green test says it's OK.

## Autonomous loop (recipe)

1. **Plan**: what will change, which files, which tests? (brief, mentally)
2. **Write the test**: failing first (red).
3. **Implement**: the smallest change that passes.
4. **Run**: the relevant `testDebugUnitTest` target.
5. If red → read logs, diagnose, fix, back to step 4.
   - **3 failed turns** or an architectural decision needed → ask the user.
6. Green → `assembleDebug` (smoke) → commit → next step.
7. End of phase: short report — what changed, which tests added/run.

## Do / Don't

- **DO** keep Koin modules feature-scoped; add new modules under `di/`.
- **DO** confirm new Firestore collections / Room entities with the user before adding.
- **DO** be test-first on Stripe, Firebase Functions, and any money / side-effect flow; never touch prod.
- **DO** atomic commits. `feat(<phase>): …`, `fix(<phase>): …`, `test(<phase>): …`, `refactor: …`.
- **DON'T** put business logic in composables, scatter `mutableStateOf`, or use string routes.
- **DON'T** add a Material 2 component.
- **DON'T** soften an assertion or `@Ignore` a test to make CI green — write the reason and tell the user.
- **DON'T** add a co-author line in git commits (see `feedback_no_coauthor` memory).
- **DON'T** run destructive commands (`--no-verify`, `git reset --hard`, force push) **without explicit user request**.

## Phase 0 — Test Backfill (ongoing)

Existing code arrived without tests. **Phase 0** establishes unit + UI test backfill across all modules.

- Plan + checklist: **`PHASE-0-TEST-BACKFILL.md`** (cross-session tracker)
- Commit prefix: `test(backfill/<feature>): …` / `fix(backfill/<feature>): …`
- Bugs found are fixed in the same turn (if small); large ones go into `PRODUCT_BUGS_AND_GAPS.md` + leave the test `@Ignore`d + report to user
- **Rule before starting a new phase**: the backfill tests for the affected feature must be green; if not, do backfill first.

## Repo notes

- Modules: `:app`, `:data`, `:domain` (`settings.gradle.kts`)
- Room schemas: `data/schemas/` (committed; treated as source-of-truth for migration audits)
- Functions: `functions/` (Firebase Cloud Functions — Node)
- Live phase docs: `PRODUCT_BUGS_AND_GAPS.md`, `SEARCH_FILTER_SPEC.md`, `CONTINUATION_NOTES.md`
- Last completed: Phase 5 Discounts (3 plans, last commit `36cc54a`)
