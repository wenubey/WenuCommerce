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

### 2A · Room DAOs (in-memory `Room.inMemoryDatabaseBuilder`)
- [ ] `local/dao/AddressDao` — CRUD + Flow gözlem (Turbine)
- [ ] `local/dao/CartItemDao` — upsert/qty/clear
- [ ] `local/dao/CategoryDao`
- [ ] `local/dao/OrderDao` — durum filtreleri
- [ ] `local/dao/PendingOperationDao` — queue invariants
- [ ] `local/dao/ProductDao` — query / flow / paging (varsa)
- [ ] `local/dao/UserDao`
- [ ] `local/dao/WishlistItemDao`

### 2B · Repository implementations (Firestore/Ktor fake'ler ile)
- [ ] `AuthRepositoryImpl` — sign-in/up/out, error path
- [ ] `ProfileRepositoryImpl`
- [ ] `ProductRepositoryImpl` — list/get/search, paging
- [ ] `ProductReviewRepositoryImpl`
- [ ] `CategoryRepositoryImpl`
- [ ] `TagRepositoryImpl`
- [ ] `CartRepositoryImpl` — local + remote sync
- [ ] `WishlistRepositoryImpl`
- [ ] `AddressRepositoryImpl`
- [ ] `DiscountRepositoryImpl` (mevcut testi genişlet)
- [ ] `PaymentRepositoryImpl` — Stripe wrapper, **gerçek API'ye vurmadan**
- [ ] `FirestoreRepositoryImpl` (base/util)

### Dalga 2 çıkış kapısı
- [ ] `./gradlew :data:testDebugUnitTest` → yeşil
- [ ] DAO testleri için `:data:connectedDebugAndroidTest` (in-memory Room JVM'de koşamıyorsa) → yeşil

---

## Dalga 3 — `:app` ViewModel'leri (Turbine + MainDispatcherRule)

> Önce **para/auth** akışı, sonra seller core, sonra customer, sonra admin.

### 3A · Para / auth (KRİTİK)
- [ ] `AuthViewModel`
- [ ] `sign_in/SignInViewModel`
- [ ] `sign_up/SignUpViewModel`
- [ ] `verify_email/VerifyEmailViewModel`
- [ ] `customer/customer_cart/CartViewModel`
- [ ] `customer/checkout/CheckoutViewModel` (mevcut testi genişlet — full state coverage)
- [ ] `seller/seller_discounts/DiscountCreateEditViewModel` (mevcut testi audit + gap'leri kapat)
- [ ] `seller/seller_discounts/DiscountListViewModel`

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

---

## İlerleme özeti

| Dalga | Toplam | Bitti | Bug bulundu | Bug düzeltildi |
|-------|--------|-------|-------------|----------------|
| 1 — domain | 18 | **18 ✅** | 1 | 0 |
| 2 — data | 19 | 0 | 0 | 0 |
| 3 — app VM | 28 | 0 | 0 | 0 |
| 4 — app UI | 24 | 0 | 0 | 0 |
| **Toplam** | **89** | **18** | **1** | **0** |

> Her commit sonrası bu tablo + ilgili checkbox güncellenir.
