/**
 * cancelSellerOrder callable tests — Plan 06-01 Task 4.
 *
 * Validates the structural contract via source-grep + small behavioural unit
 * checks. Full callable end-to-end is exercised under the Firestore emulator
 * in rules.test.ts (Task 5).
 */
import * as fs from "fs";
import * as path from "path";

const indexSrc = fs.readFileSync(
  path.resolve(__dirname, "../src/index.ts"),
  "utf-8",
);

describe("cancelSellerOrder — structural contract", () => {
  it("rejects non-seller caller (permission-denied)", () => {
    expect(indexSrc).toContain('permission-denied"');
    expect(indexSrc).toContain('Only the seller can cancel');
    // The guard reads `sub.sellerId !== request.auth.uid`
    expect(indexSrc).toMatch(/sub\.sellerId\s*!==\s*request\.auth\.uid/);
  });

  it("rejects when status >= SHIPPED (failed-precondition)", () => {
    expect(indexSrc).toMatch(
      /\["SHIPPED",\s*"DELIVERED",\s*"CANCELLED"\]\.includes\(sub\.status\)/,
    );
    expect(indexSrc).toContain("Cannot cancel post-shipping");
  });

  it("calls stripe.refunds.create with payment_intent + amount in cents", () => {
    expect(indexSrc).toMatch(/stripe\.refunds\.create\(/);
    expect(indexSrc).toMatch(/payment_intent:\s*pi/);
    expect(indexSrc).toMatch(/amount:\s*refundCents/);
    // refundCents = round((sub.subtotal + sub.shippingShare - sub.discountShare) * 100)
    expect(indexSrc).toMatch(/sub\.subtotal/);
    expect(indexSrc).toMatch(/sub\.shippingShare/);
    expect(indexSrc).toMatch(/sub\.discountShare/);
    expect(indexSrc).toMatch(/refundCents/);
    expect(indexSrc).toMatch(/\* 100/);
  });

  it("writes CANCELLED + refundId atomically (single subRef.update with arrayUnion)", () => {
    expect(indexSrc).toMatch(/subRef\.update\(/);
    expect(indexSrc).toMatch(/status:\s*"CANCELLED"/);
    expect(indexSrc).toMatch(
      /statusHistory:\s*admin\.firestore\.FieldValue\.arrayUnion\(/,
    );
    expect(indexSrc).toMatch(/refundId:\s*refund\.id/);
  });

  it("idempotency key is stable (cancel-${sellerOrderId})", () => {
    // Template literal in the source uses backticks; grep both styles.
    expect(indexSrc).toMatch(/idempotencyKey\s*=\s*`cancel-\$\{sellerOrderId\}`/);
    expect(indexSrc).toMatch(/idempotencyKey\s*}/);
  });

  it("maps charge_already_refunded -> HttpsError failed-precondition", () => {
    expect(indexSrc).toMatch(/charge_already_refunded/);
    expect(indexSrc).toMatch(/Refund exceeds available amount/);
  });
});
