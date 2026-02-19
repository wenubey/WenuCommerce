# Features Research: WenuCommerce

**Research type:** Project Research — Features dimension
**Milestone context:** Subsequent milestone — adding cart/checkout, wishlist, order tracking, reviews, discounts, notifications, personalization
**Date:** 2026-02-19
**App:** Multi-role Android e-commerce (Customer, Seller, Admin) — Kotlin, Jetpack Compose, Firebase, Room, Stripe

---

## Summary

This document categorizes features for the WenuCommerce next milestone into table stakes (users leave without them), differentiators (competitive advantage), and anti-features (deliberately exclude). Each feature includes expected behavior, complexity rating, and dependencies.

Existing baseline: auth (email/password + Google), product browsing/search, seller product CRUD with admin moderation, categories. The next milestone adds the commercial layer that converts browsing into transactions.

---

## Table of Contents

1. [Table Stakes Features](#table-stakes-features)
2. [Differentiating Features](#differentiating-features)
3. [Anti-Features](#anti-features)
4. [Feature Dependency Map](#feature-dependency-map)
5. [Complexity Summary](#complexity-summary)

---

## Table Stakes Features

These are non-negotiable. Users comparing WenuCommerce to any other e-commerce app will abandon if these are missing or broken.

---

### 1. Cart

**Expected behavior:**
- Persistent cart across app restarts (Room-backed locally, synced to Firebase when online)
- Add product from product detail screen with quantity selector
- Cart badge/count visible on nav tab at all times
- Line items: product image thumbnail, name, seller name, unit price, quantity stepper (+/-), line total, remove button
- Cart summary: subtotal, estimated shipping (or "calculated at checkout"), total
- Out-of-stock items highlighted with "Remove" prompt — not silently kept
- Quantity capped at seller-specified stock level
- If a product is deleted by seller or admin after being added, show inline warning in cart ("This item is no longer available")
- Empty cart state: illustration + CTA to browse products
- Swipe-to-delete on cart items (Android convention)

**Offline behavior:**
- Cart reads from Room always (offline-first); mutations written to Room immediately, synced to Firestore when connectivity returns
- Conflict resolution: if stock changed while offline, alert user on next sync

**Complexity:** Medium
**Dependencies:** Product model, Room CartEntity, Firestore cart collection (or user subcollection), Stock/inventory field on Product

---

### 2. Checkout (Stripe PaymentSheet)

**Expected behavior:**
- Single-page or bottom-sheet checkout summary before payment
- Stripe PaymentSheet is the integration pattern — do not build a custom card form
- Flow: (1) app backend creates PaymentIntent via Stripe API, (2) app receives client_secret, (3) PaymentSheet.Configuration configured with merchant display name, (4) PaymentSheet.present() called, (5) PaymentSheet.FlowController result handled (Completed / Canceled / Failed)
- Payment methods shown by Stripe: card (required), Google Pay if device/merchant configured
- Address collection: either within PaymentSheet (Stripe handles) or a separate address screen before PaymentSheet
- On PaymentSheet.Completed: create Order document in Firestore, clear cart, navigate to Order Confirmation screen
- On PaymentSheet.Failed: show error from Stripe result, allow retry without re-entering payment info
- On PaymentSheet.Canceled: return to checkout summary, cart intact
- Order confirmation screen: order ID, items summary, estimated delivery note, CTA to view order tracking

**What NOT to build:**
- Do not store card numbers or PAN data — Stripe handles this entirely
- Do not build a custom payment form (PCI scope risk)
- Do not implement refunds in the app at MVP — that is an admin backend operation

**Backend requirement (non-negotiable):**
- A Cloud Function (or backend endpoint) must create the PaymentIntent server-side; the Stripe secret key must never be in the Android APK
- This is the single largest dependency blocker for checkout

**Complexity:** High (Stripe integration + Cloud Function backend + order creation atomicity)
**Dependencies:** Cart feature, Stripe Android SDK, Firebase Cloud Functions (or equivalent backend), Order model, address/shipping data on user profile or collected at checkout

---

### 3. Order Tracking (Customer View)

**Expected behavior:**
- Orders list screen: reverse-chronological, each item shows order ID (truncated), date, total, item count, current status badge
- Order detail screen: full item list with images, shipping address, payment summary, status timeline/stepper
- Status flow (seller-driven manual updates): `PENDING` → `CONFIRMED` → `SHIPPED` → `DELIVERED` (and `CANCELLED` as terminal state from any pre-SHIPPED stage)
- Status timeline shows each status with timestamp when it was set
- Current status is prominent (chip/badge with color coding: yellow=pending, blue=confirmed, orange=shipped, green=delivered, red=cancelled)
- Pull-to-refresh on orders list
- Deep link into order detail from FCM notification (when notification tapped)

**What users expect but often don't get:**
- Estimated delivery date (even a rough range) — sellers should be prompted to set this when marking SHIPPED
- Tracking number / carrier link when SHIPPED — optional field sellers can fill

**Complexity:** Medium
**Dependencies:** Order model in Firestore, Seller order management screen (seller must be able to update status), FCM notifications for status changes

---

### 4. Order Management (Seller View)

**Expected behavior:**
- Seller sees only their own orders (Firestore security rules enforce this)
- Orders list filtered by status (All / Pending / Confirmed / Shipped / Delivered / Cancelled)
- Order detail: customer info (name, shipping address), items, totals
- Status update button: seller can advance status forward only (cannot go backward), with confirmation dialog
- CONFIRM button visible on PENDING orders
- SHIP button visible on CONFIRMED orders (optionally prompt for tracking number)
- DELIVER button visible on SHIPPED orders
- CANCEL button visible on PENDING and CONFIRMED orders
- On status update: Firestore document updated, FCM notification triggered to customer

**Complexity:** Medium
**Dependencies:** Firestore security rules (seller scoped), Order model, FCM Cloud Function trigger on order status change

---

### 5. Reviews and Ratings

**Expected behavior:**
- Only customers who have a DELIVERED order containing that product can leave a review (enforce server-side via Cloud Function or Firestore rules)
- Star rating 1–5 (required), text review (optional, 500 char limit)
- One review per customer per product (update allowed, not duplicate)
- Reviews display on product detail screen: aggregate star rating (average + count), individual reviews with star, reviewer display name (first name + last initial), date, text
- Seller cannot delete customer reviews (admin can)
- Reviews sorted by: most recent (default), highest rated
- Review submission confirmation feedback (snackbar or dialog)
- Review count shown on product cards in browse/search results

**What users expect:**
- "Verified purchase" badge on reviews from actual buyers — this is now table stakes on any platform with reviews
- Ability to edit or delete own review

**Anti-patterns to avoid:**
- Do not allow reviews without a verified purchase — fake review problem destroys trust
- Do not show reviews before the product has any (show "Be the first to review" prompt instead of empty stars)

**Complexity:** Medium-High (purchase verification logic, aggregate computation, Firestore subcollection structure)
**Dependencies:** Order model (DELIVERED status required), Product model (average_rating + review_count fields for display), ProductReview repository (already scaffolded per git status)

---

### 6. Wishlist / Save for Later

**Expected behavior:**
- Heart/bookmark icon on every product card and product detail screen
- Tapping toggles saved state immediately (optimistic UI update)
- Dedicated Wishlist tab or section in Customer home
- Wishlist items show: product image, name, current price, seller name, "Add to Cart" button, "Remove" button
- If a wishlisted product goes out of stock or is deleted, show inline indicator (don't silently remove)
- Wishlist persisted in Room (offline-first), synced to Firestore user document or subcollection
- Item count badge optional (not required for MVP)

**Complexity:** Low-Medium
**Dependencies:** Room WishlistEntity, Firestore user wishlist subcollection, Product model

---

### 7. FCM Push Notifications

**Expected behavior (by notification type):**

| Trigger | Recipient | Content |
|---|---|---|
| Order placed | Seller | "New order #XYZ from [Customer]" |
| Order status: CONFIRMED | Customer | "Your order #XYZ has been confirmed" |
| Order status: SHIPPED | Customer | "Your order #XYZ has shipped" + tracking if available |
| Order status: DELIVERED | Customer | "Your order #XYZ has been delivered. Leave a review!" |
| Order status: CANCELLED | Customer | "Your order #XYZ was cancelled" |
| Seller account approved | Seller | "Your seller account has been approved" |
| New product review | Seller | "A customer reviewed [Product Name]" |

- Notifications tapped must deep-link to the relevant screen (order detail, product detail)
- Android 13+ requires POST_NOTIFICATIONS runtime permission — request it gracefully with rationale
- Notification channels: at minimum, separate channels for "Order Updates" and "Account" (allows user to control per-channel in system settings)
- In-app notification settings screen: per-type toggles (order updates, review alerts, promotions) — persisted in Room + Firestore user preferences
- FCM token refresh must be handled (onNewToken → update Firestore user document)
- Foreground notifications must show as heads-up notification or in-app banner

**Complexity:** High (Cloud Functions for triggers, FCM token management, deep linking, permission flow, notification channels)
**Dependencies:** Cloud Functions (every notification trigger is a Cloud Function), Order model, deep link routes in NavGraph

---

### 8. Personalization Settings

**Expected behavior:**

**Dark/Light Mode:**
- System default (follow device theme) as the default setting
- Manual override: Light / Dark / System
- Persisted in DataStore (not SharedPreferences — DataStore is the modern Android convention)
- Applied immediately without app restart
- No flash of wrong theme on cold start (read from DataStore synchronously or use a splash delay)

**Notification Settings:**
- Per-type toggles: Order Updates, Review Alerts, Promotions/Deals
- Stored in Room + Firestore (so settings persist across device reinstalls when signed in)
- Checking a toggle OFF stops the app from registering for that topic (FCM topic subscription) or filters at notification display time

**Orientation Lock:**
- User setting: Allow rotation / Lock to portrait
- Persisted in DataStore
- Applied via Activity requestedOrientation — must be set on Activity, not Composable

**Language/Locale:**
- Not required for MVP given single-locale target; add to anti-features list

**Complexity:** Low (theme) to Medium (notification settings + FCM topic sync)
**Dependencies:** DataStore, FCM topic subscriptions, notification channels

---

### 9. Profile Management

**Expected behavior:**
- Edit display name, profile photo
- Profile photo upload to Firebase Storage with progress indicator
- View email (read-only; changing email requires re-auth flow — skip for MVP)
- Change password flow: requires current password, then new password with confirmation (Firebase re-auth)
- Seller-specific: business name, bio, contact info visible on storefront
- Delete account: confirmation dialog, requires re-auth, deletes Firestore user document + Storage files (Firebase Auth account deletion)

**Complexity:** Low-Medium
**Dependencies:** Firebase Auth, Firebase Storage, Firestore user document

---

## Differentiating Features

These create competitive advantage for WenuCommerce. Not required for launch but add significant value.

---

### 10. Seller Discounts and Coupon Codes

**Expected behavior (baseline differentiator):**
- Seller creates discount: percentage off (10%) or fixed amount off ($5)
- Discount can apply to: entire store, specific category, or specific product
- Coupon code: alphanumeric string, optional expiry date, optional usage limit
- Customer enters coupon code at checkout (before PaymentSheet); discount shown in order summary
- Coupon validation: Cloud Function checks code exists, not expired, usage limit not exceeded, then returns discount amount
- Applied discount recorded on Order document
- Automatic discounts (no code required) — flash sales — are a step above coupon codes

**Seller discount management screen:**
- Create/edit/delete discount codes
- View usage count per code

**Complexity:** High (coupon validation must be server-side to prevent manipulation; atomic usage count increment in Firestore; Stripe supports applying discounts via `amount` reduction on PaymentIntent or via Stripe Coupons API)
**Dependencies:** Stripe integration (checkout), Cloud Functions (validation), Order model (discount field)

---

### 11. Favorite Sellers / Seller Following

**Expected behavior:**
- Customer can "follow" or "favorite" a seller from seller storefront screen
- Followed sellers list in customer profile/settings
- Optional: receive notifications when a followed seller adds a new product
- Seller can see follower count (not individual followers — privacy)

**Complexity:** Low-Medium
**Dependencies:** Seller storefront screen, Firestore user follows subcollection, FCM (optional new product notification)

---

### 12. Seller Storefront

**Expected behavior:**
- Public profile page for each seller: business name, bio, profile photo, average rating, follower count, product listings
- Customer-facing — accessible by tapping seller name anywhere in the app
- Seller's products listed with search/filter within storefront

**Complexity:** Low (mostly composition of existing data)
**Dependencies:** Seller profile data, Product repository (filter by sellerId)

---

### 13. Offline-First Sync (Room + Firebase)

**Expected behavior:**
- All read operations source from Room first, then sync from Firestore in background
- Write operations: write to Room immediately (optimistic), enqueue Firestore write, handle failure with retry or user-visible error
- Sync strategy: Firestore snapshot listeners update Room in background when online
- Conflict resolution strategy must be defined per entity:
  - Cart: last-write-wins (simple), or merge (complex — skip for MVP)
  - Orders: Firestore is source of truth (read-only for customer after creation)
  - Products: Firestore is source of truth (seller edits online only)
  - Wishlist: merge (union of local + remote at login)
- Connectivity state: observe `ConnectivityManager.NetworkCallback`, surface in UI when offline (non-intrusive banner)
- WorkManager for background sync of queued mutations when network returns

**Complexity:** High (conflict resolution is the hard part; scoping what is truly offline-capable vs. what requires connectivity)
**Dependencies:** Room, WorkManager, Firestore snapshot listeners, all repository implementations

---

## Anti-Features

Deliberately exclude these from WenuCommerce at this stage. Each has a rationale.

| Feature | Rationale for Exclusion |
|---|---|
| In-app chat (buyer-seller messaging) | Firebase Realtime Database or a separate service required; adds significant complexity; use seller contact info on storefront instead |
| Multi-currency / internationalization | Single locale target; adds complexity to Stripe configuration and number formatting throughout |
| Email/SMS marketing campaigns | Requires third-party service (Mailchimp, Twilio); out of scope for seller tools at this stage |
| Product recommendations / ML personalization | Requires training data volume that doesn't exist at MVP stage; Firebase Recommendations requires Firebase ML setup |
| Auction / bidding | Completely different pricing model; real-time race conditions; out of scope |
| Split payments / marketplace payout routing | Stripe Connect (marketplace) is a different product from Payment Intents; significant legal/compliance overhead |
| Loyalty points / rewards program | Complex accounting; not table stakes |
| Product comparison side-by-side | Low mobile utility; adds UI complexity |
| AR product preview | High development cost; dependent on product type |
| Customer-to-customer resale | Different trust model; requires significant moderation tooling |
| Change email (in profile) | Firebase re-auth flow + email verification cycle is fragile; skip for MVP |
| Refunds in-app | Refund initiation should be Stripe dashboard or admin backend operation only; do not put refund keys in app |

---

## Feature Dependency Map

```
Stripe PaymentSheet ──────────────────────────────┐
                                                   ▼
Cart ──────────────────────────────────────► Checkout ──────────────► Order (created)
                                                                           │
                                                          ┌────────────────┼────────────────┐
                                                          ▼                ▼                ▼
                                                   Order Tracking    FCM Notification  Review (after DELIVERED)
                                                   (Customer)        (status change)        │
                                                          │                │                ▼
                                                   Order Management  Cloud Function   Product rating aggregate
                                                   (Seller)         (FCM trigger)

Wishlist ──────────────────────────────────► Add to Cart (from wishlist)

Seller Discounts ──────────────────────────► Checkout (coupon validation)

FCM Notifications ─────────────────────────► Notification Settings (DataStore) ◄─── Personalization Settings
                                                                                            │
                                                                                     Dark/Light Mode (DataStore)
                                                                                     Orientation Lock (DataStore)

Favorite Sellers ──────────────────────────► Seller Storefront ◄──────────────── Seller Profile (profile mgmt)

Offline-First (Room) ──────────────────────► ALL features (cross-cutting concern)
```

**Critical path to checkout:**
1. Cloud Function for PaymentIntent (backend — blocks all checkout work)
2. Cart (Room + Firestore)
3. Stripe SDK integration
4. Order creation on payment success
5. Order tracking (customer)
6. Order management (seller)
7. FCM on order status change

---

## Complexity Summary

| Feature | Complexity | Effort Estimate | Blocking Dependencies |
|---|---|---|---|
| Cart | Medium | 3-5 days | Room schema, Product model |
| Checkout (Stripe) | High | 5-8 days | Cloud Function (PaymentIntent), Cart |
| Order Tracking (Customer) | Medium | 2-3 days | Order model, Checkout |
| Order Management (Seller) | Medium | 2-3 days | Order model, FCM |
| Reviews & Ratings | Medium-High | 4-6 days | Order (DELIVERED), Product model aggregate fields |
| Wishlist | Low-Medium | 2-3 days | Room schema, Product model |
| FCM Notifications | High | 4-6 days | Cloud Functions, all triggering features |
| Dark/Light Mode | Low | 1 day | DataStore |
| Notification Settings | Medium | 2-3 days | DataStore, FCM topics |
| Orientation Lock | Low | 0.5 days | DataStore |
| Profile Management | Low-Medium | 2-3 days | Firebase Auth, Storage |
| Seller Discounts | High | 5-7 days | Checkout, Cloud Functions |
| Favorite Sellers | Low-Medium | 2-3 days | Seller Storefront |
| Seller Storefront | Low | 1-2 days | Seller profile, Product repo |
| Offline-First Sync | High | Ongoing | Room, WorkManager (cross-cutting) |

**Highest risk items:**
1. **Cloud Functions for Stripe PaymentIntent** — blocks all checkout. Must be built first; Android dev cannot unblock without it.
2. **Coupon validation Cloud Function** — must be server-side; client-side coupon validation is trivially bypassable.
3. **FCM token lifecycle** — token refresh handling is a common source of notifications silently stopping.
4. **Review purchase verification** — Firestore security rules alone cannot easily verify a completed purchase; Cloud Function or trusted server check recommended.
5. **Offline-First conflict resolution** — must define explicit strategy per entity before writing sync code, or you get data loss bugs later.

---

## Expected Behavior Quick Reference (for requirements definition)

### Cart — Minimum acceptance criteria
- [ ] Add/remove/update quantity
- [ ] Persists across app restarts (Room)
- [ ] Syncs to Firestore when online
- [ ] Reflects stock availability inline
- [ ] Empty state with browse CTA

### Checkout — Minimum acceptance criteria
- [ ] Stripe PaymentSheet (not custom form)
- [ ] PaymentIntent created server-side (Cloud Function)
- [ ] Order document created on successful payment
- [ ] Cart cleared on success
- [ ] User lands on order confirmation with order ID
- [ ] Graceful handling of payment failure (retry, don't lose cart)

### Order Tracking (Customer) — Minimum acceptance criteria
- [ ] Orders list with status
- [ ] Order detail with item list and status timeline
- [ ] Status updates reflected within 30 seconds (Firestore listener)
- [ ] Tapping FCM notification deep-links to order detail

### Order Management (Seller) — Minimum acceptance criteria
- [ ] See only own orders
- [ ] Advance status forward only (PENDING → CONFIRMED → SHIPPED → DELIVERED)
- [ ] Cancel available pre-SHIPPED
- [ ] Status update triggers FCM to customer

### Reviews — Minimum acceptance criteria
- [ ] Only verified purchasers can review
- [ ] One review per product per customer
- [ ] Star rating required, text optional
- [ ] Aggregate rating shown on product card and detail
- [ ] "Verified purchase" label

### Wishlist — Minimum acceptance criteria
- [ ] Toggle from product card and detail
- [ ] Dedicated wishlist view
- [ ] Add to cart from wishlist
- [ ] Persists across restarts (Room)

### FCM — Minimum acceptance criteria
- [ ] Order status changes notify customer
- [ ] New orders notify seller
- [ ] POST_NOTIFICATIONS permission requested on Android 13+
- [ ] Separate notification channels for order updates vs. account
- [ ] FCM token refresh handled

### Dark/Light Mode — Minimum acceptance criteria
- [ ] System default option
- [ ] Manual Light / Dark override
- [ ] Persisted in DataStore
- [ ] No theme flash on cold start

### Notification Settings — Minimum acceptance criteria
- [ ] Per-type toggles persisted
- [ ] Respects user preference (don't show notifications for disabled types)
