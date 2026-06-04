---
phase: 05-discounts
plan: 02
subsystem: discount-management-ui
tags: [discount, seller-ui, admin-ui, compose, navigation, koin]
dependency_graph:
  requires: [05-01]
  provides: [discount-list-screen, discount-create-edit-screen, seller-discount-tab, admin-discount-tab]
  affects: [seller-navigation, admin-navigation, koin-viewmodel-module]
tech_stack:
  added: []
  patterns: [seller-tab-extension, full-screen-form, filter-chip-selector, date-picker-dialog, product-picker]
key_files:
  created:
    - app/src/main/java/com/wenubey/wenucommerce/seller/seller_discounts/DiscountListAction.kt
    - app/src/main/java/com/wenubey/wenucommerce/seller/seller_discounts/DiscountListState.kt
    - app/src/main/java/com/wenubey/wenucommerce/seller/seller_discounts/DiscountListViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/seller/seller_discounts/SellerDiscountListScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/seller/seller_discounts/DiscountCreateEditAction.kt
    - app/src/main/java/com/wenubey/wenucommerce/seller/seller_discounts/DiscountCreateEditState.kt
    - app/src/main/java/com/wenubey/wenucommerce/seller/seller_discounts/DiscountCreateEditViewModel.kt
    - app/src/main/java/com/wenubey/wenucommerce/seller/seller_discounts/SellerDiscountCreateEditScreen.kt
  modified:
    - app/src/main/java/com/wenubey/wenucommerce/seller/SellerTabs.kt
    - app/src/main/java/com/wenubey/wenucommerce/seller/SellerTabScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/admin/AdminTabs.kt
    - app/src/main/java/com/wenubey/wenucommerce/admin/AdminTabScreen.kt
    - app/src/main/java/com/wenubey/wenucommerce/navigation/AppNavigationObjects.kt
    - app/src/main/java/com/wenubey/wenucommerce/navigation/TabNavRoutes.kt
    - app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt
    - app/src/main/res/values/strings.xml
decisions:
  - "DiscountCreateEditViewModel and full state/action files created in Task 1 (needed for compilation) then screen implemented in Task 2"
  - "Status badge derives state from isActive + expiresAt + usageCount vs usageLimit (no server-side status field)"
  - "FilterChip row used for discount type selector (compact, Material3 native) instead of ExposedDropdownMenu"
  - "Product picker uses LazyColumn with heightIn(max=200.dp) to prevent nested scroll issues"
  - "Admin product picker calls observeSellerProducts('') -- admin-specific all-products query can be refined in future"
metrics:
  duration_minutes: 6
  completed_date: "2026-06-04"
  tasks_completed: 2
  tasks_total: 2
  files_created: 8
  files_modified: 8
---

# Phase 5 Plan 02: Seller/Admin Discount Management UI Summary

Discount list screen with status badges, usage counts, and deactivate/delete actions; full-screen create/edit form with coupon code generation, type selector, value/cap fields, expiry date picker, usage limit, and searchable product picker.

## What Was Built

### Task 1: Discount list screen, ViewModel, tab wiring, and navigation
- **SellerTabs.Discounts** inserted between Orders (ordinal 2) and Profile (ordinal 4) with LocalOffer icons
- **AdminTabs.Discounts** appended as last tab (ordinal 8) -- no index shift for existing tabs
- **SellerTabScreen** and **AdminTabScreen** when(page) blocks updated with new Discounts case rendering SellerDiscountListScreen
- **DiscountListViewModel** observes discount codes via DiscountRepository, supports delete and deactivate actions
- **SellerDiscountListScreen** shows LazyColumn of discount cards with:
  - Code (bold) + status badge (Active=green, Expired=amber, Used up=red, Inactive=gray)
  - Discount description ("20% off", "$10.00 off", "Free Shipping")
  - Usage fraction ("3/10 used" or "3/unlimited used")
  - Deactivate and Delete icon buttons per item
  - FAB for create, item tap for edit
  - Empty state and loading state
- **SellerDiscountCreateEdit** navigation route added to AppNavigationObjects with code and isSeller params
- **TabNavRoutes** wired for both seller (isSeller=true) and admin (isSeller=false) navigation
- **ViewmodelModule** registered DiscountListViewModel and DiscountCreateEditViewModel

### Task 2: Discount create/edit full-screen form
- **SellerDiscountCreateEditScreen** full implementation with scrollable Column form:
  - Coupon code OutlinedTextField + Generate button (disabled in edit mode, forces uppercase)
  - Discount type FilterChip row: Percentage / Fixed Amount / Free Shipping
  - Value field with contextual label (hidden for Free Shipping)
  - Max discount cap field (only visible for Percentage type)
  - Minimum order amount field (optional)
  - Usage limit field (empty = unlimited)
  - Expiry date section with Material3 DatePickerDialog, formatted display, Clear button
  - Product picker with search filtering, LazyColumn of checkboxes
  - Save button with CircularProgressIndicator during save
  - Snackbar error display from saveError state
  - LaunchedEffect navigates back on saveSuccess
- **DiscountCreateEditViewModel** handles:
  - Edit mode: loads existing discount from observeDiscountCodes, populates all fields
  - Product picker: loads seller's products via ProductRepository, maps to ProductPickerItem
  - GenerateCode: 8-char uppercase alphanumeric (excludes ambiguous I, O, 0, 1)
  - Save: validates code required, value > 0, percentage <= 100; builds DiscountCode; calls create or update
  - All field update actions via sealed interface

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Smart cast fix for DiscountCode.usageLimit**
- **Found during:** Task 1 compilation
- **Issue:** `discount.usageLimit` is a public API property from a different module; Kotlin cannot smart cast it after null check
- **Fix:** Extracted to local `val limit = discount.usageLimit` before comparison
- **Files modified:** SellerDiscountListScreen.kt
- **Commit:** 83a098f

## Known Stubs

None -- all files contain real implementation.

## Self-Check: PASSED
