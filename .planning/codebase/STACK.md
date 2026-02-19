# Technology Stack

**Analysis Date:** 2026-02-19

## Languages

**Primary:**
- Kotlin 2.1.0 - Main application language
- Java 11 - Compilation target

**Build Configuration:**
- Source compatibility: Java 11
- Target compatibility: Java 11
- JVM target: 11

## Runtime

**Environment:**
- Android SDK (Compile SDK 35, Min SDK 24, Target SDK 35)
- Android Gradle Plugin (AGP) 8.7.3

**Package Manager:**
- Gradle with Kotlin DSL
- Lockfile: Version catalog in `gradle/libs.versions.toml`

## Frameworks

**Core UI & Composition:**
- Jetpack Compose (2025.01.00 BOM)
  - `androidx.ui` - UI components
  - `androidx.ui-graphics` - Graphics functionality
  - `androidx.ui-tooling` & `androidx.ui-tooling-preview` - Development tools
  - `androidx.material3` - Material Design 3 components
  - `androidx.material-icons-extended` - Extended icon set
  - Location: `app/src/main`, `app/src/main/java/com/wenubey/wenucommerce/`

**Navigation:**
- Jetpack Navigation Compose (2.8.5) - In-app navigation routing
  - Location: `app/src/main/java/com/wenubey/wenucommerce/navigation/`

**Activity & Lifecycle:**
- androidx.activity:activity-compose (1.10.0) - Compose activity integration
- androidx.lifecycle:lifecycle-runtime-ktx (2.8.7) - Lifecycle awareness
- androidx.lifecycle:lifecycle-viewmodel-compose - ViewModel integration with Compose
- androidx.core-splashscreen (1.0.1) - Material splash screen API

**Dependency Injection:**
- Koin (4.0.1 BOM) - Service locator & DI framework
  - `koin-core` - Core functionality
  - `koin-android` - Android integration
  - `koin-androidx-compose` - Compose integration
  - Location: `app/src/main/java/com/wenubey/wenucommerce/di/`

**Networking:**
- Ktor Client (2.3.12) - HTTP client library
  - `ktor-client-core` - Core HTTP functionality
  - `ktor-client-okhttp` - OkHttp engine
  - `ktor-client-content-negotiation` - Content type handling
  - `ktor-serialization-kotlinx-json` - JSON serialization
  - `ktor-client-logging` - HTTP logging
  - Location: `data/src/main/java/com/wenubey/data/`

**Image Loading:**
- Coil 3.0.4 - Image loading library for Compose
  - `coil-compose` - Compose integration
  - `coil-network-okhttp` - OkHttp network transport
  - Used in: UI screens and components

**Data Management:**
- Jetpack DataStore (1.1.2) - Preferences storage
  - `datastore-preferences` - Key-value preferences
  - Location: Used in `NotificationPreferences` and preference modules

**Database:**
- Room (2.6.1) - SQLite ORM
  - `room-compiler` - Code generation (KSP processor)
  - `room-runtime` - Runtime library
  - `room-paging` - Paging support
  - `room-ktx` - Kotlin extensions
  - `room-testing` - Testing utilities
  - Location: `data/build.gradle.kts` declares schema directory at `data/schemas/`

**Paging:**
- androidx.paging (3.3.5) - List pagination
  - `paging-runtime-ktx` - Core paging
  - `paging-compose` - Compose integration
  - Used in product and review listings

**Serialization:**
- kotlinx serialization - Data serialization framework
  - Plugin: `org.jetbrains.kotlin.plugin.serialization`
  - Used across domain models and DTOs

## Key Dependencies

**Critical:**
- Firebase BOM (33.8.0) - Google Firebase platform suite
  - Firebase Auth - User authentication
  - Firestore - Real-time NoSQL database
  - Firebase Storage - File storage
  - Firebase Cloud Functions - Serverless backend
  - Firebase Cloud Messaging - Push notifications
  - Location: `app/build.gradle.kts`, `data/build.gradle.kts`, `domain/build.gradle.kts`

**Google Services:**
- Play Services Auth (21.3.0) - Google Sign-In integration
- Google Identity (1.1.1) - Credential management and Google ID tokens
  - Location: Used in `DataModule.kt` for OAuth configuration

**Platform & System:**
- androidx.appcompat (1.7.0) - Backward compatibility
- android.material (1.12.0) - Material Design components
- androidx.core-ktx (1.15.0) - Core Kotlin extensions
- androidx.credentials (via CredentialManager) - Unified credential management

**Code Generation:**
- KSP (2.1.0-1.0.29) - Kotlin Symbol Processing
  - Plugins: `com.google.devtools.ksp`
  - Used for Room compiler and other annotation processing

**Logging:**
- Timber (5.0.1) - Logging facade
  - Location: `data/build.gradle.kts`
  - Initialized in `WenuCommerce.kt` with debug tree in debug builds

## Testing Dependencies

**Unit Testing:**
- JUnit 4.13.2 - Test framework
- Mockk 1.12.4 - Mocking library for Kotlin
- Mockito (5.12.0) - Traditional mocking
- mockito-kotlin (5.4.0) - Kotlin extensions for Mockito
- kotlinx-coroutines-test (1.9.0) - Coroutine testing
- Truth (1.0.1) - Assertions library
- core-testing (1.1.1) - Android arch component testing utilities
- logback-classic (1.5.16) - Logging implementation for tests

**Android Testing:**
- androidx.test.ext:junit (1.2.1) - Android JUnit extensions
- androidx.test.espresso:espresso-core (3.6.1) - UI testing framework
- androidx.test:runner (1.6.2) - Test runner
- Compose UI Testing:
  - androidx.ui-test-manifest - Test manifest
  - androidx.ui-test-junit4 - Compose testing with JUnit4

**Mocking:**
- ktor-client-mock (2.3.12) - Ktor mock HTTP client for testing

## Configuration

**Environment:**
- Configuration loaded from `local.properties` file (git-ignored)
- BuildConfig fields injected at compile-time:
  - `TMDB_API_KEY` - External API key (app module only)
  - `GOOGLE_ID_WEB_CLIENT` - OAuth Web Client ID (app and data modules)
  - `ADMIN_EMAIL` - Admin email address (data module only)

**Build Configuration:**
- Minification: Disabled in release builds (isMinifyEnabled = false)
- ProGuard rules: `proguard-rules.pro`
- Kotlin code style: "official"
- AndroidX enabled with non-transitive R classes
- Clear text traffic enabled for development

**Gradle Configuration:**
- JVM args: `-Xmx2048m -Dfile.encoding=UTF-8`
- Version catalog: `gradle/libs.versions.toml` (centralized dependency versioning)

## Platform Requirements

**Development:**
- Android Studio (implied by `.idea/` configuration)
- JDK 11+ (for compilation)
- Gradle 8.0+ (via wrapper)
- Kotlin 2.1.0 plugin

**Production:**
- Android 7.0+ (API 24) minimum
- Android 15+ (API 35) target
- Google Play Services
- Firebase backend

---

*Stack analysis: 2026-02-19*
