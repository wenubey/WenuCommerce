---
phase: quick
plan: 1
type: execute
wave: 1
depends_on: []
files_modified:
  - data/src/main/java/com/wenubey/data/local/SyncManager.kt
autonomous: true
requirements: [QUICK-BUG-01]

must_haves:
  truths:
    - "Pull-to-refresh with no network emits SyncEvent.SyncFailed"
    - "Snackbar 'Sync failed — showing cached data' appears briefly after failed pull-to-refresh"
    - "Cached content remains visible after sync failure"
  artifacts:
    - path: "data/src/main/java/com/wenubey/data/local/SyncManager.kt"
      provides: "Manual sync with forced server fetch and reliable event emission"
      contains: "Source.SERVER"
  key_links:
    - from: "SyncManager.manualSync()"
      to: "SyncManager._syncEvents"
      via: "emit() in catch block"
      pattern: "_syncEvents\\.emit"
    - from: "SyncManager.syncEvents"
      to: "MainActivity LaunchedEffect"
      via: "SharedFlow collection"
      pattern: "syncManager\\.syncEvents\\.collect"
---

<objective>
Fix the sync failure snackbar not appearing when pull-to-refresh is triggered on CustomerHomeScreen with no network connection.

Purpose: The snackbar "Sync failed — showing cached data" must appear when manual sync fails offline, per the locked decision from plan 01-04. Currently it never shows because Firestore's `get()` silently falls back to its local cache instead of throwing.

Output: Fixed SyncManager.manualSync() that reliably emits SyncEvent.SyncFailed when offline.
</objective>

<execution_context>
@/Users/wenubey/.claude/get-shit-done/workflows/execute-plan.md
@/Users/wenubey/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@data/src/main/java/com/wenubey/data/local/SyncManager.kt
@app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt
@app/src/main/java/com/wenubey/wenucommerce/customer/customer_home/CustomerHomeViewModel.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Fix SyncManager.manualSync() to detect offline and emit failure reliably</name>
  <files>data/src/main/java/com/wenubey/data/local/SyncManager.kt</files>
  <action>
Two bugs cause the snackbar to never appear:

**Bug 1 — Firestore cache fallback hides the failure:**
`firestore.collection(PRODUCTS_COLLECTION).get().await()` uses `Source.DEFAULT`, which silently returns cached data when offline. No exception is thrown, so the `catch` block with `tryEmit(SyncEvent.SyncFailed(...))` never executes.

Fix: Change both `get()` calls in `manualSync()` to `get(com.google.firebase.firestore.Source.SERVER)`. This forces a server-only fetch that throws `FirebaseFirestoreException` when the device is offline, ensuring the catch block runs.

Add import: `import com.google.firebase.firestore.Source`

Change line ~94:
```kotlin
// BEFORE
val productsSnapshot = firestore.collection(PRODUCTS_COLLECTION).get().await()
// AFTER
val productsSnapshot = firestore.collection(PRODUCTS_COLLECTION).get(Source.SERVER).await()
```

Change line ~105:
```kotlin
// BEFORE
val categoriesSnapshot = firestore.collection(CATEGORIES_COLLECTION).get().await()
// AFTER
val categoriesSnapshot = firestore.collection(CATEGORIES_COLLECTION).get(Source.SERVER).await()
```

**Bug 2 — tryEmit can silently drop events:**
`tryEmit` is non-suspending and returns false if the buffer is full (extraBufferCapacity=1). Since `manualSync()` is already a suspend function, use the suspending `emit()` instead for guaranteed delivery.

Change line ~117 inside the catch block:
```kotlin
// BEFORE
_syncEvents.tryEmit(SyncEvent.SyncFailed("Sync failed — showing cached data"))
// AFTER
_syncEvents.emit(SyncEvent.SyncFailed("Sync failed — showing cached data"))
```

Do NOT change the `tryEmit` calls in `startSync()` — those are inside non-suspending `addSnapshotListener` callbacks where `emit()` cannot be used. The `startSync()` listeners use `callbackFlow.catch` which is a different code path that does work correctly.
  </action>
  <verify>
Build the project to confirm compilation:
```bash
cd /Users/wenubey/AndroidStudioProjects/WenuCommerce && ./gradlew :data:compileDebugKotlin 2>&1 | tail -5
```

Verify the changes are correct:
1. `Source.SERVER` import is present in SyncManager.kt
2. Both `get()` calls in `manualSync()` use `Source.SERVER`
3. The catch block uses `emit()` not `tryEmit()`
4. The `tryEmit` calls in `startSync()` are unchanged
  </verify>
  <done>
SyncManager.manualSync() uses Source.SERVER for both Firestore fetches, so offline pull-to-refresh throws an exception. The catch block uses suspending emit() to reliably deliver SyncEvent.SyncFailed to collectors. The snackbar in MainActivity will now appear when pull-to-refresh is triggered with no network.
  </done>
</task>

</tasks>

<verification>
1. `./gradlew :data:compileDebugKotlin` succeeds — no compile errors from Source import or emit() usage
2. Grep SyncManager.kt for `Source.SERVER` — two occurrences (products and categories)
3. Grep SyncManager.kt for `_syncEvents.emit(` in manualSync — one occurrence in catch block
4. Grep SyncManager.kt for `_syncEvents.tryEmit(` — two occurrences remain ONLY in startSync() catch blocks
</verification>

<success_criteria>
- SyncManager.manualSync() forces server-only Firestore fetch via Source.SERVER
- Offline manualSync() throws exception, caught by existing try/catch
- Catch block emits SyncEvent.SyncFailed reliably via suspend emit()
- startSync() real-time listeners remain unchanged (tryEmit in non-suspend context)
- Project compiles successfully
</success_criteria>

<output>
After completion, create `.planning/quick/1-fix-sync-failure-snackbar-not-showing-on/1-SUMMARY.md`
</output>
