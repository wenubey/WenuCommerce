# Requirements: WenuCommerce

**Defined:** 2026-02-19
**Core Value:** Customers can browse, search, and purchase products with a seamless offline-capable experience

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Offline-First Infrastructure

- [x] **SYNC-01**: Room database serves as single source of truth for all data reads
- [ ] **SYNC-02**: Firestore snapshot listeners write-through to Room in background
- [ ] **SYNC-03**: Offline writes queue in PendingOperationEntity, auto-sync via WorkManager when online
- [ ] **SYNC-04**: User sees "pending sync" indicator when offline mutations are queued
- [ ] **SYNC-05**: Connectivity state observed via ConnectivityManager and surfaced in UI
- [ ] **SYNC-06**: Existing repositories (Product, Category, Auth) migrated to Room-first pattern

### Cart

- [ ] **CART-01**: Customer can add product to cart from product detail with quantity selector
- [ ] **CART-02**: Cart persists across app restarts via Room
- [ ] **CART-03**: Cart syncs to Firestore when online
- [ ] **CART-04**: Customer can update quantity (increment/decrement) or remove items
- [ ] **CART-05**: Cart shows subtotal, item count, and per-line totals
- [ ] **CART-06**: Out-of-stock or deleted products show warning inline
- [ ] **CART-07**: Cart badge/count visible on navigation tab
- [ ] **CART-08**: Empty cart shows illustration with CTA to browse products

### Checkout

- [ ] **CHKT-01**: Firebase Cloud Function creates Stripe PaymentIntent server-side
- [ ] **CHKT-02**: Customer sees checkout summary (items, totals, address) before payment
- [ ] **CHKT-03**: Stripe PaymentSheet presented for card entry (no custom form)
- [ ] **CHKT-04**: On payment success: Order document created in Firestore + Room, cart cleared
- [ ] **CHKT-05**: On payment failure: error shown, cart preserved, retry allowed
- [ ] **CHKT-06**: Order confirmation screen shows order ID, items, and link to order tracking
- [ ] **CHKT-07**: Customer can enter or select shipping address at checkout

### Discounts

- [ ] **DISC-01**: Seller can create percentage or fixed-amount discount codes
- [ ] **DISC-02**: Discount codes have optional expiry date and usage limit
- [ ] **DISC-03**: Customer can enter coupon code at checkout
- [ ] **DISC-04**: Cloud Function validates coupon (exists, not expired, usage limit not exceeded)
- [ ] **DISC-05**: Validated discount applied to PaymentIntent amount
- [ ] **DISC-06**: Original and discounted prices shown in checkout summary
- [ ] **DISC-07**: Seller can view, edit, and delete their discount codes

### Order Tracking

- [ ] **ORDR-01**: Customer can view order history (reverse-chronological list)
- [ ] **ORDR-02**: Each order shows: order ID, date, total, item count, status badge
- [ ] **ORDR-03**: Order detail shows full item list, shipping address, payment summary
- [ ] **ORDR-04**: Status timeline displays each status transition with timestamp
- [ ] **ORDR-05**: Status flow: PENDING → CONFIRMED → SHIPPED → DELIVERED (CANCELLED as terminal)
- [ ] **ORDR-06**: Seller can advance order status forward only
- [ ] **ORDR-07**: Seller can cancel orders that are pre-SHIPPED
- [ ] **ORDR-08**: Seller prompted for optional tracking number when marking SHIPPED
- [ ] **ORDR-09**: Seller sees only their own orders (security rules enforced)
- [ ] **ORDR-10**: Status update triggers FCM notification to customer

### Reviews & Ratings

- [ ] **REVW-01**: Customer can rate purchased products 1-5 stars (required) with optional text review
- [ ] **REVW-02**: Only customers with DELIVERED order for the product can review (verified purchase)
- [ ] **REVW-03**: One review per customer per product (can edit, not duplicate)
- [ ] **REVW-04**: Product detail shows aggregate rating (average + count) and individual reviews
- [ ] **REVW-05**: Reviews display "Verified Purchase" badge
- [ ] **REVW-06**: Reviews sorted by most recent (default), with option for highest rated
- [ ] **REVW-07**: Review count shown on product cards in browse/search

### Wishlist

- [ ] **WISH-01**: Customer can add/remove products from wishlist via heart icon on product cards and detail
- [ ] **WISH-02**: Wishlist persists offline via Room, syncs to Firestore
- [ ] **WISH-03**: Dedicated wishlist screen showing saved products
- [ ] **WISH-04**: Customer can add to cart directly from wishlist
- [ ] **WISH-05**: Deleted or unavailable products show inline warning in wishlist

### Favorite Sellers

- [ ] **FAVS-01**: Customer can follow/unfollow sellers from seller storefront
- [ ] **FAVS-02**: Customer can view list of followed sellers
- [ ] **FAVS-03**: Seller storefront shows: business name, bio, photo, rating, follower count, product listings
- [ ] **FAVS-04**: Seller can see their follower count (not individual followers)

### Notifications

- [ ] **NOTF-01**: FCM push notification sent to customer on order status change
- [ ] **NOTF-02**: FCM push notification sent to seller on new order placed
- [ ] **NOTF-03**: FCM push notification sent to seller on new product review
- [ ] **NOTF-04**: Tapping notification deep-links to relevant screen (order detail, product)
- [ ] **NOTF-05**: Android 13+ POST_NOTIFICATIONS runtime permission requested with rationale
- [ ] **NOTF-06**: Separate notification channels: Order Updates, Account, Promotions
- [ ] **NOTF-07**: FCM token refresh handled (update Firestore user document)
- [ ] **NOTF-08**: In-app notification history screen

### Personalization

- [ ] **PRSN-01**: Dark/light/system theme toggle persisted in DataStore
- [ ] **PRSN-02**: Theme applied immediately without app restart, no flash on cold start
- [ ] **PRSN-03**: Screen orientation lock toggle (portrait lock vs allow rotation)
- [ ] **PRSN-04**: Notification settings: per-type toggles (order updates, reviews, promotions)

### Profile Management

- [ ] **PROF-01**: Customer can update display name
- [ ] **PROF-02**: Customer can update profile photo (Firebase Storage upload with progress)
- [ ] **PROF-03**: Customer can change password (requires re-authentication)
- [ ] **PROF-04**: Customer can add/edit shipping address(es)
- [ ] **PROF-05**: Seller can update business name, bio, and contact info
- [ ] **PROF-06**: User can delete account (requires re-authentication, deletes data)

### Testing

- [ ] **TEST-01**: Repository layer unit tests with fake implementations
- [ ] **TEST-02**: ViewModel unit tests using Turbine for Flow assertions
- [ ] **TEST-03**: Compose UI tests for key flows (auth, browse, cart, checkout)
- [ ] **TEST-04**: Test infrastructure: MainDispatcherRule, test fixtures, Koin test modules
- [ ] **TEST-05**: MockK upgraded to 1.13.14+ for Kotlin 2.x compatibility

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Notifications (Advanced)

- **NOTF-V2-01**: Notification for followed seller adding new product
- **NOTF-V2-02**: Promotional campaign notifications (admin-triggered)

### Seller Features (Advanced)

- **SELL-V2-01**: Flash sale (time-limited automatic discounts, no code required)
- **SELL-V2-02**: Seller analytics dashboard (sales, views, conversion)

### Social

- **SOCL-V2-01**: Product sharing via deep link
- **SOCL-V2-02**: Buyer-seller messaging

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Hilt migration | Koin works, avoid migration cost |
| Feature modules | 3-module structure sufficient, avoid complexity |
| Convention plugins | Not needed for current module count |
| In-app chat | High complexity, not core to purchase flow |
| Multi-currency | Single locale target |
| ML recommendations | No training data at MVP scale |
| Stripe Connect (marketplace payouts) | Separate product, legal overhead |
| In-app refunds | Admin-only via Stripe dashboard |
| Change email | Firebase re-auth flow fragile |
| Auction / bidding | Different pricing model |
| AR product preview | High cost, product-type dependent |
| Shipping carrier API integration | Manual seller updates sufficient |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| SYNC-01 | Phase 1 | Complete |
| SYNC-02 | Phase 1 | Pending |
| SYNC-05 | Phase 1 | Pending |
| SYNC-06 | Phase 1 | Pending |
| SYNC-03 | Phase 2 | Pending |
| SYNC-04 | Phase 2 | Pending |
| CART-01 | Phase 3 | Pending |
| CART-02 | Phase 3 | Pending |
| CART-03 | Phase 3 | Pending |
| CART-04 | Phase 3 | Pending |
| CART-05 | Phase 3 | Pending |
| CART-06 | Phase 3 | Pending |
| CART-07 | Phase 3 | Pending |
| CART-08 | Phase 3 | Pending |
| WISH-01 | Phase 3 | Pending |
| WISH-02 | Phase 3 | Pending |
| WISH-03 | Phase 3 | Pending |
| WISH-04 | Phase 3 | Pending |
| WISH-05 | Phase 3 | Pending |
| CHKT-01 | Phase 4 | Pending |
| CHKT-02 | Phase 4 | Pending |
| CHKT-03 | Phase 4 | Pending |
| CHKT-04 | Phase 4 | Pending |
| CHKT-05 | Phase 4 | Pending |
| CHKT-06 | Phase 4 | Pending |
| CHKT-07 | Phase 4 | Pending |
| DISC-01 | Phase 5 | Pending |
| DISC-02 | Phase 5 | Pending |
| DISC-03 | Phase 5 | Pending |
| DISC-04 | Phase 5 | Pending |
| DISC-05 | Phase 5 | Pending |
| DISC-06 | Phase 5 | Pending |
| DISC-07 | Phase 5 | Pending |
| ORDR-01 | Phase 6 | Pending |
| ORDR-02 | Phase 6 | Pending |
| ORDR-03 | Phase 6 | Pending |
| ORDR-04 | Phase 6 | Pending |
| ORDR-05 | Phase 6 | Pending |
| ORDR-06 | Phase 6 | Pending |
| ORDR-07 | Phase 6 | Pending |
| ORDR-08 | Phase 6 | Pending |
| ORDR-09 | Phase 6 | Pending |
| ORDR-10 | Phase 6 | Pending |
| REVW-01 | Phase 7 | Pending |
| REVW-02 | Phase 7 | Pending |
| REVW-03 | Phase 7 | Pending |
| REVW-04 | Phase 7 | Pending |
| REVW-05 | Phase 7 | Pending |
| REVW-06 | Phase 7 | Pending |
| REVW-07 | Phase 7 | Pending |
| NOTF-01 | Phase 8 | Pending |
| NOTF-02 | Phase 8 | Pending |
| NOTF-03 | Phase 8 | Pending |
| NOTF-04 | Phase 8 | Pending |
| NOTF-05 | Phase 8 | Pending |
| NOTF-06 | Phase 8 | Pending |
| NOTF-07 | Phase 8 | Pending |
| NOTF-08 | Phase 8 | Pending |
| FAVS-01 | Phase 9 | Pending |
| FAVS-02 | Phase 9 | Pending |
| FAVS-03 | Phase 9 | Pending |
| FAVS-04 | Phase 9 | Pending |
| PRSN-01 | Phase 10 | Pending |
| PRSN-02 | Phase 10 | Pending |
| PRSN-03 | Phase 10 | Pending |
| PRSN-04 | Phase 10 | Pending |
| PROF-01 | Phase 10 | Pending |
| PROF-02 | Phase 10 | Pending |
| PROF-03 | Phase 10 | Pending |
| PROF-04 | Phase 10 | Pending |
| PROF-05 | Phase 10 | Pending |
| PROF-06 | Phase 10 | Pending |
| TEST-01 | Phase 11 | Pending |
| TEST-02 | Phase 11 | Pending |
| TEST-03 | Phase 11 | Pending |
| TEST-04 | Phase 11 | Pending |
| TEST-05 | Phase 11 | Pending |

**Coverage:**
- v1 requirements: 77 total
- Mapped to phases: 77
- Unmapped: 0

---
*Requirements defined: 2026-02-19*
*Last updated: 2026-02-19 — traceability populated after roadmap creation*
