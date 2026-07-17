# Phase 8: Notifications - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-17
**Phase:** 8-notifications
**Areas discussed:** History source, Permission timing, Channel mapping, Review deep-link target

---

## Notification history source (NOTF-08)

| Option | Description | Selected |
|--------|-------------|----------|
| Firestore notifications/{uid} → Room mirror | Functions write a notifications doc + FCM; app mirrors to Room; history reads Room | ✓ |
| Data-only FCM + app persists locally | Functions send data-only; app always handles + persists; no Firestore collection | |
| Hybrid (data-only + best-effort) | Data-only + local Room, no Firestore mirror | |

**User's choice:** Firestore notifications/{uid} → Room mirror.
**Notes:** Server-authoritative + Room-first (same as orders/sellerOrders). Complete history regardless of delivery/foreground/app-killed; survives reinstall.

---

## Permission timing (NOTF-05)

| Option | Description | Selected |
|--------|-------------|----------|
| Contextual (post-login) + rationale, graceful denial | Rationale dialog after login before system dialog; no nag on denial; Settings re-enable path | ✓ |
| Ask on first launch | Rationale + system dialog during onboarding | |
| Opt-in only (from settings) | A toggle in Profile/Settings requests when enabled | |

**User's choice:** Contextual post-login + rationale + graceful denial.
**Notes:** Android 13+ only; pre-33 = channels only, no runtime permission.

---

## Channel mapping (NOTF-06)

| Option | Description | Selected |
|--------|-------------|----------|
| Order Updates: order+new_order+review · Account: login · Promotions: empty | 3 channels with required names; review under Order Updates | ✓ |
| Keep review separate | review on its own channel (breaks the exactly-3-channel requirement) | |

**User's choice:** Order Updates (HIGH) ← order_status+new_order+new_review; Account (DEFAULT) ← device_login; Promotions (LOW) reserved/empty.
**Notes:** Centralise channel creation (currently scattered in MessagingService).

---

## Review-notification deep-link target (NOTF-04)

| Option | Description | Selected |
|--------|-------------|----------|
| SellerProductReviews screen (Phase 7) | Seller lands on their product's read-only reviews list | ✓ |
| Customer product detail | Lands on the public product detail | |

**User's choice:** SellerProductReviews (Phase 7 screen).
**Notes:** Extend MainActivity deep-link consumer with a new_review target.

---

## Claude's Discretion

- notifications Firestore doc shape, Room NotificationEntity + MIGRATION_9_10 (v10), permission mechanism (Accompanist vs manual ActivityResult), unread badge, notification de-dup.

## Deferred Ideas

- Promotional content + pipeline, notification grouping/quiet-hours/mute toggles, rich notifications — future phases.
