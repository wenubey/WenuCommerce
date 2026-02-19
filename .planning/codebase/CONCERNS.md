# Codebase Concerns

**Analysis Date:** 2026-02-19

## Tech Debt

**Product/Category UUID Inconsistency (RESOLVED):**
- Issue: `AdminCategoryViewModel` generates UUID for image upload, but `CategoryRepositoryImpl.createCategory()` generates a different UUID for Firestore document. Image stored at path with one ID, document has different ID.
- Files: `app/src/main/java/com/wenubey/wenucommerce/admin/admin_categories/AdminCategoryViewModel.kt`, `data/src/main/java/com/wenubey/data/repository/CategoryRepositoryImpl.kt`
- Status: FIXED - `CategoryRepositoryImpl.createCategory()` now respects `category.id` if non-blank (line 80): `val categoryId = if (category.id.isNotBlank()) category.id else UUID.randomUUID().toString()`

**Coroutine Leak in submitForReview (RESOLVED):**
- Issue: `SellerProductCreateViewModel.submitForReview()` previously used `_state.collect { }` which never completes. Every submit leaked a coroutine collecting state forever.
- Files: `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductCreateViewModel.kt` (lines 291-305)
- Status: FIXED - Now uses `_state.first { it.savedProductId != null }` (line 292) which gets single emission and completes automatically

**Orphaned Images in Firebase Storage (RESOLVED):**
- Issue: `SellerProductEditViewModel.removeImage()` removed images from local state but never deleted from Firebase Storage, accumulating storage costs.
- Files: `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductEditViewModel.kt` (lines 263-286)
- Status: FIXED - Now calls `productRepository.deleteProductImage(image.storagePath)` before removing (lines 270-273)

**Subcategory Duplication on Retry (RESOLVED):**
- Issue: `CategoryRepositoryImpl.addSubcategory()` uses `FieldValue.arrayUnion()` without checking if subcategory already exists. Duplicate IDs on network retries.
- Files: `data/src/main/java/com/wenubey/data/repository/CategoryRepositoryImpl.kt`
- Status: FIXED - Now uses Firestore transaction with ID uniqueness check

---

## Known Bugs

**"Add to Cart" Button is Non-functional (BLOCKER):**
- Symptoms: Customer sees "Add to Cart" button on product detail screen but nothing happens when clicked
- Files: `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt` (button exists but `onClick = { /* TODO: Add to cart */ }`)
- Trigger: Click "Add to Cart" button on any product detail page
- Impact: CRITICAL - Blocks entire order flow. No cart system exists (no domain models, repositories, or UI)
- Required fix: Build Cart system (domain models `Cart`/`CartItem`, repository interface and impl, UI screens)

**Admin Product Search Approve/Suspend are No-ops:**
- Symptoms: Admin clicks "Approve" or "Suspend" in product detail dialog, only the dialog closes. Action doesn't execute.
- Files: `app/src/main/java/com/wenubey/wenucommerce/admin/admin_products/AdminProductSearchScreen.kt` (lines 221-222)
- Trigger: Open admin product search, select a product, click Approve or Suspend button
- Impact: Admin cannot take moderation actions from search results. Only works from moderation tab, not search.
- Fix approach: Add `OnApproveProduct(productId)` and `OnSuspendProduct(productId, reason)` actions to `AdminProductSearchAction`, implement in `AdminProductSearchViewModel`

**Unarchive Status Reset (DESIGN DECISION):**
- Symptoms: When seller unarchives a product, status always becomes DRAFT regardless of previous status
- Files: `data/src/main/java/com/wenubey/data/repository/ProductRepositoryImpl.kt` (unarchiveProduct method)
- Impact: Products that were ACTIVE before archiving must re-enter review process. May be intentional.
- Fix approach: Either document as design choice with comment, or add `statusBeforeArchive` field to Product model to restore previous status

---

## Security Considerations

**No Input Validation on Tag Creation:**
- Risk: Raw user input for tags goes directly to `tagRepository.resolveOrCreateTag()` without sanitization. Potential for injection if tag values aren't validated at storage layer.
- Files: `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductCreateViewModel.kt` (line 212), `SellerProductEditViewModel.kt` (line 154)
- Current mitigation: Only checks for blank/duplicate, not for malicious content
- Recommendations: Add length limits, character whitelisting, or XSS-safe escaping before persisting tags to Firestore

**Image URLs Not Validated:**
- Risk: Product images loaded directly from downloadUrl without validation. Malicious image URLs or content could be served.
- Files: `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt`, `app/src/main/java/com/wenubey/wenucommerce/admin/admin_products/AdminProductSearchScreen.kt`, `app/src/main/java/com/wenubey/wenucommerce/seller/seller_storefront/SellerStorefrontScreen.kt`
- Current mitigation: URLs come from Firebase Storage only
- Recommendations: Validate downloadUrl domain, add Content Security Policy considerations for Coil image loading

**Firebase Security Rules Not Documented:**
- Risk: No explicit documentation of Firestore/Storage security rules. Unclear what data is publicly readable vs. admin-only.
- Current mitigation: Assumed Firebase is configured with default rules
- Recommendations: Document and audit security rules for each collection (categories, products, users, reviews, etc.)

---

## Performance Bottlenecks

**Inefficient Product Filtering (Medium Impact):**
- Problem: `SellerProductListViewModel.applyFilters()` does client-side filtering on full product list every time state changes. No pagination or lazy loading.
- Files: `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductListViewModel.kt` (lines 123-142)
- Cause: All products loaded into memory via `observeSellerProducts()`, then filtered with nested array checks
- Improvement path: Implement Firestore composite query with category/subcategory filters. Use `whereIn()` for status filters. Implement pagination with Paging 3 library (already in dependencies at version 3.3.5 per `libs.versions.toml`)

**Real-Time Listener Never Unsubscribed:**
- Problem: `SellerProductListViewModel.observeSellerProducts()` uses callbackFlow listener that stays active across all state changes. If called multiple times, creates listener leak.
- Files: `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductListViewModel.kt` (lines 37-52)
- Current mitigation: `observeJob?.cancel()` called before new observe, but old listener reference may persist
- Improvement path: Cancel job on `onCleared()` (already done line 197), but verify listener cleanup in callbackFlow implementation

**Category Images Not Displayed (LOW):**
- Problem: Customer home screen shows generic category icons instead of uploaded category images, missing visual experience
- Files: `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt` (CategoryCard uses `Icons.Default.Category`)
- Cause: `category.imageUrl` field populated but not used in UI
- Improvement path: Pass `category.imageUrl` to `CategoryCard`, use `AsyncImage(model = category.imageUrl)` with fallback to generic icon

---

## Fragile Areas

**Image Upload Process (HIGH):**
- Files: `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductCreateViewModel.kt` (uploadImages method, lines 307-344)
- Why fragile: `uploadImages()` is fire-and-forget (no await). If any image fails to upload, error logged but product still saved with empty images list. Partially uploaded products can silently degrade.
- Safe modification: Wrap entire uploadImages block in try-catch, rollback product if upload count doesn't match expected count
- Test coverage: No unit tests exist for image upload retry logic or partial failure scenarios

**Category/Subcategory Deserialization (HIGH):**
- Files: `data/src/main/java/com/wenubey/data/repository/CategoryRepositoryImpl.kt` (lines 43-49, 68-74)
- Why fragile: Deserialization failures silently dropped with `mapNotNull`. Firestore schema changes break without warning.
- Safe modification: Log count of dropped documents, add schema versioning comment on Category model
- Test coverage: No tests for deserialization edge cases

**Tag Resolution with Create Side Effect (MEDIUM):**
- Files: `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductCreateViewModel.kt` (line 212), `SellerProductEditViewModel.kt` (line 154)
- Why fragile: `tagRepository.resolveOrCreateTag()` has side effect (creates tag if not found). If called twice quickly due to debounce race, could create duplicate tags.
- Safe modification: Use transaction-based resolve, or add idempotency check
- Test coverage: No tests for concurrent tag resolution

**Empty/Unverified State Transitions (MEDIUM):**
- Files: `SellerProductCreateViewModel.kt` initialization checks seller verification, but `saveDraft()` doesn't re-check. User could draft unverified, become verified in background, then submit.
- Safe modification: Re-check verification in `submitForReview()` before submission
- Test coverage: No tests for race conditions between auth state changes

---

## Scaling Limits

**Single Seller Product List Load (HIGH):**
- Current capacity: All seller products loaded at once via `observeSellerProducts()` callbackFlow
- Limit: Breaks with >1000 products per seller (Firestore document read limit, memory overhead)
- Scaling path: Implement Paging 3 library with `PagingSource<Int, Product>` querying Firestore with offset/limit

**Search Query Full Scan (HIGH):**
- Current capacity: `AdminProductSearchViewModel` searches all products with text matching on `searchKeywords` field (already denormalized)
- Limit: Firestore full collection scan at scale (no index on searchKeywords for substring match)
- Scaling path: `whereArrayContainsAny(searchKeywords, [...splitQuery])` already used, but add composite index for category+status+searchKeywords if filtering by those fields

**Image Storage Per Product (MEDIUM):**
- Current capacity: Max 8 images per product stored in Firebase Storage at `product_images/{productId}/{imageId}.jpg`
- Limit: No sharding or CDN layer. All images from single Firebase Storage bucket.
- Scaling path: Consider Firebase Storage sharding or CDN like Cloudflare, implement image resizing with Cloud Functions

---

## Dependencies at Risk

**Firebase BOM 33.8.0 (MEDIUM):**
- Risk: Firebase libraries frequently release breaking changes. No version pinning beyond BOM.
- Current state: Uses Firebase BOM 33.8.0 per `libs.versions.toml`
- Mitigation: BOM handles transitive dependency alignment
- Recommendation: Monitor Firebase release notes quarterly

**Coil 3.0.4 (LOW):**
- Risk: Recently released major version. AsyncImage API may change in 3.1+.
- Current state: Uses Coil 3.0.4 with coil-network-okhttp
- Mitigation: All imports use `coil3.compose.AsyncImage` which is stable
- Recommendation: Pin minor version until migration to 4.x becomes urgent

---

## Missing Critical Features

**Cart System (BLOCKER):**
- Problem: "Add to Cart" button non-functional. No Cart domain model, repository, or UI screens exist.
- Blocks: Entire order flow (checkout, payment, order tracking)
- Priority: HIGHEST - gates MVP completeness

**Review Submission UI (MEDIUM):**
- Problem: `ProductReviewRepository.submitReview()` backend fully implemented with atomic rating updates, but no UI for customers to write reviews
- Blocks: Customer review loop (read reviews works, write doesn't)
- Priority: HIGH

**Cart/Wishlist Persistence (MEDIUM):**
- Problem: No decision on whether cart is local-only or synced to Firestore. If local, cross-device sync missing. If Firestore, no indexing for user carts.
- Impact: Users lose cart when app reinstalled or switching devices
- Decision needed: Design spec before implementation

---

## Test Coverage Gaps

**No Unit Tests (CRITICAL):**
- What's not tested: All application logic. Only example stub tests exist in each module.
- Files: `app/src/test/java/com/wenubey/wenucommerce/ExampleUnitTest.kt` (empty), `data/src/test/java/com/wenubey/data/ExampleUnitTest.kt` (empty), `domain/src/test/java/com/wenubey/domain/ExampleUnitTest.kt` (empty)
- Risk: Critical bugs in ViewModels, repositories, and state management go undetected until user-facing
- Priority: HIGH - Add unit tests for:
  - `SearchKeywordsGenerator.generateKeywords()` - keyword splitting logic
  - `SellerProductCreateViewModel.submitForReview()` - coroutine flow
  - `ProductRepositoryImpl` queries - Firestore interactions
  - All repository `Result<T>` fold paths - error handling

**No Integration Tests:**
- What's not tested: Firestore integration, image upload flow, authentication state management
- Risk: Database schema changes break silently
- Priority: MEDIUM

**No UI Tests (No Instrumented Tests):**
- What's not tested: Navigation, composition state, user interactions
- Risk: UI bugs discovered only in manual testing or user reports
- Priority: MEDIUM

---

## Architectural Debt

**Unidirectional Data Flow Inconsistency:**
- Problem: Most screens use MVVM (ViewModel + StateFlow), but real-time listeners (categories, seller products) subscribe via `collectAsStateWithLifecycle()` without explicit action dispatch
- Files: `SellerProductListViewModel.kt` (line 42 direct .collect instead of action)
- Impact: Mixed patterns make code harder to reason about and test
- Recommendation: Route all data changes through actions, even for real-time updates

**Firebase Module Directly Injected (MEDIUM):**
- Problem: `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage` injected directly into repositories instead of wrapped in abstraction
- Files: `data/src/main/java/com/wenubey/data/repository/*.kt` (all repository constructors)
- Impact: Tight coupling to Firebase. Hard to swap for mock in tests or different backend.
- Recommendation: Wrap Firebase in repository interfaces already defined in domain module

---

*Concerns audit: 2026-02-19*
