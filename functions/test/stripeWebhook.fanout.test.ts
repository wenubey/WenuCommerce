/**
 * Payment-gated fan-out tests.
 *
 * The order/sellerOrders fan-out moved out of createPaymentIntent (which ran
 * before payment) into the Stripe webhook (after payment_intent.succeeded).
 * `buildFanoutDocs` is the pure builder both would share; we test it directly
 * so the exact /orders and /sellerOrders doc shapes are guaranteed without a
 * Firestore emulator. Timestamps are injected as sentinels.
 */
import { buildFanoutDocs } from "../src/index";

const SERVER_TS = "SERVER_TS" as unknown;
const STATUS_TS = "STATUS_TS" as unknown;

function twoSellerSession() {
  return {
    orderId: "order-1",
    userId: "cust-1",
    shippingAddress: {
      fullName: "Jane Doe",
      line1: "1 St",
      city: "Town",
      state: "CA",
      postalCode: "90000",
      country: "US",
    },
    items: [
      { productId: "p-a1", productTitle: "A1", quantity: 1, snapshotPrice: 40, lineTotal: 40 },
      { productId: "p-b1", productTitle: "B1", quantity: 1, snapshotPrice: 60, lineTotal: 60 },
    ],
    sellers: [
      {
        sellerOrderId: "sub-A",
        sellerId: "seller-A",
        sellerName: "Alpha",
        sellerLogoUrl: "",
        items: [{ productId: "p-a1", productTitle: "A1", quantity: 1, snapshotPrice: 40, lineTotal: 40 }],
        subtotalCents: 4000,
        shippingShareCents: 400,
        discountShareCents: 0,
      },
      {
        sellerOrderId: "sub-B",
        sellerId: "seller-B",
        sellerName: "Beta",
        sellerLogoUrl: "",
        items: [{ productId: "p-b1", productTitle: "B1", quantity: 1, snapshotPrice: 60, lineTotal: 60 }],
        subtotalCents: 6000,
        shippingShareCents: 600,
        discountShareCents: 0,
      },
    ],
    subtotalCents: 10000,
    shippingCents: 1000,
    finalTotalCents: 11000,
    discountCents: 0,
    discountCode: "",
    stripePaymentIntentId: "pi_123",
  };
}

describe("buildFanoutDocs — payment-gated fan-out", () => {
  it("emits 1 parent order + N sellerOrders for an N-seller session", () => {
    const docs = buildFanoutDocs(twoSellerSession(), SERVER_TS, STATUS_TS);
    expect(docs.order.id).toBe("order-1");
    expect(docs.sellerOrders).toHaveLength(2);
    expect(docs.sellerOrders.map((s) => s.id)).toEqual(["sub-A", "sub-B"]);
  });

  it("parent order links every child via sellerOrderIds and carries totals", () => {
    const docs = buildFanoutDocs(twoSellerSession(), SERVER_TS, STATUS_TS);
    const o = docs.order.data;
    expect(o.status).toBe("PENDING");
    expect(o.aggregateStatus).toBe("PENDING");
    expect(o.sellerOrderIds).toEqual(["sub-A", "sub-B"]);
    expect(o.subtotal).toBe(100);          // 10000c
    expect(o.shippingTotal).toBe(10);      // 1000c
    expect(o.totalAmount).toBe(110);       // 11000c
    expect(o.stripePaymentIntentId).toBe("pi_123");
    expect(o.userId).toBe("cust-1");
    expect(o.items).toHaveLength(2);
  });

  it("each sellerOrder carries only its own items, allocated money, and a single PENDING history entry", () => {
    const docs = buildFanoutDocs(twoSellerSession(), SERVER_TS, STATUS_TS);
    const a = docs.sellerOrders[0].data;
    expect(a.parentOrderId).toBe("order-1");
    expect(a.userId).toBe("cust-1");
    expect(a.sellerId).toBe("seller-A");
    expect(a.items).toHaveLength(1);
    expect(a.items[0].productId).toBe("p-a1"); // productId preserved, not mutated
    expect(a.subtotal).toBe(40);               // 4000c
    expect(a.shippingShare).toBe(4);           // 400c
    expect(a.status).toBe("PENDING");
    expect(a.statusHistory).toHaveLength(1);
    expect(a.statusHistory[0].status).toBe("PENDING");
    expect(a.statusHistory[0].timestamp).toBe(STATUS_TS);
    expect(a.refundedAmount).toBe(0);
  });

  it("injects the server timestamp sentinel into createdAt/updatedAt", () => {
    const docs = buildFanoutDocs(twoSellerSession(), SERVER_TS, STATUS_TS);
    expect(docs.order.data.createdAt).toBe(SERVER_TS);
    expect(docs.order.data.updatedAt).toBe(SERVER_TS);
    expect(docs.sellerOrders[0].data.createdAt).toBe(SERVER_TS);
  });
});
