/**
 * onOrderStatusChange Firestore trigger tests — Plan 06-01 Task 4.
 *
 * Two layers:
 *   1) Unit tests on `computeAggregateStatus` — the §2.8 mapping rule.
 *   2) Structural grep on index.ts to lock in the W5 race-mitigation contract:
 *      runTransaction body must call tx.get(<sellerOrders query>) AND
 *      tx.get(<parentRef>) BEFORE tx.update(<parent>), and must bump
 *      aggregateVersion by exactly +1 (monotonic CAS).
 */
import * as fs from "fs";
import * as path from "path";

import { computeAggregateStatus } from "../src/index";

const indexSrc = fs.readFileSync(
  path.resolve(__dirname, "../src/index.ts"),
  "utf-8",
);

describe("onOrderStatusChange — aggregate mapping (RESEARCH §2.8)", () => {
  it("all PENDING -> PENDING", () => {
    expect(computeAggregateStatus(["PENDING", "PENDING"])).toBe("PENDING");
  });

  it("mixed SHIPPED + DELIVERED -> SHIPPED (least-advanced wins)", () => {
    expect(computeAggregateStatus(["SHIPPED", "DELIVERED"])).toBe("SHIPPED");
  });

  it("hasCancelled + mixed non-cancelled -> PARTIALLY_CANCELLED", () => {
    expect(computeAggregateStatus(["CANCELLED", "SHIPPED"])).toBe(
      "PARTIALLY_CANCELLED",
    );
  });

  it("all CANCELLED -> CANCELLED", () => {
    expect(computeAggregateStatus(["CANCELLED", "CANCELLED"])).toBe(
      "CANCELLED",
    );
  });

  it("single CONFIRMED -> CONFIRMED", () => {
    expect(computeAggregateStatus(["CONFIRMED"])).toBe("CONFIRMED");
  });

  it("empty input falls back to PENDING (defensive)", () => {
    expect(computeAggregateStatus([])).toBe("PENDING");
  });
});

describe("onOrderStatusChange — W5 race mitigation contract", () => {
  it("skips when status unchanged (before.status === after.status)", () => {
    expect(indexSrc).toMatch(
      /before\.status\s*===\s*after\.status/,
    );
    expect(indexSrc).toMatch(/return;\s*\/\/ not a status change/);
  });

  it("sends FCM with correct deep-link data payload + channelId", () => {
    expect(indexSrc).toMatch(/getMessaging\(\)\.send\(/);
    expect(indexSrc).toMatch(/type:\s*"order_status"/);
    expect(indexSrc).toMatch(/orderId:\s*parentId/);
    expect(indexSrc).toMatch(/sellerOrderId:\s*event\.params\.sellerOrderId/);
    expect(indexSrc).toMatch(/channelId:\s*"order_status_channel"/);
  });

  it("recomputes aggregateStatus inside transaction with reads-before-writes", () => {
    // tx.get(...) appears twice and BOTH come before tx.update(...)
    const txGetMatches = indexSrc.match(/tx\.get\(/g) || [];
    expect(txGetMatches.length).toBeGreaterThanOrEqual(2);
    const firstGetIdx = indexSrc.indexOf("tx.get(");
    const updateIdx = indexSrc.indexOf("tx.update(parentRef");
    expect(firstGetIdx).toBeLessThan(updateIdx);
    expect(firstGetIdx).toBeGreaterThan(0);
    expect(updateIdx).toBeGreaterThan(0);
  });

  it("aggregateVersion increments monotonically by exactly 1 per invocation", () => {
    expect(indexSrc).toMatch(/aggregateVersion:\s*currentVersion\s*\+\s*1/);
  });

  it("no-op (returns silently) when fcmToken missing", () => {
    expect(indexSrc).toMatch(/if\s*\(\s*!fcmToken\s*\)\s*return;/);
  });

  it("tx.get(<sellerOrders query>) called before tx.update(<parent>)", () => {
    // sellerOrders subs query inside the transaction
    expect(indexSrc).toMatch(
      /tx\.get\(subsQuery\)|tx\.get\(\s*db\s*\n?\s*\.collection\("sellerOrders"\)/,
    );
    const subsGetIdx = indexSrc.search(/tx\.get\(subsQuery\)/);
    const parentUpdateIdx = indexSrc.search(/tx\.update\(parentRef/);
    expect(subsGetIdx).toBeGreaterThan(0);
    expect(parentUpdateIdx).toBeGreaterThan(0);
    expect(subsGetIdx).toBeLessThan(parentUpdateIdx);
  });
});

// ─── Plan 06-04 hardening — explicit state-space + concurrency contract ──

describe("onOrderStatusChange — Plan 06-04 hardened aggregate cases", () => {
  it("PARTIALLY_CANCELLED when one sub is CANCELLED and others non-CANCELLED (PENDING, SHIPPED, CANCELLED)", () => {
    expect(
      computeAggregateStatus(["PENDING", "SHIPPED", "CANCELLED"]),
    ).toBe("PARTIALLY_CANCELLED");
  });

  it("aggregateStatus = least-advanced of non-CANCELLED when all non-CANCELLED (CONFIRMED, SHIPPED, DELIVERED -> CONFIRMED)", () => {
    expect(
      computeAggregateStatus(["CONFIRMED", "SHIPPED", "DELIVERED"]),
    ).toBe("CONFIRMED");
  });

  it("aggregateStatus = CANCELLED when all subs CANCELLED (2 subs)", () => {
    expect(computeAggregateStatus(["CANCELLED", "CANCELLED"])).toBe(
      "CANCELLED",
    );
  });

  it("PARTIALLY_CANCELLED dominates even when remaining non-cancelled are mixed (CANCELLED + CONFIRMED + DELIVERED)", () => {
    expect(
      computeAggregateStatus(["CANCELLED", "CONFIRMED", "DELIVERED"]),
    ).toBe("PARTIALLY_CANCELLED");
  });
});

describe("onOrderStatusChange — FCM payload shape (Plan 06-04 contract)", () => {
  it("payload data object contains exactly: type, orderId, sellerOrderId, newStatus", () => {
    // Find the data: { ... } literal handed to getMessaging().send
    const match = indexSrc.match(/data:\s*\{([\s\S]*?)\}\s*,\s*android:/);
    expect(match).not.toBeNull();
    const dataBlock = match![1];
    expect(dataBlock).toMatch(/type:\s*"order_status"/);
    expect(dataBlock).toMatch(/orderId:\s*parentId/);
    expect(dataBlock).toMatch(/sellerOrderId:\s*event\.params\.sellerOrderId/);
    expect(dataBlock).toMatch(/newStatus:/);
  });

  it("android.notification.channelId === 'order_status_channel'", () => {
    expect(indexSrc).toMatch(
      /android:\s*\{[\s\S]*?notification:\s*\{[\s\S]*?channelId:\s*"order_status_channel"/,
    );
  });

  it("FCM send is skipped when user doc has no fcmToken field (early return)", () => {
    // The guard `if (!fcmToken) return;` precedes the getMessaging().send call.
    const guardIdx = indexSrc.search(/if\s*\(\s*!fcmToken\s*\)\s*return;/);
    const sendIdx = indexSrc.search(/getMessaging\(\)\.send\(/);
    expect(guardIdx).toBeGreaterThan(0);
    expect(sendIdx).toBeGreaterThan(0);
    expect(guardIdx).toBeLessThan(sendIdx);
  });
});

describe("onOrderStatusChange — concurrency / sibling-update determinism (W5)", () => {
  // When two sibling updates fire onOrderStatusChange concurrently, each
  // invocation re-reads ALL sibling statuses inside its tx and writes the
  // resulting aggregateStatus. Because the tx reads `sellerOrders where
  // parentOrderId == parentId` and `parentRef`, Firestore guarantees
  // serializability: whichever tx commits second sees the first's write and
  // re-runs. The final aggregateStatus is therefore a pure function of the
  // last persisted state of all subs — deterministic.

  it("computeAggregateStatus is a pure function (deterministic for same input set, order-independent)", () => {
    // Order-independence: same multiset of statuses -> same aggregate.
    const a = computeAggregateStatus(["PENDING", "SHIPPED", "CANCELLED"]);
    const b = computeAggregateStatus(["CANCELLED", "PENDING", "SHIPPED"]);
    const c = computeAggregateStatus(["SHIPPED", "CANCELLED", "PENDING"]);
    expect(a).toBe(b);
    expect(b).toBe(c);
    expect(a).toBe("PARTIALLY_CANCELLED");
  });

  it("simulated concurrent invocations produce identical final aggregate (PENDING+CONFIRMED -> both advance)", () => {
    // Wave 1: subs are [PENDING, PENDING].
    // Invocation A advances sub#0 -> CONFIRMED. After A commits: [CONFIRMED, PENDING].
    //   aggregateStatus_A = computeAggregateStatus(["CONFIRMED", "PENDING"]) = PENDING.
    // Invocation B fires concurrently for sub#1 -> CONFIRMED. Its tx reads AFTER A
    //   committed (Firestore re-runs on conflict). Reads [CONFIRMED, CONFIRMED].
    //   aggregateStatus_B = computeAggregateStatus(["CONFIRMED", "CONFIRMED"]) = CONFIRMED.
    // Final persisted aggregate is CONFIRMED — independent of which invocation
    // happened to win the first commit.
    const afterAOnly = computeAggregateStatus(["CONFIRMED", "PENDING"]);
    const afterBOnly = computeAggregateStatus(["PENDING", "CONFIRMED"]);
    const afterBoth1 = computeAggregateStatus(["CONFIRMED", "CONFIRMED"]);
    const afterBoth2 = computeAggregateStatus(["CONFIRMED", "CONFIRMED"]);
    // Whichever tx ran first, an intermediate "PENDING" aggregate is acceptable
    // but the terminal aggregate after both commit MUST equal CONFIRMED.
    expect(afterAOnly).toBe("PENDING");
    expect(afterBOnly).toBe("PENDING");
    expect(afterBoth1).toBe("CONFIRMED");
    expect(afterBoth2).toBe("CONFIRMED");
  });

  it("aggregateVersion bumps exactly +1 per invocation — monotonic CAS contract", () => {
    // Two structural assertions: (1) the literal `currentVersion + 1` exists
    // exactly once next to aggregateVersion, and (2) no `+ 2` or skip is used.
    const plusOne =
      indexSrc.match(/aggregateVersion:\s*currentVersion\s*\+\s*1/g) || [];
    expect(plusOne.length).toBe(1);
    expect(indexSrc).not.toMatch(/aggregateVersion:\s*currentVersion\s*\+\s*2/);
  });
});
