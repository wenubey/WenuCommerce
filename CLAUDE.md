# CLAUDE.md — WenuCommerce (Android)

> **Project memory for Claude Code.** Project-specific decisions only.
> Genel Kotlin/Compose best practices yüklü **Agent Skills**'ten gelir — burada tekrar etme.
> Workflow: **GSD** (discuss → plan → execute → verify → ship). Atomic commits: bir mantıksal değişiklik = bir commit.

## Working mode (read first) — **Autonomous test-driven**
Kullanıcı (developer) sürekli "şu çalışıyor mu?" diye sormak istemiyor. Bu yüzden:
- **Yeni özellik / değişiklik = test ile gelir.** Test yoksa iş bitmiş sayılmaz.
- Testleri **sen yazar, sen çalıştırırsın.** Build + ilgili test suite **yeşilse → iş bitti.**
- Kırmızıysa kullanıcıya sormadan **kendi tanı + onar + tekrar koş** döngüsüne gir. Aynı hata 3 turdur tekrar ediyorsa veya bir mimari karar gerekiyorsa **o zaman** sor.
- Kullanıcı onayı **sadece şu durumlarda** gerekir:
  - Yeni bir kütüphane / pattern eklenecek (bu dosyada listelenmiyorsa)
  - Veri modeli / Firestore şeması / Room migration değişecek
  - Faturalanabilir bir entegrasyon (Stripe, Firebase Functions ücretli plan) etkilenecek
  - Geri alınması zor bir işlem (`git reset --hard`, prod push, Firestore toplu yazma)
- Aksi halde: küçük adım → test yaz → koştur → commit. Rapor 1-2 cümle: ne değişti, testler yeşil mi.

## What we're building
Çok-rollü bir **mobil e-ticaret** uygulaması: customer (alışveriş + checkout), seller (ürün/kategori/storefront/discount/dashboard), admin (analytics/dashboard). Backend Firebase (Auth + Firestore + Functions), ödeme Stripe, offline cache Room.

## Tech stack (decided — yenisi gerekirse SOR)
- Kotlin (latest stable), JDK 11 (modülde tanımlı), AGP/Compose BOM via version catalog
- Jetpack Compose + **Material 3**, single-Activity
- **Navigation Compose**, type-safe routes (no string routes)
- Coroutines + Flow; UI state **`StateFlow<XxxUiState>`** + event callbacks (UDF)
- **Koin** DI (`koin-bom 4.0.1`, modüller `app/di/`)
- **Room** (offline cache + schema export → `data/schemas/`)
- **Firebase**: Auth, Firestore, Functions, Crashlytics
- **Stripe** Payments SDK
- minSdk 24, targetSdk 35, compileSdk 35
- Tüm bağımlılıklar **`gradle/libs.versions.toml`** üzerinden — direkt string ile eklemek YOK

## Architecture — Multi-module + MVVM + UDF
```
:app      → UI (Compose), ViewModel, navigation, DI modules
:domain   → pure Kotlin: models, use cases, repository interfaces
:data     → Room, Firebase, Stripe adapters, repository implementations
```
Kurallar:
- `:domain` Android'e ve Firebase'e bağımlı **olamaz** (saf Kotlin / coroutines).
- `:app` `:data`'ya **doğrudan** çağrı yapmaz — repository interface'leri `:domain`'den enjekte edilir.
- Composable'larda business logic **yok**. Stateless tut, state'i hoist et.
- Her ViewModel **tek bir immutable `XxxUiState`** yayar (`StateFlow`), event'leri callback olarak alır.
- Repository Flow döner → ViewModel UiState'e map'ler.
- Domain logic (discount calc, validation, fiyat, vergi, kupon kuralları) `:domain/usecase` içinde **bağımsız unit test'li**.

## Package layout (feature-by-feature inside `:app`)
```
com.wenubey.wenucommerce
├── core/        (components, validators, connectivity, common UI)
├── di/          (Koin modules)
├── navigation/  (type-safe routes)
├── ui/theme/    (M3 theme)
├── customer/   seller/   admin/
│   └── <feature>/  (screen + viewmodel + uistate)
├── sign_in/ sign_up/ verify_email/ onboard/
└── notification/ queue_management/
```

## Testing & verification — **non-negotiable gate**
Bu projenin omurgası. "Çalışıyor mu?" sorusu testlerle cevaplanır.

### Her özellik için minimum
- **ViewModel** → state machine unit test (`StandardTestDispatcher` + `MainDispatcherRule` + Turbine)
- **UseCase / domain logic** → JUnit unit test (pure)
- **Repository** → fake DAO / fake remote ile unit test (gerçek Firestore'a vurmaz)
- **Composable** (eğer ekran ise) → en azından bir Compose UI test: state render + bir kritik etkileşim
- **Validator / formatter** → tablo testi (parametreli)

### Test ne yapmaz
- Gerçek Firebase'e bağlanmaz. Repository implementasyonları için **fake / in-memory** kullan.
- `Thread.sleep` ile bekleme — `advanceUntilIdle()` / `IdlingResource` / Turbine kullan.
- Composable testlerinde hardcoded coordinate / `mutableStateOf` kaçağı yok.

### Komutlar (sen koşturacaksın)
```bash
./gradlew :app:assembleDebug                       # build sağlığı
./gradlew testDebugUnitTest                        # tüm unit testler (3 modül)
./gradlew :domain:testDebugUnitTest                # sadece domain (en hızlı feedback)
./gradlew :app:testDebugUnitTest --tests "*DiscountCreateEditViewModelTest"
./gradlew connectedDebugAndroidTest                # UI/instrumentation (emülatör gerekli)
./gradlew lint
```

### "İş bitti" tanımı
- [ ] `:app:assembleDebug` yeşil
- [ ] Etkilenen modüllerin `testDebugUnitTest` çıktısı yeşil
- [ ] Yeni/değişen ekran varsa ilgili Compose UI test yeşil
- [ ] Atomic commit atıldı, mesaj GSD formatında (`feat(05-02): …`)

Yeşil değilse → iş yok. Soramazsın "tamam mı?" — testin yeşili tamam'ı söyler.

## Autonomous loop (recipe)
1. **Plan**: ne değişecek, hangi dosya, hangi testler? (kısa, mental olarak)
2. **Test yaz**: önce başarısız olacak şekilde (red).
3. **Implement**: en küçük geçirici değişiklik.
4. **Run**: ilgili `testDebugUnitTest` görevi.
5. Kırmızıysa → log oku, tanıla, onar, tekrar 4'e dön.
   - **3 başarısız tur** veya **mimari karar gerekiyorsa** kullanıcıya sor.
6. Yeşilse → `assembleDebug` (smoke) → commit → bir sonraki adım.
7. Faz bitince kısa rapor: ne değişti, hangi testler eklendi/koştu.

## Do / Don't
- **DO** Koin modüllerini feature bazında ayır; yeni modül eklerken `di/` altında yer ver.
- **DO** Yeni Firestore koleksiyonu / Room entity eklerken **önce** kullanıcıya şemayı doğrula.
- **DO** Stripe, Firebase Functions gibi para/işlem yan etkili akışlarda test öncelikli git, gerçek ortama dokunma.
- **DO** Atomic commit. `feat(<phase>): …`, `fix(<phase>): …`, `test(<phase>): …`, `refactor: …`.
- **DON'T** Composable içine business logic koy, `mutableStateOf` saç, string route kullan.
- **DON'T** Material 2 component'i ekle.
- **DON'T** Test'i geçmek için assertion'ı gevşet veya `@Ignore` at — sebebi yaz, kullanıcıya bildir.
- **DON'T** Co-author satırı git commit'lere ekleme (bkz. user feedback memory).
- **DON'T** `--no-verify`, `git reset --hard`, force push gibi destructive komutları **kullanıcı açıkça istemeden** koşturma.

## Phase 0 — Test Backfill (devam eden)
Mevcut kod test kapsamasız geldi. **Phase 0** tüm modüller için unit + UI test backfill'i kuruyor.
- Plan + checklist: **`PHASE-0-TEST-BACKFILL.md`** (oturumlar arası kalıcı tracker)
- Commit prefix: `test(backfill/<feature>): …` / `fix(backfill/<feature>): …`
- Bulunan bug **aynı turda düzeltilir** (küçükse), büyükse `PRODUCT_BUGS_AND_GAPS.md`'a yazılır + test `@Ignore` ile bırakılır + kullanıcıya rapor
- **Yeni phase başlatma kuralı**: dokunulacak feature'ın backfill testi yeşil olmalı; değilse önce backfill

## Repo notes
- Modüller: `:app`, `:data`, `:domain` (settings.gradle.kts)
- Room schemas: `data/schemas/` (commit'lenir, generated değil sayılır — migration audit için)
- Functions: `functions/` (Firebase Cloud Functions — Node)
- Mevcut faz dokümanları: kökteki `PRODUCT_BUGS_AND_GAPS.md`, `SEARCH_FILTER_SPEC.md`, `CONTINUATION_NOTES.md`
- Son tamamlanan: Phase 5 Discounts (3 plan, son commit `36cc54a`)
