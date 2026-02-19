# Testing Patterns

**Analysis Date:** 2026-02-19

## Test Framework

**Runner:**
- JUnit 4 (version 4.13.2)
- AndroidJUnit4 test runner for instrumented tests
- Config: Implicit; uses test runner via `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `app/build.gradle.kts`

**Assertion Library:**
- JUnit assertions (e.g., `assertEquals()`)
- Google Truth (version 1.0.1) - available for assertion in androidTest

**Run Commands:**
```bash
./gradlew test                 # Run all unit tests (local)
./gradlew connectedAndroidTest # Run instrumented tests (device/emulator)
./gradlew build                # Build and run test phase
./gradlew testDebug            # Run debug variant tests
```

## Test File Organization

**Location:**
- Unit tests: `{module}/src/test/java/com/{package}/`
- Instrumented tests: `{module}/src/androidTest/java/com/{package}/`
- Test files are co-located with source in parallel directory structure

**Naming:**
- Format: `{ClassName}Test.kt` or `{FeatureName}Test.kt`
- Example paths:
  - `app/src/test/java/com/wenubey/wenucommerce/ExampleUnitTest.kt`
  - `app/src/androidTest/java/com/wenubey/wenucommerce/ExampleInstrumentedTest.kt`
  - `data/src/test/java/com/wenubey/data/ExampleUnitTest.kt`
  - `domain/src/test/java/com/wenubey/domain/ExampleUnitTest.kt`

**Structure:**
```
{module}/
├── src/
│   ├── main/java/
│   │   └── com/wenubey/{package}/
│   │       └── FeatureClass.kt
│   ├── test/java/
│   │   └── com/wenubey/{package}/
│   │       └── FeatureClassTest.kt
│   └── androidTest/java/
│       └── com/wenubey/{package}/
│           └── FeatureClassInstrumentedTest.kt
```

## Test Structure

**Suite Organization:**
```kotlin
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.wenubey.wenucommerce", appContext.packageName)
    }
}
```

**Patterns:**
- Class-level test suite with multiple @Test methods
- @RunWith(AndroidJUnit4::class) annotation for instrumented tests
- @Test annotation for individual test methods
- Assertions using JUnit assertEquals, assertTrue, assertFalse, etc.
- No visible setup/teardown patterns in existing placeholder tests

## Mocking

**Framework:** MockK (version 1.12.4) and Mockito (version 5.12.0)

**Dependencies Available:**
```
testImplementation(libs.mockk)
testImplementation(libs.mockito.core)
testImplementation(libs.mockito.kotlin)
testImplementation(libs.ktor.client.mock)
```

**Patterns:**
Not yet demonstrated in codebase (placeholder tests only). Based on available dependencies, use:

```kotlin
// MockK example (for Kotlin-first mocking)
val mockRepository = mockk<CategoryRepository>()
coEvery { mockRepository.getCategories() } returns Result.success(emptyList())

// Mockito example (for Java-interop mocking)
val mockRepository = mock<ProductRepository>()
`when`(mockRepository.getCategories()).thenReturn(Result.success(emptyList()))

// Mockito Kotlin example (for Kotlin convenience)
val mockRepository = mock<ProductRepository>()
whenever(mockRepository.getCategories()).thenReturn(Result.success(emptyList()))
```

**What to Mock:**
- Repository implementations (use interfaces)
- External dependencies (Firebase, API clients)
- Dispatchers (use DispatcherProvider injection)

**What NOT to Mock:**
- Domain models and data classes
- Mapper functions
- State/Action sealed types
- Local Flow transformations

## Fixtures and Factories

**Test Data:**
Not yet established in codebase. Pattern to follow based on domain models:

```kotlin
object TestDataFactory {
    fun createCategory(
        id: String = "test-id",
        name: String = "Test Category"
    ): Category = Category(id = id, name = name)

    fun createUser(
        uuid: String = "test-uuid",
        role: UserRole = UserRole.CUSTOMER
    ): User = User(uuid = uuid, role = role)
}

// Usage in tests
val testCategory = TestDataFactory.createCategory()
```

**Location:**
- Should be placed in `src/test/java/com/wenubey/data/util/TestDataFactory.kt` (per module)
- Or in package-level test utilities directory

## Coverage

**Requirements:** Not enforced; no coverage configuration found

**View Coverage:**
```bash
./gradlew testDebug jacocoTestDebugReport  # Requires Jacoco plugin setup (not currently configured)
```

## Test Types

**Unit Tests:**
- Scope: Individual functions, ViewModels, Repositories without external dependencies
- Approach: Use mocked dependencies, test state mutations, verify business logic
- Location: `src/test/java/`
- Example targets:
  - ViewModel action handlers
  - Repository methods with mocked Firebase
  - Utility functions
  - State calculation logic

**Integration Tests:**
- Scope: Multiple components working together (ViewModel + Repository)
- Approach: Mock external services (Firebase), test data flow through layers
- Location: `src/test/java/` or `src/androidTest/java/`
- Example targets:
  - ViewModel observation of repository changes
  - Repository transaction handling
  - Flow pipeline transformations

**Instrumented Tests (Android Tests):**
- Scope: Tests requiring Android framework or device context
- Framework: AndroidJUnit4
- Location: `src/androidTest/java/`
- Example targets:
  - Compose UI testing (via androidx.compose.ui.test)
  - SharedPreferences/DataStore integration
  - Database operations (Room)

**E2E Tests:**
- Status: Not used; manual testing assumed for user flows

## Async Testing

**Coroutines:**
Pattern to follow for testing suspend functions:

```kotlin
// Option 1: Using runTest (recommended)
@Test
fun testGetCategories() = runTest {
    val mockRepository = mockk<CategoryRepository>()
    coEvery { mockRepository.getCategories() } returns Result.success(emptyList())

    val result = mockRepository.getCategories()

    assertEquals(Result.success(emptyList()), result)
}

// Option 2: Using MainDispatcherRule (for ViewModels)
@get:Rule
val mainDispatcherRule = MainDispatcherRule()

@Test
fun testStateUpdates() = runTest {
    val viewModel = CustomerHomeViewModel(mockCategoryRepo, mockProductRepo, mockDispatcher)
    // Test async operations
}
```

**Available Dependency:**
```
testImplementation(libs.kotlinx.coroutines.test)
```

## Testing State Management (MVVM-U)

**ViewModel Testing Pattern:**

```kotlin
@Test
fun testCategorySelection() = runTest {
    val mockRepository = mockk<CategoryRepository>()
    coEvery { mockRepository.observeCategories() } returns flowOf(testCategories)

    val viewModel = CustomerHomeViewModel(mockRepository, mockProductRepo, dispatcherProvider)

    // Act
    viewModel.onAction(CustomerHomeAction.OnCategorySelected("cat-1"))

    // Assert
    val state = viewModel.homeState.first()
    assertEquals("cat-1", state.selectedCategoryId)
}
```

**State Assertions:**
- Collect state via `.first()` or `.take(n)`
- Assert state properties directly
- Use `collectAsStateWithLifecycle()` in real UI, `collectAsState()` in tests

## Test Fixtures Available

**Core Testing:**
```
testImplementation(libs.core.testing)        # Android arch testing utilities
testImplementation(libs.room.testing)         # Room database testing
testImplementation(libs.ktor.client.mock)     # Ktor mock client
```

**Logback Configuration:**
```
testRuntimeOnly(libs.logback.classic)        # For logging in tests
```

## Repository Testing Pattern

```kotlin
@Test
fun testGetCategories() = runTest {
    // Setup
    val mockFirestore = mockk<FirebaseFirestore>()
    val mockAuth = mockk<FirebaseAuth>()
    val mockDispatcher = DispatcherProviderImpl()

    val repository = CategoryRepositoryImpl(
        firestore = mockFirestore,
        auth = mockAuth,
        storage = mockk(),
        dispatcherProvider = mockDispatcher
    )

    // Act & Assert
    val result = repository.getCategories()
    result.fold(
        onSuccess = { categories ->
            assertTrue(categories.isEmpty())
        },
        onFailure = { exception ->
            fail("Should succeed")
        }
    )
}
```

## Current Test State

**Placeholder Tests Only:**
- `app/src/test/java/com/wenubey/wenucommerce/ExampleUnitTest.kt` - Basic arithmetic test
- `app/src/androidTest/java/com/wenubey/wenucommerce/ExampleInstrumentedTest.kt` - Context verification
- `data/src/test/java/com/wenubey/data/ExampleUnitTest.kt` - Not implemented
- `domain/src/test/java/com/wenubey/domain/ExampleUnitTest.kt` - Not implemented

**Gap:** No actual feature tests exist; testing infrastructure is set up but tests need to be written.

## Build Integration

**Gradle Configuration:**
- Test dependencies organized in `gradle/libs.versions.toml`
- Test implementations in each module's `build.gradle.kts`
- AndroidJUnit4 runner configured via `testInstrumentationRunner`

**Test Execution:**
Tests are part of standard Gradle build lifecycle:
1. `./gradlew test` - unit tests only
2. `./gradlew connectedAndroidTest` - instrumented tests (requires device/emulator)
3. `./gradlew build` - complete build including test phase

---

*Testing analysis: 2026-02-19*
