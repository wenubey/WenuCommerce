# Product & Category — Bugs and Missing Features

## Bugs found during Phase 0 — Test Backfill

### TB-1: `generateSearchKeywords` strips Turkish (and all non-ASCII) characters (MEDIUM)

**File:** `domain/src/main/java/com/wenubey/domain/util/SearchKeywordsGenerator.kt`

**Problem:** The regex `[^a-z0-9]` removes any character outside basic ASCII, so Turkish characters (`ı`, `ş`, `ç`, `ğ`, `ü`, `ö`) and any Unicode letter are silently stripped. Example mangling:

- `"Akıllı Kalem"` → `["akll", "kalem"]` (should be `["akıllı", "kalem"]`)
- `"Kırtasiye"` → `["krtasiye"]` (should be `["kırtasiye"]`)
- `"ıı şş"` → `[]` (whole token vanishes)

**Impact:** Any product, category, subcategory, or tag with Turkish characters generates broken search keywords. Search for "akıllı" will not match products whose stored keyword is `akll`.

**Why not auto-fixed here:** The fix is one-line (`[^\\p{L}\\p{N}]` instead of `[^a-z0-9]`) **but** the search-query side must tokenize identically, otherwise queries and stored keywords disagree and search breaks. Need to verify all call sites (likely `SearchQueryNormalizer` or similar) match before changing this. Tests in `SearchKeywordsGeneratorTest` currently pin **buggy** behavior and must be updated in lockstep with the fix.

**Action:** Pinned by tests (`bug pin -` prefix); fix coordinated separately.

### TB-2: `AuthRepository` interface leaks `FirebaseUser` into the domain layer (LOW)

**File:** `domain/src/main/java/com/wenubey/domain/repository/AuthRepository.kt`

**Problem:** The repository interface in `:domain` exposes `val currentFirebaseUser: FirebaseUser?` — a concrete `com.google.firebase.auth.FirebaseUser` from the Firebase Android SDK. Domain should be a pure Kotlin module with no Firebase deps, but this field forces every `:domain` consumer to pull in Firebase. It also makes the interface impossible to fake cleanly in JVM unit tests because `FirebaseUser` isn't trivially constructible.

**Impact:** `AuthViewModelTest` cannot exercise the branch where `currentFirebaseUser != null` but `currentUser == null` (the "authenticated, profile not yet loaded → 3-second timeout → Onboarding fallback") because faking that combination requires a real `FirebaseUser` instance. Test coverage is missing for that one branch only; the rest of the state machine is covered.

**Fix:** Replace `currentFirebaseUser: FirebaseUser?` with `isAuthenticated: Boolean` (or `currentAuthUserId: String?`) on the interface. The concrete `AuthRepositoryImpl` keeps its private reference to `FirebaseAuth`. ~10-line refactor + 1 grep for call sites.

**Action:** Deferred. Single missing branch noted in the test file. Not blocking.

### TB-3: `CheckoutViewModel` hardcoded `Dispatchers.IO` instead of using `DispatcherProvider` (LOW — FIXED)

**File:** `app/src/main/java/com/wenubey/wenucommerce/customer/checkout/CheckoutViewModel.kt`

**Problem:** Every other ViewModel in the codebase routes IO work through the injected `DispatcherProvider` (so tests can swap in a `TestDispatcher` and use virtual time). `CheckoutViewModel` was an exception — it had no `DispatcherProvider` parameter and called `Dispatchers.IO` directly in 5 places (observeCartItems, observeSavedAddresses, applyCoupon, createPaymentIntent, handlePaymentSuccess). This made every IO-bound code path uncontrollable from tests: virtual time wouldn't advance the coroutines, `advanceUntilIdle()` returned before any IO work finished, and assertions saw stale state.

**Fix applied:** Added `dispatcherProvider: DispatcherProvider` to the constructor, derived `private val ioDispatcher = dispatcherProvider.io()`, replaced 5 occurrences of `Dispatchers.IO` with `ioDispatcher`, and dropped the `Dispatchers` import. Koin's `viewModelOf(::CheckoutViewModel)` auto-resolves the new parameter from the existing `DispatcherProviderImpl` singleton — no DI module change needed. Unblocked 27 `CheckoutViewModelTest` cases in this same commit.

**Action:** Fixed in the same commit that added `CheckoutViewModelTest`.

---

## Bugs to Fix

### Bug 1: `submitForReview` Coroutine Leak (HIGH)

**File:** `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductCreateViewModel.kt`

**Problem:** `submitForReview()` uses `_state.collect { }` on a `StateFlow` which **never completes**. `return@collect` only skips a single emission — it does NOT cancel the collection. Every submit attempt leaks a coroutine that continues collecting state forever.

**Fix:** Replace `_state.collect` with `_state.first { it.savedProductId != null }` to get a single emission and automatically complete.

```kotlin
// BEFORE (broken):
viewModelScope.launch(mainDispatcher) {
    var productId: String? = null
    _state.collect { state ->
        if (state.savedProductId != null && productId == null) {
            productId = state.savedProductId
            withContext(ioDispatcher) {
                productRepository.submitForReview(productId!!).fold(...)
            }
            return@collect // Does NOT cancel the collect
        }
    }
}

// AFTER (fixed):
viewModelScope.launch(mainDispatcher) {
    val state = _state.first { it.savedProductId != null }
    val productId = state.savedProductId!!
    withContext(ioDispatcher) {
        productRepository.submitForReview(productId).fold(...)
    }
}
```

---

### Bug 2: Category UUID Mismatch — Image Path vs Document ID (HIGH)

**Files:**
- `app/src/main/java/com/wenubey/wenucommerce/admin/admin_categories/AdminCategoryViewModel.kt`
- `data/src/main/java/com/wenubey/data/repository/CategoryRepositoryImpl.kt`

**Problem:** `AdminCategoryViewModel.createCategory()` generates a UUID and uploads the category image using that ID as the storage path. But `CategoryRepositoryImpl.createCategory()` generates a **different** UUID and overwrites the `category.id`. Result: the image is stored at `category_images/{vmId}_category_image.jpg` but the Firestore document has `id = {repoId}`. The image URL still works (it's a full URL), but the IDs are inconsistent and any path-reconstruction logic will break.

**Fix:** Have `CategoryRepositoryImpl.createCategory()` respect the `category.id` if it's non-blank, instead of always generating a new one:

```kotlin
// In CategoryRepositoryImpl.createCategory():
val categoryId = if (category.id.isNotBlank()) category.id else UUID.randomUUID().toString()
```

---

### Bug 3: Image Removal Doesn't Delete from Firebase Storage (MEDIUM)

**File:** `app/src/main/java/com/wenubey/wenucommerce/seller/seller_products/SellerProductEditViewModel.kt`

**Problem:** When a seller removes an already-uploaded image in the edit screen, the image is removed from the local state (`existingImages.removeAt(index)`) but `productRepository.deleteProductImage(storagePath)` is **never called**. The file remains in Firebase Storage as an orphan, accumulating storage costs over time.

**Fix:** Before removing from state, call the delete method if the image has a non-blank `storagePath`:

```kotlin
// In removeImage():
val image = existingImages[index]
if (image.storagePath.isNotBlank()) {
    viewModelScope.launch(ioDispatcher) {
        productRepository.deleteProductImage(image.storagePath)
    }
}
existingImages.removeAt(index)
```

---

### Bug 4: Subcategory Duplication on Retry (MEDIUM)

**File:** `data/src/main/java/com/wenubey/data/repository/CategoryRepositoryImpl.kt`

**Problem:** `addSubcategory()` uses `FieldValue.arrayUnion(subcategory.toMap())` to add a subcategory. Firestore's `arrayUnion` compares maps structurally — if the exact same subcategory map is added twice (e.g., user taps "Create" twice quickly, or a network retry), it won't duplicate. However, if anything differs (like a new UUID generated on retry), a duplicate entry will appear.

**Fix:** Use a Firestore transaction: read the document, check if a subcategory with the same `id` already exists, then write:

```kotlin
override suspend fun addSubcategory(categoryId: String, subcategory: Subcategory): Result<Unit> =
    safeApiCall(ioDispatcher) {
        firestore.runTransaction { transaction ->
            val docRef = categoriesCollection.document(categoryId)
            val snapshot = transaction.get(docRef)
            val existing = snapshot.toObject(Category::class.java)
                ?: throw Exception("Category not found")
            if (existing.subcategories.any { it.id == subcategory.id }) return@runTransaction
            val updated = existing.subcategories + subcategory
            transaction.update(docRef, "subcategories", updated.map { it.toMap() })
        }.await()
    }
```

---

### Bug 5: `unarchiveProduct` Always Resets to DRAFT (LOW)

**File:** `data/src/main/java/com/wenubey/data/repository/ProductRepositoryImpl.kt`

**Problem:** When a seller unarchives a product, the status is always set to `DRAFT` regardless of what it was before archiving. A product that was `ACTIVE` before archiving must go through the review process again.

**Fix (optional — may be by design):** Either:
- Accept this as intended behavior (archived products always need re-review) — add a comment documenting the design choice.
- OR add a `statusBeforeArchive` field to `Product` model. Set it in `archiveProduct()`, restore it in `unarchiveProduct()`.

---

## Missing Features

### Missing 1: "Add to Cart" Button is a Stub (BLOCKER for Order Flow)

**File:** `app/src/main/java/com/wenubey/wenucommerce/customer/customer_products/CustomerProductDetailScreen.kt`

**Problem:** The "Add to Cart" button has `onClick = { /* TODO: Add to cart */ }`. No Cart domain model, repository, or UI exists.

**Required before order flow:**
1. Create `domain/model/cart/Cart.kt` and `CartItem.kt` models
2. Create `domain/repository/CartRepository.kt` interface
3. Create `data/repository/CartRepositoryImpl.kt` (can be local-only with DataStore/Room, or Firestore-backed)
4. Add `OnAddToCart(productId, variantId, quantity)` action to `CustomerProductDetailAction`
5. Wire the button in `CustomerProductDetailScreen`
6. Build a Cart screen (list items, update quantity, remove, proceed to checkout)

---

### Missing 2: No Review Submission UI (MEDIUM)

**Files:**
- `domain/repository/ProductReviewRepository.kt` — `submitReview()` is defined
- `data/repository/ProductReviewRepositoryImpl.kt` — `submitReview()` is fully implemented with atomic rating update
- **No UI exists** for customers to write a review

**Required:**
1. Add a "Write a Review" button on `CustomerProductDetailScreen` (only for customers who purchased the product)
2. Create a `WriteReviewBottomSheet` or dialog with: star rating selector, title, body text fields
3. Add `OnSubmitReview(productId, rating, title, body)` action to `CustomerProductDetailAction`
4. Wire to `ProductReviewRepository.submitReview()` in the ViewModel

---

### Missing 3: Admin Search Approve/Suspend are No-ops (MEDIUM)

**File:** `app/src/main/java/com/wenubey/wenucommerce/admin/admin_products/AdminProductSearchScreen.kt`

**Problem:** Lines 221–223 — `onApprove` and `onSuspend` callbacks in the `ProductDetailDialog` just dismiss the dialog. Admin cannot take moderation actions from search results.

**Fix:** Add `OnApproveProduct(productId)` and `OnSuspendProduct(productId, reason)` actions to `AdminProductSearchAction` and implement them in `AdminProductSearchViewModel` by calling `productRepository.approveProduct()` and `productRepository.suspendProduct()`.

---

### Missing 4: Category Images Not Displayed to Customers (LOW)

**File:** `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt`

**Problem:** `CategoryCard` composable uses a generic `Icons.Default.Category` icon for every category, even though categories have an `imageUrl` field with uploaded images.

**Fix:** Pass `category.imageUrl` to `CategoryCard` and use `AsyncImage` (Coil) to display the category image. Fall back to the generic icon if `imageUrl` is blank.

---

### Missing 5: No "View My Storefront" for Sellers (LOW)

**Problem:** `SellerStorefrontScreen` exists and is routed, but there's no button or entry point in the seller's own tab screens to navigate to it.

**Fix:** Add a "View My Storefront" button to `SellerProfileScreen` or `SellerDashboardScreen` that navigates to `SellerStorefront(currentUserId)`.

---

### Missing 6: `isLoadingCategories` Hardcoded to `false` on Customer Home (LOW)

**File:** `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt`, line 90

**Problem:** `isLoadingCategories = false` is hardcoded when passing to `WenuSearchBar`. The filter panel spinner never shows during category load.

**Fix:** Add an `isLoadingCategories: Boolean` field to `CustomerHomeState`, set it to `true` while categories are loading, and pass `state.isLoadingCategories` to `WenuSearchBar`.

---

### Missing 7: Dead Code Cleanup (LOW)

| Item | File | Action |
|------|------|--------|
| `SellerDashboardViewModel.addProduct()` | `SellerDashboardViewModel.kt` | Empty stub. Remove the method and `OnAddProduct` action, or wire navigation through it |
| `observeActiveProductsByCategory()` | `ProductRepository.kt` / `ProductRepositoryImpl.kt` | Never called anywhere. Remove or mark deprecated — `observeActiveProductsByCategoryAndSubcategory` handles all cases |

---

## Priority Order for Fixes

1. **Bug 1** — submitForReview coroutine leak (quick fix, high impact)
2. **Bug 2** — Category UUID mismatch (data consistency)
3. **Bug 3** — Image deletion from Storage (resource leak)
4. **Bug 4** — Subcategory duplication guard (data integrity)
5. **Missing 1** — Cart / Add to Cart (blocker for order flow)
6. **Missing 3** — Admin search approve/suspend (functionality gap)
7. **Missing 2** — Review submission UI (complete the loop)
8. **Bug 5** — Unarchive status decision (document or fix)
9. **Missing 4–7** — Low priority polish items
