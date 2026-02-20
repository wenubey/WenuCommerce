---
status: resolved
phase: 02-offline-write-queue
source: [02-01-SUMMARY.md, 02-02-SUMMARY.md]
started: 2026-02-20T15:16:14Z
updated: 2026-02-20T17:00:00Z
---

## Current Test

[testing complete]

## Tests

### 1. App Launch After Database Migration
expected: App launches without crash. Home screen loads normally. No migration errors or blank screens.
result: pass

### 2. Offline Banner Appears
expected: Disable network (airplane mode or WiFi+data off). An amber banner appears at the top showing "No internet connection" with a WifiOff icon.
result: pass

### 3. Offline Banner Not Dismissible
expected: While offline with no pending operations, the amber banner should NOT show a close/dismiss (X) button. It stays visible as long as you're offline.
result: pass

### 4. Banner Tap Opens Queue Screen
expected: Tapping the offline banner navigates to a screen titled "Pending Sync Queue" with a back arrow in the top bar.
result: issue
reported: "I can not see the back arrow and top bar also"
severity: major

### 5. Queue Management Empty State
expected: The Pending Sync Queue screen shows centered text "No pending operations" when no items are queued.
result: pass

### 6. Back Navigation from Queue Screen
expected: Tapping the back arrow on the Queue Management screen returns to the previous screen.
result: pass

### 7. Online Recovery Hides Banner
expected: Re-enable network (turn off airplane mode). The amber banner disappears automatically once connectivity is restored.
result: pass

## Summary

total: 7
passed: 6
issues: 1
pending: 0
skipped: 0

## Gaps

- truth: "Tapping the offline banner navigates to a screen titled 'Pending Sync Queue' with a back arrow in the top bar"
  status: resolved
  reason: "User reported: I can not see the back arrow and top bar also"
  severity: major
  test: 4
  root_cause: "PendingSyncBanner overlay in MainActivity persists on all routes including QueueManagement. The banner's statusBarsPadding() positions it at the exact same vertical space as the TopAppBar, physically covering it."
  artifacts:
    - path: "app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt"
      issue: "Banner overlay (lines 113-126) rendered at Alignment.TopCenter with no route-aware suppression"
    - path: "app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncBanner.kt"
      issue: "statusBarsPadding() (line 54) places banner at same position as TopAppBar"
    - path: "app/src/main/java/com/wenubey/wenucommerce/queue_management/QueueManagementScreen.kt"
      issue: "Correctly implemented — not at fault"
  missing:
    - "Auto-dismiss banner when navigating to QueueManagement screen, or suppress banner on QueueManagement route"
  debug_session: ".planning/debug/02-missing-topbar.md"
