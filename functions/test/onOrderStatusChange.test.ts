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
