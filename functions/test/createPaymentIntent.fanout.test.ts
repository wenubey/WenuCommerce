/**
 * createPaymentIntent fan-out tests — Plan 06-01 Task 4.
 *
 * Strategy: exercise the pure helpers (`allocateProRata`, `allocateDiscount`)
 * directly. This validates the allocation contract without spinning up the
 * Firestore emulator inside Jest. The end-to-end fan-out (batch write of
 * 1 parent + N children) is structurally guaranteed by static review of the
 * single batch.commit() call in index.ts and is exercised in the rules
 * emulator test in Task 5 (which writes real docs through the rules).
 */
import {
  allocateProRata,
  allocateDiscount,
} from "../src/index";
import * as fs from "fs";
import * as path from "path";

const indexSrc = fs.readFileSync(
  path.resolve(__dirname, "../src/index.ts"),
  "utf-8",
);

describe("createPaymentIntent — payment-gated (fan-out moved to webhook)", () => {
  // Isolate the createPaymentIntent function body.
  const body = indexSrc.slice(
    indexSrc.indexOf("export const createPaymentIntent"),
    indexSrc.indexOf("export const stripeWebhook"),
  );

  it("stashes a checkoutSessions doc with AWAITING_PAYMENT status", () => {
    expect(body).toMatch(/collection\("checkoutSessions"\)/);
    expect(body).toMatch(/AWAITING_PAYMENT/);
  });

  it("does NOT materialise /orders or /sellerOrders (no batch write pre-payment)", () => {
    // The fan-out used db.batch()/batch.set(...). Those must be gone from
    // createPaymentIntent — order creation happens only in the webhook now.
    expect(body).not.toMatch(/db\.batch\(\)/);
    expect(body).not.toMatch(/batch\.set\(/);
  });

  it("(N4) rejects sub-50c orders instead of silently clamping with Math.max(50)", () => {
    // The old Math.max(50, net) clamp diverged charged vs stored totals.
    expect(body).not.toMatch(/Math\.max\(\s*50/);
    expect(body).toMatch(/netTotalCents\s*<\s*50/);
    expect(body).toMatch(/below the minimum chargeable/i);
  });
});

describe("createPaymentIntent — seller fan-out (allocators)", () => {
  it("fans out 2 sellerOrders for a 2-seller cart (pro-rata shipping by subtotal)", () => {
    // Seller A: $40 subtotal. Seller B: $60. Shipping $10 total.
    const sellerSubs = new Map<string, number>([
      ["A", 4000],
      ["B", 6000],
    ]);
    const result = allocateProRata(sellerSubs, 10000, 1000);
    expect(result.get("A")).toBe(400);
    expect(result.get("B")).toBe(600);
    const sum = Array.from(result.values()).reduce((a, b) => a + b, 0);
    expect(sum).toBe(1000);
  });

  it("allocates shipping pro-rata when subtotals are uneven", () => {
    // 25% / 75% split of $20 shipping.
    const sellerSubs = new Map<string, number>([
      ["X", 2500],
      ["Y", 7500],
    ]);
    const result = allocateProRata(sellerSubs, 10000, 2000);
    expect(result.get("X")).toBe(500);
    expect(result.get("Y")).toBe(1500);
  });

  it("applies single-seller coupon entirely to the target seller", () => {
    const items = new Map<string, any[]>([
      ["A", [{ productId: "p-a1" }, { productId: "p-a2" }]],
      ["B", [{ productId: "p-b1" }]],
    ]);
    const sellerSubs = new Map<string, number>([
      ["A", 4000],
      ["B", 6000],
    ]);
    // Coupon targets only p-a1 -> all $5 discount goes to A.
    const result = allocateDiscount(
      items as any,
      sellerSubs,
      10000,
      500,
      ["p-a1"],
    );
    expect(result.get("A")).toBe(500);
    expect(result.get("B")).toBe(0);
  });

  it("rounding remainder lands on last seller so sum equals total exactly", () => {
    // 33% / 33% / 34% with $100 — rounding remainder must land on last seller.
    const sellerSubs = new Map<string, number>([
      ["A", 1000],
      ["B", 1000],
      ["C", 1000],
    ]);
    const totalCents = 100;
    const result = allocateProRata(sellerSubs, 3000, totalCents);
    const sum = Array.from(result.values()).reduce((a, b) => a + b, 0);
    expect(sum).toBe(totalCents);
    // Last seller (C) is the one that absorbs the remainder.
    // First two get 33 each by rounding; C gets 100 - 66 = 34.
    expect(result.get("A")).toBe(33);
    expect(result.get("B")).toBe(33);
    expect(result.get("C")).toBe(34);
  });

  it("cart-wide coupon (no targetProductIds) falls back to pro-rata", () => {
    const items = new Map<string, any[]>([
      ["A", [{ productId: "p-a1" }]],
      ["B", [{ productId: "p-b1" }]],
    ]);
    const sellerSubs = new Map<string, number>([
      ["A", 4000],
      ["B", 6000],
    ]);
    const result = allocateDiscount(items as any, sellerSubs, 10000, 1000, []);
    expect(result.get("A")).toBe(400);
    expect(result.get("B")).toBe(600);
  });
});
