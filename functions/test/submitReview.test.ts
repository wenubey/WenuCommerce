/**
 * submitReview callable tests — Plan 07-01 Task 1.
 *
 * Validates the structural contract via source-grep (mirrors
 * cancelSellerOrder.test.ts) plus behavioural unit checks of the exported
 * pure helper `computeNewAggregate`, which needs no Firestore. Full callable
 * end-to-end is exercised under the Firestore emulator in rules.test.ts.
 */
import * as fs from "fs";
import * as path from "path";
import { computeNewAggregate, buildReviewData } from "../src/index";

const indexSrc = fs.readFileSync(
  path.resolve(__dirname, "../src/index.ts"),
  "utf-8",
);

describe("submitReview — structural contract", () => {
  it("is exported as an onCall callable", () => {
    expect(indexSrc).toContain("export const submitReview");
  });

  it("rejects unauthenticated caller (auth guard)", () => {
    expect(indexSrc).toContain('unauthenticated"');
    expect(indexSrc).toContain("Sign in required");
  });

  it("rejects caller with no DELIVERED order (failed-precondition — REVW-02)", () => {
    expect(indexSrc).toContain("failed-precondition");
    expect(indexSrc).toContain("No delivered order found for this product");
    expect(indexSrc).toMatch(/status.*DELIVERED/);
  });

  it("validates the 1-5 rating range (REVW-01)", () => {
    expect(indexSrc).toMatch(/rating < 1 \|\| rating > 5/);
  });

  it("sets isVerifiedPurchase: true on the review doc (REVW-05)", () => {
    expect(indexSrc).toMatch(/isVerifiedPurchase:\s*true/);
  });

  it("runs a transaction writing review + updating product aggregate (REVW-04)", () => {
    expect(indexSrc).toMatch(/db\.runTransaction/);
    expect(indexSrc).toMatch(/reviewCount/);
    expect(indexSrc).toMatch(/averageRating/);
  });

  it("exports the pure helpers buildReviewData + computeNewAggregate", () => {
    expect(indexSrc).toContain("export function buildReviewData");
    expect(indexSrc).toContain("export function computeNewAggregate");
  });

  it("edit path: count unchanged (REVW-03, Pitfall 1)", () => {
    expect(indexSrc).toMatch(/isEdit/);
    expect(indexSrc).toMatch(/newCount = currentCount/);
  });
});

describe("computeNewAggregate — behavioural (pure helper)", () => {
  it("first review from empty product → avg=rating, count=1", () => {
    const { newAvg, newCount } = computeNewAggregate(0, 0, 5);
    expect(newAvg).toBe(5);
    expect(newCount).toBe(1);
  });

  it("second new review (avg=5,count=1,rating=3) → avg=4, count=2", () => {
    const { newAvg, newCount } = computeNewAggregate(5, 1, 3);
    expect(newAvg).toBe(4);
    expect(newCount).toBe(2);
  });

  it("edit (avg=4,count=2,rating 3→5,isEdit) → avg=5, count UNCHANGED", () => {
    const { newAvg, newCount } = computeNewAggregate(4, 2, 5, 3, true);
    expect(newAvg).toBe(5);
    expect(newCount).toBe(2);
  });
});

describe("buildReviewData — behavioural (pure helper)", () => {
  const data = {
    productId: "p-1",
    rating: 4,
    title: "Great",
    body: "Loved it",
    reviewerName: "Ada",
    reviewerPhotoUrl: "http://x/y.png",
  };

  it("builds a new review doc with isVerifiedPurchase=true and reviewId echoed", () => {
    const doc = buildReviewData("uid-1", "rev-1", "p-1", data, "so-1");
    expect(doc.id).toBe("rev-1");
    expect(doc.reviewerId).toBe("uid-1");
    expect(doc.productId).toBe("p-1");
    expect(doc.purchaseId).toBe("so-1");
    expect(doc.rating).toBe(4);
    expect(doc.isVerifiedPurchase).toBe(true);
    expect(doc.helpfulCount).toBe(0);
    expect(typeof doc.createdAt).toBe("string");
  });

  it("on edit preserves the original createdAt + helpfulCount", () => {
    const existing = { createdAt: "1700000000000", helpfulCount: 7 };
    const doc = buildReviewData("uid-1", "rev-1", "p-1", data, "so-1", existing, true);
    expect(doc.createdAt).toBe("1700000000000");
    expect(doc.helpfulCount).toBe(7);
    expect(doc.isVerifiedPurchase).toBe(true);
  });
});
