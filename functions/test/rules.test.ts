/**
 * Firestore Security Rules tests — Phase 6 Order Tracking.
 *
 * Wave-0 scaffold: placeholder it.todo entries. Real assertions land in
 * Task 5 of Plan 06-01 (must pass under `firebase emulators:exec --only firestore`).
 */
describe("firestore.rules /orders", () => {
  it.todo("customer reads own /orders/{id} succeeds");
  it.todo("customer cannot read foreign /orders/{id}");
});

describe("firestore.rules /sellerOrders", () => {
  it.todo("seller advances own sellerOrder PENDING -> CONFIRMED");
  it.todo("seller cannot revert SHIPPED -> CONFIRMED");
  it.todo("seller cannot mutate items / subtotal");
  it.todo("seller cannot cancel post-SHIPPED");
  it.todo("foreign seller cannot update");
  it.todo("statusHistory must append exactly one entry");
});
