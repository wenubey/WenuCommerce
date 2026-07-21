/**
 * onFollowedSellerWrite Firestore trigger tests — Plan 09-02 Task 1 (FAVS-04).
 *
 * Two layers (mirrors onNewReview.test.ts):
 *   1) Unit test on the pure exported helper `buildFollowedSellerDelta` — no
 *      Firestore, no emulator.
 *   2) Structural grep on index.ts locking the FAVS-04 contract:
 *      - triggers on users/{customerId}/followed_sellers/{sellerId} via onDocumentWritten
 *      - contains the `wasFollowing === isNowFollowing` idempotency no-op guard
 *        (RESEARCH Pitfall 3)
 *      - uses set(..., { merge: true }) — never .update() — on the seller USERS
 *        doc so a missing/deleted seller doc doesn't throw NOT_FOUND
 *        (RESEARCH Pitfall 4)
 *      - applies FieldValue.increment(delta) to a `followerCount` field
 *        (Admin-SDK-only atomic counter)
 *      - wraps the Firestore write in try/catch so the trigger never crashes
 *        (matches Phase 8 non-blocking trigger style)
 */
import * as fs from "fs";
import * as path from "path";

import { buildFollowedSellerDelta } from "../src/index";

const indexSrc = fs.readFileSync(
  path.resolve(__dirname, "../src/index.ts"),
  "utf-8",
);

// Isolate the onFollowedSellerWrite function body so ordering assertions can't
// be satisfied by unrelated triggers elsewhere in index.ts.
const onFollowedSellerWriteSrc = (() => {
  const start = indexSrc.indexOf("export const onFollowedSellerWrite");
  expect(start).toBeGreaterThan(0);
  // Everything from the export to the end of file is safe —
  // onFollowedSellerWrite is the last trigger added in Plan 09-02.
  return indexSrc.slice(start);
})();

describe("onFollowedSellerWrite — pure helper", () => {
  it("buildFollowedSellerDelta(true) === +1 (follow adds one)", () => {
    expect(buildFollowedSellerDelta(true)).toBe(1);
  });

  it("buildFollowedSellerDelta(false) === -1 (unfollow removes one)", () => {
    expect(buildFollowedSellerDelta(false)).toBe(-1);
  });
});

describe("onFollowedSellerWrite — structural contract (FAVS-04)", () => {
  it("is exported as an onDocumentWritten trigger", () => {
    expect(indexSrc).toContain("export const onFollowedSellerWrite");
    expect(onFollowedSellerWriteSrc).toMatch(/onDocumentWritten\(/);
  });

  it("triggers on users/{customerId}/followed_sellers/{sellerId}", () => {
    expect(onFollowedSellerWriteSrc).toContain(
      '"users/{customerId}/followed_sellers/{sellerId}"',
    );
  });

  it("has the wasFollowing === isNowFollowing no-op guard (idempotency, Pitfall 3)", () => {
    expect(onFollowedSellerWriteSrc).toMatch(
      /wasFollowing\s*===\s*isNowFollowing/,
    );
    // The guard MUST return early so the increment never fires on a
    // same-existence-state write (e.g. metadata-only update).
    expect(onFollowedSellerWriteSrc).toMatch(
      /if\s*\(\s*wasFollowing\s*===\s*isNowFollowing\s*\)[\s\S]*?return/,
    );
  });

  it("uses set(..., { merge: true }) on USERS — never .update() — (Pitfall 4)", () => {
    // The counter write MUST use merge:true so a missing seller USERS doc
    // upserts instead of throwing NOT_FOUND. Grep the trigger body:
    // The trigger derives the seller id from event.params.sellerId (either
    // inline or via a local destructuring) then targets USERS/<sellerId>.set.
    expect(onFollowedSellerWriteSrc).toMatch(/event\.params\.sellerId/);
    expect(onFollowedSellerWriteSrc).toMatch(
      /collection\("USERS"\)\s*\.doc\([^)]*sellerId[^)]*\)\s*\.set\(/,
    );
    expect(onFollowedSellerWriteSrc).toMatch(/merge:\s*true/);
    // And explicitly does NOT call .update on the USERS doc for the counter.
    expect(onFollowedSellerWriteSrc).not.toMatch(
      /collection\("USERS"\)\s*\.doc\([^)]*\)\s*\.update\(/,
    );
  });

  it("applies FieldValue.increment to the followerCount field", () => {
    expect(onFollowedSellerWriteSrc).toMatch(/FieldValue\.increment\(/);
    expect(onFollowedSellerWriteSrc).toMatch(/followerCount/);
  });

  it("delta is computed via the pure helper (testability + single source of truth)", () => {
    expect(onFollowedSellerWriteSrc).toMatch(
      /buildFollowedSellerDelta\(\s*isNowFollowing\s*\)/,
    );
  });

  it("Firestore write is wrapped in try/catch (never throws — trigger must not crash)", () => {
    expect(onFollowedSellerWriteSrc).toMatch(
      /try\s*\{[\s\S]*?FieldValue\.increment\([\s\S]*?catch/,
    );
  });
});
