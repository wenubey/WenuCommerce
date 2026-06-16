/**
 * onOrderStatusChange Firestore trigger tests — Phase 6 Order Tracking.
 *
 * Wave-0 scaffold. Real assertions land in Task 4 of Plan 06-01.
 */
describe("onOrderStatusChange", () => {
  it.todo("skips when status unchanged");
  it.todo("sends FCM with correct deep-link payload");
  it.todo("recomputes aggregateStatus inside transaction with reads-before-writes");
  it.todo("no-op when fcmToken missing");
  it.todo("aggregateVersion increments monotonically");
});
