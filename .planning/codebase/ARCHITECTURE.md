# Architecture

**Analysis Date:** 2026-02-19

## Pattern Overview

**Overall:** Clean Architecture with MVVM presentation layer + Repository pattern for data access

**Key Characteristics:**
- Three-module structure: `domain` (business logic), `data` (repositories), `app` (presentation/UI)
- Reactive flows with Kotlin Coroutines and StateFlow for state management
- Koin for dependency injection
- Jetpack Compose for UI with role-based navigation (Customer, Seller, Admin)
- Firebase as backend (Firestore + Storage + Authentication)

## Layers

**Domain Layer:**
- Purpose: Define business logic contracts, models, and use cases
- Location: `domain/src/main/java/com/wenubey/domain/`
- Contains: Repository interfaces, domain models (Product, User, Category, etc.), utility functions, auth result types
- Depends on: Kotlin standard library
- Used by: Data layer (implements), App layer (uses repositories)

**Data Layer:**
- Purpose: Implement repository contracts and handle Firebase interactions
- Location: `data/src/main/java/com/wenubey/data/`
- Contains: Repository implementations, Firebase client setup, data models, utilities for safe API calls
- Depends on: Domain layer (implements interfaces), Firebase SDK, Kotlin Coroutines
- Used by: App layer through dependency injection

**Presentation Layer (App):**
- Purpose: UI rendering, navigation, state management, user interactions
- Location: `app/src/main/java/com/wenubey/wenucommerce/`
- Contains: Screens (Composable functions), ViewModels (state management), navigation graphs, themes, components
- Depends on: Domain layer, Data layer (indirectly via DI)
- Used by: Android OS through MainActivity entry point

## Data Flow

**Authentication to Dashboard:**

1. `MainActivity` starts → installs splash screen → creates `AuthViewModel`
2. `AuthViewModel.observeUserChanges()` collects from `AuthRepository.currentUser` StateFlow
3. On user state change, `updateStartDestination()` routes to CustomerTab, SellerTab, or AdminTab
4. `RootNavigationGraph` renders appropriate tab screens based on routing

**Product Browsing (Customer):**

1. `CustomerHomeScreen` displays with `CustomerHomeViewModel`
2. ViewModel calls `CategoryRepository.observeCategories()` → collects categories, auto-selects first
3. On category selection, `observeActiveProductsByCategoryAndSubcategory()` streams products
4. User search triggers debounced `searchActiveProducts()` in ViewModel
5. Results update `homeState: StateFlow<CustomerHomeState>` → Screen recomposes

**Product Management (Seller):**

1. `SellerProductListViewModel` observes `ProductRepository.observeSellerProducts(sellerId)`
2. On create/edit, `SellerProductCreateScreen` or `SellerProductEditScreen` loads ViewModel
3. Submit triggers `ProductRepository.createProduct()` or `updateProduct()` with `ProductStatus.DRAFT`
4. Images upload to Firebase Storage via `uploadProductImage()`
5. On review submit, status changes to `PENDING_REVIEW` via `submitForReview()`

**Product Moderation (Admin):**

1. `AdminProductModerationViewModel` observes `ProductRepository.observeProductsByStatus(PENDING_REVIEW)`
2. Admin approves → `approveProduct()` changes status to `ACTIVE`
3. Admin suspends → `suspendProduct()` stores reason, changes status to `SUSPENDED`

## Key Abstractions

**AuthRepository:**
- Purpose: Manages authentication state and user session
- Examples: `app/src/main/java/com/wenubey/wenucommerce/AuthViewModel.kt`, `data/src/main/java/com/wenubey/data/repository/AuthRepositoryImpl.kt`
- Pattern: Exposes `currentUser: StateFlow<User?>`, implements Firebase Auth integration with email/password and Google Sign-In

**ProductRepository:**
- Purpose: Product CRUD, search, status transitions, and inventory management
- Examples: `data/src/main/java/com/wenubey/data/repository/ProductRepositoryImpl.kt`
- Pattern: Separate methods for seller operations (`createProduct`, `submitForReview`), customer operations (`observeActiveProductsByCategory`, `searchActiveProducts`), and admin operations (`approveProduct`, `suspendProduct`)

**CategoryRepository:**
- Purpose: Category and subcategory retrieval for browsing and filtering
- Examples: `domain/src/main/java/com/wenubey/domain/repository/CategoryRepository.kt`, `data/src/main/java/com/wenubey/data/repository/CategoryRepositoryImpl.kt`
- Pattern: `observeCategories()` emits List<Category>, drives category carousels and filter sheets

**ViewModel Pattern:**
- Purpose: Hold UI state, handle user actions, coordinate repository calls
- Examples: `CustomerHomeViewModel`, `SellerProductCreateViewModel`, `AdminProductModerationViewModel`
- Pattern: State class (e.g., `CustomerHomeState`) + sealed interface for actions (e.g., `CustomerHomeAction`) + `onAction(action)` method. State emitted via `StateFlow` for reactive composition.

## Entry Points

**MainActivity:**
- Location: `app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt`
- Triggers: Android OS on app launch
- Responsibilities: Install splash screen, initialize Compose, create NavHostController, display RootNavigationGraph based on AuthViewModel startDestination

**WenuCommerce (Application class):**
- Location: `app/src/main/java/com/wenubey/wenucommerce/WenuCommerce.kt`
- Triggers: Before MainActivity (Android Application lifecycle)
- Responsibilities: Initialize Firebase, start Koin DI, setup Timber logging

**RootNavigationGraph:**
- Location: `app/src/main/java/com/wenubey/wenucommerce/navigation/RootNavigationGraph.kt`
- Triggers: Rendered by MainActivity's Compose content
- Responsibilities: Route to `authNavRoutes()` (SignUp, SignIn, Onboarding) or `tabNavRoutes()` (CustomerTab, SellerTab, AdminTab)

## Error Handling

**Strategy:** Result<T> sealed class for repository methods; catch blocks update ViewModel state with error messages

**Patterns:**
- `safeApiCall()` wrapper in `data/src/main/java/com/wenubey/data/util/SafeApiCall.kt` wraps all repository operations with try-catch, logs to Timber
- ViewModels update state with `errorMessage` field (e.g., `CustomerHomeState.errorMessage`)
- UI renders error messages or loading states based on ViewModel state flags
- Firebase task failures (`.await()`) thrown as exceptions, caught by `safeApiCall()`

## Cross-Cutting Concerns

**Logging:**
- Tool: Timber library
- Pattern: Injected in `WenuCommerce.onCreate()` with DebugTree in debug builds
- Usage: Repository implementations log operations (e.g., `Timber.d("Product created: $productId")`)

**Validation:**
- Pattern: Inline in ViewModel action handlers (e.g., check `query.isBlank()` before search)
- Examples: `CustomerHomeViewModel.performSearch()` returns empty list for blank query

**Authentication:**
- Pattern: Firebase Auth manages session; `AuthViewModel` observes `currentUser` StateFlow and gates navigation
- Examples: `AuthViewModel.observeUserChanges()` checks `user != null`, `authRepository.currentFirebaseUser == null`
- Gating: Unauthenticated users see SignUp/SignIn; authenticated users routed by role

---

*Architecture analysis: 2026-02-19*
