# WenuCommerce

## What This Is

A multi-role e-commerce Android app (Customer, Seller, Admin) built with Jetpack Compose, Clean Architecture, and Firebase backend. Customers browse and buy products, sellers manage inventory and fulfill orders, admins moderate content. The app follows Google's recommended practices (inspired by Now in Android) with an offline-first architecture using Room as the single source of truth and Firebase for cloud sync.

## Core Value

Customers can browse, search, and purchase products with a seamless offline-capable experience — the app works reliably regardless of network conditions.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. Inferred from existing codebase. -->

- ✓ User can sign up with email/password — existing
- ✓ User can sign in with Google — existing
- ✓ User receives email verification after signup — existing
- ✓ Role-based navigation (Customer, Seller, Admin) — existing
- ✓ Customer can browse products by category and subcategory — existing
- ✓ Customer can search products with debounced query — existing
- ✓ Seller can create products with images — existing
- ✓ Seller can edit and manage product inventory — existing
- ✓ Seller can submit products for review — existing
- ✓ Admin can approve or suspend products — existing
- ✓ Admin can moderate sellers (approval flow) — existing
- ✓ Category and subcategory browsing — existing
- ✓ Splash screen with auth-gated routing — existing

### Active

<!-- Current scope. Refactor existing + build new features. -->

**Offline-First Architecture:**
- [ ] Room database as single source of truth for all data
- [ ] Background sync with Firebase (Firestore ↔ Room)
- [ ] Queue & sync for offline writes (save locally, auto-sync when online)
- [ ] Pending sync status visible to users
- [ ] Conflict resolution strategy for concurrent edits

**Cart & Checkout:**
- [ ] Customer can add products to cart
- [ ] Cart persists locally (Room)
- [ ] Customer can proceed to checkout with Stripe PaymentSheet
- [ ] Stripe payment processing via Firebase Cloud Functions
- [ ] Order created on successful payment

**Wishlist:**
- [ ] Customer can add/remove products from wishlist
- [ ] Wishlist persists offline

**Order Tracking:**
- [ ] Customer can view order history
- [ ] Order statuses: placed, confirmed, shipped, delivered
- [ ] Seller can manually update order status
- [ ] Customer sees status updates in order detail

**Reviews & Ratings:**
- [ ] Customer can rate purchased products (1-5 stars)
- [ ] Customer can write text reviews
- [ ] Product detail shows average rating and reviews
- [ ] Seller can view reviews on their products

**Favorite Seller:**
- [ ] Customer can favorite/unfavorite sellers
- [ ] Customer can browse favorite sellers list

**Discounts:**
- [ ] Seller can create discount codes or percentage discounts on products
- [ ] Discounts applied at checkout
- [ ] Customer sees original vs discounted price

**Notifications (FCM):**
- [ ] Push notifications for discount announcements
- [ ] Push notifications for order status changes
- [ ] General promotional notifications
- [ ] Notification history viewable in-app

**Personalization & Settings:**
- [ ] Dark mode / light mode toggle
- [ ] Screen orientation lock setting
- [ ] Notification preferences (enable/disable by type)
- [ ] Profile updates (name, email, phone, address)
- [ ] Password change

**Testing:**
- [ ] Repository layer unit tests
- [ ] ViewModel unit tests
- [ ] Compose UI tests for key user flows
- [ ] Test infrastructure (fakes, test fixtures, test rules)

### Out of Scope

<!-- Explicit boundaries. -->

- Feature modules (NiA-style modularization) — keep current 3-module structure, avoid migration complexity
- Hilt migration — keep Koin, already works well
- Convention plugins — not needed for current module count
- Shipping carrier integration — sellers update status manually
- Real-time chat between buyer/seller — high complexity, not core
- OAuth providers beyond Google — email + Google sufficient
- Biometric/PIN app lock — not requested
- Seller analytics dashboard — defer to future milestone

## Context

- Existing codebase with Clean Architecture: `domain` (contracts/models), `data` (Firebase repos), `app` (Compose UI)
- Firebase backend: Auth, Firestore, Storage, Cloud Functions, FCM
- Koin 4.0.1 for DI, Jetpack Compose with Material 3, Navigation Compose 2.8.5
- Room 2.6.1 already in dependencies but not yet used as primary data source
- Ktor client available for HTTP calls
- Three user roles with distinct navigation flows
- Product lifecycle: DRAFT → PENDING_REVIEW → ACTIVE / SUSPENDED
- Existing `safeApiCall()` wrapper pattern for error handling
- Codebase map available at `.planning/codebase/`

## Constraints

- **Tech stack**: Android (Kotlin, Jetpack Compose), Firebase backend, Koin DI — no framework changes
- **Min SDK**: API 24 (Android 7.0)
- **Payment provider**: Stripe only, via Firebase Cloud Functions backend
- **Offline sync**: Room as source of truth, Firebase as cloud layer
- **Testing**: Must cover repository, ViewModel, and UI layers

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Keep Koin over Hilt | Already integrated, avoid migration cost | — Pending |
| Room as source of truth | Offline-first requires local DB as primary data layer | — Pending |
| Queue & sync for writes | Balance between offline capability and complexity | — Pending |
| Stripe via Cloud Functions | Keep payment secrets server-side, app only handles UI | — Pending |
| Manual order status updates | Simpler than carrier integration, sufficient for v1 | — Pending |
| 3-module architecture | Keep domain/data/app structure, defer feature modules | — Pending |

---
*Last updated: 2026-02-19 after initialization*
