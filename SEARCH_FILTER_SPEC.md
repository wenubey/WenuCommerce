# SEARCH_FILTER_SPEC.md
# Feature Specification: Enhanced Search with Category Filtering and Tag Display

**Date:** 2026-02-18
**Branch:** feature/categories
**Author:** Architect Agent

---

## 1. Overview

- **Feature Title:** Enhanced Search: Category Filter + Tag Chips in Results
- **Priority:** High
- **Estimated Complexity:** Medium
- **Summary:** Augment the existing search experience across all three roles (Customer, Seller, Admin) with two-step category/subcategory filtering accessible via a filter icon button next to `WenuSearchBar`. Product tag names are surfaced as chip labels inside search result cards. Combined text + category + subcategory filtering is applied client-side for Seller (who uses an in-memory product list) and via additional repository parameters for Customer and Admin (who call Firestore search methods).

---

## 2. User Stories

- As a Customer, I want to filter search results by a main category and optionally a subcategory so that I see only products relevant to what I am browsing.
- As a Customer, I want to see product tags displayed on search result cards so that I can quickly understand what a product is about without opening its detail page.
- As a Seller, I want to filter my own product list by category and subcategory, combined with my existing text and status filters, so that I can locate specific products in a large catalog.
- As an Admin, I want to narrow product search results by category and subcategory so that I can moderate and review products within a specific tree without scrolling through unrelated results.
- As any role, I want a visible badge on the filter button that counts how many category filters are currently active so that I know at a glance whether a filter is in effect.

---

## 3. Functional Requirements

- FR-1: `WenuSearchBar` accepts two new optional parameters: `onFilterClick: (() -> Unit)?` and `activeFilterCount: Int`. When `onFilterClick` is non-null, a filter icon (`Icons.Default.FilterList`) is rendered as a trailing `IconButton`. When `activeFilterCount > 0`, a `BadgedBox` wraps the icon showing the count.
- FR-2: When the filter icon is tapped, a `ModalBottomSheet` (Material 3) is presented. This sheet is owned and controlled by the calling screen composable, not by `WenuSearchBar` itself.
- FR-3: The filter bottom sheet renders a scrollable list of main-category chips. Chips are sourced from the role's already-loaded category list (Customer) or from a new one-time `getCategories()` call (Seller, Admin). Only `isActive == true` categories are shown.
- FR-4: When a main category chip is selected, the subcategory chip row appears below the main category row. The subcategories are taken from `Category.subcategories` of the selected item — no extra network call.
- FR-5: The filter sheet contains a "Clear Filters" `TextButton` that resets both `selectedFilterCategoryId` and `selectedFilterSubcategoryId` to null.
- FR-6: Closing the sheet (swipe-down or scrim tap) preserves the currently selected filters without resetting them.
- FR-7: For the **Customer** role, when a search query is active AND a filter category is selected, `searchActiveProducts` is called with the additional `categoryId` and `subcategoryId` parameters (see Section 5). When only a filter category is set but the search query is blank, the existing `observeActiveProductsByCategoryAndSubcategory` flow is reused — no change to that browse path.
- FR-8: For the **Admin** role, `searchAllProducts` is called with the additional `categoryId` and `subcategoryId` parameters. The status filter continues to be applied client-side on top of the category-filtered results (existing behavior preserved).
- FR-9: For the **Seller** role, category filtering is applied entirely client-side inside `applyFilters()`. `selectedFilterCategoryId` and `selectedFilterSubcategoryId` are added to `SellerProductListState` and used as additional predicates alongside the existing search-text and status predicates.
- FR-10: `CustomerProductCard` displays a horizontally scrollable `LazyRow` of `SuggestionChip` items for each entry in `product.tagNames`. The row is only rendered when `tagNames` is non-empty. Maximum 5 tags are shown (remaining are silently truncated to avoid card bloat).
- FR-11: `AdminProductSearchCard` receives the same tag chip treatment as FR-10.
- FR-12: `SellerProductCard` does NOT show tags (sellers already see their own tags in the product edit flow; adding them here would clutter the action-heavy card layout). This is an explicit exclusion.
- FR-13: The filter bottom sheet shows a `CircularProgressIndicator` while categories are loading (Seller and Admin paths only).
- FR-14: The active filter count badge value equals: `(if selectedFilterCategoryId != null then 1 else 0) + (if selectedFilterSubcategoryId != null then 1 else 0)`. Maximum displayed value is 2.

---

## 4. Non-Functional Requirements

- NFR-1: Category data for Seller and Admin filter sheets must be fetched at most once per ViewModel lifecycle (fetched lazily on first filter-sheet open, then cached in state). Do not start a real-time listener — a one-shot `getCategories()` call is sufficient and cheaper.
- NFR-2: The Firestore `searchActiveProducts` and `searchAllProducts` query changes must not introduce a new composite index beyond what currently exists. Category filtering for these methods is applied client-side after the primary keyword fetch (see Section 5 for the exact strategy).
- NFR-3: Tag chip rows in product cards must never cause the card height to grow unboundedly. Clip to `maxLines = 1` equivalent by showing at most 5 tags and using `LazyRow` with `horizontalArrangement = Arrangement.spacedBy(4.dp)`.
- NFR-4: The filter bottom sheet must be dismissed and re-openable without losing the selected filter state (state survives recompositions and sheet show/hide cycles).
- NFR-5: All new state fields default to null/empty so that existing behavior is identical when the feature is not engaged.

---

## 5. Technical Design Guidance

### 5.1 Suggested Architecture Pattern

- `WenuSearchBar` remains a pure, stateless UI component. It gains the filter icon as an optional parameter — the sheet itself lives in the calling screen.
- Each role screen owns a `var showFilterSheet by rememberSaveable { mutableStateOf(false) }` local boolean and passes `onFilterClick = { showFilterSheet = true }` to the search bar.
- The filter sheet is a separate private composable `CategoryFilterSheet(...)` defined once in a shared location and reused across all three screens.
- Category + subcategory filter selections flow through the existing Action/State/ViewModel pattern already in place for each role. No new architectural layer is introduced.

### 5.2 Repository Layer: searchActiveProducts and searchAllProducts

**Decision: Client-side category post-filter. Do not add a new Firestore `.whereEqualTo("categoryId", ...)` compound query on top of `.whereArrayContains("searchKeywords", ...)` because Firestore requires a composite index for each such combination and that index does not currently exist.**

Both methods receive two new optional parameters:

```kotlin
suspend fun searchActiveProducts(
    query: String,
    categoryId: String? = null,
    subcategoryId: String? = null,
): Result<List<Product>>

suspend fun searchAllProducts(
    query: String,
    categoryId: String? = null,
    subcategoryId: String? = null,
): Result<List<Product>>
```

In `ProductRepositoryImpl`, after the existing client-side token-matching filter block, append an additional filter step:

```kotlin
// After the existing tokens.all { token -> searchable.contains(token) } filter:
.filter { product ->
    if (categoryId == null) true
    else {
        val categoryMatches = product.categoryId == categoryId
        val subcategoryMatches = subcategoryId == null || product.subcategoryId == subcategoryId
        categoryMatches && subcategoryMatches
    }
}
```

This is zero-index-cost because both `categoryId` and `subcategoryId` are plain String equality checks on already-fetched objects in memory.

### 5.3 Key Components / Modules

**A. `WenuSearchBar` (modified)**
- File: `app/src/main/java/com/wenubey/wenucommerce/core/components/WenuSearchBar.kt`
- Add parameters: `onFilterClick: (() -> Unit)? = null`, `activeFilterCount: Int = 0`
- Render logic: when `query.isBlank() && !isLoading`, the trailing slot shows either: (a) the filter icon button (if `onFilterClick != null`), or nothing. When `query.isNotBlank()`, the trailing slot shows the Clear X button (existing behavior). The filter icon is always accessible because in practice all three calling screens will pass an empty query or a filled query — the icon must show in both states. Reconsider: show the filter icon alongside the clear button using a `Row` in the trailing slot when query is non-blank and `onFilterClick` is non-null.
- Trailing slot layout when both clear and filter are needed:
  ```
  Row {
      if (query.isNotBlank()) IconButton(clear)
      if (onFilterClick != null) BadgedBox(badge = { if activeFilterCount > 0 Badge { Text(count) } }) { IconButton(filter) }
  }
  ```

**B. `CategoryFilterSheet` (new shared composable)**
- File: `app/src/main/java/com/wenubey/wenucommerce/core/components/CategoryFilterSheet.kt`
- Parameters:
  ```kotlin
  @Composable
  fun CategoryFilterSheet(
      categories: List<Category>,
      isLoadingCategories: Boolean,
      selectedCategoryId: String?,
      selectedSubcategoryId: String?,
      onCategorySelected: (String?) -> Unit,
      onSubcategorySelected: (String?) -> Unit,
      onClearFilters: () -> Unit,
      onDismiss: () -> Unit,
  )
  ```
- Uses `ModalBottomSheet` from `androidx.compose.material3`.
- Layout (top to bottom):
  1. Handle bar (built into `ModalBottomSheet`)
  2. Row: "Filter by Category" title (labelLarge) + Spacer + "Clear Filters" TextButton (only enabled when either filter is active)
  3. `LazyRow` of category `FilterChip` items. No "All" chip — null selection means no category filter, and clearing is done via the Clear button.
  4. If `isLoadingCategories`: `CircularProgressIndicator` centered
  5. If a category is selected and its `subcategories` is non-empty: subtitle "Subcategory" + `LazyRow` of subcategory `FilterChip` items, preceded by an "All" chip (which sets subcategory to null).
  6. Bottom `Spacer` of `WindowInsets.navigationBars` height to avoid system bar overlap.

**C. Tag chips in product cards (modified composables)**

File: `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerHomeScreen.kt` — `CustomerProductCard`

After the seller name `Text`, add:
```
if (product.tagNames.isNotEmpty()) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        items(product.tagNames.take(5)) { tag ->
            SuggestionChip(
                onClick = {},
                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}
```

File: `app/src/main/java/com/wenubey/wenucommerce/admin/admin_products/AdminProductSearchScreen.kt` — `AdminProductSearchCard`

After the price/status Badge row, add the same `LazyRow` pattern with `product.tagNames.take(5)`.

**D. State changes**

See Section 5.4 for full field listings per role.

**E. Action changes**

See Section 5.5 for full sealed interface additions per role.

**F. ViewModel changes**

See Section 5.6.

**G. DI changes**

See Section 5.7.

### 5.4 State Changes Per Role

**CustomerHomeState** — add two fields:
```kotlin
// Filter sheet state (separate from browse category selection)
val filterSheetCategoryId: String? = null,
val filterSheetSubcategoryId: String? = null,
val isFilterSheetOpen: Boolean = false,  // optional, can use local state instead
```
Assumption: the existing `selectedCategoryId` / `selectedSubcategoryId` control the browse-by-category path on the home screen. The new `filterSheetCategoryId` / `filterSheetSubcategoryId` are exclusively for the search filter. These must remain independent to avoid breaking the existing horizontal category card UX.

**AdminProductSearchState** — add:
```kotlin
val categories: List<Category> = listOf(),
val isLoadingCategories: Boolean = false,
val filterCategoryId: String? = null,
val filterSubcategoryId: String? = null,
```

**SellerProductListState** — add:
```kotlin
val categories: List<Category> = listOf(),
val isLoadingCategories: Boolean = false,
val filterCategoryId: String? = null,
val filterSubcategoryId: String? = null,
```

### 5.5 Action Changes Per Role

**CustomerHomeAction** — add:
```kotlin
data class OnSearchFilterCategorySelected(val categoryId: String?) : CustomerHomeAction
data class OnSearchFilterSubcategorySelected(val subcategoryId: String?) : CustomerHomeAction
data object OnClearSearchFilters : CustomerHomeAction
```

**AdminProductSearchAction** — add:
```kotlin
data class OnFilterCategorySelected(val categoryId: String?) : AdminProductSearchAction
data class OnFilterSubcategorySelected(val subcategoryId: String?) : AdminProductSearchAction
data object OnClearCategoryFilters : AdminProductSearchAction
data object OnRequestCategoryLoad : AdminProductSearchAction  // triggers lazy fetch
```

**SellerProductListAction** — add:
```kotlin
data class OnFilterCategorySelected(val categoryId: String?) : SellerProductListAction
data class OnFilterSubcategorySelected(val subcategoryId: String?) : SellerProductListAction
data object OnClearCategoryFilters : SellerProductListAction
data object OnRequestCategoryLoad : SellerProductListAction  // triggers lazy fetch
```

### 5.6 ViewModel Changes Per Role

**CustomerHomeViewModel**

- `performSearch(query: String)` signature changes to pull `filterSheetCategoryId` and `filterSheetSubcategoryId` from current state and pass them to `productRepository.searchActiveProducts(query, categoryId, subcategoryId)`.
- The `searchQueryFlow.debounce` pipeline must also re-trigger when filter values change. The cleanest approach: create a combined `searchTriggerFlow` that combines `searchQueryFlow` with a `filterFlow: MutableStateFlow<Pair<String?, String?>>`. When either changes, re-execute search.
  - Alternative (simpler): keep `searchQueryFlow` debounce as-is for text. On `OnSearchFilterCategorySelected` / `OnSearchFilterSubcategorySelected` actions, call `performSearch(_homeState.value.searchQuery)` directly (no debounce needed for filter taps since they are deliberate user actions).
  - **Recommended:** use the simpler alternative. Filter selection immediately triggers `performSearch` in `viewModelScope.launch`. No debounce for deliberate filter taps.
- Add handler for three new actions:
  ```kotlin
  is CustomerHomeAction.OnSearchFilterCategorySelected -> {
      _homeState.update { it.copy(filterSheetCategoryId = action.categoryId, filterSheetSubcategoryId = null) }
      if (_homeState.value.searchQuery.isNotBlank()) performSearchWithCurrentFilters()
  }
  is CustomerHomeAction.OnSearchFilterSubcategorySelected -> {
      _homeState.update { it.copy(filterSheetSubcategoryId = action.subcategoryId) }
      if (_homeState.value.searchQuery.isNotBlank()) performSearchWithCurrentFilters()
  }
  is CustomerHomeAction.OnClearSearchFilters -> {
      _homeState.update { it.copy(filterSheetCategoryId = null, filterSheetSubcategoryId = null) }
      if (_homeState.value.searchQuery.isNotBlank()) performSearchWithCurrentFilters()
  }
  ```
- Extract `performSearchWithCurrentFilters()` as a private function that reads current state for query and filters, then calls `performSearch`.

**AdminProductSearchViewModel**

- Add `CategoryRepository` as a constructor dependency.
- Add lazy category load: `OnRequestCategoryLoad` action checks `if (state.categories.isEmpty() && !state.isLoadingCategories)` before fetching, sets `isLoadingCategories = true`, calls `categoryRepository.getCategories()`, stores result in state.
- On `OnFilterCategorySelected`: update state, reset subcategory, re-run `performSearch` with current query.
- On `OnFilterSubcategorySelected`: update state, re-run `performSearch` with current query.
- On `OnClearCategoryFilters`: reset both filter fields, re-run `performSearch` if query is non-blank.
- `performSearch(query)` is already a `suspend fun`. Refactor to `performSearch(query, categoryId, subcategoryId)` pulled from state at the call site, or pass them as parameters.
- `applyStatusFilter()` must remain applied after category filtering — the order is: Firestore fetch -> keyword client-filter -> category client-filter -> status client-filter -> store in `filteredResults`. The category filter is baked into `searchAllProducts` parameters and returned in results, so `applyStatusFilter()` continues to work on top of that naturally.

**SellerProductListViewModel**

- Add `CategoryRepository` as a constructor dependency.
- Category load follows same lazy-fetch pattern as Admin.
- `applyFilters()` gains two additional predicates:
  ```kotlin
  val matchesFilterCategory = current.filterCategoryId == null ||
      product.categoryId == current.filterCategoryId
  val matchesFilterSubcategory = current.filterSubcategoryId == null ||
      product.subcategoryId == current.filterSubcategoryId
  matchesSearch && matchesStatus && matchesFilterCategory && matchesFilterSubcategory
  ```
- `applyFilters()` is called after `OnFilterCategorySelected`, `OnFilterSubcategorySelected`, and `OnClearCategoryFilters`.

### 5.7 DI Changes

**`viewModelModule` in `app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt`**

`AdminProductSearchViewModel` and `SellerProductListViewModel` currently use `viewModelOf(::...)` which relies on Koin's constructor injection by type. Since `CategoryRepository` is already registered as a singleton in `repositoryModule`, no change to the module declaration is required as long as the new constructor parameter is added — Koin will resolve it automatically.

No changes needed to `DataModule.kt` or `firebaseModule`.

### 5.8 File Locations Summary

| File | Action |
|---|---|
| `app/.../core/components/WenuSearchBar.kt` | Modify — add filter icon + badge |
| `app/.../core/components/CategoryFilterSheet.kt` | Create new — shared bottom sheet composable |
| `app/.../customer/CustomerHomeScreen.kt` | Modify — wire filter sheet + add tags to `CustomerProductCard` |
| `app/.../customer/customer_home/CustomerHomeState.kt` | Modify — add 2 new filter fields |
| `app/.../customer/customer_home/CustomerHomeAction.kt` | Modify — add 3 new actions |
| `app/.../customer/customer_home/CustomerHomeViewModel.kt` | Modify — handle new actions, pass filters to search |
| `app/.../admin/admin_products/AdminProductSearchScreen.kt` | Modify — wire filter sheet + add tags to `AdminProductSearchCard` |
| `app/.../admin/admin_products/AdminProductSearchState.kt` | Modify — add 4 new fields |
| `app/.../admin/admin_products/AdminProductSearchAction.kt` | Modify — add 4 new actions |
| `app/.../admin/admin_products/AdminProductSearchViewModel.kt` | Modify — add `CategoryRepository`, handle new actions |
| `app/.../seller/SellerProductsScreen.kt` | Modify — wire filter sheet |
| `app/.../seller/seller_products/SellerProductListState.kt` | Modify — add 4 new fields |
| `app/.../seller/seller_products/SellerProductListAction.kt` | Modify — add 4 new actions |
| `app/.../seller/seller_products/SellerProductListViewModel.kt` | Modify — add `CategoryRepository`, extend `applyFilters()` |
| `domain/.../repository/ProductRepository.kt` | Modify — update signatures of `searchActiveProducts` and `searchAllProducts` |
| `data/.../repository/ProductRepositoryImpl.kt` | Modify — implement category post-filter in both search methods |

---

## 6. Edge Cases and Error Handling

- **Category load failure (Admin/Seller filter sheet):** If `getCategories()` returns a `Result.failure`, set `isLoadingCategories = false` and keep `categories` empty. The filter sheet renders an error `Text` in place of the chip list. The sheet remains openable and dismissible. Do not crash.
- **Selected filter category no longer exists:** If categories are reloaded and the previously-selected `filterCategoryId` is no longer in the list (admin deleted it), the filter silently returns zero results. The user can clear the filter via the Clear button. No automatic reset is performed.
- **Blank query with active category filter (Customer):** When the user clears the search query while a filter category is set, the search results pane disappears (the screen reverts to browse mode). The browse mode already uses `selectedCategoryId` / `selectedSubcategoryId` (the existing carousel), which are completely independent from the filter sheet selections. The filter badge still shows the count, indicating a pending filter that will apply if the user types again. This is acceptable UX — documented as an assumption below.
- **Blank query with active category filter (Admin):** Admin search requires a query string. If query is blank, the existing "Enter a search term to find products" empty state is shown regardless of filter. The filter badge shows the count as a visual reminder.
- **Seller: very large product list + category filter:** `applyFilters()` is O(n) across all seller products. Sellers with extremely large catalogs (unlikely in current product scope) should not experience jank since filtering runs synchronously within the ViewModel on a background-collected Flow result. Monitor if product counts exceed ~500.
- **`tagNames` is empty list:** The `LazyRow` for tags is wrapped in `if (product.tagNames.isNotEmpty())`, so no empty row or extra padding is emitted.
- **`tagNames` contains very long strings:** Each `SuggestionChip` label uses `MaterialTheme.typography.labelSmall` and relies on the chip's built-in text overflow. Tags are rendered in a horizontally scrollable row so truncation is less of a concern, but tag strings longer than ~20 characters should be clipped at the `Text` level with `maxLines = 1, overflow = TextOverflow.Ellipsis` inside the chip label.
- **Filter sheet opened before categories load (Admin/Seller):** Dispatch `OnRequestCategoryLoad` from the sheet's `LaunchedEffect(Unit)` block inside `CategoryFilterSheet` when called with `isLoadingCategories = false && categories.isEmpty()`. Alternatively, dispatch it from the screen-level `onFilterClick` lambda before setting `showFilterSheet = true`. The latter is simpler and avoids passing dispatch callbacks into the shared composable — recommended.
- **Subcategory chip rendered for a category with zero subcategories:** The subcategory row is only rendered when `selectedFilterCategory.subcategories.isNotEmpty()`. No empty row is shown.
- **Concurrent filter change and debounced search:** If the user changes the text query (triggering the 300ms debounce) and simultaneously taps a filter chip (triggering an immediate `performSearch`), both can race. Since both ultimately call `_state.update { ... }` and `performSearch` is called within `viewModelScope.launch`, the last write wins for state and the last-completing Firestore call updates results. This is acceptable for the current use-case. The `collectLatest` pattern in the debounce pipeline cancels the previous coroutine, so the text-debounce path is safe. The filter action path is a plain `launch`, not `collectLatest`, so two calls could overlap. Mitigation: hold a `searchJob: Job?` variable and cancel it before launching a new search in the filter action handlers, matching the existing `collectLatest` safety.

---

## 7. Acceptance Criteria

- [ ] `WenuSearchBar` renders a filter icon button when `onFilterClick` is non-null.
- [ ] When `activeFilterCount == 0`, no badge is shown on the filter icon.
- [ ] When `activeFilterCount > 0`, a numeric badge is shown on the filter icon.
- [ ] Tapping the filter icon opens the `CategoryFilterSheet` bottom sheet.
- [ ] The bottom sheet shows the list of active categories as `FilterChip` items.
- [ ] Selecting a main category chip marks it as selected and reveals subcategory chips below.
- [ ] Selecting a subcategory chip marks it as selected.
- [ ] Tapping "Clear Filters" deselects all filter chips and (if search is active) re-runs search without filters.
- [ ] Dismissing the sheet via swipe or scrim tap does not reset the selected filters.
- [ ] Customer: search results when a query is entered AND a category filter is set return only products matching both the keyword and the selected category/subcategory.
- [ ] Customer: the horizontal category browse carousel continues to work independently of the search filter.
- [ ] Admin: search results respect category and subcategory filters in addition to the existing status filter.
- [ ] Admin: category data is fetched only once per ViewModel instance (not re-fetched each time the sheet is opened).
- [ ] Seller: the in-memory product list is filtered by category/subcategory in addition to text and status.
- [ ] Seller: category data is fetched only once per ViewModel instance.
- [ ] `CustomerProductCard` displays up to 5 tag chips when `tagNames` is non-empty.
- [ ] `CustomerProductCard` shows no tag row when `tagNames` is empty.
- [ ] `AdminProductSearchCard` displays up to 5 tag chips when `tagNames` is non-empty.
- [ ] `SellerProductCard` does not display tags.
- [ ] `ProductRepository.searchActiveProducts` and `searchAllProducts` signatures are updated with optional `categoryId` and `subcategoryId` parameters, with defaults of `null` so all existing call sites compile without modification.
- [ ] The filter bottom sheet for Admin and Seller shows a loading indicator while categories are being fetched.
- [ ] All existing search, browse, and filter behaviors work identically when no category filter is selected (regression-free).

---

## 8. Out of Scope

- Server-side Firestore composite index creation for `searchKeywords + categoryId`. The category filter is intentionally kept client-side to avoid index management overhead. If needed in a future iteration, a Firestore index on `(status, searchKeywords, categoryId)` could be added.
- Persisting filter selections across app process restarts (no `SharedPreferences` or `DataStore` involvement).
- Animated chip appearance in the filter sheet.
- Multi-select category filtering (user can select only one main category at a time).
- Tag-based search (clicking a tag chip to initiate a new tag search). Tags are display-only in this spec.
- Adding the filter sheet to the seller storefront view.
- Changing the Category data model or the Firestore `categories` collection schema.
- Pagination of search results (existing limit of 200 on fallback queries is unchanged).

---

## 9. Assumptions and Open Questions

### Assumptions Made

- A1: The Customer filter sheet category list is sourced from `state.categories` (already loaded in `CustomerHomeViewModel` via `observeCategories()`). There is no need to fetch again — the data is already reactive.
- A2: For Admin and Seller, a one-shot `getCategories()` call is preferred over `observeCategories()` to avoid maintaining an additional real-time listener that would stay alive for the duration of the ViewModel's lifecycle unnecessarily.
- A3: The filter sheet is implemented as a **shared composable** (`CategoryFilterSheet`) and placed in `app/src/main/java/com/wenubey/wenucommerce/core/components/`. This avoids code duplication across three screens.
- A4: When the search query is blank and a category filter is active on the Customer screen, the filter silently "waits" — it is applied the moment a query is typed. This is preferrable to auto-triggering a filtered browse mode which would conflict with the existing carousel browse.
- A5: `SellerProductListViewModel` does not currently depend on `CategoryRepository`. Adding it as a constructor parameter is safe because Koin's `viewModelOf` will resolve it automatically from the existing `repositoryModule` binding.
- A6: The `filterSheetCategoryId` in `CustomerHomeState` is named distinctly from `selectedCategoryId` (which drives the browse carousel). These two selection states must never be merged or aliased.
- A7: Tag chips in cards use `SuggestionChip` (non-interactive, purely decorative) rather than `FilterChip` or `AssistChip` to avoid implying that tapping a tag initiates a search.
- A8: The existing `BadgedBox` and `Badge` composables from Material 3 are already used in `AdminProductSearchScreen.kt` (for status badges), confirming they are available on the classpath. No new dependencies are required.

### Open Questions (non-blocking)

- OQ-1: Should the Customer filter sheet category list exclude the already-selected browse category (the carousel selection)? Suggested default: No — show all active categories. Mixing the two selection mechanisms would confuse users.
- OQ-2: Should the filter bottom sheet remember scroll position between open/close cycles? Suggested default: No — `ModalBottomSheet` resets on dismiss by default and that is acceptable.
- OQ-3: Should Admin and Seller reload categories if the first fetch failed and the user closes and reopens the filter sheet? Suggested default: Yes — on re-open, if `categories.isEmpty() && !isLoadingCategories`, re-dispatch `OnRequestCategoryLoad`. This provides a natural retry without an explicit retry button.

---

## 10. Implementation Notes for Lead Developer

### Suggested Implementation Order

1. **Repository layer first.** Update `ProductRepository` interface and `ProductRepositoryImpl` to add the two optional parameters. All call sites still compile because parameters default to `null`. This is the safest starting change and unlocks everything downstream. Test that existing Customer and Admin search still works before proceeding.

2. **`WenuSearchBar` update.** Add the filter icon + `BadgedBox` to the trailing slot. Test the component in isolation with a preview. Ensure the Clear X and Filter icon co-exist in the trailing slot correctly.

3. **`CategoryFilterSheet` new composable.** Build and preview in isolation with hardcoded data before wiring to ViewModels. Verify two-step category → subcategory reveal, "Clear" button behavior, and sheet dismiss without state reset.

4. **Customer role.** Lowest risk because categories are already loaded. Add the three new actions and two state fields, wire the ViewModel handler, connect the filter sheet in `CustomerHomeScreen`, and add tag chips to `CustomerProductCard`.

5. **Admin role.** Add `CategoryRepository` to `AdminProductSearchViewModel` constructor, implement lazy load, wire actions/state, connect filter sheet in `AdminProductSearchScreen`, add tags to `AdminProductSearchCard`.

6. **Seller role.** Same pattern as Admin for category loading. Extend `applyFilters()` in `SellerProductListViewModel`. No repository call changes — purely client-side.

### Key Risks and Gotchas

- **`WenuSearchBar` trailing slot layout:** The current implementation uses a simple `when` block for the trailing icon. Replacing it with a `Row` that can hold both the Clear button and the Filter button requires wrapping in a `Row` composable with `intrinsicSize`. Test that the `OutlinedTextField` trailing slot does not clip the `Row`. Material 3 `OutlinedTextField` trailing content is constrained to a single slot — if the `Row` is too wide, the text input area will shrink. Consider limiting the trailing `Row` width or measuring with `wrapContentWidth()`.

- **`CustomerHomeState` field naming collision risk:** The existing fields `selectedCategoryId` and `selectedSubcategoryId` drive the carousel. The new fields `filterSheetCategoryId` and `filterSheetSubcategoryId` drive the search filter. Any developer conflating these two will introduce bugs that are difficult to trace. Comments in the state data class are essential.

- **`AdminProductSearchViewModel` search job cancellation:** The existing implementation launches `performSearch` inside `collectLatest` (via the debounce flow), which automatically cancels the previous call. When filter actions trigger `performSearch` directly via a plain `launch`, there is no cancellation. Extract a `searchJob: Job?` field and call `searchJob?.cancel()` before `searchJob = viewModelScope.launch { performSearch(...) }` in the filter action handlers.

- **Koin constructor injection for `CategoryRepository` in Seller/Admin ViewModels:** `viewModelOf(::SellerProductListViewModel)` will break at runtime if the ViewModel's constructor parameter count changes but the Koin module is not aware. Because `CategoryRepository` is already bound in `repositoryModule` as a singleton, `viewModelOf` will resolve it automatically. However, verify this at startup by checking the Koin graph at app init time (Koin's `checkModules()` in tests).

- **`ModalBottomSheet` lifecycle:** In Compose, `ModalBottomSheet` must be called conditionally (inside `if (showFilterSheet)`) or managed via `SheetState`. The simplest pattern is: `var showFilterSheet by rememberSaveable { mutableStateOf(false) }` in the screen composable, and `if (showFilterSheet) { CategoryFilterSheet(..., onDismiss = { showFilterSheet = false }) }`. Using `rememberSaveable` ensures the boolean survives configuration changes.

- **`LazyRow` inside `LazyColumn` item:** `CustomerProductCard` is rendered as an item inside a `LazyColumn`. A `LazyRow` inside a `LazyColumn` item is valid in Compose and does not cause measurement issues. Do not use nested `LazyColumn`.

- **`SuggestionChip` import:** Available in `androidx.compose.material3.SuggestionChip`. Confirm the project's `material3` BOM version includes this component (it was introduced in Material 3 1.1.0). If the BOM is older, substitute with a custom small `Card` + `Text` chip or use `AssistChip`.

### Recommended Testing Strategy

- **Unit tests (`ViewModel` layer):**
  - `CustomerHomeViewModel`: assert that `performSearch` is called with the correct `categoryId`/`subcategoryId` after each filter action. Use a fake `ProductRepository`.
  - `AdminProductSearchViewModel`: assert lazy category load fires only once; assert that `filteredResults` changes correctly after category filter + status filter combinations.
  - `SellerProductListViewModel`: assert `filteredProducts` filters correctly when `filterCategoryId` and `filterSubcategoryId` are set, in combination with text and status filters.

- **Integration tests (`ProductRepositoryImpl`):**
  - Verify that `searchActiveProducts(query = "shoes", categoryId = "cat-1", subcategoryId = null)` returns only products in `categoryId == "cat-1"`.
  - Verify that `searchActiveProducts(query = "shoes", categoryId = "cat-1", subcategoryId = "sub-2")` further narrows to the subcategory.
  - Verify that existing calls with no category args return the same results as before.

- **UI tests (Compose):**
  - `WenuSearchBar`: when `onFilterClick` is provided and `activeFilterCount = 0`, no badge; when `activeFilterCount = 1`, badge shows "1".
  - `CategoryFilterSheet`: selecting a category reveals subcategory row; "Clear Filters" resets both selections and invokes callbacks.
  - `CustomerProductCard`: tag row is present when product has tags; absent when `tagNames` is empty.

### Patterns to Follow

- Follow the same `viewModelScope.launch(mainDispatcher)` pattern used throughout `CustomerHomeViewModel` and `AdminProductSearchViewModel` for the new category-triggered search calls.
- Follow the same `safeApiCall(ioDispatcher)` wrapper pattern in `ProductRepositoryImpl` — no changes to that wrapper are needed.
- Follow the `FilterChip` + `LazyRow` pattern already used in `CustomerHomeScreen` for the subcategory row — apply the same pattern inside `CategoryFilterSheet` for both category and subcategory chip rows.
- The `BadgedBox` + `Badge` pattern already exists in `AdminProductSearchScreen.kt` for status badges — reuse the exact same import and usage style for the filter icon badge.

---

## Appendix: Concrete Diff Summary (by file)

### `domain/.../repository/ProductRepository.kt`
```
- suspend fun searchActiveProducts(query: String): Result<List<Product>>
+ suspend fun searchActiveProducts(
+     query: String,
+     categoryId: String? = null,
+     subcategoryId: String? = null,
+ ): Result<List<Product>>

- suspend fun searchAllProducts(query: String): Result<List<Product>>
+ suspend fun searchAllProducts(
+     query: String,
+     categoryId: String? = null,
+     subcategoryId: String? = null,
+ ): Result<List<Product>>
```

### `data/.../repository/ProductRepositoryImpl.kt`
In both `searchActiveProducts` and `searchAllProducts`, after the existing token-matching `.filter { ... }` block, chain:
```kotlin
.filter { product ->
    if (categoryId == null) true
    else product.categoryId == categoryId &&
         (subcategoryId == null || product.subcategoryId == subcategoryId)
}
```
Update both function signatures to match the interface change.

### `app/.../core/components/WenuSearchBar.kt`
New parameters:
```kotlin
onFilterClick: (() -> Unit)? = null,
activeFilterCount: Int = 0,
```
Trailing slot replacement — `Row` containing Clear X (conditional on `query.isNotBlank()`) and Filter icon (conditional on `onFilterClick != null`) wrapped in `BadgedBox`.

### `app/.../customer/customer_home/CustomerHomeState.kt`
```kotlin
+ val filterSheetCategoryId: String? = null,
+ val filterSheetSubcategoryId: String? = null,
```

### `app/.../customer/customer_home/CustomerHomeAction.kt`
```kotlin
+ data class OnSearchFilterCategorySelected(val categoryId: String?) : CustomerHomeAction
+ data class OnSearchFilterSubcategorySelected(val subcategoryId: String?) : CustomerHomeAction
+ data object OnClearSearchFilters : CustomerHomeAction
```

### `app/.../customer/customer_home/CustomerHomeViewModel.kt`
- Handle 3 new actions in `onAction()`
- `performSearch()` passes `state.filterSheetCategoryId` and `state.filterSheetSubcategoryId`
- Add `private var searchJob: Job?` for direct-launch search cancellation in filter handlers

### `app/.../customer/CustomerHomeScreen.kt`
- Wire filter sheet: `var showFilterSheet by rememberSaveable { ... }`; `onFilterClick = { showFilterSheet = true }` in `WenuSearchBar` call
- `if (showFilterSheet) CategoryFilterSheet(...)` at end of screen
- Add `activeFilterCount` computation: `listOfNotNull(state.filterSheetCategoryId, state.filterSheetSubcategoryId).size`
- `CustomerProductCard`: add tag `LazyRow` after seller name

### `app/.../admin/admin_products/AdminProductSearchState.kt`
```kotlin
+ val categories: List<Category> = listOf(),
+ val isLoadingCategories: Boolean = false,
+ val filterCategoryId: String? = null,
+ val filterSubcategoryId: String? = null,
```

### `app/.../admin/admin_products/AdminProductSearchAction.kt`
```kotlin
+ data class OnFilterCategorySelected(val categoryId: String?) : AdminProductSearchAction
+ data class OnFilterSubcategorySelected(val subcategoryId: String?) : AdminProductSearchAction
+ data object OnClearCategoryFilters : AdminProductSearchAction
+ data object OnRequestCategoryLoad : AdminProductSearchAction
```

### `app/.../admin/admin_products/AdminProductSearchViewModel.kt`
- Add `private val categoryRepository: CategoryRepository` constructor param
- Handle 4 new actions
- `performSearch()` passes category filter params from state
- `applyStatusFilter()` remains on top of category-filtered results

### `app/.../admin/admin_products/AdminProductSearchScreen.kt`
- Wire filter sheet
- `AdminProductSearchCard`: add tag `LazyRow`

### `app/.../seller/seller_products/SellerProductListState.kt`
```kotlin
+ val categories: List<Category> = listOf(),
+ val isLoadingCategories: Boolean = false,
+ val filterCategoryId: String? = null,
+ val filterSubcategoryId: String? = null,
```

### `app/.../seller/seller_products/SellerProductListAction.kt`
```kotlin
+ data class OnFilterCategorySelected(val categoryId: String?) : SellerProductListAction
+ data class OnFilterSubcategorySelected(val subcategoryId: String?) : SellerProductListAction
+ data object OnClearCategoryFilters : SellerProductListAction
+ data object OnRequestCategoryLoad : SellerProductListAction
```

### `app/.../seller/seller_products/SellerProductListViewModel.kt`
- Add `private val categoryRepository: CategoryRepository` constructor param
- Handle 4 new actions
- `applyFilters()` gains category predicates

### `app/.../seller/SellerProductsScreen.kt`
- Wire filter sheet
- No tag changes to `SellerProductCard`

### New file: `app/.../core/components/CategoryFilterSheet.kt`
- New composable as specified in Section 5.3 (Component B)
