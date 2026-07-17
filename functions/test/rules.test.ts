/**
 * Firestore Security Rules tests — Plan 06-01 Task 5.
 *
 * MUST be run under `firebase emulators:exec --only firestore "jest rules"`.
 * 7 assertions mapped to ORDR-05 / ORDR-06 / ORDR-09 in RESEARCH §4.
 */
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import * as fs from "fs";
import * as path from "path";

const PROJECT_ID = "wenucommerce-rules-test";

let env: RulesTestEnvironment;

const seedSellerOrder = {
  parentOrderId: "ord-parent-1",
  userId: "customer-1",
  sellerId: "seller-1",
  sellerName: "Acme",
  sellerLogoUrl: "",
  items: [
    { productId: "p-1", productTitle: "Widget", quantity: 1, snapshotPrice: 10, lineTotal: 10 },
  ],
  subtotal: 10,
  shippingShare: 2,
  discountShare: 0,
  status: "PENDING",
  statusHistory: [
    { status: "PENDING", timestamp: "2026-06-15T10:00:00Z", note: null, trackingNumber: null },
  ],
  trackingNumber: null,
  refundId: null,
  refundedAmount: 0,
  createdAt: "2026-06-15T10:00:00Z",
  updatedAt: "2026-06-15T10:00:00Z",
};

const seedReview = {
  id: "rev-1",
  productId: "p-1",
  reviewerId: "customer-1",
  reviewerName: "Ada",
  reviewerPhotoUrl: "",
  purchaseId: "so-1",
  rating: 5,
  title: "Great",
  body: "Loved it",
  isVerifiedPurchase: true,
  helpfulCount: 0,
  isVisible: true,
  createdAt: "1700000000000",
  updatedAt: "1700000000000",
};

const seedNotification = {
  id: "n1",
  type: "new_review",
  title: "New review",
  body: "Your product received a new review.",
  orderId: "",
  sellerOrderId: "",
  productId: "p-1",
  productTitle: "Widget",
  read: false,
  createdAt: "1700000000000",
};

const seedParentOrder = {
  userId: "customer-1",
  status: "PENDING",
  subtotal: 10,
  shippingTotal: 2,
  totalAmount: 12,
  currency: "USD",
  stripePaymentIntentId: "pi_test",
  shippingAddress: {},
  items: [],
  discountAmount: 0,
  discountCode: "",
  sellerOrderIds: ["so-1"],
  aggregateStatus: "PENDING",
  aggregateVersion: 0,
  createdAt: "2026-06-15T10:00:00Z",
  updatedAt: "2026-06-15T10:00:00Z",
};

beforeAll(async () => {
  const rules = fs.readFileSync(
    path.resolve(__dirname, "../../firestore.rules"),
    "utf-8",
  );
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: "127.0.0.1",
      port: 8085,
    },
  });
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  // Seed parent + sellerOrder with security rules disabled.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    await fs.doc("orders/ord-parent-1").set(seedParentOrder);
    await fs.doc("sellerOrders/so-1").set(seedSellerOrder);
    await fs.doc("PRODUCTS/p-1/REVIEWS/rev-1").set(seedReview);
    await fs.doc("notifications/uidA/items/n1").set(seedNotification);
  });
});

function nextHistory(extra: object) {
  return [...seedSellerOrder.statusHistory, {
    timestamp: "2026-06-15T11:00:00Z",
    note: null,
    trackingNumber: null,
    ...extra,
  }];
}

describe("firestore.rules /orders", () => {
  it("(a) customer reads own /orders/{id} succeeds", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertSucceeds(ctx.firestore().doc("orders/ord-parent-1").get());
  });

  it("(b) customer cannot read foreign /orders/{id}", async () => {
    const ctx = env.authenticatedContext("someone-else");
    await assertFails(ctx.firestore().doc("orders/ord-parent-1").get());
  });
});

describe("firestore.rules /sellerOrders", () => {
  it("(c) seller advances own sellerOrder PENDING -> CONFIRMED", async () => {
    const ctx = env.authenticatedContext("seller-1");
    await assertSucceeds(
      ctx.firestore().doc("sellerOrders/so-1").update({
        status: "CONFIRMED",
        statusHistory: nextHistory({ status: "CONFIRMED" }),
      }),
    );
  });

  it("(d) seller cannot revert SHIPPED -> CONFIRMED", async () => {
    // First, escalate the seed to SHIPPED with rules disabled.
    await env.withSecurityRulesDisabled(async (rulesCtx) => {
      await rulesCtx.firestore().doc("sellerOrders/so-1").set({
        ...seedSellerOrder,
        status: "SHIPPED",
        statusHistory: [
          ...seedSellerOrder.statusHistory,
          { status: "CONFIRMED", timestamp: "t1", note: null, trackingNumber: null },
          { status: "SHIPPED",   timestamp: "t2", note: null, trackingNumber: null },
        ],
      });
    });
    const ctx = env.authenticatedContext("seller-1");
    await assertFails(
      ctx.firestore().doc("sellerOrders/so-1").update({
        status: "CONFIRMED",
        statusHistory: [
          ...seedSellerOrder.statusHistory,
          { status: "CONFIRMED", timestamp: "t1", note: null, trackingNumber: null },
          { status: "SHIPPED",   timestamp: "t2", note: null, trackingNumber: null },
          { status: "CONFIRMED", timestamp: "t3", note: null, trackingNumber: null },
        ],
      }),
    );
  });

  it("(e) seller tries CONFIRMED -> SHIPPED but mutates items -> assertFails", async () => {
    // First escalate to CONFIRMED via rules-disabled.
    await env.withSecurityRulesDisabled(async (rulesCtx) => {
      await rulesCtx.firestore().doc("sellerOrders/so-1").set({
        ...seedSellerOrder,
        status: "CONFIRMED",
        statusHistory: [
          ...seedSellerOrder.statusHistory,
          { status: "CONFIRMED", timestamp: "t1", note: null, trackingNumber: null },
        ],
      });
    });
    const ctx = env.authenticatedContext("seller-1");
    await assertFails(
      ctx.firestore().doc("sellerOrders/so-1").update({
        status: "SHIPPED",
        items: [
          // Mutated lineTotal — immutability check must fail.
          { productId: "p-1", productTitle: "Widget", quantity: 1, snapshotPrice: 10, lineTotal: 9999 },
        ],
        statusHistory: [
          ...seedSellerOrder.statusHistory,
          { status: "CONFIRMED", timestamp: "t1", note: null, trackingNumber: null },
          { status: "SHIPPED",   timestamp: "t2", note: null, trackingNumber: null },
        ],
      }),
    );
  });

  it("(f) seller tries to cancel SHIPPED (post-SHIPPED cancel forbidden)", async () => {
    await env.withSecurityRulesDisabled(async (rulesCtx) => {
      await rulesCtx.firestore().doc("sellerOrders/so-1").set({
        ...seedSellerOrder,
        status: "SHIPPED",
      });
    });
    const ctx = env.authenticatedContext("seller-1");
    await assertFails(
      ctx.firestore().doc("sellerOrders/so-1").update({
        status: "CANCELLED",
        statusHistory: nextHistory({ status: "CANCELLED" }),
      }),
    );
  });

  it("(g) foreign seller cannot update", async () => {
    const ctx = env.authenticatedContext("seller-OTHER");
    await assertFails(
      ctx.firestore().doc("sellerOrders/so-1").update({
        status: "CONFIRMED",
        statusHistory: nextHistory({ status: "CONFIRMED" }),
      }),
    );
  });

  it("statusHistory must append exactly one entry (no skip)", async () => {
    const ctx = env.authenticatedContext("seller-1");
    // Try to advance but APPEND TWO entries — must fail.
    await assertFails(
      ctx.firestore().doc("sellerOrders/so-1").update({
        status: "CONFIRMED",
        statusHistory: [
          ...seedSellerOrder.statusHistory,
          { status: "CONFIRMED", timestamp: "x", note: null, trackingNumber: null },
          { status: "CONFIRMED", timestamp: "y", note: null, trackingNumber: null },
        ],
      }),
    );
  });

  it("customer can read own /sellerOrders/{id} (denormalised userId == auth.uid)", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertSucceeds(ctx.firestore().doc("sellerOrders/so-1").get());
  });

  it("(S2) a foreign user (not seller, not customer) cannot read /sellerOrders/{id}", async () => {
    const ctx = env.authenticatedContext("stranger");
    await assertFails(ctx.firestore().doc("sellerOrders/so-1").get());
  });
});

describe("firestore.rules /PRODUCTS/{id}/REVIEWS (server-only writes)", () => {
  it("(h) authenticated user can read a review (reads stay open — D-02)", async () => {
    const ctx = env.authenticatedContext("any-user");
    await assertSucceeds(
      ctx.firestore().doc("PRODUCTS/p-1/REVIEWS/rev-1").get(),
    );
  });

  it("(i) client cannot create a review directly (REVW-02 integrity)", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertFails(
      ctx.firestore().doc("PRODUCTS/p-1/REVIEWS/rev-99").set(seedReview),
    );
  });

  it("(j) client cannot update a review directly", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertFails(
      ctx.firestore().doc("PRODUCTS/p-1/REVIEWS/rev-1").update({ rating: 1 }),
    );
  });

  it("(k) client cannot write a helpfulVote directly (D-04 — vote stuffing)", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertFails(
      ctx
        .firestore()
        .doc("PRODUCTS/p-1/REVIEWS/rev-1/helpfulVotes/customer-1")
        .set({ votedAt: "now" }),
    );
  });
});

describe("firestore.rules /notifications (owner-read + read-flag-only update)", () => {
  it("(n-a) owner reads own notification item -> succeeds (T-08-02)", async () => {
    const ctx = env.authenticatedContext("uidA");
    await assertSucceeds(
      ctx.firestore().doc("notifications/uidA/items/n1").get(),
    );
  });

  it("(n-b) foreign user cannot read another user's notification (T-08-02)", async () => {
    const ctx = env.authenticatedContext("uidB");
    await assertFails(
      ctx.firestore().doc("notifications/uidA/items/n1").get(),
    );
  });

  it("(n-c) owner update setting {read:true} -> succeeds (mark-as-read)", async () => {
    const ctx = env.authenticatedContext("uidA");
    await assertSucceeds(
      ctx.firestore().doc("notifications/uidA/items/n1").update({ read: true }),
    );
  });

  it("(n-d) owner update mutating a non-read field -> assertFails (T-08-03)", async () => {
    const ctx = env.authenticatedContext("uidA");
    await assertFails(
      ctx.firestore().doc("notifications/uidA/items/n1").update({ title: "spoofed" }),
    );
  });

  it("(n-e) client create at notifications/{uid}/items -> assertFails (T-08-01)", async () => {
    const ctx = env.authenticatedContext("uidA");
    await assertFails(
      ctx.firestore().doc("notifications/uidA/items/n2").set(seedNotification),
    );
  });

  it("(n-f) client delete of own notification -> assertFails (server-only)", async () => {
    const ctx = env.authenticatedContext("uidA");
    await assertFails(
      ctx.firestore().doc("notifications/uidA/items/n1").delete(),
    );
  });
});

describe("firestore.rules /checkoutSessions (server-only)", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("checkoutSessions/cs-1").set({
        orderId: "cs-1",
        userId: "customer-1",
        shippingAddress: { line1: "secret" },
        items: [],
        sellers: [],
      });
    });
  });

  it("(T3) authenticated client cannot read a checkoutSession (PII payload)", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertFails(ctx.firestore().doc("checkoutSessions/cs-1").get());
  });

  it("(T3) authenticated client cannot write a checkoutSession", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertFails(
      ctx.firestore().doc("checkoutSessions/cs-2").set({ orderId: "cs-2" }),
    );
  });
});
