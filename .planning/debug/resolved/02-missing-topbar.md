---
status: resolved
trigger: "Missing TopAppBar and back arrow on QueueManagementScreen"
created: 2026-02-20T00:00:00Z
updated: 2026-02-20T17:00:00Z
symptoms_prefilled: true
goal: find_root_cause_only
---

## Current Focus

hypothesis: CONFIRMED — The global Scaffold in MainActivity ignores its own padding values, and the PendingSyncBanner overlay sits at Alignment.TopCenter using a Box overlay. The QueueManagementScreen's own Scaffold and TopAppBar are structurally sound. The root cause is that the PendingSyncBanner renders as a floating overlay on top of every screen including QueueManagementScreen, which physically obscures the TopAppBar. Additionally, the global Scaffold's padding is discarded (`{ _ ->`), and edge-to-edge is disabled, meaning the system status bar consumes space that is NOT compensated for inside QueueManagementScreen's TopAppBar — the banner sits exactly where the TopAppBar would appear.
test: Traced full rendering tree from MainActivity -> RootNavigationGraph -> QueueManagementScreen
expecting: Confirmed — banner overlay covers the TopAppBar
next_action: Report findings (diagnose-only mode)

## Symptoms

expected: TopAppBar with back arrow visible when navigating to Pending Sync Queue screen
actual: Screen content (empty state) renders correctly but no TopAppBar or back arrow is visible
errors: none (visual/UI issue only)
reproduction: Tap offline banner -> navigate to QueueManagementScreen (with banner still visible)
started: Phase 02-02 implementation

## Eliminated

- hypothesis: QueueManagementScreen has no Scaffold or TopAppBar at all
  evidence: Lines 51-64 of QueueManagementScreen.kt clearly show a Scaffold with a TopAppBar and a back arrow NavigationIcon. The composable is correctly implemented.
  timestamp: 2026-02-20

- hypothesis: QueueManagement route is not registered in the nav graph
  evidence: TabNavRoutes.kt lines 93-99 register composable<QueueManagement> and wire onNavigateBack to navController.popBackStack(). Registration is correct.
  timestamp: 2026-02-20

- hypothesis: Navigation call from banner is wrong
  evidence: MainActivity.kt line 124: `onTap = { navController.navigate(QueueManagement) }` — correct route object used.
  timestamp: 2026-02-20

## Evidence

- timestamp: 2026-02-20
  checked: QueueManagementScreen.kt (lines 43-99)
  found: Scaffold with TopAppBar is present and correctly implemented. NavigationIcon with ArrowBack is wired to onNavigateBack lambda. No issue here.
  implication: The TopAppBar exists in code. Something is hiding or overlaying it at runtime.

- timestamp: 2026-02-20
  checked: TabNavRoutes.kt (lines 93-99)
  found: composable<QueueManagement> registered. onNavigateBack = navController.popBackStack(). Correct.
  implication: Navigation routing is not the problem.

- timestamp: 2026-02-20
  checked: MainActivity.kt (lines 94-129)
  found: THREE stacked layout issues:
    1. Global Scaffold at line 98 with `snackbarHost` only — its padding is IGNORED: `{ _ ->` (line 100). This means the Box inside gets no top inset compensation.
    2. The PendingSyncBanner is rendered INSIDE the Box as a floating overlay using `Alignment.TopCenter` (line 115). It renders on TOP of whatever screen is active via NavHost — including QueueManagementScreen.
    3. `enableEdgeToEdge()` is commented out (line 46) with a TODO comment: "// TODO research this topBar issue and fix it". The developer already noted a top bar issue exists.
  implication: When the user taps the banner and navigates to QueueManagementScreen, the banner REMAINS VISIBLE (shouldShowBanner is still true since we are offline/have pending items). The banner overlay physically sits at the top of the screen, covering exactly where the QueueManagementScreen's TopAppBar would render.

- timestamp: 2026-02-20
  checked: PendingSyncBanner.kt (line 54)
  found: Banner applies `.statusBarsPadding()` to itself. It pushes itself down to sit below the system status bar. QueueManagementScreen's TopAppBar also renders below the system status bar. They occupy the same vertical space.
  implication: The amber banner Surface directly occludes the TopAppBar of QueueManagementScreen when both are visible simultaneously — which is always the case when navigation is triggered from the banner tap.

- timestamp: 2026-02-20
  checked: CustomerTabScreen.kt, SellerTabScreen.kt, AdminTabScreen.kt
  found: Each tab screen has its own nested Scaffold with topBar. These are nested Scaffolds inside the global MainActivity Scaffold. The QueueManagementScreen follows the same pattern.
  implication: Nested Scaffold pattern is used consistently. The issue is specific to the global banner overlay persisting on QueueManagementScreen.

## Resolution

root_cause: |
  The PendingSyncBanner is rendered as a persistent floating overlay (Box + Alignment.TopCenter) in MainActivity, sitting on top of the entire NavHost. When the user taps the banner and navigates to QueueManagementScreen, the banner does NOT dismiss — `shouldShowBanner` remains true because the device is still offline or has pending items. The banner's Surface (with statusBarsPadding) occupies the exact same vertical position as QueueManagementScreen's TopAppBar, physically covering it. The TopAppBar exists in the composable tree and is composed, but it is rendered beneath the amber banner Surface and therefore invisible to the user.

  Additionally, `enableEdgeToEdge()` is commented out in MainActivity (line 46) with a related TODO, which means edge-to-edge inset handling is incomplete — but this is a secondary concern. The primary cause is the overlay covering the TopAppBar.

  There is also a secondary contributing factor: the global Scaffold's padding is discarded with `{ _ ->` on line 100 of MainActivity, meaning nothing inside the Box accounts for system bars, but this is partially mitigated by the banner's own `statusBarsPadding()`.

fix: (not applied — diagnose-only mode)
  Two viable fix directions:
  1. DISMISS the banner automatically when navigating to QueueManagementScreen (simplest — call pendingSyncVm.dismissBanner() or hide the banner when current route is QueueManagement).
  2. EXCLUDE QueueManagementScreen from banner visibility — check the current back stack destination and suppress the banner overlay when on the QueueManagement route.
  3. Restore `enableEdgeToEdge()` and handle insets properly — this is a broader fix for the commented-out TODO but does not address the overlay issue on its own.

verification: n/a (diagnose-only)
files_changed: []

## Files Involved

- app/src/main/java/com/wenubey/wenucommerce/MainActivity.kt
    Lines 46, 98-129: Global Scaffold with floating banner overlay. enableEdgeToEdge() commented out. Banner rendered as persistent top overlay that is never dismissed on navigation.

- app/src/main/java/com/wenubey/wenucommerce/queue_management/QueueManagementScreen.kt
    Lines 51-64: TopAppBar is correctly implemented — not the source of the bug.

- app/src/main/java/com/wenubey/wenucommerce/core/connectivity/PendingSyncBanner.kt
    Line 54: statusBarsPadding() — banner positions itself exactly where QueueManagementScreen's TopAppBar renders.
