# Coding Conventions

**Analysis Date:** 2026-02-19

## Naming Patterns

**Files:**
- Model/Data classes: PascalCase, suffixed with descriptive type (e.g., `User.kt`, `Purchase.kt`, `Product.kt`)
- ViewModel classes: `{Feature}ViewModel.kt` (e.g., `CustomerHomeViewModel.kt`, `SellerProductListViewModel.kt`)
- State data classes: `{Feature}State.kt` (e.g., `CustomerHomeState.kt`, `SellerProductListState.kt`)
- Action sealed interfaces: `{Feature}Action.kt` (e.g., `CustomerHomeAction.kt`, `SellerProductListAction.kt`)
- Screen composables: `{Feature}Screen.kt` (e.g., `CustomerHomeScreen.kt`, `SellerTabScreen.kt`)
- Component files: `{ComponentName}.kt` (e.g., `CreateCategoryDialog.kt`, `WenuSearchBar.kt`)
- Repository interfaces: `{Entity}Repository.kt` (e.g., `CategoryRepository.kt`, `ProductRepository.kt`)
- Repository implementations: `{Entity}RepositoryImpl.kt` (e.g., `CategoryRepositoryImpl.kt`, `ProductRepositoryImpl.kt`)
- Directory names: snake_case for feature modules (e.g., `customer_home/`, `seller_products/`, `admin_seller_approval/`)

**Functions:**
- camelCase for all function names
- Prefer descriptive, action-oriented names (e.g., `observeCategories()`, `performSearch()`, `addSubcategory()`)
- Suspend functions for async operations (e.g., `suspend fun getCategories(): Result<List<Category>>`)
- Private functions prefixed with underscore convention not used; prefer private visibility modifier
- Factory functions in companion objects use `default()` or specific names (e.g., `User.default()`)

**Variables:**
- camelCase for all local and member variables
- Private backing properties use underscore prefix only for flows/state (e.g., `_homeState`, `_uiState`)
- Public exposed versions drop underscore (e.g., `val homeState = _homeState.asStateFlow()`)
- Boolean flags prefixed with `is` (e.g., `isLoading`, `isEmailVerified`, `isSearching`)
- Suffix collections with plural names (e.g., `categories`, `products`, `purchaseHistory`)

**Types:**
- PascalCase for all type names (classes, interfaces, data classes, sealed classes)
- Sealed interfaces for action/event types (e.g., `sealed interface CustomerHomeAction`)
- Suffix repository interfaces with `Repository` (e.g., `CategoryRepository`, `ProductRepository`)
- Enums in PascalCase (e.g., `UserRole`, `ProductStatus`, `Gender`)
- Type parameters use single uppercase letters (e.g., `<T>`) or descriptive names in generics

**Constants:**
- UPPER_SNAKE_CASE for top-level constants (e.g., `USER_COLLECTION`, `CATEGORIES_COLLECTION`, `PROFILE_IMAGES_FOLDER`)
- Located in `Constants.kt` files in util packages (e.g., `data/src/main/java/com/wenubey/data/util/Constants.kt`)

## Code Style

**Formatting:**
- Kotlin default formatting (no explicit formatter configured)
- 4-space indentation (standard Kotlin convention)
- Line wrapping at function/class declarations spanning multiple lines
- Trailing commas in multiline constructs follow Kotlin conventions

**Linting:**
- No explicit linter configuration found
- Android Studio/Kotlin IDE inspections used for code quality
- Build-in Kotlin compiler checks enforced

## Import Organization

**Order:**
1. Android framework imports (`androidx.*`)
2. Google/Firebase imports (`com.google.*`, `com.google.firebase.*`)
3. Third-party library imports (`io.coil.*`, `org.koin.*`, `io.ktor.*`, `kotlinx.*`, `timber.*`)
4. Project domain imports (`com.wenubey.domain.*`)
5. Project data imports (`com.wenubey.data.*`)
6. Project presentation imports (`com.wenubey.wenucommerce.*`)
7. Standard library imports (`java.*`, `kotlin.*`)

**Path Aliases:**
- No explicit path aliases configured in build or imports
- Imports use full package paths (e.g., `com.wenubey.domain.repository.CategoryRepository`)

## Error Handling

**Patterns:**
- Result<T> type used for async operations returning success/failure (e.g., `suspend fun getCategories(): Result<List<Category>>`)
- fold() extension used to handle Result success/failure cases (e.g., `.fold(onSuccess = { ... }, onFailure = { ... })`)
- catch() for Flow exceptions with error state update (e.g., `.catch { error -> _homeState.update { it.copy(errorMessage = ...) } }`)
- Timber logging for error tracking (e.g., `Timber.e(error, "Error message")`)
- safeApiCall() wrapper utility in `data/src/main/java/com/wenubey/data/util/SafeApiCall.kt` wraps suspend functions
- try-catch for specific exception handling in repositories (e.g., deserialization errors)
- Error messages stored in state as nullable String fields (e.g., `errorMessage: String? = null`)

## Logging

**Framework:** Timber for structured logging

**Patterns:**
- Error logs: `Timber.e(error, "Descriptive message")` or `Timber.e(exception, "Context")`
- Debug logs: `Timber.d("Status message")` for operation tracking
- Logging in repositories at operation boundaries (create, update, delete, read)
- Logging in flow listeners and async callbacks
- Error deserialization failures logged with context: `Timber.e(e, "Failed to deserialize category document: ${doc.id}")`
- No verbose logging of sensitive data

## Comments

**When to Comment:**
- Complex business logic requiring explanation (e.g., comments explaining search debounce strategy)
- Non-obvious workarounds or temporary fixes (prefix with TODO or FIXME)
- State management separating different concerns (e.g., "Search filter sheet state (separate from browse category selection)")
- Function-level comments for public or complex functions

**JSDoc/TSDoc:**
- Minimal usage; mostly absent from codebase
- KDoc style available but not heavily used
- Comments on public repository interfaces not standardized
- Comments in ViewModel classes for complex operations (e.g., search debounce explanation)

## TODO/FIXME Comments

**Patterns found:**
- `// TODO` used for incomplete features (e.g., `// TODO: Add to cart`)
- `// TODO Refactor Later` for technical debt (e.g., refactor screens needing component extraction)
- `//TODO add document validation with AI` for feature expansion
- `// TODO refactor this for adding more admins` for extensibility
- Comments appear throughout codebase indicating active development

## Function Design

**Size:** Functions prefer focused responsibility; observe callbacks and repository methods are single-purpose

**Parameters:**
- Named parameters for clarity (e.g., `onSuccess = { ... }`, `onFailure = { ... }`)
- Destructuring in lambda parameters (e.g., `{ categoryId -> ... }` for sealed class variants)
- Default parameter values common in state objects and composables
- No varargs patterns observed

**Return Values:**
- Suspend functions return Result<T> or Unit
- Composable functions return Unit implicitly
- Flow returns for observable state (e.g., `Flow<List<Category>>`)
- StateFlow for viewmodel exposed state

## Module Design

**Exports:**
- Repositories exported as interfaces in domain layer
- Implementations in data layer
- ViewModels instantiated via Koin DI
- Composables exported at package level for reuse

**Barrel Files:**
- No barrel files observed
- Each feature has separate directories (e.g., `customer_home/`, `seller_products/`)
- Navigation objects in `navigation/AppNavigationObjects.kt`

## State Management Architecture

**State Pattern (MVVM-U):**
- State: data class with immutable properties (e.g., `CustomerHomeState`)
- Actions: sealed interface defining user actions (e.g., `CustomerHomeAction`)
- ViewModel: handles state mutations and business logic
- UI: Composables collect state via `collectAsStateWithLifecycle()`

**StateFlow Usage:**
- Private mutable backing field: `private val _homeState = MutableStateFlow(initialState)`
- Public exposed readonly: `val homeState: StateFlow<State> = _homeState.asStateFlow()`
- Updates via: `_homeState.update { it.copy(field = newValue) }`

**Flow Operators:**
- debounce() for search input throttling (e.g., `searchQueryFlow.debounce(300L)`)
- distinctUntilChanged() to filter duplicate values
- collectLatest() for reactive updates, canceling previous collections
- catch() for error handling in flows
- fold() on Result types for success/failure handling

## Dependency Injection

**Framework:** Koin (DI container)

**Patterns:**
- Module definition with `val module = module { ... }`
- Single instances: `single { ... }`
- Factory instances: `factory { ... }`
- ViewModel registration: `viewModelOf(::ClassName)` or `viewModel { ... }`
- Binding to interfaces: `.bind<InterfaceType>()`
- Qualifier support: `get(named("qualifier"))`
- Named qualifiers for device-specific injection (e.g., `named("deviceId")`)

## Data Classes

**Patterns:**
- All domain models are data classes with default parameters
- Serializable annotation for models used with Firebase/Ktor (`@Serializable`)
- Companion objects for factory functions (e.g., `companion object { fun default(): User = ... }`)
- toMap() extension functions for Firestore conversion

## Sealed Interfaces/Classes

**Action Types:**
- Used for typed event handling in ViewModels
- Sealed interface inheritance for type safety
- Both object and data class variants (e.g., `data object OnClearSearch`, `data class OnCategorySelected(...)`)
- onAction(action) handler pattern in ViewModels

---

*Convention analysis: 2026-02-19*
