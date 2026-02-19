# External Integrations

**Analysis Date:** 2026-02-19

## APIs & External Services

**Google Services:**
- Google Sign-In (OAuth 2.0)
  - SDK/Client: `com.google.android.gms:play-services-auth` (21.3.0)
  - Auth SDK: `com.google.android.libraries.identity.googleid:googleid` (1.1.1)
  - Auth: `BuildConfig.GOOGLE_ID_WEB_CLIENT` environment variable
  - Implementation: `DataModule.kt` line 69-81 - Creates `GetGoogleIdOption` with nonce
  - Used in: `AuthRepositoryImpl.kt` - OAuth credential handling
  - CredentialManager: Android unified credential management API

**TMDB API (Movie/Media Database):**
- Purpose: External movie/media information source
- SDK/Client: HTTP client integration (Ktor)
- Auth: `BuildConfig.TMDB_API_KEY` environment variable
- Implementation: Configured in app-level build config
- Status: Dependency configured but usage pattern not directly visible (likely future feature or data enrichment)

## Data Storage

**Databases:**
- **Firestore (Cloud Firestore)**
  - Type: NoSQL cloud database
  - Provider: Google Firebase
  - Connection: `Firebase.firestore` singleton (DI via Koin)
  - Location: `DataModule.kt` line 47
  - Client: Firebase Firestore SDK (`com.google.firebase:firebase-firestore`)
  - Collections:
    - `USERS_COLLECTION` - User profiles and authentication data
    - `PRODUCTS_COLLECTION` - Product catalog
    - `CATEGORIES_COLLECTION` - Product categories
    - `TAGS_COLLECTION` - Product tags
    - `REVIEWS_SUBCOLLECTION` - Product reviews (nested under PRODUCTS)
  - Implementation: `FirestoreRepositoryImpl.kt`, `AuthRepositoryImpl.kt`, `ProfileRepositoryImpl.kt`
  - References: `data/src/main/java/com/wenubey/data/util/Constants.kt`

**Local Database:**
- **Room (SQLite)**
  - Type: Local SQLite database
  - Location: Schema directory at `data/schemas/`
  - Status: Configured in dependencies but no Dao/Database implementations found in current codebase
  - Likely reserved for offline-first features or caching

**File Storage:**
- **Firebase Storage**
  - Type: Cloud object storage
  - Provider: Google Firebase
  - Connection: `FirebaseStorage.getInstance()` singleton (DI via Koin)
  - Location: `DataModule.kt` line 49
  - Client: Firebase Storage SDK (`com.google.firebase:firebase-storage-ktx`)
  - Folders:
    - `profile_images/` - User profile photos
    - `product_images/` - Product images
    - `seller_documents/` - Seller verification documents (tax, business license, identity)
  - Implementation: `ProfileRepositoryImpl.kt` (profile photo upload), `FirestoreRepositoryImpl.kt` (document handling)
  - File naming: Profile images use pattern `{userId}_profile_image.jpeg`

**Caching:**
- DataStore Preferences (`androidx.datastore:datastore-preferences`)
  - Location: `data/build.gradle.kts`, `app/build.gradle.kts`
  - Use case: Local preference storage (e.g., notification settings)
  - Implementation: `NotificationPreferences.kt` - Notification banner visibility state

## Authentication & Identity

**Auth Provider:**
- Google Firebase Authentication
  - Type: Multi-provider authentication
  - Implementation: `AuthRepositoryImpl.kt` lines 1-250+
  - Supported methods:
    - Email/Password sign-in and registration
    - Google OAuth (via Google Sign-In)
    - CredentialManager integration for password auto-fill
  - Client: Firebase Auth SDK (`com.google.firebase:firebase-auth`)
  - Location: `DataModule.kt` line 46 - `Firebase.auth` singleton
  - Current User State:
    - Exposed as `StateFlow<User?>` in `AuthRepository`
    - Synchronized with Firestore user document via listeners (`startUserListener`)
    - Updated on auth state changes
  - OAuth Flow:
    - `GetGoogleIdOption` created with Web Client ID from BuildConfig
    - Nonce generated with SecureRandom (60 bytes, Base64 URL-safe)
    - Credential response handled in sign-in/sign-up methods
    - Token exchanged for Firebase credential via `GoogleAuthProvider.getCredential()`

**Email Verification:**
- Firebase Auth email verification built-in
- UI Component: `EmailVerificationNotificationBar` - Notification banner on all tab screens
- Location: `app/src/main/java/com/wenubey/wenucommerce/core/email_verification_banner/`

## Serverless Backend

**Firebase Cloud Functions:**
- SDK: `com.google.firebase:firebase-functions-ktx`
- Location: `DataModule.kt` line 51 - `Firebase.functions` singleton
- Implementation: `FirestoreRepositoryImpl.kt`
- Callable functions:
  - `handleDeviceLogin` - Called at `FirestoreRepositoryImpl.kt` line 57
    - Purpose: Track device login/authentication
    - Parameters: `{"uuid": userUid, "newDevice": deviceData}`
    - Uses `DeviceInfoProvider.getDeviceData()` to capture device details
    - Response handled with success/failure listeners

## Messaging & Notifications

**Firebase Cloud Messaging (FCM):**
- Type: Push notification service
- SDK: `com.google.firebase:firebase-messaging-ktx`
- Location: `DataModule.kt` line 50 - `FirebaseMessaging.getInstance()` singleton
- Service Implementation: `MessagingService` (custom FCM service)
  - Location: `app/src/main/AndroidManifest.xml` lines 34-40
  - Exported: false (internal service only)
  - Intent Filter: `com.google.firebase.MESSAGING_EVENT`
  - File: `app/src/main/java/com/wenubey/wenucommerce/notification/MessagingService.kt`
- Local Preferences: `NotificationPreferences.kt` - User notification settings and banner visibility

## Environment Configuration

**Required env vars:**
- `GOOGLE_ID_WEB_CLIENT` - OAuth 2.0 Web Client ID (required for Google Sign-In)
- `TMDB_API_KEY` - TMDB API authentication key (app module)
- `ADMIN_EMAIL` - Admin email for role-based access control (data module)

**Secrets location:**
- `local.properties` - Local development file (git-ignored, load-time variable injection)
- `google-services.json` - Firebase configuration file
  - Location: `app/google-services.json`
  - Applied by `com.google.gms.google-services` plugin (version 4.4.2)

## System Integrations

**Android System Services:**
- Credential Manager (`androidx.credentials.CredentialManager`)
  - Purpose: Unified password and OAuth credential management
  - Supports: Password auto-fill, biometric auth delegation
  - Implementation: `AuthRepositoryImpl.kt` lines 4-14 (credential operations)

**Location Services:**
- Implementation: `LocationServiceImpl.kt` (data module)
- Purpose: Device/IP-based location detection
- Data Model: `IpLocationDto.kt` in `data/src/main/java/com/wenubey/data/model/`

**Device Information:**
- `DeviceIdProvider.kt` - Unique device identifier generation
- `DeviceInfoProvider.kt` - Device metadata collection
  - Captures: Device type, OS version, app version, etc.
  - Used in: `handleDeviceLogin` Cloud Function calls
  - Location: `data/src/main/java/com/wenubey/data/util/`

## Permissions Required

**Media Access:**
- `READ_MEDIA_IMAGES` - Access image files (for profile/product uploads)
- `READ_EXTERNAL_STORAGE` - Fallback media access
- `READ_MEDIA_VISUAL_USER_SELECTED` - User-selected media (Android 13+)

**Account & Network:**
- `GET_ACCOUNTS` - Access device accounts (for OAuth)
- `INTERNET` - Network communication

## Admin & Special Features

**Admin Panel:**
- Identified by email in BuildConfig.ADMIN_EMAIL
- Implementation: `AdminUtils.kt` checks `BuildConfig.ADMIN_EMAIL`
- Special Admin Access:
  - `AdminTabScreen.kt` - Admin dashboard
  - `AdminProductsScreen.kt` - Admin product management
  - Role assignment via `FirestoreRepositoryImpl.kt` at onboarding completion

---

*Integration audit: 2026-02-19*
