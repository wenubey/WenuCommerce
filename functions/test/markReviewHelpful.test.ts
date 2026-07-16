/**
 * markReviewHelpful callable tests — Plan 07-01 Task 1.
 *
 * Structural contract via source-grep (mirrors cancelSellerOrder.test.ts).
 * The idempotent double-vote guard (already-exists) and the atomic
 * helpfulVotes/{uid} write + FieldValue.increment(1) are the D-04 contract.
 */
import * as fs from "fs";
import * as path from "path";

const indexSrc = fs.readFileSync(
  path.resolve(__dirname, "../src/index.ts"),
  "utf-8",
);

describe("markReviewHelpful — structural contract", () => {
  it("is exported as an onCall callable", () => {
    expect(indexSrc).toContain("export const markReviewHelpful");
  });

  it("rejects unauthenticated caller (auth guard)", () => {
    expect(indexSrc).toContain('unauthenticated"');
    expect(indexSrc).toContain("Sign in required");
  });

  it("rejects a second vote by the same user (already-exists — Pitfall 5)", () => {
    expect(indexSrc).toMatch(/already-exists/);
    expect(indexSrc).toContain("Already marked helpful");
  });

  it("writes the vote under helpfulVotes and increments helpfulCount atomically (D-04)", () => {
    expect(indexSrc).toMatch(/helpfulVotes/);
    expect(indexSrc).toMatch(/FieldValue\.increment\(1\)/);
    expect(indexSrc).toMatch(/db\.runTransaction/);
  });
});
