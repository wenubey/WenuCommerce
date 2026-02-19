# Phase 1: Room Foundation - Context

**Gathered:** 2026-02-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish Room as the single source of truth for all data reads. Migrate existing repositories off raw Firestore flows so the app works offline with cached data. Deliver a connectivity indicator so users know when they're offline. This phase does NOT include offline writes (Phase 2) or any new features — it's the architectural migration that everything else depends on.

</domain>

<decisions>
## Implementation Decisions

### Connectivity indicator
- Top banner, visible on every screen (global, not per-screen)
- Warning yellow/amber color tone to signal degraded experience
- Includes a no-wifi icon + "No internet connection" text
- Overlays on top of content (no layout shift)
- Animates in (slide down) and out (slide up) — smooth transitions
- Auto-dismisses immediately when connectivity returns (no "Back online" message)

### Offline browsing experience
- The offline banner is the sole staleness cue — no "last synced" timestamps or visual degradation on content
- First launch with no network: empty state with friendly illustration + "Connect to the internet to get started" + retry button
- Uncached product images show a generic product placeholder icon — layout stays consistent
- All cached content is fully interactive offline — browsing, product detail, categories all work from cache
- Prices shown as-is from cache — no disclaimers; accuracy is validated at checkout time
- Search works against cached Room data — results may be incomplete but functional
- Deleted products (server-side) are handled on reconnect: show cached data while offline, then show "product unavailable" after sync detects deletion

### Sync & loading strategy
- Pull-to-refresh available on content screens + automatic background sync
- Firestore snapshot listeners stay active for real-time updates — data pushes to Room as it arrives
- On app launch with network: navigate to home immediately with progressive loading — show content as it syncs in
- Loading indicator: shimmer/skeleton placeholders mimicking content layout (product cards, category lists)
- Shimmer appears both on first launch (empty Room) and during pull-to-refresh
- No additional feedback on pull-to-refresh completion — data just updates
- On sync failure: brief snackbar "Sync failed — showing cached data" then continue with cached content

### Claude's Discretion
- Shimmer skeleton exact design and card shapes
- Room entity field mapping and DAO query design
- Firestore listener lifecycle management (when to attach/detach)
- ConnectivityObserver implementation approach
- Schema version 1 entity design details
- Error retry logic for Firestore listeners

</decisions>

<specifics>
## Specific Ideas

- Connectivity banner should feel like Gmail's "No connection" bar but in amber/yellow instead of dark
- Progressive loading on first launch — user sees the home screen structure immediately, content fills in as sync progresses
- Everything tappable offline — the experience should feel like a fully working app, just with potentially stale data

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-room-foundation*
*Context gathered: 2026-02-19*
