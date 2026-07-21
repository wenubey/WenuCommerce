---
phase: 09-seller-storefronts-favorite-sellers
plan: 02
subsystem: backend
tags: [firebase-functions, firestore-rules, follower-count, favs-04]
requires:
  - firebase-functions ^6.x (onDocumentWritten v2)
  - firebase-admin ^13.x (FieldValue.increment, set/merge)
provides:
  - functions/src/index.ts::onFollowedSellerWrite (Firestore trigger)
  - functions/src/index.ts::buildFollowedSellerDelta (pure helper)
  - firestore.rules::match /USERS/{userId}/followed_sellers/{sellerId}
affects:
  - USERS/{sellerId}.followerCount (server-authoritative, Admin-SDK-only)
  - USERS/{customerId}/followed_sellers/{sellerId} (owner-scoped subcollection)
tech-stack:
  added: []
  patterns:
    - "Named-export pure helper + structural-grep Jest contract (mirrors buildNewReviewBody/onNewReview)"
    - "Idempotency no-op guard on onDocumentWritten (wasFollowing === isNowFollowing)"
    - "set({...}, {merge:true}) not update() — missing-doc safety (Pitfall 4)"
    - "Best-effort try/catch — trigger never crashes on Firestore write failure"
key-files:
  created:
    - functions/test/onFollowedSellerWrite.test.ts
  modified:
    - functions/src/index.ts
    - firestore.rules
    - functions/test/rules.test.ts
decisions:
  - "Retain the pre-Phase-6 broad /USERS/{userId}/{document=**} recursive rule this phase (Open Question 2). The new match /followed_sellers/{sellerId} rule is the structural FAVS-04 intent that takes effect once the wildcard is retired in the deferred USERS-tightening housekeeping pass."
  - "Follow doc is immutable in the rules (allow update: if false): follow = create, unfollow = delete. No in-place mutation is a valid operation on the client."
  - "followerCount is written ONLY by the Admin-SDK trigger. Clients never touch it from any code path."
metrics:
  duration: "~15 min autonomous"
  completed_date: "2026-07-21"
  tasks_completed: 2 (of 3 — Task 3 is a blocking human-action deploy gate)
  files_touched: 4
  new_tests: 9 (Jest unit/structural) + 2 (rules-jest emulator)
  test_suites_green: "onFollowedSellerWrite (9/9), rules (24/24), full functions suite (96/96)"
---

# Phase 9 Plan 02: onFollowedSellerWrite Trigger + FAVS-04 Rules Summary

Server-side follower-count mechanism: a 2nd-gen `onFollowedSellerWrite` Firestore trigger that atomically maintains `USERS/{sellerId}.followerCount` via `FieldValue.increment(±1)` whenever a customer creates or deletes a `users/{customerId}/followed_sellers/{sellerId}` doc, plus the owner-scoped Firestore security rule for the follow subcollection.

## What shipped

- **`buildFollowedSellerDelta(isNowFollowing: boolean): number`** — pure named export in `functions/src/index.ts`. `true` → `+1`, `false` → `-1`. Extracted for testability without an emulator (mirrors the `buildNewReviewBody` / `computeAggregateStatus` idiom).
- **`onFollowedSellerWrite`** — `onDocumentWritten` trigger on `users/{customerId}/followed_sellers/{sellerId}`.
  - Idempotency: skips when `wasFollowing === isNowFollowing` (metadata-only writes never double-increment — RESEARCH Pitfall 3).
  - Missing-doc safety: uses `set({...}, { merge: true })` rather than `update()` on the seller USERS doc, so a follow event that fires after the seller was deleted upserts a stub instead of throwing NOT_FOUND (RESEARCH Pitfall 4).
  - Wrapped in try/catch — an isolated counter-drift never crashes the trigger (Phase 8 non-blocking style).
- **Firestore rule** in `firestore.rules` inside `match /USERS/{userId}`:
  ```
  match /followed_sellers/{sellerId} {
    allow read, create, delete: if request.auth != null && request.auth.uid == userId;
    allow update: if false;
  }
  ```
  Structural FAVS-04 intent: a customer can only touch their own follow docs; follow docs are immutable (unfollow is a delete).
- **Tests**:
  - `functions/test/onFollowedSellerWrite.test.ts` — 2 pure-helper cases + 7 structural-contract cases against `functions/src/index.ts`. All green.
  - `functions/test/rules.test.ts` — added `(fs-a)` owner-create and `(fs-b)` owner-delete cases. Ran the full rules suite against the Firestore emulator: **24 / 24 green**.
  - Full non-emulator functions suite: **96 / 96 green** (regression check — the new trigger doesn't affect any other suite).

## Commits

| Task | Message                                                                            | Hash    |
| ---- | ---------------------------------------------------------------------------------- | ------- |
| 1    | `test(09-02): add failing test for onFollowedSellerWrite`                          | `286fba9` |
| 1    | `feat(09-02): add onFollowedSellerWrite trigger + buildFollowedSellerDelta helper` | `7fab284` |
| 2    | `feat(09-02): add followed_sellers Firestore rule + rules-jest tests`              | `22c9c9f` |

Atomic, English, GSD-prefixed. Author: `73711986+wenubey@users.noreply.github.com`. No co-author lines.

## Deviations from Plan

**One test-assertion refinement (Rule 1 — bug in the test's structural regex, not in production code):**

- **Found during:** Task 1 GREEN run.
- **Issue:** The initial structural regex required `.doc(event.params.sellerId)` literally, but the trigger destructures `const sellerId = event.params.sellerId` for readability + reuse in the log line, so `.doc(sellerId)` is what actually appears. The test was too strict on shape.
- **Fix:** Split into two assertions — one that `event.params.sellerId` is referenced somewhere in the trigger, and one that `.collection("USERS").doc(<...sellerId...>).set(` appears. Both intents preserved, destructuring allowed.
- **Files modified:** `functions/test/onFollowedSellerWrite.test.ts`.
- **Commit:** folded into `7fab284` (feat) because the test change was a spec refinement discovered during GREEN, not a separate behavioral change.

**One documented residual (planned, not a deviation):**

- The pre-Phase-6 broad `match /USERS/{userId}` rule (`allow read, write: if request.auth != null`) AND the recursive `match /{document=**}` wildcard under it are intentionally retained per **Open Question 2** in `09-RESEARCH.md`. This means the new `match /followed_sellers/{sellerId}` scoped rule is currently OR'd with a permissive rule that grants any authenticated user access — so the cross-user-denied and update-immutability assertions would fail today. That is documented follow-up debt (matches T-09-05 residual note in the plan's threat register). Two rules-jest cases that would have exercised these deferred assertions were intentionally omitted; the `rules.test.ts` file carries an explanatory comment where they would live.

No architectural (Rule 4) decisions triggered. No auth gates hit.

## Task 3 — DEPLOY (blocking human-action checkpoint — NOT run by Claude)

Per CLAUDE.md ("Billable integrations … Hard-to-reverse operations") and the Phase 8 functions-deploy precedent, the deploy is a **user action**. The code + rules + tests are committed but **NOT yet live on the backend**.

**User: run these locally with the Firebase CLI logged in.**

```bash
cd functions && npm run build              # confirm clean TS build (exit 0)
firebase deploy --only functions:onFollowedSellerWrite,firestore:rules
```

Expected:
- `onFollowedSellerWrite` created under Functions (us-central1, Node 20 2nd Gen — matches Phase 8).
- `firestore:rules` deployed.
- Exit 0. If a transient GCP "Internal error" appears (as in Phase 8), retry — it typically clears.

**Optional live smoke (post-deploy):**
1. In the Firebase console, confirm `onFollowedSellerWrite` appears with an `onDocumentWritten` trigger on `users/{customerId}/followed_sellers/{sellerId}`.
2. Create `USERS/<yourUid>/followed_sellers/<someSellerId>` from the console → confirm `USERS/<someSellerId>.followerCount` increments to `1`.
3. Delete the doc → confirm `followerCount` decrements to `0`.

## Verification results

| Check                                                             | Result |
| ----------------------------------------------------------------- | ------ |
| `cd functions && npm test -- --testPathPattern=onFollowedSellerWrite` | 9/9 green |
| `cd functions && npx tsc --noEmit`                                | Exit 0 (no TS errors) |
| Full non-rules functions suite (regression)                       | 96/96 green |
| `cd functions && npm run test:rules` (emulator)                   | 24/24 green (incl. fs-a, fs-b) |
| `grep "match /followed_sellers/{sellerId}" firestore.rules`       | Present |
| `grep "request.auth.uid == userId" firestore.rules`               | Present |
| `grep "allow update: if false" firestore.rules`                   | Present |
| Broad `allow read, write: if request.auth != null` on `/USERS/{userId}` still present | Yes (deferred debt) |
| firestore.rules brace balance                                     | 0 (balanced) |
| `firebase deploy` executed by Claude                              | **NO** (user gate) |
| STATE.md / ROADMAP.md modified                                    | **NO** (parallel-executor policy) |

## Known Stubs

None. This plan is server-side only; no UI stubs.

## Threat Flags

None. All new surface is enumerated in the plan's existing `<threat_model>` (T-09-05 through T-09-08 + T-09-SC).

## Self-Check: PASSED

- `functions/src/index.ts` — modified, contains `export const onFollowedSellerWrite` and `export function buildFollowedSellerDelta`.
- `functions/test/onFollowedSellerWrite.test.ts` — created.
- `firestore.rules` — modified, contains `match /followed_sellers/{sellerId}`.
- `functions/test/rules.test.ts` — modified, contains `firestore.rules /USERS/{uid}/followed_sellers (Phase 9, FAVS-04)` describe block.
- Commits `286fba9`, `7fab284`, `22c9c9f` present in `git log`.
