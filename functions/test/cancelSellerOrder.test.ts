/**
 * cancelSellerOrder callable tests — Phase 6 Order Tracking.
 *
 * Wave-0 scaffold. Real assertions land in Task 4 of Plan 06-01.
 */
describe("cancelSellerOrder", () => {
  it.todo("rejects non-seller caller");
  it.todo("rejects when status >= SHIPPED");
  it.todo("calls stripe.refunds.create with payment_intent + cents");
  it.todo("writes CANCELLED + refundId atomically");
  it.todo("idempotency key is stable");
});
