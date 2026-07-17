---
phase: 08-notifications
reviewed: 2026-07-17T00:00:00Z
depth: standard
files_reviewed: 20
files_reviewed_list:
  - app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryState.kt
  - app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryAction.kt
  - app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryViewModel.kt
  - app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryScreen.kt
  - app/src/main/java/com/wenubey/wenucommerce/notification/permission/NotificationPermissionRationaleDialog.kt
  - app/src/test/java/com/wenubey/wenucommerce/notification/NotificationHistoryViewModelTest.kt
  - app/src/test/java/com/wenubey/wenucommerce/testing/fakes/FakeNotificationRepository.kt
  - app/src/androidTest/java/com/wenubey/wenucommerce/notification/NotificationHistoryScreenTest.kt
  - app/src/main/java/com/wenubey/wenucommerce/navigation/AppNavigationObjects.kt
  - app/src/main/java/com/wenubey/wenucommerce/navigation/TabNavRoutes.kt
  - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabs.kt
  - app/src/main/java/com/wenubey/wenucommerce/seller/SellerTabs.kt
  - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabScreen.kt
  - app/src/main/java/com/wenubey/wenucommerce/customer/CustomerProfileScreen.kt
  - app/src/main/java/com/wenubey/wenucommerce/seller/SellerTabScreen.kt
  - app/src/main/java/com/wenubey/wenucommerce/seller/SellerProfileScreen.kt
  - app/src/main/java/com/wenubey/wenucommerce/di/ViewmodelModule.kt
  - data/src/main/java/com/wenubey/data/repository/NotificationPreferences.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/res/values/strings.xml
findings:
  critical: 3
  warning: 8
  info: 6
  total: 17
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-07-17
**Depth:** standard
**Files Reviewed:** 20
**Status:** issues_found

## Summary

Reviewed the newly-added UI/permission surface of Phase 8 plan 08-04: notification-history screen + ViewModel, the once-per-install POST_NOTIFICATIONS rationale flow, bottom-nav unread badges, the standalone deep-link route, and the DataStore gate. Cross-referenced the data-layer contracts (`NotificationRepositoryImpl`, `NotificationDao`, `NotificationMapper`, `AuthRepositoryImpl`) to validate the auth-uid scoping claims and the `createdAt` format assumptions the UI relies on.

The auth-uid scoping threats T-08-12 / T-08-13 are structurally handled by the data layer (uid never travels cross-user; `markAsRead` resolves the doc from `currentUserId` bound to the auth listener). However, there are three correctness BLOCKERs: (1) a data-format contract mismatch — the UI parses `createdAt` as ISO-8601 while the entire data layer stores it as an **epoch-millis String**, so every timestamp renders as a raw number to users; (2) the `unreadCount`/`observeNotifications` uid is captured once at ViewModel construction and is never rebound, so a VM built during the cold-start auth race binds to `userId = ""` for its lifetime (empty list + zero badge forever); (3) the "Mark all read" batch fires N sequential Firestore round-trips instead of one, and the primary VM test masks the epoch/ISO bug by feeding ISO fixtures the repo never produces.

The permission flow is largely correct (gate set on both Enable and Not-now, pessimistic default, API-33 guard) but the `permissionAlreadyRequested` naming and the redundant `unreadCount` are minor. Overall the feature will compile and pass its (fixture-biased) tests but ships user-visible wrong timestamps and a badge that silently breaks under a realistic launch race.

## Critical Issues

### CR-01: `createdAt` format contract mismatch — timestamps render as raw epoch-millis numbers

**File:** `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryScreen.kt:318-340`
**Issue:** `relativeTimestamp()` calls `Instant.parse(isoTimestamp)`, which only accepts ISO-8601 (e.g. `2026-07-17T12:00:00Z`). But the data layer stores `createdAt` as an **epoch-millis String** — confirmed in three places:
- `NotificationRepositoryImpl.toNotificationEntity` (`data/.../NotificationRepositoryImpl.kt:160-165`): `is Timestamp -> raw.toDate().time.toString()` → `"1752754800000"`.
- `NotificationMapper.toDomain` (`data/.../NotificationMapper.kt:23`): passes `createdAt` through unchanged with the comment "`createdAt` stays a String (epoch-millis)".
- `08-RESEARCH.md:295` and `08-01-SUMMARY.md:51`: "epoch-millis string, consistent with other entities".

`Instant.parse("1752754800000")` throws `DateTimeParseException`, so the `catch (_: Exception)` on line 337 returns the raw input, and the row displays `1752754800000` to the user. Every notification row is affected in production. The gate `if (item.createdAt.isNotBlank())` (line 272) does not help — an epoch-millis string is non-blank. This is masked in tests because both `NotificationHistoryViewModelTest` and `NotificationHistoryScreenTest` hardcode `createdAt = "2026-07-17T12:00:00Z"` (ISO), a value the repository never produces.

**Fix:** Parse epoch-millis in `relativeTimestamp`, matching the data-layer contract. Keep the ISO fallback if you also want to be robust to server-side ISO strings.
```kotlin
internal fun relativeTimestamp(rawCreatedAt: String): String {
    val instant = rawCreatedAt.toLongOrNull()
        ?.let { Instant.ofEpochMilli(it) }
        ?: runCatching { Instant.parse(rawCreatedAt) }.getOrNull()
        ?: return "" // unparseable: render nothing rather than a raw number
    val now = Instant.now()
    val minutesAgo = ChronoUnit.MINUTES.between(instant, now)
    val hoursAgo = ChronoUnit.HOURS.between(instant, now)
    val daysAgo = ChronoUnit.DAYS.between(instant, now)
    return when {
        minutesAgo < 1 -> "Just now"
        minutesAgo < 60 -> "$minutesAgo min ago"
        hoursAgo < 24 -> "$hoursAgo hours ago"
        daysAgo == 1L -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            .withZone(ZoneId.systemDefault()).format(instant)
    }
}
```
Additionally, add a ViewModel/UI test that uses an epoch-millis fixture (e.g. `createdAt = "1752754800000"`) so the correct format is regression-guarded.

### CR-02: Unread badge and notification list bind to `userId = ""` under the cold-start auth race

**File:** `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryViewModel.kt:59-65, 71-76`
**Issue:** The uid is captured **once at ViewModel construction** and never rebound to auth-state changes:
- `unreadCount` (line 60): `observeUnreadCount(authRepository.currentUser.value?.uuid ?: "")` — evaluated in the property initializer.
- `observeNotifications()` (line 72): `val uid = currentUserId ?: run { ...isLoading = false...; return }` — evaluated once in `init`.

`currentUser` is a `StateFlow<User?>` (`AuthRepository.kt:16`) whose value is `null` until the profile loads (`AuthRepositoryImpl` sets it asynchronously on the auth-state transition). `koinViewModel()` constructs this VM the first time the tab composes; if that composition wins the race against profile load, `unreadCount` is permanently wired to `observeUnreadCount("")` → DAO `WHERE userId = ""` → `0` for the VM's entire lifetime, and `observeNotifications` returns early leaving `notifications` empty with no retry. Because the VM is nav/activity-scoped and shared across `CustomerTabScreen`/`SellerTabScreen`, the badge and history stay dead until process death — not just a first-frame flicker. `.value?.uuid` reads a point-in-time snapshot, not a subscription, so it never self-heals.

**Fix:** Drive both flows off the auth `StateFlow` with `flatMapLatest` so they rebind when the uid appears/changes:
```kotlin
val unreadCount: StateFlow<Int> = authRepository.currentUser
    .map { it?.uuid }
    .distinctUntilChanged()
    .flatMapLatest { uid ->
        if (uid.isNullOrBlank()) flowOf(0)
        else notificationRepository.observeUnreadCount(uid)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

init {
    authRepository.currentUser
        .map { it?.uuid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else notificationRepository.observeNotifications(uid)
        }
        .onEach { list -> _state.update { it.copy(notifications = list, isLoading = false, hasUnread = list.any { n -> !n.isRead }) } }
        .catch { e -> _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Unknown error") } }
        .launchIn(viewModelScope)
}
```
Note this also fixes the case where a sign-out/sign-in on the same process would otherwise leave the badge bound to the previous user's (now empty) cache.

### CR-03: `SharingStarted.Eagerly` on `unreadCount` never stops the Firestore listener; combined with CR-02 hides an empty-uid query

**File:** `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryViewModel.kt:59-65`
**Issue:** `unreadCount` uses `SharingStarted.Eagerly`. `observeUnreadCount` is a pure Room `Flow` (`NotificationDao.kt:22-23`), so eager collection itself is cheap — but the eager start means the (empty-uid, from CR-02) query is subscribed immediately and, being a `StateFlow` cached over `viewModelScope`, the wrong value is latched and served to every collector without a re-evaluation opportunity. Even after CR-02 is fixed, `Eagerly` keeps the collection alive for the full VM lifetime with no `WhileSubscribed` timeout; when the VM is shared across two tab screens this is a live upstream that never quiesces. The doc comment ("Eagerly started so the count is always current regardless of collector presence") is not a correctness requirement here — the badge composables collect via `collectAsStateWithLifecycle`, so `WhileSubscribed(5_000)` keeps the value current while any tab is on-screen and lets it drop otherwise.

**Fix:** Use `SharingStarted.WhileSubscribed(5_000)` (see CR-02 snippet). This bounds the subscription to actual UI presence and preserves the value across configuration changes / brief tab switches.

## Warnings

### WR-01: "Mark all read" fires N sequential Firestore writes instead of one batch

**File:** `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryViewModel.kt:124-135`
**Issue:** `markAllRead()` loops `unreadItems.forEach { markAsRead(it.id) }`, and each `markAsRead` (`NotificationRepositoryImpl.kt:109-126`) does an `await()`ed Firestore `update` per document. With, say, 30 unread notifications this is 30 sequential network round-trips on `viewModelScope`, and a partial failure leaves an inconsistent read state with only per-item `Timber.e` logging and no user feedback. This is a correctness/robustness concern (partial completion, no atomicity), not merely performance.
**Fix:** Add a `markAllAsRead(userId)` repository method backed by a single Room `UPDATE ... WHERE userId = :uid AND isRead = 0` plus a single Firestore `WriteBatch`, and call it once. At minimum, run the per-item calls concurrently (`unreadItems.map { async { markAsRead(it.id) } }.awaitAll()`) and surface a snackbar if any fail.

### WR-02: `refresh()` is a no-op that cannot recover from the error state it clears

**File:** `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryViewModel.kt:137-145`
**Issue:** Pull-to-refresh clears `errorMessage` and toggles `isRefreshing` on/off within the same coroutine with no suspension in between, so the spinner never visibly appears and, more importantly, nothing re-subscribes the notification flow. If `observeNotifications` previously errored (line 77 `catch`), that flow terminated — `refresh()` does not restart it, so the screen's own copy in the docstring ("Pull down to retry") is false. The `@Suppress("UNUSED_VARIABLE") val uid = currentUserId ?: return` is dead scaffolding.
**Fix:** Make refresh actually re-establish the collection (e.g. cancel + relaunch the `observeNotifications` job, or expose a repo `refresh()` that re-syncs Firestore→Room), and only set `isRefreshing = false` after that completes. Remove the unused `uid`.

### WR-03: Error snackbar always shows generic copy, discarding the real error and mislabeling non-load failures

**File:** `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryScreen.kt:102-107`
**Issue:** The `LaunchedEffect(state.errorMessage)` shows the fixed string "Couldn't load notifications. Pull down to retry." regardless of the actual `errorMessage`, and immediately dispatches `OnDismissError`. Because refresh cannot actually retry (WR-02), the instruction is misleading. Also, keying the effect on `state.errorMessage` while the effect itself dispatches `OnDismissError` (which nulls `errorMessage`) is fragile: if two distinct errors arrive back-to-back with the same message string, the second will not re-trigger because the key did not change between recompositions after the first was cleared. Prefer a one-shot `Channel` effect for errors, consistent with the navigation effect already in this VM.
**Fix:** Route errors through a one-shot effect channel (like `navigationEffect`) instead of a state field, or key the snackbar on a monotonically increasing error id. Keep the user-facing copy but only claim "Pull down to retry" once WR-02 makes retry real.

### WR-04: Newest-first ordering re-sorts by `createdAt` **string**, which is wrong for epoch-millis of unequal length and duplicates DAO work

**File:** `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryScreen.kt:163-166`
**Issue:** `items(items = state.notifications.sortedByDescending { it.createdAt }, ...)` sorts the `createdAt` **String** lexicographically. The DAO already returns rows `ORDER BY createdAt DESC` (`NotificationDao.kt:16`), so this re-sort is redundant; worse, lexicographic ordering of epoch-millis strings only coincides with numeric ordering while all values have the same digit count. Any mix of lengths (e.g. a legacy/malformed row, or a `0`/empty `createdAt` produced by the `else -> ""` branch in `toNotificationEntity`) sorts to the wrong position. Sorting inside `items(...)` also runs on every recomposition of the list.
**Fix:** Trust the DAO ordering and drop the re-sort: `items(items = state.notifications, key = { it.id }) { ... }`. If a client-side guarantee is desired, sort numerically once in the VM: `sortedByDescending { it.createdAt.toLongOrNull() ?: Long.MIN_VALUE }`.

### WR-05: `handleItemClick` navigates even when `markAsRead` fails, and can send a deep-link with a blank id

**File:** `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryViewModel.kt:105-122`
**Issue:** Two robustness gaps. (1) `resolveNavDestination` reads `item.sellerOrderId` / `item.productId` / `item.orderId`, all of which default to `""` on the domain model (`Notification.kt:18-21`). A malformed or mistyped notification (e.g. a `new_review` row missing `productId`) will navigate to `ProductReviews("", "")` or `OrderDetail("")`, landing the user on a broken detail screen with an empty key. (2) The `else` branch (line 120) treats every unknown `type` as an `OrderDetail(orderId)` — a `device_login` notification (which the UI explicitly renders an icon/label for, Screen lines 297/307) has no `orderId`, so tapping it deep-links to `OrderDetail("")`.
**Fix:** Guard the destination fields and make unknown/id-less types a no-op (stay on the list) rather than navigating to an empty detail:
```kotlin
private fun resolveNavDestination(item: Notification): NavigationDestination? = when (item.type) {
    FCM_TYPE_NEW_ORDER -> item.sellerOrderId.ifBlank { null }?.let { NavigationDestination.SellerOrder(it) }
    FCM_TYPE_NEW_REVIEW -> item.productId.ifBlank { null }?.let { NavigationDestination.ProductReviews(it, item.productTitle) }
    FCM_TYPE_ORDER_STATUS -> item.orderId.ifBlank { null }?.let { NavigationDestination.OrderDetail(it) }
    else -> null
}
```
Only `send(...)` when non-null.

### WR-06: `permissionAlreadyRequested` state and `LaunchedEffect(Unit)` gate can miss a permission grant that happens between resume and load

**File:** `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabScreen.kt:92-105` (and `SellerTabScreen.kt:105-115`)
**Issue:** The rationale-gate `LaunchedEffect(Unit)` reads `notificationsEnabled` at the moment it runs. `notificationsEnabled` is initialized from `checkSelfPermission` at composition and also updated by `LifecycleResumeEffect`, but the `LaunchedEffect(Unit)` body captures the value at first composition and only runs once (`Unit` key). If the permission state changes after first composition but the gate has not yet been persisted (e.g. the user grants via system settings on a very first launch before the effect body executes), the dialog decision is based on a stale snapshot. More concretely: because the customer screen stores the loaded gate in `permissionAlreadyRequested` but the seller screen uses a local `val requested`, the two call sites diverge for no reason, increasing the surface for drift. This is a WARNING (edge-timing) rather than a BLOCKER because the DataStore gate still prevents re-nag on the common path.
**Fix:** Recompute the show condition against current `notificationsEnabled` inside the effect and/or move the gate check into a `snapshotFlow { notificationsEnabled }`-driven effect. Extract the identical rationale/permission block from `CustomerTabScreen` and `SellerTabScreen` into one reusable composable (e.g. `rememberNotificationPermissionGate(...)`) so the two screens cannot diverge (see also WR-07).

### WR-07: Rationale + permission flow is duplicated verbatim across Customer and Seller tab screens

**File:** `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabScreen.kt:63-140` and `app/src/main/java/com/wenubey/wenucommerce/seller/SellerTabScreen.kt:75-136`
**Issue:** The permission-state `remember`, the `LifecycleResumeEffect` recheck, the `LaunchedEffect` gate, the `rememberLauncherForActivityResult`, and the `NotificationPermissionRationaleDialog` invocation are copy-pasted between the two screens with only trivial differences. This is exactly the kind of security-sensitive flow (permission request + once-per-install gate) where two copies will drift — the divergence in WR-06 (`permissionAlreadyRequested` field vs local `requested`) is already an instance. Duplicated permission logic is a maintainability + correctness risk.
**Fix:** Extract a single composable/helper, e.g. `@Composable fun NotificationPermissionGate(notificationPreferences, onNotificationsEnabledChanged): Boolean`, and consume it from both tab screens.

### WR-08: `onNotificationsClick` starts an implicit settings Intent with no `try/catch` and no `FLAG_ACTIVITY_NEW_TASK` safeguard

**File:** `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerTabScreen.kt:196-207` and `app/src/main/java/com/wenubey/wenucommerce/seller/SellerTabScreen.kt:199-209`
**Issue:** `context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)...)` can throw `ActivityNotFoundException` on OEM ROMs / restricted profiles that do not resolve the action. There is no `resolveActivity`/`try-catch` guard, so a missing settings activity crashes the app from the Profile tap. `LocalContext.current` inside a Composable is an Activity context here so the flag is usually fine, but the unguarded launch is the real defect.
**Fix:** Wrap in `runCatching { context.startActivity(intent) }.onFailure { /* fallback to ACTION_APP_NOTIFICATION_SETTINGS-less ACTION_APPLICATION_DETAILS_SETTINGS, or show a snackbar */ }`.

## Info

### IN-01: `LaunchedEffect(viewModel.navigationEffect)` uses a new Flow reference as its key

**File:** `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryScreen.kt:88`
**Issue:** `receiveAsFlow()` (VM line 51) returns a stable property here, so keying on `viewModel.navigationEffect` is fine in practice, but keying a `LaunchedEffect` on a Flow is a smell — if the property were ever changed to return a fresh flow per access, the effect would relaunch and drop buffered events. Conventionally these one-shot collectors key on `Unit` or `viewModel`.
**Fix:** `LaunchedEffect(Unit) { viewModel.navigationEffect.collect { ... } }`.

### IN-02: `NotificationHistoryViewModelTest.OnDismissError` test name/comment do not match what it exercises

**File:** `app/src/test/java/com/wenubey/wenucommerce/notification/NotificationHistoryViewModelTest.kt:218-232`
**Issue:** The comment says "Force an error into state manually via OnRefresh with repo returning error" but the test never induces an error — it just dispatches `OnDismissError` on a clean state and asserts `errorMessage` is null (which is the default). The test passes vacuously and provides no real coverage of the dismiss-clears-error behavior.
**Fix:** Emit an error via the fake (`FakeNotificationRepository` currently cannot fail `observeNotifications`; add an error-emitting hook), assert `errorMessage != null`, then dispatch `OnDismissError` and assert it becomes null. Remove the misleading comment.

### IN-03: `FakeNotificationRepository` cannot simulate observe/markAsRead failure paths

**File:** `app/src/test/java/com/wenubey/wenucommerce/testing/fakes/FakeNotificationRepository.kt:9-44`
**Issue:** The fake always succeeds on `observeNotifications` (returns the happy `_notifications` flow) and only lets `markAsRead` return a preset `Result`. The VM's error `catch` (VM line 77) and `markAsRead().onFailure` logging are therefore untested. `observeNotificationsCalls` / `observeUnreadCountCalls` counters are set but never asserted.
**Fix:** Add a way to make `observeNotifications` emit via a channel that can throw (or expose a `MutableSharedFlow` you can complete exceptionally), enabling the error-path tests referenced in CR-01/IN-02.

### IN-04: `NotificationHistoryScreenTest` documents itself as authored-but-not-run

**File:** `app/src/androidTest/java/com/wenubey/wenucommerce/notification/NotificationHistoryScreenTest.kt:24-27`
**Issue:** The class doc states "Device run is deferred per project convention" and the tests mock the entire ViewModel with `mockk(relaxed = true)`, so they validate composable rendering only, not the VM↔UI contract. `verify { vm.onAction(any()) }` (line 131) accepts any action, so the row-tap test would pass even if the wrong action were dispatched. This is acceptable as a smoke test but should not be counted as behavioral coverage of item-click semantics.
**Fix:** Use `slot<NotificationHistoryAction>()` (already imported but unused) and assert the captured action is `OnItemClick` with the expected item.

### IN-05: Hardcoded ARGB colors bypass the Material 3 theme (project rule: Material 3 only, themed colors)

**File:** `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerProfileScreen.kt:118,136`, `app/src/main/java/com/wenubey/wenucommerce/notification/notification_history/NotificationHistoryScreen.kt:296`, `app/src/main/java/com/wenubey/wenucommerce/seller/SellerProfileScreen.kt:299-305`
**Issue:** Literal colors like `Color(0xFFF44336)` (red for disabled notifications / sign-out) and `Color(0xFFFF9800)` (review star) are used instead of `MaterialTheme.colorScheme.error` / a theme token. CLAUDE.md mandates Material 3 with themed colors; these literals will not adapt to dark theme / dynamic color and duplicate a semantic that `colorScheme.error` already provides. Note `CustomerProfileScreen.kt:118` also fully-qualifies `androidx.compose.material3.MaterialTheme.colorScheme.onSurface` inline, indicating a missing import cleanup.
**Fix:** Replace the red literals with `MaterialTheme.colorScheme.error`; keep brand-specific accents (star gold) as a named theme extension rather than an inline literal. Add the `MaterialTheme` import and drop the FQN at line 118.

### IN-06: Leftover `//TODO Refactor Later` markers and dead click handlers in Profile screens

**File:** `app/src/main/java/com/wenubey/wenucommerce/customer/CustomerProfileScreen.kt:36`, `app/src/main/java/com/wenubey/wenucommerce/seller/SellerProfileScreen.kt:45,58` and empty lambdas at `CustomerProfileScreen.kt:101,109,128,137` / `SellerProfileScreen.kt:127,185,197,203,209,215`
**Issue:** Multiple `//TODO Refactor Later` comments and no-op `onClick = { }` / `{ /* Navigate to ... */ }` handlers (Wishlist, Addresses, Settings, Sign Out, Edit Shop Profile, Payment/Shipping/Analytics/Help). These are pre-existing placeholders surfaced by this phase's edits; the notification affordance is wired but its siblings are dead. Not a Phase-8 regression, but flagged since these files are in scope.
**Fix:** Track the dead handlers in `PRODUCT_BUGS_AND_GAPS.md` and remove the stale `Refactor Later` markers or convert them to specific TODOs with issue references.

## Structural Notes

- **Auth-uid scoping (T-08-12 / T-08-13):** Verified as structurally sound. `observeNotifications`/`observeUnreadCount` are always called with the current uid, and `markAsRead(notificationId)` resolves the owning Firestore doc from `NotificationRepositoryImpl.currentUserId`, which is bound to the auth-state listener (`AuthRepositoryImpl.kt:87,95`) and cleared on sign-out. No cross-user id ever crosses a call boundary. The residual risk is the *staleness* captured in CR-02 (empty-uid binding), not cross-user disclosure.
- **DataStore gate (NOTF-05 / T-08-14):** Correct — `setNotificationPermissionRequested(true)` is called on both Enable and Not-now in both tab screens, with a pessimistic `true` default while loading, so no re-nag. The only concern is the duplication (WR-07) and the timing edge (WR-06).
- **Type-safe routes:** `NotificationHistory` route and all deep-link targets use type-safe `@Serializable` destinations — compliant with the project convention.

---

_Reviewed: 2026-07-17_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
