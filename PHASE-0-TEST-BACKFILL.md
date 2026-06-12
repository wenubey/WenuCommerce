# Phase 0 — Test Backfill

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

**Firestore-coupled (ertelendi — emulator gerekli):**
- [ ] `AuthRepositoryImpl`
- [ ] `ProfileRepositoryImpl`
- [ ] `ProductRepositoryImpl`
- [ ] `ProductReviewRepositoryImpl`
- [ ] `CategoryRepositoryImpl`
- [ ] `TagRepositoryImpl`
- [ ] `CartRepositoryImpl`
- [ ] `WishlistRepositoryImpl`
- [ ] `AddressRepositoryImpl`
- [ ] `DiscountRepositoryImpl`
- [ ] `FirestoreRepositoryImpl`
- [ ] `PaymentRepositoryImpl` — Stripe SDK + Cloud Function, aynı sorun

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
- [ ] `customer/checkout/CheckoutViewModel` — 354 satır, kendi turunu hak ediyor (placeholder dosyası hâlâ duruyor)
- [x] `seller/seller_discounts/DiscountCreateEditViewModel` — 15 test (placeholder silindi)
- [x] `seller/seller_discounts/DiscountListViewModel` — 9 test

### 3B · Seller core
- [ ] `seller/seller_products/SellerProductListViewModel`
- [ ] `seller/seller_products/SellerProductCreateViewModel`
- [ ] `seller/seller_products/SellerProductEditViewModel`
- [ ] `seller/seller_categories/SellerCategoryViewModel`
- [ ] `seller/seller_storefront/SellerStorefrontViewModel`
- [ ] `seller/seller_verification/SellerVerificationViewModel`
- [ ] `seller/seller_dashboard/SellerDashboardViewModel`

### 3C · Customer browse
- [ ] `customer/customer_home/CustomerHomeViewModel`
- [ ] `customer/customer_products/CustomerProductDetailViewModel`
- [ ] `customer/customer_wishlist/WishlistViewModel`

### 3D · Admin
- [ ] `admin/AdminBadgeViewModel`
- [ ] `admin/admin_categories/AdminCategoryViewModel`
- [ ] `admin/admin_products/AdminProductModerationViewModel`
- [ ] `admin/admin_products/AdminProductSearchViewModel`
- [ ] `admin/admin_seller_approval/AdminApprovalViewModel`

### 3E · Core / cross-cutting
- [ ] `core/connectivity/ConnectivityViewModel`
- [ ] `core/connectivity/PendingSyncViewModel`
- [ ] `core/email_verification_banner/EmailVerificationBannerViewModel`
- [ ] `onboard/OnboardingViewModel`
- [ ] `queue_management/QueueManagementViewModel`

### Dalga 3 çıkış kapısı
- [ ] `./gradlew testDebugUnitTest` (3 modül) → yeşil
- [ ] Her ViewModel için minimum: initial state + 1 happy path + 1 error path testi

---

## Dalga 4 — `:app` Compose UI testleri

> İlk turda **kritik akış** ekranları. Geri kalanlar isteğe bağlı 2. tur.

### 4A · Kritik (para + auth)
- [ ] Sign-in ekranı — validasyon, hata gösterimi, navigate-on-success
- [ ] Sign-up ekranı
- [ ] Verify-email ekranı
- [ ] Cart ekranı — add/remove/qty change
- [ ] Checkout ekranı — kupon uygulama, ödeme butonu state'i
- [ ] Discount create/edit form — full form state
- [ ] Discount list — actions (edit/delete)

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

- **TB-1** `SearchKeywordsGenerator` — `[^a-z0-9]` regex Turkish karakterleri silip kelimeleri bozuyor (`akıllı` → `akll`). Test ile pinlendi, fix ertelendi (search query tarafı da koordineli güncellenmeli). Detay: `PRODUCT_BUGS_AND_GAPS.md` § TB-1.
- **TB-2** `AuthRepository.currentFirebaseUser: FirebaseUser?` interface'i domain'e Firebase SDK tipi sızdırıyor. ~10 satır refaktör (`isAuthenticated: Boolean` ile değiştir). `AuthViewModelTest`'te tek bir branch test edilemiyor (firebase-user-var-ama-profil-yok timeout fallback). Detay: `PRODUCT_BUGS_AND_GAPS.md` § TB-2.

---

## İlerleme özeti

| Dalga | Toplam | Bitti | Bug bulundu | Bug düzeltildi |
|-------|--------|-------|-------------|----------------|
| 1 — domain | 18 | **18 ✅** | 1 | 0 |
| 2 — data | 19 | 13 (2A komple + 2B kısmi) | 0 | 0 |
| 3 — app VM | 28 | 7 (3A: 7/8) | 1 (TB-2) | 0 |
| 4 — app UI | 24 | 0 | 0 | 0 |
| **Toplam** | **89** | **38** | **2** | **0** |

> Her commit sonrası bu tablo + ilgili checkbox güncellenir.
