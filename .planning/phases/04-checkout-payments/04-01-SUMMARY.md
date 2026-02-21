---
phase: 04-checkout-payments
plan: 01
subsystem: firebase-cloud-functions
tags: [stripe, cloud-functions, typescript, payment-intent, firestore]
dependency_graph:
  requires: []
  provides: [createPaymentIntent-cloud-function, functions-project]
  affects: [04-02, 04-03, 04-04]
tech_stack:
  added:
    - firebase-functions@^6.0.0
    - firebase-admin@^13.0.0
    - stripe@17.7.0 (npm, server-side)
  patterns:
    - defineSecret for STRIPE_SECRET_KEY (Google Secret Manager, not env vars)
    - onCall v2 callable function with auth gate
    - Server-side stock validation via Firestore reads
    - Shipping cost per-product-line (not per-unit)
    - PENDING Order document pre-created before PaymentIntent returns
key_files:
  created:
    - functions/src/index.ts
    - functions/package.json
    - functions/tsconfig.json
    - functions/.gitignore
    - firebase.json
  modified:
    - .gitignore (added functions/node_modules/ and functions/lib/)
decisions:
  - "Stripe apiVersion updated to 2025-02-24.acacia (plan specified 2025-01-27.acacia but installed stripe 17.7.0 requires the newer version)"
  - "userId extracted from request.auth.uid not from request.data.userId (security: always use server-verified auth token)"
  - "Shipping cost per-product-line: shippingCents += Math.round(shippingCost * 100) once per cart item regardless of quantity"
  - "Firestore reads for stock check and shipping cost are combined in a single loop (one Firestore read per product)"
metrics:
  duration: 4 min
  completed: 2026-02-21
  tasks_completed: 2
  files_created: 5
  files_modified: 1
---

# Phase 4 Plan 1: Firebase Cloud Functions Setup Summary

**One-liner:** TypeScript Firebase Cloud Function `createPaymentIntent` with server-side auth, stock validation, Stripe PaymentIntent creation, and PENDING Firestore Order pre-write.

## What Was Built

A complete Firebase Cloud Functions project in the `functions/` directory at project root. The single exported function `createPaymentIntent` is a v2 callable function that:

1. Validates the caller is authenticated (throws `unauthenticated`)
2. Validates cart items array is non-empty with valid fields (throws `invalid-argument`)
3. Reads each product doc from Firestore to validate stock — fails fast with all out-of-stock product names (throws `failed-precondition`)
4. Computes subtotal in cents with integer arithmetic (`Math.round(price * 100) * quantity`)
5. Computes shipping in cents per-product-line (once per cart line, not multiplied by quantity)
6. Creates a Stripe PaymentIntent via `stripe.paymentIntents.create()` with `automatic_payment_methods: { enabled: true }`
7. Pre-creates a PENDING Order document in Firestore `orders/{orderId}` with full item snapshot and address
8. Returns `{ clientSecret, amountCents, orderId }` to the Android client

The `STRIPE_SECRET_KEY` is managed via Firebase Secret Manager using `defineSecret` — it never appears in code or environment variables.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Initialize Firebase Functions project and configure dependencies | 7b4f9b0 | functions/package.json, functions/tsconfig.json, functions/.gitignore, firebase.json |
| 2 | Implement createPaymentIntent Cloud Function | 829808b | functions/src/index.ts |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Stripe API version updated from 2025-01-27.acacia to 2025-02-24.acacia**
- **Found during:** Task 2 compilation
- **Issue:** Plan specified `apiVersion: "2025-01-27.acacia"` but installed stripe 17.7.0 requires `2025-02-24.acacia`; TypeScript type check fails with `Type '"2025-01-27.acacia"' is not assignable to type '"2025-02-24.acacia"'`
- **Fix:** Updated apiVersion to `2025-02-24.acacia` to match the installed library's type definitions
- **Files modified:** functions/src/index.ts
- **Commit:** 829808b

**2. [Rule 1 - Bug] Removed unused `userId` destructuring from request.data**
- **Found during:** Task 2 compilation
- **Issue:** TypeScript strict mode (`noUnusedLocals: true`) rejected the unused `userId` variable; function correctly uses `request.auth.uid` for the authenticated user ID
- **Fix:** Removed `userId` from destructuring pattern while keeping the type annotation for documentation clarity
- **Files modified:** functions/src/index.ts
- **Commit:** 829808b

## Verification Results

- `npx tsc --noEmit`: PASS (no TypeScript errors)
- `createPaymentIntent` exported: PASS
- Auth check (`unauthenticated`): PASS
- Input validation (`invalid-argument`): PASS (2 locations)
- Stock validation (`failed-precondition`): PASS (2 locations — missing product + insufficient stock)
- `paymentIntents.create()`: PASS
- Firestore order creation: PASS
- `firebase.json` references functions source: PASS
- Stripe secret key only in Cloud Function via `defineSecret`: PASS (no client-side key)

## Next Steps

- User must set Stripe secret key: `firebase functions:secrets:set STRIPE_SECRET_KEY`
- Plans 04-02 through 04-04 consume the `createPaymentIntent` function via Firebase callable (`getHttpsCallable("createPaymentIntent")`)
- The function can be tested locally with Firebase Emulator Suite: `npm run serve` in `functions/`

## Self-Check: PASSED

| Item | Status |
|------|--------|
| functions/src/index.ts | FOUND |
| functions/package.json | FOUND |
| functions/tsconfig.json | FOUND |
| functions/.gitignore | FOUND |
| firebase.json | FOUND |
| Commit 7b4f9b0 (Task 1) | VERIFIED |
| Commit 829808b (Task 2) | VERIFIED |
