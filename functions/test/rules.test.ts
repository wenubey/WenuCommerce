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

  it("customer can read own /sellerOrders/{id} (parent.userId == auth.uid)", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertSucceeds(ctx.firestore().doc("sellerOrders/so-1").get());
  });
});
