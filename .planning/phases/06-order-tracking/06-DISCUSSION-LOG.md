# Phase 6: Order Tracking & Management — Discussion Log

**Date:** 2026-06-15
**Mode:** discuss (default)
**Areas selected by user:** Multi-seller order architecture, Status timeline storage + transition authority, Customer-facing list & detail UX, Tracking number + cancellation policy

---

## Area 1 — Multi-seller order architecture

**Q:** One order doc with denormalized sellerIds, split into sub-orders, or split with parent-only timeline?
**Options:** A (one doc, shared status), B (split with per-seller timelines), B' (split but aggregate timeline in v1)
**User selected:** **B — split into seller sub-orders.**
**Notes:** Cleaner Firestore rules, per-seller FCM messages are meaningful, accurate forward-only semantics per seller. Single Stripe PaymentIntent stays.

## Area 2 — Status timeline storage + transition authority

**Q:** Embedded array vs subcollection? Rules vs callable function vs hybrid?
**Options:** Embedded + hybrid (rules + Firestore trigger FCM), embedded + callable, subcollection + callable
**User selected:** **Embedded `statusHistory` + Firestore rules enforce forward-only/append-only + `onOrderStatusChange` Firestore trigger for FCM.**
**Notes:** Sellers can advance status offline (write queues in Firestore SDK). FCM via trigger keeps notification logic separate from write path. Roadmap plan 06-04 already named the trigger.

## Area 3 — Customer-facing list & detail UX

**Q1:** List grouping?
**Options:** Filter chips above flat list, two tabs (Active/Completed), flat with no filter
**User selected:** **Filter chips (All / Active / Delivered / Cancelled).**

**Q2:** Timeline visual?
**Options:** Vertical stepper per seller (collapsed if multi-seller), horizontal milestone bar, single aggregated timeline
**User selected:** **Vertical stepper per seller, collapsed by default if multi-seller.**
**Notes:** Single-seller orders skip the collapsible header. Tracking number rendered at the SHIPPED step.

**Q3:** Sync behavior?
**Options:** Room Flow + FCM-driven sync + pull-to-refresh fallback; Room Flow + open Firestore listener; Room Flow + pull-to-refresh only
**User selected:** **Room Flow + FCM-driven sync + pull-to-refresh fallback.**

## Area 4 — Tracking number + cancellation policy

**Q1:** Tracking number entry richness?
**Options:** Free text + copy chip, carrier picker + tappable URL, free text + optional URL
**User selected:** **Free text only, copy-to-clipboard chip.**
**Notes:** Carrier picker deferred to v2.

**Q2:** Cancellation policy + payment handling?
**Options:** Seller-only + manual refund; both can cancel + auto-refund; seller-only + auto-refund
**User selected:** **Seller-only cancel + auto-refund via Stripe (atomic in a Cloud Function callable).**
**Notes:** Customer self-cancel deferred. Refund is partial against the parent PaymentIntent, sized to the sub-order's share (subtotal + shippingShare − discountShare). Cancel requires online; status-advance stays offline-capable.

---

## Claude's Discretion (no decision needed)

- Aggregate status badge color scheme
- Stepper visual styling (line vs dot thickness, colors)
- Section collapse animation
- Copy-toast wording for tracking number
- Refund footer typography
- Exact short-hash format for order IDs in list rows
- Empty-state illustration & copy

## Scope Creep — Deferred (recorded in CONTEXT.md `<deferred>`)

- Stripe Connect / split payouts
- Carrier picker + tracking URL
- Customer self-cancel
- In-app refund status timeline from Stripe webhooks
- Returns / RMA flow
- Notification channel split (Phase 8)
- Multi-seller notification debouncing
