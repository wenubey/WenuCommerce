# Roadmap: WenuCommerce

## Overview

WenuCommerce transforms from a browsing app into a full transactional e-commerce platform. The milestone begins with the Room offline-first foundation that every subsequent feature depends on, then delivers the commercial transaction critical path (cart → checkout → orders), followed by social and engagement features (reviews, wishlist, storefronts, notifications), and closes with personalization and comprehensive test coverage.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Room Foundation** - Establish Room as single source of truth; migrate existing repositories off raw Firestore flows
- [ ] **Phase 2: Offline Write Queue** - WorkManager-backed write queue; connectivity awareness; pending-sync UI
- [ ] **Phase 3: Cart & Wishlist** - Room-persisted cart with badge and stock awareness; offline-capable wishlist with add-to-cart
- [ ] **Phase 4: Checkout & Payments** - Stripe PaymentSheet via Cloud Function; order creation on success; order confirmation screen
- [ ] **Phase 5: Discounts** - Seller coupon management; server-validated coupon entry at checkout; discounted pricing in summary
- [ ] **Phase 6: Order Tracking & Management** - Customer order history and status timeline; seller order advancement; FCM on status change
- [ ] **Phase 7: Reviews & Ratings** - Verified-purchase reviews; aggregate ratings on product cards; seller review visibility
- [ ] **Phase 8: Notifications** - FCM push for all event types; typed notification channels; in-app notification history; permission flow
- [ ] **Phase 9: Seller Storefronts & Favorite Sellers** - Public seller profile pages; follow/unfollow from storefront; followed sellers list
- [ ] **Phase 10: Personalization & Profile** - Theme, orientation, notification toggles in DataStore; profile and address management
- [ ] **Phase 11: Testing** - Repository fakes, ViewModel Turbine tests, Compose UI tests for key flows; test infrastructure

## Phase Details

### Phase 1: Room Foundation
**Goal**: The app reads all data from Room, not directly from Firestore — offline browsing works with zero network on first launch
**Depends on**: Nothing (first phase — addresses load-bearing architectural prerequisite)
**Requirements**: SYNC-01, SYNC-02, SYNC-05, SYNC-06
**Success Criteria** (what must be TRUE):
  1. User opens the app with no network and sees the last-synced product and category data without errors
  2. Product detail screen loads from Room; no Firestore `callbackFlow` is observed by any ViewModel
  3. App displays a connectivity status indicator when the device is offline
  4. Room schema version 1 is committed with schema JSON files tracked in git; no `fallbackToDestructiveMigration()` in release build config
**Plans:** 2/4 plans executed

Plans:
- [ ] 01-01-PLAN.md — Room database setup: entities, DAOs, TypeConverters, mappers, Koin databaseModule, schema v1
- [ ] 01-02-PLAN.md — Migrate Product and Category repositories to Room-first with SyncManager
- [ ] 01-03-PLAN.md — ConnectivityObserver, global offline banner UI, AuthRepositoryImpl Room caching
- [ ] 01-04-PLAN.md — Empty network state, shimmer/skeleton placeholders, pull-to-refresh, sync failure snackbar

### Phase 2: Offline Write Queue
**Goal**: Offline writes made by customers and sellers are queued locally and auto-sync to Firestore when connectivity is restored — no data is silently lost when the device is offline
**Depends on**: Phase 1
**Requirements**: SYNC-03, SYNC-04
**Success Criteria** (what must be TRUE):
  1. User adds a product to cart while offline; the cart item persists after app restart and syncs to Firestore once the device comes back online
  2. User sees a "N items pending sync" indicator in the UI while offline writes are queued
  3. The pending-sync indicator disappears after successful sync to Firestore
  4. App does not crash or lose data when toggling between offline and online while operations are pending
**Plans**: TBD

Plans:
- [ ] 02-01: PendingOperationEntity design, WriteQueueWorker (WorkManager), conflict resolution policy implementation per entity type
- [ ] 02-02: SyncRepository with pending-count flow; KoinWorkerFactory wiring; pending-sync UI component

### Phase 3: Cart & Wishlist
**Goal**: Customers can build a cart and wishlist that persist across app restarts and work offline, giving them full control over what they intend to buy
**Depends on**: Phase 1, Phase 2
**Requirements**: CART-01, CART-02, CART-03, CART-04, CART-05, CART-06, CART-07, CART-08, WISH-01, WISH-02, WISH-03, WISH-04, WISH-05
**Success Criteria** (what must be TRUE):
  1. Customer adds a product to cart from product detail; cart badge on the nav tab increments immediately; cart item survives app restart
  2. Customer increments, decrements, and removes items from the cart screen; subtotal and per-line totals update in real time
  3. Cart shows an inline warning on items that are out of stock or deleted; empty cart shows an illustration with a CTA to browse
  4. Customer taps the heart icon on a product card or detail screen to toggle wishlist membership; wishlist screen shows saved products
  5. Customer adds a product to cart directly from the wishlist screen; wishlist shows an inline warning for unavailable products
**Plans**: TBD

Plans:
- [ ] 03-01: CartItemEntity + CartDAO + CartRepository domain interface; CartRepositoryImpl (Room + Firestore sync)
- [ ] 03-02: Cart UI — CartScreen, quantity controls, subtotal, badge count, empty state, stock-aware warnings
- [ ] 03-03: WishlistEntity + WishlistDAO + WishlistRepository domain interface; WishlistRepositoryImpl
- [ ] 03-04: Wishlist UI — heart toggle on product cards/detail, WishlistScreen, add-to-cart from wishlist, unavailable product warning

### Phase 4: Checkout & Payments
**Goal**: Customers can complete a purchase end-to-end — from cart to payment to order confirmation — with payment logic fully server-side
**Depends on**: Phase 3
**Requirements**: CHKT-01, CHKT-02, CHKT-03, CHKT-04, CHKT-05, CHKT-06, CHKT-07
**Success Criteria** (what must be TRUE):
  1. Customer proceeds from cart to checkout summary screen showing items, totals, and shipping address before payment
  2. Customer selects or enters a shipping address at checkout
  3. Stripe PaymentSheet appears; customer enters card details and payment completes without any card data passing through the Android app
  4. On payment success, cart clears, an Order document exists in Firestore and Room, and the order confirmation screen shows the order ID and items
  5. On payment failure, the error is displayed, the cart is preserved, and the customer can retry payment
**Plans**: TBD

Plans:
- [ ] 04-01: Cloud Function — createPaymentIntent (server-side PaymentIntent creation, receives cart items and address, returns clientSecret)
- [ ] 04-02: PaymentRepository domain interface and impl; Stripe Android SDK integration in app module; CheckoutViewModel with SavedStateHandle
- [ ] 04-03: Order + OrderItem domain models and Room entities; OrderRepositoryImpl (creation path)
- [ ] 04-04: CheckoutScreen (summary, address, coupon field placeholder); Stripe PaymentSheet launch flow
- [ ] 04-05: OrderConfirmationScreen; cart-clear logic on PaymentSheetResult.Completed; error handling for PaymentSheetResult.Failed

### Phase 5: Discounts
**Goal**: Sellers can create and manage discount codes that customers can apply at checkout, with validation enforced server-side so no coupon can be bypassed
**Depends on**: Phase 4
**Requirements**: DISC-01, DISC-02, DISC-03, DISC-04, DISC-05, DISC-06, DISC-07
**Success Criteria** (what must be TRUE):
  1. Seller creates a percentage or fixed-amount discount code with an optional expiry date and usage limit from the seller dashboard
  2. Seller views, edits, and deletes their existing discount codes from the same dashboard
  3. Customer enters a coupon code at checkout; invalid, expired, or limit-exceeded codes show an error; valid codes show a success confirmation
  4. Checkout summary shows both the original price and the discounted price after a valid coupon is applied
  5. Discount is applied to the Stripe PaymentIntent amount by the Cloud Function, not the client app
**Plans**: TBD

Plans:
- [ ] 05-01: DiscountCode Firestore model and domain type; Cloud Function — validateCoupon (checks existence, expiry, usage count, atomically decrements)
- [ ] 05-02: Seller discount management UI — create/edit/delete discount codes screen with expiry and usage-limit fields
- [ ] 05-03: Checkout coupon entry UI — text field, validation feedback, original vs discounted price display; update createPaymentIntent to accept coupon

### Phase 6: Order Tracking & Management
**Goal**: Customers can track every order from placement to delivery, and sellers can advance order status and provide tracking information — all order changes trigger push notifications
**Depends on**: Phase 4
**Requirements**: ORDR-01, ORDR-02, ORDR-03, ORDR-04, ORDR-05, ORDR-06, ORDR-07, ORDR-08, ORDR-09, ORDR-10
**Success Criteria** (what must be TRUE):
  1. Customer sees a reverse-chronological list of their orders showing order ID, date, total, item count, and status badge
  2. Customer opens order detail and sees the full item list, shipping address, payment summary, and a status timeline with timestamps for each transition
  3. Seller sees only their own orders and can advance order status forward (PENDING → CONFIRMED → SHIPPED → DELIVERED); seller cannot skip or reverse statuses
  4. Seller is prompted for an optional tracking number when marking an order as SHIPPED; seller can cancel orders that are pre-SHIPPED
  5. Customer receives a push notification when their order status changes, and tapping it deep-links to the order detail screen
**Plans**: TBD

Plans:
- [ ] 06-01: OrderRepository — observeOrders (customer-scoped), observeSellerOrders (seller-scoped), updateStatus; Firestore security rules for orders
- [ ] 06-02: CustomerOrderHistoryScreen and CustomerOrderDetailScreen with status timeline component
- [ ] 06-03: SellerOrdersScreen with forward-only status controls, cancellation, and tracking number prompt for SHIPPED
- [ ] 06-04: Cloud Function — onOrderStatusChange FCM trigger (sends notification to customer on status advancement)

### Phase 7: Reviews & Ratings
**Goal**: Customers who received a product can rate and review it; product listings display aggregate ratings so shoppers can evaluate products before buying
**Depends on**: Phase 6
**Requirements**: REVW-01, REVW-02, REVW-03, REVW-04, REVW-05, REVW-06, REVW-07
**Success Criteria** (what must be TRUE):
  1. Customer with a DELIVERED order for a product sees a "Write a Review" prompt on the product detail screen; customers without a DELIVERED order cannot access it
  2. Customer submits a 1-5 star rating with optional text; submitting again replaces the existing review rather than creating a duplicate
  3. Product detail shows the aggregate star rating (average and count), individual reviews sorted by most recent, each with a "Verified Purchase" badge
  4. Customer can switch review sort between most recent and highest rated
  5. Product cards in browse and search results display the review count
**Plans**: TBD

Plans:
- [ ] 07-01: ReviewEntity + ReviewDAO + ReviewRepository domain interface; ReviewRepositoryImpl; Cloud Function — submitReview (purchase verification via Firestore, one-per-product-per-customer enforcement)
- [ ] 07-02: Review UI on product detail — aggregate rating display, individual review list with sort, Verified Purchase badge, write/edit review form
- [ ] 07-03: Review count surface on product cards in browse and search; seller review visibility in seller product management

### Phase 8: Notifications
**Goal**: Customers and sellers receive timely push notifications for all relevant events and can view notification history in-app; Android 13+ permission is handled gracefully
**Depends on**: Phase 6
**Requirements**: NOTF-01, NOTF-02, NOTF-03, NOTF-04, NOTF-05, NOTF-06, NOTF-07, NOTF-08
**Success Criteria** (what must be TRUE):
  1. Customer receives a push notification when their order status changes; seller receives a notification when a new order is placed and when a new review is posted on their product
  2. Tapping any notification deep-links to the relevant screen (order detail for order updates, product detail for review notifications)
  3. On Android 13+ devices, the app requests POST_NOTIFICATIONS permission with a rationale dialog; the app gracefully handles denial
  4. Three distinct notification channels exist (Order Updates, Account, Promotions) visible in Android system notification settings
  5. In-app notification history screen shows all received notifications with timestamps; FCM token is refreshed and updated in Firestore via WorkManager (not fire-and-forget)
**Plans**: TBD

Plans:
- [ ] 08-01: NotificationEntity + NotificationDAO; extended MessagingService with type routing, Room persistence, notification channel dispatch
- [ ] 08-02: FCM token lifecycle via WorkManager; Android 13+ POST_NOTIFICATIONS permission flow with rationale
- [ ] 08-03: In-app notification history screen (Room-backed); notification deep-link handling for order and product destinations
- [ ] 08-04: Cloud Function — onNewOrder FCM trigger to seller; onNewReview FCM trigger to seller

### Phase 9: Seller Storefronts & Favorite Sellers
**Goal**: Customers can discover seller profiles, follow sellers they like, and browse a list of their followed sellers — sellers see their follower count
**Depends on**: Phase 1
**Requirements**: FAVS-01, FAVS-02, FAVS-03, FAVS-04
**Success Criteria** (what must be TRUE):
  1. Customer navigates to a seller storefront from a product card or search result and sees the seller's business name, bio, photo, aggregate rating, follower count, and active product listings
  2. Customer taps Follow/Unfollow on the seller storefront and the follower count updates immediately
  3. Customer views a dedicated "Followed Sellers" list showing all sellers they follow
  4. Seller sees their own follower count on their seller dashboard (not individual follower identities)
**Plans**: TBD

Plans:
- [ ] 09-01: SellerStorefrontScreen — business name, bio, photo, rating, follower count, product grid; follow/unfollow action with optimistic UI
- [ ] 09-02: FollowedSellersRepository domain interface and impl (Room + Firestore sync); FollowedSellersScreen for customer
- [ ] 09-03: Seller follower count surface in seller dashboard

### Phase 10: Personalization & Profile
**Goal**: Users can control their app experience (theme, orientation, notifications) and manage their profile information without needing to contact support
**Depends on**: Phase 1
**Requirements**: PRSN-01, PRSN-02, PRSN-03, PRSN-04, PROF-01, PROF-02, PROF-03, PROF-04, PROF-05, PROF-06
**Success Criteria** (what must be TRUE):
  1. User toggles between dark, light, and system theme; the theme applies immediately with no flash on cold start
  2. User enables portrait lock; the app restricts rotation; user disables it and rotation resumes
  3. User disables specific notification types (order updates, reviews, promotions); subsequent FCM messages of that type are not shown as system notifications
  4. Customer updates display name, profile photo (with upload progress), and shipping address(es); changes persist across restarts
  5. Seller updates business name, bio, and contact info from their profile
  6. User changes their password after re-authenticating; user deletes their account after re-authenticating and all their data is removed
**Plans**: TBD

Plans:
- [ ] 10-01: Theme preference — DataStore ThemeRepository; immediate theme application, no cold-start flash; system/dark/light toggle UI
- [ ] 10-02: Orientation lock preference and notification per-type toggle preferences in DataStore; wire toggles to MessagingService filtering
- [ ] 10-03: Customer profile management — display name, profile photo upload with progress indicator, shipping address CRUD
- [ ] 10-04: Seller business profile — name, bio, contact info; password change with re-auth; account deletion with re-auth and data cleanup

### Phase 11: Testing
**Goal**: Every critical user flow has automated test coverage; the test infrastructure enables future features to be verified without manual regression testing
**Depends on**: Phases 1-10 (covers all features; run parallel to implementation, full pass at end)
**Requirements**: TEST-01, TEST-02, TEST-03, TEST-04, TEST-05
**Success Criteria** (what must be TRUE):
  1. Repository layer tests use fake implementations (no Firestore or network in unit tests); all repository contracts have at least one test
  2. ViewModel tests use Turbine to assert Flow emissions for state and effects; no ViewModel test uses `Thread.sleep()` or manual delays
  3. Compose UI tests cover the auth, browse, cart, and checkout flows end-to-end; tests run on CI without device-dependent flakiness
  4. A shared test infrastructure module provides MainDispatcherRule, test fixtures, fake repositories, and Koin test modules usable across all tests
  5. MockK is at 1.13.x or higher; all mocks work correctly with Kotlin 2.x coroutines
**Plans**: TBD

Plans:
- [ ] 11-01: Test infrastructure — MockK upgrade, Turbine dependency, Koin test modules, MainDispatcherRule, shared test fixtures
- [ ] 11-02: Repository layer unit tests (fake implementations for Product, Category, Cart, Wishlist, Order, Review, Notification repositories)
- [ ] 11-03: ViewModel unit tests (CustomerHomeViewModel, CartViewModel, CheckoutViewModel, OrderViewModel using Turbine)
- [ ] 11-04: Compose UI tests for key flows (auth, product browse, add to cart, checkout with Stripe stub)

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Room Foundation | 2/4 | In Progress|  |
| 2. Offline Write Queue | 0/2 | Not started | - |
| 3. Cart & Wishlist | 0/4 | Not started | - |
| 4. Checkout & Payments | 0/5 | Not started | - |
| 5. Discounts | 0/3 | Not started | - |
| 6. Order Tracking & Management | 0/4 | Not started | - |
| 7. Reviews & Ratings | 0/3 | Not started | - |
| 8. Notifications | 0/4 | Not started | - |
| 9. Seller Storefronts & Favorite Sellers | 0/3 | Not started | - |
| 10. Personalization & Profile | 0/4 | Not started | - |
| 11. Testing | 0/4 | Not started | - |
