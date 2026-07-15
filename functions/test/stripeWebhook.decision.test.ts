/**
 * stripeWebhook decision-logic tests.
 *
 * The webhook's branching (idempotency, missing-session retry, failed/canceled
 * cleanup, unknown events) is extracted into the pure `decideWebhookAction` so
 * every branch is unit-testable without Firestore/Stripe. `buildStockDecrements`
 * is likewise pure. Firestore I/O (the batch itself) is exercised only in the
 * rules/emulator layer; here we lock the decisions + the stock math.
 */
import { decideWebhookAction, buildStockDecrements } from "../src/index";

describe("decideWebhookAction — payment_intent.succeeded", () => {
  it("materialises when order absent + session present", () => {
    expect(decideWebhookAction("payment_intent.succeeded", "o1", false, true))
      .toEqual({ action: "materialise", status: 200 });
  });

  it("skips (200) when the order already exists — idempotent redelivery", () => {
    expect(decideWebhookAction("payment_intent.succeeded", "o1", true, false))
      .toEqual({ action: "skip-already-done", status: 200 });
  });

  it("returns 500 (retry) when charged but session missing — must not drop the order", () => {
    expect(decideWebhookAction("payment_intent.succeeded", "o1", false, false))
      .toEqual({ action: "retry-missing-session", status: 500 });
  });

  it("skips (200) when metadata has no orderId", () => {
    expect(decideWebhookAction("payment_intent.succeeded", undefined, false, false))
      .toEqual({ action: "skip-no-order-id", status: 200 });
  });
});

describe("decideWebhookAction — failure / cancellation / unknown", () => {
  it("drops the session on payment_failed", () => {
    expect(decideWebhookAction("payment_intent.payment_failed", "o1", false, true))
      .toEqual({ action: "drop-session", status: 200 });
  });

  it("drops the session on canceled", () => {
    expect(decideWebhookAction("payment_intent.canceled", "o1", false, true))
      .toEqual({ action: "drop-session", status: 200 });
  });

  it("ignores (200) any other event type", () => {
    expect(decideWebhookAction("charge.refunded", "o1", true, true))
      .toEqual({ action: "ignore", status: 200 });
  });
});

describe("buildStockDecrements", () => {
  const session = (items: { productId: string; quantity: number }[]) => ({
    orderId: "o1",
    userId: "u1",
    shippingAddress: {} as never,
    items: items.map((i) => ({
      productId: i.productId, productTitle: "x", quantity: i.quantity,
      snapshotPrice: 1, lineTotal: i.quantity,
    })),
    sellers: [],
    subtotalCents: 0, shippingCents: 0, finalTotalCents: 0,
    discountCents: 0, discountCode: "", stripePaymentIntentId: "pi",
  });

  it("maps one decrement per product with its quantity", () => {
    const out = buildStockDecrements(session([
      { productId: "p-a", quantity: 2 },
      { productId: "p-b", quantity: 3 },
    ]));
    expect(out).toEqual([
      { productId: "p-a", quantity: 2 },
      { productId: "p-b", quantity: 3 },
    ]);
  });

  it("sums duplicate productIds defensively", () => {
    const out = buildStockDecrements(session([
      { productId: "p-a", quantity: 2 },
      { productId: "p-a", quantity: 5 },
    ]));
    expect(out).toEqual([{ productId: "p-a", quantity: 7 }]);
  });

  it("returns empty for an empty cart", () => {
    expect(buildStockDecrements(session([]))).toEqual([]);
  });
});
