# Codebase Structure

**Analysis Date:** 2026-02-19

## Directory Layout

```
WenuCommerce/
├── app/                          # Main application module (UI, navigation, DI)
│   └── src/main/
│       ├── java/com/wenubey/wenucommerce/
│       │   ├── MainActivity.kt
│       │   ├── AuthViewModel.kt
│       │   ├── WenuCommerce.kt   # Application class
│       │   ├── admin/            # Admin role screens
│       │   ├── customer/          # Customer role screens
│       │   ├── seller/            # Seller role screens
│       │   ├── core/              # Shared components (email banner, validators)
│       │   ├── di/                # Dependency injection modules
│       │   ├── navigation/        # Navigation graphs and objects
│       │   ├── sign_in/           # Sign-in flow
│       │   ├── sign_up/           # Sign-up flow
│       │   ├── onboard/           # Onboarding flow
│       │   ├── verify_email/      # Email verification flow
│       │   ├── notification/      # Firebase messaging service
│       │   └── ui/theme/          # Material3 theme configuration
│       └── res/                   # Android resources (drawables, strings, values)
│
├── domain/                        # Domain layer (business logic, contracts)
│   └── src/main/java/com/wenubey/domain/
│       ├── auth/                  # Auth result types (SignUpResult, SignInResult)
│       ├── model/
│       │   ├── product/           # Product, Category, Subcategory, ProductImage, etc.
│       │   ├── user/              # User, UserRole
│       │   └── onboard/           # Onboarding data models
│       ├── repository/            # Repository interfaces
│       ├── util/                  # Utility functions (SearchKeywordsGenerator)
│       └── ...
│
├── data/                          # Data layer (repository implementations)
│   └── src/main/java/com/wenubey/data/
│       ├── repository/            # Repository implementations
│       │   ├── AuthRepositoryImpl.kt
│       │   ├── ProductRepositoryImpl.kt
│       │   ├── CategoryRepositoryImpl.kt
│       │   ├── ProfileRepositoryImpl.kt
│       │   ├── ProductReviewRepositoryImpl.kt
│       │   └── ...
│       ├── model/                 # Data transfer objects (DTOs)
│       ├── util/                  # Constants, SafeApiCall, DeviceInfo
│       └── ...
│
└── gradle/                        # Gradle configuration
    └── libs.versions.toml         # Central version catalog
```

## Directory Purposes

**app/src/main/java/com/wenubey/wenucommerce/admin/:**
- Purpose: Admin dashboard, seller approval, product moderation, user management
- Contains: Screen composables, ViewModels, components
- Key files: `AdminTabScreen.kt`, `admin_seller_approval/`, `admin_products/`, `admin_categories/`, `admin_dashboard/`, `admin_analytics/`, `admin_users/`, `admin_settings/`

**app/src/main/java/com/wenubey/wenucommerce/customer/:**
- Purpose: Customer product browsing, discovery, product detail, cart (future)
- Contains: Screen composables, ViewModels for browsing and search
- Key files: `CustomerTabScreen.kt`, `CustomerHomeScreen.kt`, `customer_home/`, `customer_products/`, `customer_home/CustomerHomeViewModel.kt`

**app/src/main/java/com/wenubey/wenucommerce/seller/:**
- Purpose: Seller product management, storefront, verification, dashboard
- Contains: Product creation/editing flows, category management, verification status
- Key files: `SellerTabScreen.kt`, `seller_products/`, `seller_categories/`, `seller_verification/`, `seller_dashboard/`, `seller_storefront/`

**app/src/main/java/com/wenubey/wenucommerce/core/:**
- Purpose: Reusable components and utilities shared across roles
- Contains: Email verification banner, validators, common Composables
- Key files: `core/components/WenuSearchBar.kt`, `core/email_verification_banner/`, `core/validators/`

**app/src/main/java/com/wenubey/wenucommerce/di/:**
- Purpose: Koin dependency injection configuration
- Contains: Module definitions for Firebase, repositories, ViewModels, utilities
- Key files: `DataModule.kt` (Firebase + repositories), `ViewmodelModule.kt` (all ViewModel providers)

**app/src/main/java/com/wenubey/wenucommerce/navigation/:**
- Purpose: Navigation graph definitions and route objects
- Contains: Nav graph builders, serializable route objects, tab routing
- Key files: `RootNavigationGraph.kt`, `AppNavigationObjects.kt` (all @Serializable route objects), `TabNavRoutes.kt`, `AuthNavRoutes.kt` (implied)

**domain/src/main/java/com/wenubey/domain/repository/:**
- Purpose: Define repository contracts (interfaces)
- Contains: AuthRepository, ProductRepository, CategoryRepository, ProfileRepository, etc.
- Key files: All `.kt` files in directory (no implementations)

**domain/src/main/java/com/wenubey/domain/model/:**
- Purpose: Domain models representing business entities
- Contains: Product, Category, User, ProductVariant, ProductImage, ProductStatus enums
- Key files: `product/Product.kt`, `product/Category.kt`, `user/User.kt`, `user/UserRole.kt`

**data/src/main/java/com/wenubey/data/repository/:**
- Purpose: Implement repository interfaces with Firebase backend
- Contains: ProductRepositoryImpl, AuthRepositoryImpl, CategoryRepositoryImpl, etc.
- Key files: All `*RepositoryImpl.kt` files (each implements domain repository interface)

**data/src/main/java/com/wenubey/data/util/:**
- Purpose: Shared utility functions and constants for data layer
- Contains: SafeApiCall wrapper, Firebase collection constants, DeviceInfo providers
- Key files: `SafeApiCall.kt`, `Constants.kt`, `DeviceIdProvider.kt`

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt`: Main activity, renders RootNavigationGraph
- `app/src/main/java/com/wenubey/wenucommerce/WenuCommerce.kt`: Application class, initializes Firebase + Koin
- `app/src/main/AndroidManifest.xml`: Declares MainActivity as launcher

**Configuration:**
- `app/build.gradle.kts`: App module dependencies and build config (reads local.properties for API keys)
- `gradle/libs.versions.toml`: Central version catalog for all dependencies
- `local.properties`: Local build configuration (NOT committed, contains API keys)

**Core Logic:**
- `domain/src/main/java/com/wenubey/domain/repository/AuthRepository.kt`: Auth contract (sign-in, sign-up, logout, user state)
- `domain/src/main/java/com/wenubey/domain/repository/ProductRepository.kt`: Product contract (CRUD, search, status transitions)
- `data/src/main/java/com/wenubey/data/repository/AuthRepositoryImpl.kt`: Firebase Auth + Firestore integration
- `data/src/main/java/com/wenubey/data/repository/ProductRepositoryImpl.kt`: Product persistence and search
- `app/src/main/java/com/wenubey/wenucommerce/AuthViewModel.kt`: Top-level auth state, navigation routing

**Testing:**
- `app/src/test/java/`: Unit tests (if any)
- `app/src/androidTest/java/`: Instrumentation tests (if any)

## Naming Conventions

**Files:**
- PascalCase for Kotlin classes: `MainActivity.kt`, `CustomerHomeScreen.kt`, `ProductRepositoryImpl.kt`
- snake_case for XML resources: `activity_main.xml`, `strings.xml`
- snake_case for directory names: `customer_home/`, `seller_products/`, `admin_products/`

**Directories:**
- Feature-based: `customer/`, `seller/`, `admin/` (by role)
- Functional within features: `customer/customer_home/`, `seller/seller_products/`, `admin/admin_seller_approval/`
- Shared: `core/` for reusable components and utilities

**Classes:**
- Screen Composables: `*Screen.kt` (e.g., `CustomerHomeScreen.kt`, `SellerProductCreateScreen.kt`)
- ViewModels: `*ViewModel.kt` (e.g., `CustomerHomeViewModel.kt`, `SellerProductCreateViewModel.kt`)
- State classes: `*State.kt` (e.g., `CustomerHomeState.kt`, accompanying ViewModel)
- Action sealed interfaces: `*Action.kt` (e.g., `CustomerHomeAction.kt`, for ViewModel event handling)
- Repository implementations: `*RepositoryImpl.kt` (e.g., `ProductRepositoryImpl.kt`)

**Packages:**
- Reverse domain: `com.wenubey.wenucommerce` (app), `com.wenubey.domain` (domain), `com.wenubey.data` (data)
- Sub-packages by layer/feature: `com.wenubey.wenucommerce.customer`, `com.wenubey.domain.model.product`

## Where to Add New Code

**New Feature (e.g., Wishlist for customers):**
- Primary code: `app/src/main/java/com/wenubey/wenucommerce/customer/customer_wishlist/`
  - Create subdirectory with screens (e.g., `CustomerWishlistScreen.kt`, `CustomerWishlistDetailScreen.kt`)
  - Create ViewModel (e.g., `CustomerWishlistViewModel.kt`)
  - Create State and Action classes (e.g., `CustomerWishlistState.kt`, `CustomerWishlistAction.kt`)
- Domain contract: `domain/src/main/java/com/wenubey/domain/repository/WishlistRepository.kt`
- Implementation: `data/src/main/java/com/wenubey/data/repository/WishlistRepositoryImpl.kt`
- Models: `domain/src/main/java/com/wenubey/domain/model/product/Wishlist.kt` (or new model package if needed)
- DI: Add to `app/src/main/java/com/wenubey/wenucommerce/di/DataModule.kt` (repository binding) and `ViewmodelModule.kt` (ViewModel provider)
- Navigation: Add @Serializable route objects to `app/src/main/java/com/wenubey/wenucommerce/navigation/AppNavigationObjects.kt` and composable routes to navigation graph builders

**New Component/Module (e.g., Product rating widget used by multiple screens):**
- Implementation: `app/src/main/java/com/wenubey/wenucommerce/core/components/ProductRatingWidget.kt` (if shared)
- Or: Feature-specific component: `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/components/ProductCard.kt`

**Utilities:**
- Shared helpers (used by multiple layers): `data/src/main/java/com/wenubey/data/util/` (data utilities) or `domain/src/main/java/com/wenubey/domain/util/` (domain logic)
- Example: `domain/src/main/java/com/wenubey/domain/util/SearchKeywordsGenerator.kt` (search keyword logic used by ProductRepository)

## Special Directories

**app/src/main/res/:**
- Purpose: Android resources (strings, colors, drawables, animations)
- Generated: No (all hand-written)
- Committed: Yes

**build/ directories (all modules):**
- Purpose: Compiled outputs and build artifacts
- Generated: Yes (by Gradle)
- Committed: No (in .gitignore)

**gradle/:**
- Purpose: Gradle build system configuration
- Generated: No (version catalog maintained by developers)
- Committed: Yes

**.gradle/:**
- Purpose: Gradle daemon and wrapper state
- Generated: Yes
- Committed: No

---

*Structure analysis: 2026-02-19*
