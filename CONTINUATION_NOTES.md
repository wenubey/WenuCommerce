# WenuCommerce -- Session Continuation Notes

**Last updated:** 2026-02-19
**Branch:** `feature/product`
**Base branch:** `main`

---

## Project Overview

WenuCommerce is an Android e-commerce marketplace application built with:

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3 (Compose BOM `2025.01.00`)
- **Architecture:** Clean Architecture with 3 modules (`domain`, `data`, `app`)
- **Backend:** Firebase (Firestore, Storage, Auth, Functions, Messaging)
- **DI:** Koin 4.0.1 (BOM-based, `viewModelOf` constructor injection)
- **Navigation:** Jetpack Navigation Compose with type-safe serializable route objects
- **Image loading:** Coil 3.0.4 with `coil-network-okhttp`
- **Networking:** Ktor 2.3.12 (OkHttp engine, kotlinx.json serialization)
- **Local storage:** Room 2.6.1, DataStore Preferences 1.1.2
- **Logging:** Timber 5.0.1
- **3 user roles:** Customer, Seller, Admin
- **compileSdk / targetSdk:** 35, **minSdk:** 24

---

## Architecture Summary

### Module Structure

| Module | Purpose |
|--------|---------|
| `domain` | Pure Kotlin models, repository interfaces, utility classes. No Android dependencies. |
| `data` | Repository implementations, Firebase interactions, DataStore, Room DB, Ktor HTTP client. |
| `app` | Jetpack Compose UI, ViewModels, navigation, DI wiring, all Android-specific code. |

### Key Patterns

- **MVVM with unidirectional data flow:** Each feature has a sealed `Action` interface, a `State` data class, and a `ViewModel` that exposes `StateFlow<State>` collected in Composables via `collectAsStateWithLifecycle()`.
- **Repository pattern:** Domain defines interfaces; Data provides implementations. All repository calls return `Result<T>` and use a `safeApiCall(ioDispatcher)` wrapper.
- **Firebase Firestore real-time listeners** use `callbackFlow` for streaming data. IMPORTANT: Do NOT wrap `.collect{}` inside `withContext(ioDispatcher)` -- callbackFlow handles its own dispatcher. Only wrap one-shot suspend calls in `withContext(ioDispatcher)`.
- **SearchKeywordsGenerator** (`domain/util/SearchKeywordsGenerator.kt`) produces denormalized keyword lists stored in Firestore documents for text search via `whereArrayContainsAny`.
- **Navigation:** Type-safe routes defined as `@Serializable` data objects/classes in `AppNavigationObjects.kt`. Tab-based navigation for each role (`CustomerTab`, `SellerTab`, `AdminTab`) with tab index parameter.

### DI Modules (Koin)

Defined in `app/src/main/java/com/wenubey/wenucommerce/di/`:

| Module | Contents |
|--------|----------|
| `firebaseModule` | Firebase Auth, Firestore, Storage, Messaging, Functions singletons |
| `repositoryModule` | All repository impl bindings (Firestore, Auth, Profile, Location, Category, Product, ProductReview, Tag) |
| `dispatcherModule` | `DispatcherProviderImpl` bound to `DispatcherProvider` |
| `googleIdOptionModule` | Google Sign-In option factory |
| `deviceInfoModule` | DeviceIdProvider, DeviceInfoProvider |
| `ktorModule` | Ktor HttpClient with OkHttp engine and JSON content negotiation |
| `viewModelModule` | All ViewModel registrations via `viewModelOf(::...)` |

---

## What Has Been Built (Complete Features)

### Authentication
- Sign up (email/password + Google One Tap)
- Sign in (email/password + Google One Tap)
- Forgot password flow
- Email verification with deep link handling
- Role-based routing after auth (Customer -> CustomerTab, Seller -> SellerTab, Admin -> AdminTab)
- Onboarding flow (role selection, business info for sellers)

### Seller Features
- **Dashboard:** Overview screen with product stats
- **Product CRUD:** Create product (multi-image upload, variant support, shipping config, tag selection, category/subcategory picker), edit product, list products with search/filter by text and status
- **Category picker:** Browse categories and subcategories when creating/editing products
- **Verification flow:** Seller submits for approval, status tracking (PENDING, APPROVED, CANCELLED)
- **Storefront:** Public-facing storefront screen (routed but no entry point button yet -- see Missing 5)

### Admin Features
- **Product moderation:** Review pending products, approve/reject with notes
- **Product search:** Search all products across all statuses with text query
- **Category CRUD:** Create/edit/delete categories and subcategories with image upload
- **Seller approval:** View pending seller applications, approve/reject sellers
- **Badge system:** Notification badge counts for pending items
- **Dashboard, Users, Analytics, Settings** tabs (scaffolded)

### Customer Features
- **Home screen:** Category browsing via horizontal category card carousel, subcategory chip row, product grid filtered by selected category/subcategory
- **Product search:** Text search with debounce, inline expandable category filter via WenuSearchBar
- **Product detail:** Full detail screen with image gallery (horizontal pager), variant selector, shipping info, review list display, seller info with link to storefront
- **"Add to Cart" button** exists but is a stub (no cart system built yet)

### Shared / Core Components
- `WenuSearchBar` (`core/components/WenuSearchBar.kt`) -- Reusable search bar with filter icon, badge count, and `CategoryFilterSheet` integration
- `CategoryFilterSheet` (`core/components/CategoryFilterSheet.kt`) -- Shared Material 3 `ModalBottomSheet` for two-step category/subcategory filter selection
- `EmailVerificationBannerViewModel` -- Shared banner shown across all tab screens prompting unverified users to verify email

---

## Current State of Bugs

Bugs 1-4 from `PRODUCT_BUGS_AND_GAPS.md` have been fixed in this session:

| Bug | Description | Status |
|-----|-------------|--------|
| Bug 1 | `submitForReview` coroutine leak -- `_state.collect` replaced with `_state.first` | FIXED |
| Bug 2 | Category UUID mismatch -- repo now respects existing `category.id` if non-blank | FIXED |
| Bug 3 | Image removal doesn't delete from Firebase Storage -- now calls `deleteProductImage` | FIXED |
| Bug 4 | Subcategory duplication on retry -- now uses Firestore transaction with ID check | FIXED |
| Bug 5 | `unarchiveProduct` always resets to DRAFT | DEFERRED -- accepted as design choice (archived products require re-review) |

---

## What Has Been Built This Session (Search/Filter Feature)

The `SEARCH_FILTER_SPEC.md` spec has been implemented:

- `WenuSearchBar` updated with optional `onFilterClick` and `activeFilterCount` parameters, filter icon with `BadgedBox`
- `CategoryFilterSheet` created as shared composable in `core/components/`
- Customer, Admin, and Seller search screens all wire the filter sheet
- `ProductRepository.searchActiveProducts` and `searchAllProducts` updated with optional `categoryId`/`subcategoryId` parameters (client-side post-filter, no new Firestore index needed)
- Tag chips (`SuggestionChip` in `LazyRow`, max 5) added to `CustomerProductCard` and `AdminProductSearchCard`
- Seller product card explicitly excluded from tag display (per spec)
- State/Action/ViewModel updated for all three roles

---

## What Is Missing (Not Yet Built)

### BLOCKER for Order Flow
1. **Cart system** -- No domain model, repository, or UI exists. The "Add to Cart" button in `CustomerProductDetailScreen` is `onClick = { /* TODO */ }`. Required components:
   - `domain/model/cart/Cart.kt` and `CartItem.kt`
   - `domain/repository/CartRepository.kt`
   - `data/repository/CartRepositoryImpl.kt` (local-first with Room or DataStore, optionally Firestore-backed)
   - Cart screen UI (list items, update quantity, remove, total calculation, proceed to checkout)

### Medium Priority
2. **Review submission UI** -- Backend (`ProductReviewRepository.submitReview()`) is fully implemented with atomic rating update. No UI exists for customers to write reviews. Needs: "Write a Review" button (only for purchasers), rating selector, title/body fields, wire to ViewModel.
3. **Admin search approve/suspend actions** -- `onApprove` and `onSuspend` in `AdminProductSearchScreen`'s `ProductDetailDialog` just dismiss the dialog. Need to wire to `productRepository.approveProduct()` and `productRepository.suspendProduct()`.

### Low Priority
4. **Category images on customer home** -- `CategoryCard` uses generic `Icons.Default.Category` icon instead of the uploaded `category.imageUrl`. Fix: use `AsyncImage` from Coil with fallback.
5. **"View My Storefront" entry point** -- `SellerStorefrontScreen` exists and is routed but no button navigates to it. Add button on `SellerDashboardScreen` or `SellerProfileScreen`.
6. **`isLoadingCategories` hardcoded to `false`** on Customer home -- search bar filter spinner never shows during category load.
7. **Dead code cleanup** -- `SellerDashboardViewModel.addProduct()` is an empty stub; `observeActiveProductsByCategory()` in `ProductRepository` is never called (superseded by `observeActiveProductsByCategoryAndSubcategory`).

---

## Next Steps: Order Flow

The user wants to build the **ORDER FLOW** next. This is the critical path:

### Sequence
1. **Cart** -- domain model + repository + UI (add to cart, view cart, update quantity, remove items)
2. **Checkout** -- address confirmation, shipping method selection, order summary
3. **Order** -- domain model (`Order`, `OrderItem`, `OrderStatus`), repository, order creation from cart
4. **Payment** -- Stripe integration (the `Product` model already has a `stripeProductId: String` field prepared for this)
5. **Order tracking** -- Customer views order history and status; Seller views incoming orders and updates fulfillment status

### Preferred Workflow
The user prefers a two-phase approach:
1. **Software-architect** creates a detailed spec (like `SEARCH_FILTER_SPEC.md`) with functional requirements, technical design, state/action changes, DI changes, edge cases, and file locations
2. **User reviews** the spec and approves or requests changes
3. **Lead-developer** implements the spec

---

## Key Technical Details

### Dependency Versions (from `libs.versions.toml`)

| Dependency | Version |
|------------|---------|
| Kotlin | 2.1.0 |
| AGP | 8.7.3 |
| Compose BOM | 2025.01.00 |
| Koin BOM | 4.0.1 |
| Firebase BOM | 33.8.0 |
| Coil | 3.0.4 |
| Ktor | 2.3.12 |
| Navigation Compose | 2.8.5 |
| Room | 2.6.1 |
| DataStore | 1.1.2 |
| Paging Compose | 3.3.5 |
| KSP | 2.1.0-1.0.29 |
| Lifecycle | 2.8.7 |
| Activity Compose | 1.10.0 |
| Timber | 5.0.1 |
| Play Services Auth | 21.3.0 |

### Koin Module Registrations

**Repositories** (all singletons in `repositoryModule`):
- `FirestoreRepositoryImpl` -> `FirestoreRepository`
- `AuthRepositoryImpl` -> `AuthRepository`
- `ProfileRepositoryImpl` -> `ProfileRepository`
- `LocationServiceImpl` -> `LocationService`
- `CategoryRepositoryImpl` -> `CategoryRepository`
- `ProductRepositoryImpl` -> `ProductRepository`
- `ProductReviewRepositoryImpl` -> `ProductReviewRepository`
- `TagRepositoryImpl` -> `TagRepository`

**ViewModels** (all in `viewModelModule`):
- Auth: `SignUpViewModel`, `SignInViewModel`, `AuthViewModel`, `VerifyEmailViewModel`
- Onboarding: `OnboardingViewModel`
- Admin: `AdminApprovalViewModel`, `AdminBadgeViewModel`, `AdminCategoryViewModel`, `AdminProductModerationViewModel`, `AdminProductSearchViewModel`
- Seller: `SellerDashboardViewModel`, `SellerVerificationViewModel`, `SellerCategoryViewModel`, `SellerProductListViewModel`, `SellerProductCreateViewModel`, `SellerProductEditViewModel`, `SellerStorefrontViewModel`
- Customer: `CustomerHomeViewModel`, `CustomerProductDetailViewModel`
- Shared: `EmailVerificationBannerViewModel` (manual constructor with named params)

### Navigation Routes (`AppNavigationObjects.kt`)

All routes are `@Serializable` objects/classes:

| Route | Type | Parameters |
|-------|------|------------|
| `SignIn` | data object | -- |
| `SignUp` | data object | -- |
| `ForgotPassword` | data object | -- |
| `VerifyEmail` | data class | `email: String` |
| `Onboarding` | data object | -- |
| `CustomerTab` | data class | `tabIndex: Int` |
| `SellerTab` | data class | `tabIndex: Int` |
| `AdminTab` | data class | `tabIndex: Int` |
| `CustomerHome` | data object | -- |
| `CustomerCart` | data object | -- |
| `CustomerProfile` | data object | -- |
| `CustomerProductDetail` | data class | `productId: String` |
| `SellerVerificationStatusScreen` | data object | -- |
| `SellerProfile` | data object | -- |
| `SellerProductCreate` | data object | -- |
| `SellerProductEdit` | data class | `productId: String` |
| `SellerStorefront` | data class | `sellerId: String` |
| `AdminDashboard` | data object | -- |
| `AdminUsers` | data object | -- |
| `AdminAnalytics` | data object | -- |
| `AdminSettings` | data object | -- |
| `Home` | data object | -- |
| `Cart` | data object | -- |
| `Profile` | data object | -- |

### Important Implementation Notes

- **Coil 3** is used (not Coil 2). Import `coil3.compose.AsyncImage`, not `coil.compose.AsyncImage`. The network module is `coil-network-okhttp`.
- **Firebase Firestore real-time listeners** use `callbackFlow`. Never wrap `.collect{}` in `withContext(ioDispatcher)` -- the callbackFlow callback already runs on its own thread.
- **`SearchKeywordsGenerator`** (`domain/util/SearchKeywordsGenerator.kt`) generates denormalized keyword arrays stored on Firestore documents. Search queries tokenize input and match against `searchKeywords` field using `whereArrayContainsAny`.
- **Products have `stripeProductId: String`** field -- prepared for future Stripe payment integration.
- **`Product.toMap()`** extension function is defined in `Product.kt` for Firestore serialization.
- **User model** has `role: UserRole`, `businessInfo: BusinessInfo?` (for sellers), `purchaseHistory: List<Purchase>`, and `products: List<String>` (product IDs for sellers).
- **Product model** includes: `categoryId`/`categoryName`, `subcategoryId`/`subcategoryName`, `tags`/`tagNames` (denormalized), `images: List<ProductImage>`, `variants: List<ProductVariant>`, `shipping: ProductShipping`, `status: ProductStatus` (DRAFT, PENDING_REVIEW, ACTIVE, SUSPENDED, ARCHIVED), `averageRating`/`reviewCount`, `stripeProductId`.

---

## Product Model Fields Reference

```kotlin
data class Product(
    val id: String,
    val title: String, val description: String, val slug: String,
    val sellerId: String, val sellerName: String, val sellerLogoUrl: String,
    val categoryId: String, val categoryName: String,
    val subcategoryId: String, val subcategoryName: String,
    val tags: List<String>, val tagNames: List<String>,
    val searchKeywords: List<String>,
    val condition: ProductCondition,  // NEW, USED, REFURBISHED, etc.
    val basePrice: Double, val compareAtPrice: Double?, val currency: String,
    val images: List<ProductImage>,
    val variants: List<ProductVariant>,
    val totalStockQuantity: Int, val hasVariants: Boolean,
    val shipping: ProductShipping,
    val status: ProductStatus,  // DRAFT, PENDING_REVIEW, ACTIVE, SUSPENDED, ARCHIVED
    val moderationNotes: String,
    val suspendedBy: String, val suspendedAt: String,
    val averageRating: Double, val reviewCount: Int,
    val stripeProductId: String,
    val viewCount: Int, val purchaseCount: Int,
    val createdAt: String, val updatedAt: String,
    val publishedAt: String, val archivedAt: String,
)
```

---

## File Structure Reference

### App Module (`app/src/main/java/com/wenubey/wenucommerce/`)

```
wenucommerce/
  admin/
    admin_analytics/          -- Analytics tab (scaffolded)
    admin_categories/         -- Category CRUD (AdminCategoryViewModel)
    admin_dashboard/          -- Dashboard tab
    admin_products/           -- Product moderation + search (AdminProductModerationViewModel, AdminProductSearchViewModel)
    admin_seller_approval/    -- Seller approval flow (AdminApprovalViewModel)
    admin_settings/           -- Settings tab (scaffolded)
    admin_users/              -- Users tab
    AdminBadgeViewModel.kt    -- Badge counts for pending items
    AdminTabScreen.kt         -- Tab container
    AdminTabs.kt              -- Tab definitions
  core/
    components/
      WenuSearchBar.kt        -- Reusable search bar with filter icon + badge
      CategoryFilterSheet.kt  -- Shared category/subcategory filter bottom sheet
    email_verification_banner/ -- Shared email verification banner
  customer/
    customer_home/            -- Home screen VM, State, Action (CustomerHomeViewModel)
    customer_products/        -- Product detail screen + VM (CustomerProductDetailViewModel)
    CustomerHomeScreen.kt     -- Home screen composable with category browse + search
    CustomerTabScreen.kt      -- Tab container
  seller/
    seller_categories/        -- Category picker for product forms (SellerCategoryViewModel)
      components/
        CreateCategoryDialog.kt
        CreateSubcategoryDialog.kt
    seller_dashboard/         -- Dashboard screen + VM (SellerDashboardViewModel)
    seller_products/          -- Product list, create, edit VMs and screens
    seller_storefront/        -- Public storefront screen + VM (SellerStorefrontViewModel)
    seller_verification/      -- Verification flow + VM (SellerVerificationViewModel)
    SellerTabScreen.kt        -- Tab container
  navigation/
    AppNavigationObjects.kt   -- All @Serializable route definitions
    AuthNavRoutes.kt          -- Auth flow nav graph
    RootNavigationGraph.kt    -- Root nav graph
    TabNavRoutes.kt           -- Tab-based nav routes per role
  di/
    AppModules.kt             -- Aggregates all Koin modules
    DataModule.kt             -- Firebase, repository, dispatcher, Ktor modules
    PreferencesModule.kt      -- DataStore preferences
    ViewmodelModule.kt        -- All ViewModel registrations
  notification/               -- FCM push notification handling
  onboard/                    -- Onboarding flow
  sign_in/                    -- Sign in screen + VM
  sign_up/                    -- Sign up screen + VM
  verify_email/               -- Email verification screen + VM
  ui/                         -- Theme definitions
```

### Domain Module (`domain/src/main/java/com/wenubey/domain/`)

```
domain/
  model/
    product/
      Category.kt, Subcategory.kt
      Product.kt, ProductCondition.kt, ProductImage.kt
      ProductReview.kt, ProductShipping.kt, ShippingType.kt
      ProductStatus.kt, ProductVariant.kt, Tag.kt
    user/
      User.kt, UserRole.kt
    onboard/
      BusinessInfo.kt
    Device.kt, Gender.kt, IpLocation.kt, Purchase.kt
  repository/
    AuthRepository.kt
    CategoryRepository.kt
    DispatcherProvider.kt
    FirestoreRepository.kt
    LocationService.kt
    ProductRepository.kt
    ProductReviewRepository.kt
    ProfileRepository.kt
    TagRepository.kt
  util/
    AuthProvider.kt
    DocumentType.kt
    SearchKeywordsGenerator.kt
```

### Data Module (`data/src/main/java/com/wenubey/data/`)

```
data/
  repository/
    AuthRepositoryImpl.kt
    CategoryRepositoryImpl.kt
    DispatcherProviderImpl.kt
    FirestoreRepositoryImpl.kt
    LocationServiceImpl.kt
    NotificationPreferences.kt
    ProductRepositoryImpl.kt
    ProductReviewRepositoryImpl.kt
    ProfileRepositoryImpl.kt
    TagRepositoryImpl.kt
```

---

## Spec Files in Project Root

| File | Description |
|------|-------------|
| `PRODUCT_BUGS_AND_GAPS.md` | Audit of bugs (5) and missing features (7) in the product/category system. Bugs 1-4 fixed this session. Missing features prioritized with cart as the top blocker. |
| `SEARCH_FILTER_SPEC.md` | Full architect-level spec for the enhanced search with category filtering and tag display feature. Implemented this session. Covers all 3 roles, functional requirements (FR-1 through FR-14), non-functional requirements, technical design with state/action/VM changes per role, edge cases, acceptance criteria, and file-by-file diff summary. |
| `CONTINUATION_NOTES.md` | This file. Session context for resuming work. |

---

## Git Status Summary

The `feature/product` branch has extensive uncommitted changes spanning:
- Modified files across all three role packages (admin, seller, customer)
- New files for product feature (seller_products, admin_products, customer_products packages)
- New shared components (WenuSearchBar, CategoryFilterSheet)
- New domain models (Product, ProductImage, ProductVariant, ProductShipping, ProductCondition, ProductStatus, Tag, ProductReview, ShippingType)
- New repository interfaces and implementations (ProductRepository, ProductReviewRepository, TagRepository)
- Updated navigation routes, DI modules, and string resources
- New utility: SearchKeywordsGenerator

Recent commits on this branch:
```
aaa2c44 Implement categories feature and fix profile photo upload bug
07f81bb Fix multiple UI bugs and add email verification banner to all tab screens
1d46f1e Implement seller approval flow: CANCELLED status, action gating, verification badges, and deserialization safety
2d8d652 Merge branch 'bug/sign-up'
2359649 Sign Up Feature bug fixed.
```

---

## Resuming Tomorrow: Quick Start Checklist

1. Read this file (`CONTINUATION_NOTES.md`) first.
2. Check `git status` and `git diff --stat` to see current uncommitted work.
3. The user's next goal is the **ORDER FLOW**. Start by creating an architect-level spec for the Cart system (similar in format to `SEARCH_FILTER_SPEC.md`).
4. The Cart spec should cover: domain models (`Cart`, `CartItem`), repository interface and implementation (recommend local-first with Room or in-memory, synced to Firestore for cross-device), UI screens (cart list, quantity controls, remove, total, checkout button), state/action/VM pattern, DI wiring, navigation route additions, and edge cases.
5. After Cart spec approval, implement it, then proceed to Checkout -> Order -> Payment (Stripe).
6. Refer to `PRODUCT_BUGS_AND_GAPS.md` for remaining missing features that can be addressed in parallel.
