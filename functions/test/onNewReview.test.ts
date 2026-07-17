/**
 * onNewReview Firestore trigger tests — Plan 08-02 Task 1 (NOTF-03, D-04).
 *
 * Two layers (mirrors onOrderStatusChange.test.ts / submitReview.test.ts):
 *   1) Unit test on the pure exported helper `buildNewReviewBody` — no Firestore.
 *   2) Structural grep on index.ts locking the D-04 contract:
 *      - triggers on PRODUCTS/{productId}/REVIEWS/{reviewId}
 *      - resolves the recipient from PRODUCTS/{productId}.sellerId (server-side)
 *      - writes the notifications/{sellerId}/items doc (type "new_review")
 *        BEFORE the getMessaging().send call (history persists even if FCM fails)
 *      - FCM data payload carries productId + productTitle + notifId
 *      - android.notification.channelId === "order_updates_channel" (D-03)
 */
import * as fs from "fs";
import * as path from "path";

import { buildNewReviewBody } from "../src/index";

const indexSrc = fs.readFileSync(
  path.resolve(__dirname, "../src/index.ts"),
  "utf-8",
);

// Isolate the onNewReview function body so ordering assertions can't be
// satisfied by unrelated triggers elsewhere in index.ts.
const onNewReviewSrc = (() => {
  const start = indexSrc.indexOf("export const onNewReview");
  expect(start).toBeGreaterThan(0);
  // Everything from the export to the end of file is safe — onNewReview is the
  // last trigger added in Plan 08-02.
  return indexSrc.slice(start);
})();

describe("onNewReview — pure helper", () => {
  it("buildNewReviewBody embeds the product title", () => {
    expect(buildNewReviewBody("Blue Mug")).toBe(
      'Your product "Blue Mug" received a new review.',
    );
  });

  it("buildNewReviewBody is deterministic for the same title", () => {
    expect(buildNewReviewBody("X")).toBe(buildNewReviewBody("X"));
  });
});

describe("onNewReview — structural contract (D-04)", () => {
  it("is exported as an onDocumentCreated trigger", () => {
    expect(indexSrc).toContain("export const onNewReview");
    expect(onNewReviewSrc).toMatch(/onDocumentCreated\(/);
  });

  it("triggers on PRODUCTS/{productId}/REVIEWS/{reviewId}", () => {
    expect(onNewReviewSrc).toContain(
      '"PRODUCTS/{productId}/REVIEWS/{reviewId}"',
    );
  });

  it("resolves the recipient from PRODUCTS/{productId}.sellerId (server-side)", () => {
    expect(onNewReviewSrc).toMatch(
      /collection\("PRODUCTS"\)\s*\.doc\(productId\)\s*\.get\(\)/,
    );
    expect(onNewReviewSrc).toMatch(/sellerId\s*=\s*productSnap\.data\(\)\?\.sellerId/);
  });

  it("skips (returns) when the product has no sellerId", () => {
    expect(onNewReviewSrc).toMatch(/if\s*\(\s*!sellerId\s*\)\s*\{[\s\S]*?return;/);
  });

  it("writes the notifications history doc with type new_review", () => {
    expect(onNewReviewSrc).toMatch(/collection\("notifications"\)/);
    expect(onNewReviewSrc).toMatch(/type:\s*"new_review"/);
  });

  it("writes the notifications doc BEFORE the getMessaging().send call", () => {
    const setIdx = onNewReviewSrc.indexOf("notifRef.set(");
    const sendIdx = onNewReviewSrc.indexOf("getMessaging().send(");
    expect(setIdx).toBeGreaterThan(0);
    expect(sendIdx).toBeGreaterThan(0);
    expect(setIdx).toBeLessThan(sendIdx);
  });

  it("history write is unconditional (not gated behind the fcmToken guard)", () => {
    // The `if (!fcmToken) … return` guard must come AFTER the notifRef.set,
    // so history is written even when the seller has no token.
    const setIdx = onNewReviewSrc.indexOf("notifRef.set(");
    const tokenGuardIdx = onNewReviewSrc.search(/if\s*\(\s*!fcmToken\s*\)/);
    expect(setIdx).toBeGreaterThan(0);
    expect(tokenGuardIdx).toBeGreaterThan(0);
    expect(setIdx).toBeLessThan(tokenGuardIdx);
  });

  it("FCM data payload carries type + productId + productTitle + notifId", () => {
    const match = onNewReviewSrc.match(/data:\s*\{([\s\S]*?)\}\s*,\s*android:/);
    expect(match).not.toBeNull();
    const dataBlock = match![1];
    expect(dataBlock).toMatch(/type:\s*"new_review"/);
    expect(dataBlock).toMatch(/productId,/);
    expect(dataBlock).toMatch(/productTitle,/);
    expect(dataBlock).toMatch(/notifId:\s*notifRef\.id/);
  });

  it("android.notification.channelId === 'order_updates_channel' (D-03)", () => {
    expect(onNewReviewSrc).toMatch(
      /android:\s*\{[\s\S]*?notification:\s*\{[\s\S]*?channelId:\s*"order_updates_channel"/,
    );
  });

  it("FCM send is wrapped in try/catch (never throws — trigger must not crash)", () => {
    expect(onNewReviewSrc).toMatch(/try\s*\{[\s\S]*?getMessaging\(\)\.send\(/);
    expect(onNewReviewSrc).toMatch(/catch\s*\(err\)[\s\S]*?dispatch FAILED/);
  });
});
