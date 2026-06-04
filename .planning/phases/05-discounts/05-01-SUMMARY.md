---
phase: 05-discounts
plan: 01
subsystem: discounts-data-foundation
tags: [discount, cloud-functions, repository, domain-model, koin]
dependency_graph:
  requires: [04-checkout-payments]
  provides: [discount-domain-models, discount-repository, validate-coupon-cf, decrement-coupon-cf, payment-intent-coupon-support]
  affects: [checkout-flow, order-model, room-schema]
tech_stack:
  added: []
  patterns: [firestore-only-repository, cloud-function-callable, atomic-field-value-increment]
key_files:
  created:
    - domain/src/main/java/com/wenubey/domain/model/discount/DiscountCode.kt
    - domain/src/main/java/com/wenubey/domain/model/discount/DiscountType.kt
    - domain/src/main/java/com/wenubey/domain/model/discount/CouponValidationResult.kt
    - domain/src/main/java/com/wenubey/domain/repository/DiscountRepository.kt
    - data/src/main/java/com/wenubey/data/repository/DiscountRepositoryImpl.kt
    - data/src/test/java/com/wenubey/data/DiscountRepositoryTest.kt
    - app/src/test/java/com/wenubey/wenucommerce/DiscountCreateEditViewModelTest.kt
    - app/src/test/java/com/wenubey/wenucommerce/CheckoutViewModelDiscountTest.kt
    - domain/src/test/java/com/wenubey/domain/DiscountCalculationTest.kt
  modified:
    - domain/src/main/java/com/wenubey/domain/model/order/Order.kt
    - domain/src/main/java/com/wenubey/domain/repository/PaymentRepository.kt
    - data/src/main/java/com/wenubey/data/repository/PaymentRepositoryImpl.kt
    - data/src/main/java/com/wenubey/data/local/entity/OrderEntity.kt
    - data/src/main/java/com/wenubey/data/local/mapper/OrderMapper.kt
    - data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt
    - functions/src/index.ts
decisions:
  - "Room migration v4->v5 added for order discount columns (Rule 2 auto-add)"
  - "Firestore-only for discount codes -- no Room entity needed (server-authoritative data)"
  - "FieldValue.increment(1) for atomic usage count (not read-modify-write)"
  - "Stripe minimum 50 cents guard on final total in createPaymentIntent"
  - "Inactive coupons return 'Code not found' (not 'Inactive') for security"
metrics:
  duration_minutes: 9
  completed_date: "2026-06-04"
  tasks_completed: 3
  tasks_total: 3
  files_created: 9
  files_modified: 8
---

# Phase 5 Plan 01: Discount Data Foundation Summary

Discount domain models, Firestore-only repository with Cloud Function callables, coupon validation and payment discount application Cloud Functions, and Wave 0 test stubs for the entire phase.

## What Was Built

### Task 0: Wave 0 Test Stubs
Created four placeholder test files across all three modules (data, app, domain) covering DISC-01 through DISC-07. All stubs compile and pass with empty bodies.

### Task 1: Domain Models, Repository, and Koin Wiring
- **DiscountCode** data class with all coupon fields (type, value, maxDiscountCap, minimumOrderAmount, targetProductIds, sellerId, expiresAt, usageLimit, usageCount, isActive)
- **DiscountType** enum: PERCENTAGE, FIXED_AMOUNT, FREE_SHIPPING
- **CouponValidationResult** data class returned by validateCoupon
- **DiscountRepository** interface with CRUD + validateCoupon + decrementCouponUsage
- **DiscountRepositoryImpl** using Firestore snapshot listeners for observe, Firestore direct writes for CRUD, and Cloud Function callables for validateCoupon/decrementCouponUsage
- **Order** model extended with discountAmount and discountCode fields
- **PaymentRepository.createPaymentIntent** gains optional couponCode parameter (backward-compatible default null)
- **PaymentIntentResult** gains discountAmountCents field
- Room migration v4->v5 for order discount columns
- Koin DI wiring for DiscountRepositoryImpl

### Task 2: Cloud Functions
- **validateCoupon**: Auth check, normalize code, O(1) doc lookup, validate (active, expiry, usage, eligible items, minimum order), compute discount preview, return code/type/discountCents/description
- **decrementCouponUsage**: Atomic FieldValue.increment(1) on usageCount -- race-condition-free
- **createPaymentIntent extended**: Accepts optional couponCode, re-validates server-side, computes discount with actual shipping, applies to final total (Math.max 50 cents), stores discount on order document, returns discountAmountCents

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Critical] Room migration v4->v5 for order discount columns**
- **Found during:** Task 1
- **Issue:** Adding discountAmount and discountCode to OrderEntity requires a database migration. Plan did not mention Room migration.
- **Fix:** Created MIGRATION_4_5 with ALTER TABLE statements, bumped database version from 4 to 5, registered migration in DataModule.kt
- **Files modified:** WenuCommerceDatabase.kt, DataModule.kt

**2. [Rule 1 - Bug] Firestore await() returns Void, not Unit**
- **Found during:** Task 1
- **Issue:** Firestore set/update/delete operations return Task<Void>, causing type mismatch with Result<Unit> return type
- **Fix:** Added explicit `Unit` statement after each `.await()` call in DiscountRepositoryImpl
- **Files modified:** DiscountRepositoryImpl.kt

## Known Stubs

None -- all files in this plan contain real implementation. Wave 0 test stubs are intentional placeholders to be filled by future plans.

## Self-Check: PASSED
