# Phase 0 — Test Backfill

## 🔖 Yeni oturumda devam (mola sonrası)

**Şu an neredeyiz**: Wave 2C TAMAMLANDI ✅. 12/12 Firestore-coupled repo bitti + Storage emulator kuruldu. Pilot pattern çalıştı, 9 repo'da bug yok, 4 prod bug bulundu (TB-6 not, TB-7 not, TB-8 fix, TB-9 not).

**Çözülen iş**: ProductReviewRepositoryImpl emulator transaction race'i ÇÖZÜLDÜ.
- **Root cause**: Önceki test'te `seedProduct` ürünü `averageRating`/`reviewCount` alanları olmadan yazıyordu. `submitReview` transaction'ı önce `transaction.get(productRef)` ile okuyor (doc var, alanlar yok), sonra `transaction.update(productRef, mapOf("averageRating" to ..., "reviewCount" to ...))` çağırıyor. Bu update mevcut olmayan alanlara yazıyor — emulator'da bu "Can't update a document that doesn't exist" hatasını fırlatıyor (Firestore transaction update'ı seed doc'u fields-missing sebebiyle reject ediyor).
- **Fix**: seedProduct fonksiyonu artık `averageRating: 0.0` + `reviewCount: 0` ile başlatıyor. Production kodda değişiklik yok. 8 test yeşil.

**Sıradaki**: Wave 3D (admin VMs), Wave 3E (core/cross-cutting VMs), Wave 4 (Compose UI).

**Çalıştırma prereq** (tekrar başlarken):
```bash
# Terminal A — Firebase emulator
cd /Users/wenubey/AndroidStudioProjects/WenuCommerce
firebase emulators:start --only firestore,auth,functions

# Terminal B — AVD
emulator -avd Medium_Phone_API_35 &
adb devices  # "emulator-5554 device" görmeli

# Terminal C — test
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.wenubey.data.repository.<TestClass>
```

**Wave 2C TAMAM** ✅: 12/12 (Discount + Tag + Category + ProductReview + Auth + Firestore + Address + Wishlist + Cart + Product + Profile + Payment) + Storage backfill 2 sınıf, **132 test yeşil**.
**Tüm test toplamı**: 479 unit + 132 instrumentation = **611 test**.

**Storage emulator kuruldu** (commit ile):
- `storage.rules` (emulator-permissive)
- `firebase.json` → storage block + port 9199
- `FirebaseEmulator.kt` → `setStorageBucket("wenucommerce.appspot.com")` + `useEmulator` + `clearStorage()` helper
- Restart komutu: `firebase emulators:start --only firestore,auth,functions,storage`

**Yeni Storage testleri**:
- `ProductRepositoryImplStorageEmulatorTest` — 4 test (upload → download URL + bytes round-trip, overwrite, delete, delete-missing failure)
- `ProfileRepositoryImplStorageEmulatorTest` — 4 test (customer onboarding photo upload, seller documents 3-folder upload, updateSellerDocument replace + TB-8 pin, deleteSellerData recursive wipe)
- `FirestoreRepositoryImplEmulatorTest` → +1 test (onboardingComplete local file URI uploads via updateProfilePhoto, 11 → 12 test)

**Wave 3D-3E + Wave 4 hâlâ bekliyor** (3D admin, 3E core/cross, 4 Compose UI).

---



> **Amaç**: Tüm mevcut koda (Phase 5'e kadar yazılmış her şey) ve gelecekteki her phase'in dokunacağı koda **unit + UI test** kapsaması getirmek. Bulunan bug'lar aynı turda düzeltilir.
>
> **Yaklaşım**: Risk × hız sırasına göre 4 dalga. Her dalga otonom loop'ta yürür (`CLAUDE.md` → "Autonomous test-driven mode"). Bir item başına 1 commit. Suite yeşil → tik koy → bir sonrakine geç.
>
> **Bug policy**: Backfill sırasında bulunan bug **aynı turda düzeltilir** (kullanıcı kararı). Düzeltme ayrı commit (`fix(backfill/<feature>): …`). Düzeltme küçük değilse veya mimari karar gerektiriyorsa kullanıcıya sor.
>
> **Yeni phase başlatma kuralı**: Yeni bir phase başlamadan önce, dokunulacak feature'ların backfill testleri **yeşil olmak zorunda**. Eğer değilse o feature önce backfill'e alınır.

## Konvansiyonlar

- **Commit prefix**: `test(backfill/<feature>): …`, `fix(backfill/<feature>): …`, `chore(backfill): …`
- **Test dosya konumu**: kaynakla aynı paket; `src/test/java/.../FooTest.kt` (JVM) veya `src/androidTest/java/.../FooUiTest.kt` (Compose/instrumented)
- **Fakes**: `src/test/java/.../fakes/Fake<Repository>.kt` — gerçek Firebase'e dokunmaz
- **Compose UI test**: `createComposeRule()` + Koin yerine ManuelOverride / parametre injection
- **Bug bulunca**: ya düzelt + `fix(...)` commit, ya da büyükse `PRODUCT_BUGS_AND_GAPS.md`'ye not + test'i `@Ignore("bug: <satır>")` ile bırak → kullanıcıya rapor
- **Test failure'ında 3 kez tıkanırsan**: dur, kullanıcıya sor

## Smoke (önce bunu doğrula)

- [x] `./gradlew testDebugUnitTest` (3 modül) yeşil — baseline (`chore(backfill): bootstrap test infra`)
- [x] Eklenen test deps'leri sorunsuz resolve oluyor (mockk-android, turbine, truth, coroutines-test, core-testing)
- [ ] `./gradlew :app:assembleDebug` yeşil (Dalga 1 sonunda doğrulanacak)

---

## Dalga 1 — `:domain` (saf Kotlin, en hızlı, en yüksek ROI)

### 1A · Util & saf fonksiyonlar
- [x] `util/SearchKeywordsGenerator` — 14 test (split/case/dedup/punct/whitespace + 2 bug pin). **Bug TB-1 bulundu** (Turkish char stripping).
- [x] `util/DocumentType` — name + ordering pin (persistence safety)
- [x] `util/AuthProvider` — name + ordering pin (persistence safety)

### 1B · Modeller (validasyon / hesap / default davranış)
- [~] `model/discount/DiscountCode` — pure data carrier, no behavior to test (skip)
- [~] `model/discount/CouponValidationResult` — pure data carrier (skip)
- [x] `model/discount/DiscountType` — name pin; replaced false-positive `DiscountCalculationTest` placeholders
- [~] `model/order/Order` + `OrderItem` — totals stored, not computed; no behavior (skip)
- [x] `model/order/OrderStatus` — name + displayName pins
- [x] `model/order/ShippingAddress` — label getter + toMap contract
- [x] `model/product/Product` (+ Variant, Image, Shipping) — defaults, toMap, enum-by-name, recursive nested map
- [x] `model/product/ProductStatus` / `ProductCondition` / `ShippingType` — name pins
- [x] `model/product/Category` / `Subcategory` / `Tag` — defaults + toMap + recursive subcategory mapping
- [x] `model/product/ProductReview` — defaults + toMap
- [x] `model/user/User` + `UserRole` — pinned User() vs User.default() businessInfo divergence
- [~] `model/user/AdminDashboardData` / `UserManagementData` — TODO'lu, unused aggregates (skip)
- [x] `model/onboard/BusinessInfo` + `BusinessType` + `VerificationStatus` — name pins + toMap null-to-empty contract
- [x] `model/CartItem` — qty default 1, isProductDeleted false
- [x] `model/WishlistItem` — defaults
- [x] `model/Purchase` (defaults), `Device` (toMap), `IpLocation` (all-null defaults), `Gender` (name pin), `OrderItem` (qty default 1, lineTotal stored not computed)

### 1C · Auth result types
- [x] `auth/SignInResult` — variants + sealed exhaustive when
- [x] `auth/SignUpResult` — variants + sealed exhaustive when

### Dalga 1 çıkış kapısı
- [x] `./gradlew :domain:testDebugUnitTest` → yeşil (**93 test**)
- [ ] Coverage raporu (en az satır kapsamı raporu) — bonus (Dalga 4 sonu)

---

## Dalga 2 — `:data` (Room DAO + Repository impl)

### 2A · Room DAOs (in-memory + Robolectric, JVM)
- [x] `local/converter/RoomTypeConverters` — round-trip + malformed-JSON fallback (14 test)
- [x] `local/dao/CartItemDao` — 11 test
- [x] `local/dao/AddressDao` — 8 test
- [x] `local/dao/WishlistItemDao` — 9 test
- [x] `local/dao/PendingOperationDao` — 11 test
- [x] `local/dao/UserDao` — 6 test (single-user invariant + json defaults)
- [x] `local/dao/CategoryDao` — 6 test
- [x] `local/dao/OrderDao` — 7 test
- [x] `local/dao/ProductDao` — 13 test (status filters, search-by-title/category/keywords, seller view)

### 2B · Repository implementations (kısmi)
**Easy wins (testlendi):**
- [x] `DispatcherProviderImpl` — interface defaults
- [x] `model/IpLocationDto` — pure mapper + JSON deserialization
- [x] `LocationServiceImpl` — Ktor MockEngine (success, 5xx, malformed)
- [x] `NotificationPreferences` — gerçek DataStore (Robolectric temp file)
- [x] `DiscountRepositoryTest` placeholder dosyası silindi (sahte coverage)

**Firestore-coupled (Wave 2C — emulator integration başladı):**
Emulator setup tamam: `firebase.json` emulator bloğu + `data/src/androidTest/.../FirebaseEmulator.kt` shared base (useEmulator + clearFirestore + clearAuth + signInAnonymous).

- [x] `DiscountRepositoryImpl` — 7 emulator test (CRUD + observe + snapshot listener)
- [x] `TagRepositoryImpl` — 8 emulator test (anonymous auth + tag CRUD + dedup)
- [x] `CategoryRepositoryImpl` — 8 emulator test (Room cache + transactional subcategory)
- [x] `ProductReviewRepositoryImpl` — 8 emulator test (submit + duplicate guard + avg recompute + observe + helpful + visibility). Önceki "transaction race": gerçek sebep seed product'un `averageRating`/`reviewCount` alanlarını içermemesiydi — transaction.update mevcut olmayan alanlara yazınca emulator reject ediyordu.
- [x] `AuthRepositoryImpl` — 11 emulator test (signUp/signIn happy + duplicate + wrong pw, logOut/deleteAccount clear state, currentUser flow propagation, setCurrentUserAfterOnboarding, refresh/isAuth/isPhone/isEmailVerified). **TB-6 not** (prod): `firebaseAuth.addAuthStateListener` init'te kaydediliyor ama hiç remove edilmiyor — singleton DI'da pratikte sızıntı yok, ama test'lerde yeni instance başına listener birikir. Test çözümü: shared `WenuCommerceDatabase` (@BeforeClass / @AfterClass) kullanarak stale listener'ların kapalı db'ye yazmasını önlemek.
- [x] `ProfileRepositoryImpl` — 6 emulator test (onboarding CUSTOMER builds user without businessInfo, onboarding SELLER builds user with PENDING businessInfo, onboarding fails without auth, updateSellerBusinessInfo writes nested map, cancelSellerApplication flips to CANCELLED with previousStatus preserved, cancelSellerApplication without prior status). Storage path'leri (uploadProfilePhoto, uploadSellerDocument, deleteSellerData, updateSellerDocument) atlandı — Storage emulator yok.
- [x] `ProductRepositoryImpl` — 26 emulator test. Seller CRUD (createProduct slug + searchKeywords + auth gate, updateProduct recomputes keywords + totalStockQuantity, submitForReview, archive↔unarchive). Seller queries (observeSellerProducts, getSellerProducts Firestore→Room cache). Customer (observeActiveProductsByCategory, +Subcategory with blank fallback, getProductById Room hit / Firestore fallback / missing). Storefront ACTIVE-only filter. Admin (observeProductsByStatus, approveProduct transaction, suspendProduct, adminUpdateProduct). Counters/stock (incrementViewCount, decrementStock variant + total + purchaseCount, out-of-stock failure, unknown variant failure). addProductToSellerDocument arrayUnion idempotency. Search (blank query empty, ACTIVE-only + multi-token AND, searchAllProducts includes DRAFT/PENDING, categoryId filter).
- [x] `CartRepositoryImpl` — 12 emulator test (addToCart new/existing branches, updateQuantity positive + zero-falls-through-to-remove, removeFromCart, clearCart no-queue, restoreCartItem with addedAt preservation, observeUniqueProductCount, getCartItem, syncAddToCart/syncUpdateQuantity/syncRemoveFromCart Firestore round-trips). Test infra: `androidx.work:work-testing` eklendi + `WorkManagerTestInitHelper.initializeTestWorkManager` @BeforeClass'ta — `SyncWorker.enqueue` inline kullanılıyor.
- [x] `WishlistRepositoryImpl` — 9 emulator test (toggle add/remove dual-write, anonymous toggle stays in Room, isWishlisted reflects state, removeFromWishlist dual-delete, syncAnonymousOnLogin migrates anon rows + pulls remote-only items + merges both). TB-7 ile aynı casing tutarsızlığı burada da var (`users/{uid}/wishlist`).
- [x] `AddressRepositoryImpl` — 8 emulator test (save with generated UUID + caller-provided id, delete, snapshot listener backfill into Room, remote add propagation, remote delete propagation, empty userId guard, multi-user isolation). **TB-7 not**: prod kod adresleri lowercase `users/{uid}/addresses` altında tutuyor — user profile ise canonical `USERS/{uid}`. İki collection birbirinden bağımsız; sub-collection orphan değil (sadece keyed by uid) ama tutarsızlık görünürlüğü düşük olduğundan PRODUCT_BUGS_AND_GAPS.md'ye konsolide etmek gerekebilir.
- [x] `FirestoreRepositoryImpl` — 11 emulator test (getUser hit/miss, onboardingComplete (http URI skip-upload + null-uuid failure), updateSellerApprovalStatus approve/reject with previousStatus, observeSellersByStatus filters by role+status, observePendingResubmittedSellerCount aggregates, addUserToFirestore no-op pin).
- [x] `PaymentRepositoryImpl` — 9 emulator test (Room: createOrderInRoom/getOrderById/observeOrderById/updateOrderStatus dual-write + missing-doc failure; Cloud Function failure paths: unauthenticated, empty cart, invalid item, unknown product). Stripe happy-path skipped — requires `STRIPE_SECRET_KEY` secret + real Stripe test account. Test infra: FirebaseOptions API key fake key gerçekçi `AIzaSy...` formatına çevrildi (Functions SDK syntactic check yapıyor).

**Strateji notu**: Bu 12 repo doğrudan `FirebaseFirestore` / `FirebaseAuth` / `Firebase.functions()` SDK'larını sarmalıyor. Mockk ile zincirleme builder API'sini taklit etmek kırılgan ve düşük getirili — repo doğrudan SDK ile konuşuyor, mapping çoğunlukla inline. Doğru yaklaşımlar:

1. **Firebase emulator** kurmak (`firebase emulators:start --only firestore,auth,functions`) ve bu repo'ları emulator'a karşı integration test olarak koşmak (`:data:connectedDebugAndroidTest` veya emulator-friendly JVM test).
2. Mapper'ları (`mapDocumentToDiscountCode` vb. private fonksiyonlar) ayrı `internal` fonksiyonlara çıkarıp pure unit test'lemek — küçük bir refaktör.

Her ikisi de Wave 2 kapsamı dışı kararlar. Bunu Dalga 2'nin son adımı olarak ya **ayrı bir alt-faz (2C — emulator setup)** olarak ele alalım ya da **sonraki phase'e** ertele. Şimdilik Wave 2B'yi "kısmi tamam" işaretliyoruz.

### Dalga 2 çıkış kapısı
- [x] `./gradlew :data:testDebugUnitTest` → yeşil (**99 test**)
- [~] DAO testleri JVM'de Robolectric ile koştuğu için `connectedDebugAndroidTest` gerekmiyor
- [ ] Firestore-coupled 12 repo için 2C alt-faz kararı (emulator vs mapper refactor)

---

## Dalga 3 — `:app` ViewModel'leri (Turbine + MainDispatcherRule)

> Önce **para/auth** akışı, sonra seller core, sonra customer, sonra admin.

### 3A · Para / auth (KRİTİK)
- [x] `AuthViewModel` — 9 test (role routing, login transition, anon wishlist sync). Bug TB-2 raporlandı.
- [x] `sign_in/SignInViewModel` — 14 test (Robolectric for android.util.Patterns)
- [x] `sign_up/SignUpViewModel` — 11 test (Robolectric)
- [x] `verify_email/VerifyEmailViewModel` — 6 test (polling loop'u her test başında durduruluyor)
- [x] `customer/customer_cart/CartViewModel` — 19 test (state machine + computed property contracts)
- [x] `customer/checkout/CheckoutViewModel` — 27 test. **Bug TB-3 bulundu + düzeltildi** (Dispatchers.IO hardcode → DispatcherProvider injection). Placeholder dosyası silinmedi (CheckoutViewModelDiscountTest hâlâ duruyor, içerik artık CheckoutViewModelTest'te)
- [x] `seller/seller_discounts/DiscountCreateEditViewModel` — 15 test (placeholder silindi)
- [x] `seller/seller_discounts/DiscountListViewModel` — 9 test

### 3B · Seller core ✅ KOMPLE
- [x] `seller/seller_products/SellerProductListViewModel` — 22 test. **TB-4 fix**: FirebaseAuth → AuthRepository.
- [x] `seller/seller_products/SellerProductCreateViewModel` — 22 test (form validation, tag debounce, image cap, variant cap, save matrix)
- [x] `seller/seller_products/SellerProductEditViewModel` — 24 test (DRAFT/SUSPENDED guard, image merge, status preservation, submit-with-save)
- [x] `seller/seller_categories/SellerCategoryViewModel` — 10 test
- [x] `seller/seller_storefront/SellerStorefrontViewModel` — 5 test
- [x] `seller/seller_verification/SellerVerificationViewModel` — 16 test (Robolectric, Uri handling, RESUBMITTED status transition)
- [x] `seller/seller_dashboard/SellerDashboardViewModel` — 6 test (SavedStateHandle persistence)

### 3C · Customer browse ✅ KOMPLE
- [x] `customer/customer_home/CustomerHomeViewModel` — 18 test (mockk SyncManager+ConnectivityObserver). **TB-5 fix**: runCatching for manualSync.
- [x] `customer/customer_products/CustomerProductDetailViewModel` — 24 test (variant default selection, wishlist integration, cart prefill)
- [x] `customer/customer_wishlist/WishlistViewModel` — 17 test (undo, anonymous gate, multi-select)

### 3D · Admin ✅ KOMPLE
- [x] `admin/AdminBadgeViewModel` — 3 test (initial state, count emit, multi-update via Turbine)
- [x] `admin/admin_categories/AdminCategoryViewModel` — 17 test (observe + selection refresh, create/update/delete with image upload + error paths, addSubcategory selected refresh, dialog flags)
- [x] `admin/admin_products/AdminProductModerationViewModel` — 13 test (observe PENDING_REVIEW only, dialog flags, approve/suspend happy + failure paths, blank-reason guard, no-auth/no-selection no-ops). FirebaseAuth mockk ile mock'lanıyor (yine TB-4 benzeri anti-pattern var — sonradan AuthRepository'e refactor edilebilir).
- [x] `admin/admin_products/AdminProductSearchViewModel` — 14 test (debounce, clear, category/subcategory filters with re-search, status filter client-side, lazy category load idempotency, detail dialog)
- [x] `admin/admin_seller_approval/AdminApprovalViewModel` — 10 test (observe by status, filter switch, dialog flags, approve/reject/requestMoreInfo with happy + failure paths)

### 3E · Core / cross-cutting ✅ KOMPLE
- [x] `core/connectivity/ConnectivityViewModel` — 3 test (initial true, upstream mirror via Turbine, retained value after subscriber leaves within 5s timeout)
- [x] `core/connectivity/PendingSyncViewModel` — 5 test (isOnline mirror, pendingCount mirror, shouldShowBanner offline-only, dismissBanner DataStore write, isSyncing default false). Robolectric + WorkManagerTestInitHelper + tmp-file PreferenceDataStore.
- [x] `core/email_verification_banner/EmailVerificationBannerViewModel` — 9 test (not-auth→verified, authed+unverified→visible, authed+verified→hidden, permanently-hidden suppresses, both repo failure paths treated as verified, HideForSession vs DoNotShowAgain persistence semantics, recheckEmailVerification)
- [x] `onboard/OnboardingViewModel` — 23 test (registrationEmail bootstrap, blank-field error flags, seller 18+ age gate, validator coverage for tax/routing/bank/email, useRegistrationEmail toggle, document upload uri parsing, customer + seller form validation, onboarding success/failure routes, misc field updates). Robolectric (Patterns + Uri).
- [x] `queue_management/QueueManagementViewModel` — 8 test (operations mapping, every OperationType display name, unknown type fallback, every OperationStatus text, malformed createdAt fallback, retryOperation queues SyncWorker + status reset, discardOperation). Robolectric + WorkManagerTestInitHelper. **TB-10 not** (prod issue): `OnboardingViewModel.validateForm()` synchronously after each `when`-block, but field updates queued via `viewModelScope.launch(mainDispatcher)`. validateForm reads stale state — `isNextButtonEnabled` is always one action behind. Test'lerde "flushValidation" helper'ı kullanılıyor. Production fix: validateForm() field update'leri ile aynı launch'a taşı veya validateForm'u da launch'la (collect on _state).

### Dalga 3 çıkış kapısı
- [ ] `./gradlew testDebugUnitTest` (3 modül) → yeşil
- [ ] Her ViewModel için minimum: initial state + 1 happy path + 1 error path testi

---

## Dalga 4 — `:app` Compose UI testleri

> İlk turda **kritik akış** ekranları. Geri kalanlar isteğe bağlı 2. tur.

### 4A · Kritik (para + auth) — KISMI (2/7)
- [x] **SignInScreen** — 8 Compose UI test (renders, typing, save-credentials switch via `isToggleable()`, sign-in routes to repo, error message, navigateToVerifyEmail/Tab callbacks, sign-up navigation)
- [x] **SignUpScreen** — 7 Compose UI test (renders, sign-up button enables on valid email, password typing, switch toggle [2 toggleable nodes, password visibility + switch — clicks 2nd], sign-up routes to repo, sign-in callback, Google button → getCredential)
- [ ] Verify-email ekranı
- [ ] Cart ekranı — add/remove/qty change
- [ ] Checkout ekranı — kupon uygulama, ödeme butonu state'i
- [ ] Discount create/edit form — full form state
- [ ] Discount list — actions (edit/delete)

**Wave 4 pilot pattern**:
- `createComposeRule()` + inline `TestAuthRepository : AuthRepository` (minimal fake per test file)
- `setContent { Screen(viewModel = realVm, ...) }` ile VM enjekte ediyor
- `Dispatchers.Unconfined` ile tüm dispatcher roller
- Find: `onNodeWithText` + `isToggleable()[index]` (Switch ≠ IconToggleButton ayrımı için)
- Build infra: `androidx.work:work-testing` `META-INF/LICENSE.md` çakışması → `packaging { resources { pickFirsts += ... } }`

**Wave 4 remaining**: 4A (5 ekran), 4B (5 ekran), 4C (3 ekran), 4D (5 ekran) = **18 screen test sınıfı**. Pattern locked, ileride aynı şekilde yürür.

### 4B · Seller core
- [ ] Product list / create / edit
- [ ] Category list
- [ ] Storefront screen
- [ ] Seller verification flow
- [ ] Seller dashboard render

### 4C · Customer browse
- [ ] Customer home — sections, search field
- [ ] Product detail — image gallery, add-to-cart, wishlist toggle
- [ ] Wishlist

### 4D · Admin & misc
- [ ] Admin approval ekranı
- [ ] Admin product moderation
- [ ] Admin category
- [ ] Onboarding flow
- [ ] Email verification banner

### Dalga 4 çıkış kapısı
- [ ] `./gradlew :app:connectedDebugAndroidTest` → yeşil (emülatör ile)

---

## Bulunan bug'lar (kronolojik)

> Format: `- [#N] feature — bir cümle — fix commit hash veya @Ignore notu`

- **TB-1** ✅ **DÜZELTİLDİ**: `SearchKeywordsGenerator` + `ProductRepositoryImpl.search{Active,All}Products` 3 yerde aynı `[^a-z0-9]` regex'i kullanıyordu, Türkçe kelimeleri bozuyordu. `SEARCH_KEYWORD_STRIP_REGEX = Regex("[^\\p{L}\\p{N}]")` shared constant'a çevrildi, writer + 2 reader senkron. 4 yeni Unicode regression testi.
- **TB-2** ✅ **DÜZELTİLDİ**: `AuthRepository.currentFirebaseUser: FirebaseUser?` `:domain`'e Firebase SDK sızdırıyordu. `isAuthenticated: Boolean` + `currentAuthEmail: String?` ile değiştirildi. AuthRepositoryImpl ve 2 call site güncellendi. AuthViewModel'de daha önce test edilemeyen "firebase authed but profile not loaded → timeout → Onboarding" branch'i artık test ediliyor.
- **TB-3** ✅ **DÜZELTİLDİ**: `CheckoutViewModel` `Dispatchers.IO`'u hardcode'luyordu, DispatcherProvider almıyordu. Tüm IO çalışmaları test virtual time'a uyumsuz. Constructor'a DispatcherProvider eklendi, 5 `Dispatchers.IO` çağrısı `ioDispatcher`'a çevrildi. 27 test bu sayede koşuyor.
- **TB-4** ✅ **DÜZELTİLDİ**: `SellerProductListViewModel` `FirebaseAuth`'u doğrudan alıyordu. `AuthRepository` ile değiştirildi, `auth.currentUser?.uid` → `authRepository.currentUser.value?.uuid`. Koin auto-resolve, DI değişikliği gerekmedi.
- **TB-5** ✅ **DÜZELTİLDİ**: `CustomerHomeViewModel.onPullToRefresh` `SyncManager.manualSync()` exception'ı viewModelScope'tan kaçırıyordu (try-finally pattern). `runCatching` + Timber.e ile değiştirildi — pull-to-refresh fire-and-forget UI, exception bubble olmamalı.
- **TB-6** ✅ **DÜZELTİLDİ**: `AuthRepositoryImpl.init`'te `firebaseAuth.addAuthStateListener` kayıt ediliyordu ama hiç remove edilmiyordu. `authStateListener` property'sine tutuldu + `close()` fonksiyonu eklendi (auth state listener'ı remove + user snapshot listener'ı durdur). AuthRepositoryImplEmulatorTest shared-db workaround'ını terk edip per-test db + @After close pattern'ine geçti. Bonus: Firebase listener'ın main looper'a post ettiği initial fire timing race'i `InstrumentationRegistry.waitForIdleSync()` ile drain ediliyor.
- **TB-7** ✅ **DÜZELTİLDİ**: `AddressRepositoryImpl` + `WishlistRepositoryImpl` + `CartRepositoryImpl` lowercase `users/{uid}/...` yerine canonical `USER_COLLECTION` (`USERS`) kullanıyor artık. 3 prod dosya + 3 test dosya path'leri güncellendi.
- **TB-8** ✅ **DÜZELTİLDİ**: `ProfileRepositoryImpl.updateSellerDocument` → `updateSellerDocumentUri` yeni URL'i `businessInfo.${DocumentType.name.lowercase()}` (örn. `businessInfo.tax_documents`) altına yazıyordu; onboarding ise canonical camelCase `businessInfo.taxDocumentUri` kullanıyor. `businessInfoFieldName(documentType)` mapping helper'ı eklendi (TAX_DOCUMENTS → taxDocumentUri, BUSINESS_LICENSE → businessLicenseDocumentUri, IDENTITY_DOCUMENTS → identityDocumentUri). Test (`updateSellerDocument_replaces_existing_file_and_patches_firestore`) artık camelCase field replace + snake_case field yokluğunu pin'liyor.
- **TB-9** ✅ **DÜZELTİLDİ**: `FirestoreRepositoryImpl.updateProfilePhoto` ve `ProfileRepositoryImpl.uploadProfilePhoto` artık aynı canonical convention'a yazıyor: `profile_photos/{uid}/profile_image_{yyyyMMdd_HHmmss}.jpg`. Shared `PROFILE_PHOTOS_FOLDER` constant'ı `Constants.kt`'ye taşındı; eski `profile_images` + `IMAGE_FILE_SUFFIX` constants'ı kaldırıldı. FirestoreRepository test assertion'ı yeni path'e güncellendi. Production'da eski folder'da kalmış legacy object'ler olabilir — yeni yazımlar artık üretmiyor.
- **TB-10** ✅ **DÜZELTİLDİ**: `OnboardingViewModel` field-change handler'ları (`nameChange`, `surnameChanged`, `phoneNumberChange`, tüm seller field'ları, document upload'ları) `viewModelScope.launch(mainDispatcher) { _state.update {} }` pattern'ini kullanıyordu — `MutableStateFlow.update` zaten thread-safe + atomic olduğu için launch hop'u sadece güncellemeyi `validateForm()`'ın sync çalışan kısmından sonraya erteliyordu. Tüm field handler'ları `viewModelScope.launch(mainDispatcher)` wrapper'ı kaldırılarak inline yapıldı; `onBoardingComplete` (gerçek suspend repo call'u var) launch'ta kaldı. `flushValidation()` test helper'ı kaldırıldı. UI etkisi: kullanıcı son required field'ı doldurunca buton anında enable oluyor.

---

## İlerleme özeti

| Dalga | Toplam | Bitti | Bug bulundu | Bug düzeltildi |
|-------|--------|-------|-------------|----------------|
| 1 — domain | 18 | **18 ✅** | 1 | 0 |
| 2 — data | 19 | 13 (2A komple + 2B kısmi) | 0 | 0 |
| 3 — app VM | 28 | 18 (3A + 3B + 3C komple) | 5 (TB-2..TB-5) | 5 (TB-1..TB-5) |
| 4 — app UI | 24 | 0 | 0 | 0 |
| **Toplam** | **89** | **49** | **5** | **5** |

> Her commit sonrası bu tablo + ilgili checkbox güncellenir.
