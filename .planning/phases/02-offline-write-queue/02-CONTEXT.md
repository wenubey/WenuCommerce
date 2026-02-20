# Phase 2: Offline Write Queue - Context

**Gathered:** 2026-02-20
**Status:** Ready for planning

<domain>
## Phase Boundary

WorkManager-backed write queue that captures offline writes locally and auto-syncs to Firestore when connectivity is restored. Includes a pending-sync UI indicator and a queue management screen. No data is silently lost when the device is offline.

</domain>

<decisions>
## Implementation Decisions

### Pending sync indicator
- Global banner at the top of the screen (similar position to the existing offline banner)
- Shows count only: "3 items pending sync" — no breakdown by type
- Dismissible by the user; reappears only when the pending count increases (new offline write added)
- When offline AND pending items exist, show a single combined banner (e.g., "Offline — 3 items pending sync") instead of stacking two separate banners
- Amber/warning color tone to distinguish from the offline banner
- Shows a sync-in-progress animation (spinning icon or progress indicator) when the device reconnects and is actively syncing

### Queued action feedback
- When a user performs a write action while offline, show confirmation with a generic snackbar: "Saved locally — will sync when online"
- Snackbar only appears for offline actions — online writes go directly to Firestore without queuing
- Sellers and customers get the same snackbar + banner treatment — no role-specific differences
- First offline action shows the snackbar; subsequent rapid offline actions do not repeat it (banner is already visible)
- Certain high-stakes actions (e.g., checkout/payment) should be blocked while offline with a clear message — Claude determines which specific actions to block

### Sync completion & failure
- Successful sync is fully silent — banner simply disappears, no animation or confirmation
- Failed operations show an error snackbar: "Some items failed to sync"
- Auto-retry with a limit — retry automatically with backoff up to N times, then give up and notify
- Permanently failed operations (exhausted retries) stay in the queue marked as "failed" — user can see them and manually retry or discard

### Queue management
- Tapping the pending sync banner opens a full-screen queue management screen
- Each item in the list shows type + status only: "Cart update — Pending" or "Cart update — Failed"
- Users can retry a failed item or discard it; pending items have no actions (auto-managed)

### Claude's Discretion
- Exact retry count and backoff strategy
- Which specific high-stakes actions to block while offline
- Queue screen layout and styling details
- WorkManager constraints and scheduling details
- PendingOperationEntity schema design

</decisions>

<specifics>
## Specific Ideas

- Combined banner when offline + pending: merge the offline and pending-sync banners into one unified banner rather than stacking
- Snackbar de-duplication: only the first offline action triggers the snackbar, subsequent rapid actions rely on the banner for awareness

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-offline-write-queue*
*Context gathered: 2026-02-20*
