// Verified collection names (Plan 06-01 Task 4 step 1):
//   data/util/Constants.kt -> USER_COLLECTION = "USERS", PRODUCTS_COLLECTION = "PRODUCTS"
// Cloud Functions use these literal strings; keep in sync with Constants.kt.

import { onCall, HttpsError, onRequest } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { defineSecret } from "firebase-functions/params";
import Stripe from "stripe";
import * as admin from "firebase-admin";
import { getMessaging } from "firebase-admin/messaging";

admin.initializeApp();

const stripeSecretKey = defineSecret("STRIPE_SECRET_KEY");
// Signing secret for the Stripe webhook endpoint (Dashboard → Developers →
// Webhooks → your endpoint → "Signing secret", starts with whsec_). Set with:
//   firebase functions:secrets:set STRIPE_WEBHOOK_SECRET
const stripeWebhookSecret = defineSecret("STRIPE_WEBHOOK_SECRET");

// How long an unpaid checkoutSessions doc lives before the Firestore TTL policy
// reaps it. A real card payment completes in minutes; 24h is a safe margin that
// still bounds abandoned-checkout PII retention.
const CHECKOUT_SESSION_TTL_MS = 24 * 60 * 60 * 1000;

interface CartItem {
  productId: string;
  productTitle: string;
  quantity: number;
  price: number;
}

interface ShippingAddress {
  fullName: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

interface EnrichedItem {
  productId: string;
  productTitle: string;
  quantity: number;
  snapshotPrice: number;
  lineTotal: number;
  sellerId: string;
  sellerName: string;
  sellerLogoUrl: string;
}

// ─── Shared discount helpers ───────────────────────────────────────────

function validateCouponData(
  data: admin.firestore.DocumentData,
  cartItems: CartItem[],
  subtotalCents: number,
): void {
  if (!data.isActive) {
    throw new HttpsError("not-found", "Code not found");
  }
  if (data.expiresAt && data.expiresAt.toDate() < new Date()) {
    throw new HttpsError("failed-precondition", "This code has expired");
  }
  if (data.usageLimit != null && data.usageCount >= data.usageLimit) {
    throw new HttpsError("resource-exhausted", "Usage limit reached");
  }
  const targetProductIds: string[] = data.targetProductIds ?? [];
  if (targetProductIds.length > 0) {
    const cartProductIds = cartItems.map((i: CartItem) => i.productId);
    const hasEligible = targetProductIds.some((id: string) =>
      cartProductIds.includes(id),
    );
    if (!hasEligible) {
      throw new HttpsError(
        "failed-precondition",
        "No eligible items in your cart",
      );
    }
  }
  const minimumOrderCents = data.minimumOrderAmount != null
    ? Math.round(data.minimumOrderAmount * 100)
    : null;
  if (minimumOrderCents != null && subtotalCents < minimumOrderCents) {
    throw new HttpsError(
      "failed-precondition",
      `Minimum order of $${(minimumOrderCents / 100).toFixed(2)} required`,
    );
  }
}

function computeDiscount(
  couponData: admin.firestore.DocumentData,
  cartItems: CartItem[],
  subtotalCents: number,
  shippingCents: number,
): number {
  const type: string = couponData.type;
  const value: number = couponData.value ?? 0;
  const targetProductIds: string[] = couponData.targetProductIds ?? [];

  let eligibleSubtotalCents = subtotalCents;
  if (targetProductIds.length > 0) {
    eligibleSubtotalCents = cartItems
      .filter((item) => targetProductIds.includes(item.productId))
      .reduce(
        (sum, item) => sum + Math.round(item.price * 100) * item.quantity,
        0,
      );
  }

  switch (type) {
    case "PERCENTAGE": {
      const rawDiscount = Math.round(eligibleSubtotalCents * value / 100);
      const maxCap = couponData.maxDiscountCap != null
        ? Math.round(couponData.maxDiscountCap * 100)
        : Infinity;
      return Math.min(rawDiscount, maxCap);
    }
    case "FIXED_AMOUNT": {
      const fixedCents = Math.round(value * 100);
      return Math.min(fixedCents, eligibleSubtotalCents);
    }
    case "FREE_SHIPPING": {
      return shippingCents;
    }
    default:
      return 0;
  }
}

function buildDiscountDescription(
  data: admin.firestore.DocumentData,
): string {
  const type: string = data.type;
  const value: number = data.value ?? 0;
  switch (type) {
    case "PERCENTAGE":
      return `${value}% off`;
    case "FIXED_AMOUNT":
      return `$${value.toFixed(2)} off`;
    case "FREE_SHIPPING":
      return "Free shipping";
    default:
      return "Discount applied";
  }
}

// ─── validateCoupon Cloud Function ─────────────────────────────────────

export const validateCoupon = onCall(
  { secrets: [stripeSecretKey] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Must be signed in");
    }
    const { couponCode, cartItems, subtotalCents } = request.data as {
      couponCode: string;
      cartItems: CartItem[];
      subtotalCents: number;
    };
    if (!couponCode || typeof couponCode !== "string") {
      throw new HttpsError("invalid-argument", "Coupon code is required");
    }
    const normalized = couponCode.trim().toUpperCase();
    const db = admin.firestore();
    const doc = await db.collection("discountCodes").doc(normalized).get();
    if (!doc.exists) {
      throw new HttpsError("not-found", "Code not found");
    }
    const data = doc.data()!;
    validateCouponData(data, cartItems, subtotalCents);
    const discountCents = computeDiscount(data, cartItems, subtotalCents, 0);
    return {
      code: normalized,
      type: data.type,
      discountCents,
      description: buildDiscountDescription(data),
    };
  },
);

// NOTE: the old decrementCouponUsage callable was removed. Coupon usageCount
// is now incremented authoritatively inside the stripeWebhook materialisation
// batch (see below) — a client-callable that blindly incremented any coupon's
// usageCount was both dead (no caller) and an abuse vector (any signed-in user
// could exhaust a coupon's usage limit).

// ─── Per-seller allocation helpers (Phase 6 fan-out) ───────────────────

/**
 * Pro-rata allocation of a total amount across sellers by their subtotal share.
 * Last seller (iteration order) receives the remainder so sum equals the total.
 * Returns Map<sellerId, allocatedCents>.
 */
export function allocateProRata(
  sellerSubtotals: Map<string, number>,
  totalSubtotalCents: number,
  totalAmountCents: number,
): Map<string, number> {
  const sellerIds = Array.from(sellerSubtotals.keys());
  const result = new Map<string, number>();
  if (totalAmountCents === 0 || sellerIds.length === 0) {
    sellerIds.forEach((id) => result.set(id, 0));
    return result;
  }
  let allocated = 0;
  for (let i = 0; i < sellerIds.length - 1; i++) {
    const sid = sellerIds[i];
    const share = totalSubtotalCents === 0
      ? Math.floor(totalAmountCents / sellerIds.length)
      : Math.round(
        totalAmountCents * (sellerSubtotals.get(sid) ?? 0) / totalSubtotalCents,
      );
    result.set(sid, share);
    allocated += share;
  }
  // Last seller eats remainder so sum == totalAmountCents exactly.
  result.set(sellerIds[sellerIds.length - 1], totalAmountCents - allocated);
  return result;
}

/**
 * Discount allocation respecting Phase 5 single-seller coupon scope.
 * If `targetProductIds` is non-empty and matches items from exactly one seller,
 * the entire discount lands on that seller. Otherwise pro-rata by subtotal.
 */
export function allocateDiscount(
  itemsBySeller: Map<string, EnrichedItem[]>,
  sellerSubtotals: Map<string, number>,
  totalSubtotalCents: number,
  totalDiscountCents: number,
  targetProductIds: string[],
): Map<string, number> {
  const sellerIds = Array.from(itemsBySeller.keys());
  const result = new Map<string, number>();
  if (totalDiscountCents === 0 || sellerIds.length === 0) {
    sellerIds.forEach((id) => result.set(id, 0));
    return result;
  }
  if (targetProductIds.length > 0) {
    // Single-seller coupon: assign entire discount to seller(s) owning a target product.
    const targetSellers = sellerIds.filter((sid) =>
      (itemsBySeller.get(sid) ?? []).some((it) =>
        targetProductIds.includes(it.productId),
      ),
    );
    if (targetSellers.length === 1) {
      sellerIds.forEach((id) =>
        result.set(id, id === targetSellers[0] ? totalDiscountCents : 0),
      );
      return result;
    }
    // Coupon targets >1 seller's items: fall through to pro-rata.
  }
  return allocateProRata(sellerSubtotals, totalSubtotalCents, totalDiscountCents);
}

// ─── Checkout session + fan-out builder (payment-gated) ────────────────
//
// The order/sellerOrders fan-out used to happen inside createPaymentIntent,
// i.e. BEFORE the customer actually paid — so the seller was notified (and a
// PENDING order appeared) even if checkout was abandoned. We now stash the
// computed payload in a `checkoutSessions/{orderId}` doc and only materialise
// /orders + /sellerOrders from the Stripe webhook, after
// `payment_intent.succeeded`. onNewSellerOrder therefore fires only on a real,
// paid order.

interface OrderItemPayload {
  productId: string;
  productTitle: string;
  quantity: number;
  snapshotPrice: number;
  lineTotal: number;
}

interface SellerBreakdown {
  sellerOrderId: string;
  sellerId: string;
  sellerName: string;
  sellerLogoUrl: string;
  items: OrderItemPayload[];
  subtotalCents: number;
  shippingShareCents: number;
  discountShareCents: number;
}

interface CheckoutSession {
  orderId: string;
  userId: string;
  shippingAddress: ShippingAddress;
  items: OrderItemPayload[];
  sellers: SellerBreakdown[];
  subtotalCents: number;
  shippingCents: number;
  finalTotalCents: number;
  discountCents: number;
  discountCode: string;
  stripePaymentIntentId: string;
}

interface FanoutDocs {
  order: { id: string; data: admin.firestore.DocumentData };
  sellerOrders: { id: string; data: admin.firestore.DocumentData }[];
}

/**
 * Pure builder: turns a stored CheckoutSession into the exact /orders and
 * /sellerOrders document shapes. Kept side-effect free (timestamps injected)
 * so it can be unit-tested without Firestore. Mirrors the doc shapes the old
 * inline fan-out wrote, so nothing downstream changes.
 */
export function buildFanoutDocs(
  session: CheckoutSession,
  serverTimestamp: unknown,
  statusTimestamp: unknown,
): FanoutDocs {
  const sellerOrders = session.sellers.map((s) => ({
    id: s.sellerOrderId,
    data: {
      parentOrderId: session.orderId,
      userId: session.userId,
      sellerId: s.sellerId,
      sellerName: s.sellerName,
      sellerLogoUrl: s.sellerLogoUrl,
      items: s.items.map((it) => ({
        productId: it.productId,
        productTitle: it.productTitle,
        quantity: it.quantity,
        snapshotPrice: it.snapshotPrice,
        lineTotal: it.lineTotal,
      })),
      subtotal: s.subtotalCents / 100,
      shippingShare: s.shippingShareCents / 100,
      discountShare: s.discountShareCents / 100,
      status: "PENDING",
      statusHistory: [{
        status: "PENDING",
        timestamp: statusTimestamp,
        note: null,
        trackingNumber: null,
      }],
      trackingNumber: null,
      refundId: null,
      refundedAmount: 0,
      createdAt: serverTimestamp,
      updatedAt: serverTimestamp,
    },
  }));

  const order = {
    id: session.orderId,
    data: {
      userId: session.userId,
      status: "PENDING",
      subtotal: session.subtotalCents / 100,
      shippingTotal: session.shippingCents / 100,
      totalAmount: session.finalTotalCents / 100,
      discountAmount: session.discountCents / 100,
      discountCode: session.discountCode,
      currency: "USD",
      stripePaymentIntentId: session.stripePaymentIntentId,
      shippingAddress: session.shippingAddress,
      items: session.items.map((it) => ({
        productId: it.productId,
        productTitle: it.productTitle,
        quantity: it.quantity,
        snapshotPrice: it.snapshotPrice,
        lineTotal: it.lineTotal,
      })),
      sellerOrderIds: session.sellers.map((s) => s.sellerOrderId),
      aggregateStatus: "PENDING",
      aggregateVersion: 0,
      createdAt: serverTimestamp,
      updatedAt: serverTimestamp,
    },
  };

  return { order, sellerOrders };
}

/**
 * Aggregates the cart items in a session into per-product decrement amounts.
 * Pure so the webhook's stock logic can be unit-tested. Sums duplicate
 * productIds defensively.
 */
export function buildStockDecrements(
  session: CheckoutSession,
): { productId: string; quantity: number }[] {
  const byProduct = new Map<string, number>();
  for (const it of session.items) {
    byProduct.set(it.productId, (byProduct.get(it.productId) ?? 0) + it.quantity);
  }
  return Array.from(byProduct, ([productId, quantity]) => ({ productId, quantity }));
}

type WebhookAction =
  | "materialise"
  | "skip-no-order-id"
  | "skip-already-done"
  | "retry-missing-session"
  | "drop-session"
  | "ignore";

interface WebhookOutcome {
  action: WebhookAction;
  status: number;
}

/**
 * Pure decision function for the Stripe webhook — separated so every branch is
 * unit-testable without Firestore/Stripe. `orderExists`/`sessionExists` are the
 * results of the (side-effecting) Firestore reads the caller performs only for
 * the succeeded branch.
 *
 * Key rule (M1): a succeeded payment whose session is missing returns 500 so
 * Stripe RETRIES — the customer was charged and the order MUST eventually
 * materialise; silently 200-acking would drop it forever.
 */
export function decideWebhookAction(
  eventType: string,
  orderId: string | undefined,
  orderExists: boolean,
  sessionExists: boolean,
): WebhookOutcome {
  if (eventType === "payment_intent.succeeded") {
    if (!orderId) return { action: "skip-no-order-id", status: 200 };
    if (orderExists) return { action: "skip-already-done", status: 200 };
    if (!sessionExists) return { action: "retry-missing-session", status: 500 };
    return { action: "materialise", status: 200 };
  }
  if (
    eventType === "payment_intent.payment_failed" ||
    eventType === "payment_intent.canceled"
  ) {
    return { action: "drop-session", status: 200 };
  }
  return { action: "ignore", status: 200 };
}

// ─── createPaymentIntent Cloud Function (Phase 6 fan-out) ──────────────

export const createPaymentIntent = onCall(
  { secrets: [stripeSecretKey] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Must be signed in to checkout",
      );
    }
    console.log("RAW request.data:", JSON.stringify(request.data));
    const { cartItems, shippingAddress } = request.data as {
      cartItems: CartItem[];
      shippingAddress: ShippingAddress;
      userId: string;
    };
    console.log("Parsed cartItems:", JSON.stringify(cartItems));

    if (!Array.isArray(cartItems) || cartItems.length === 0) {
      throw new HttpsError(
        "invalid-argument",
        "Cart must contain at least one item",
      );
    }
    for (const item of cartItems) {
      if (
        !item.productId ||
        typeof item.productId !== "string" ||
        !item.quantity ||
        typeof item.quantity !== "number" ||
        item.quantity <= 0 ||
        !item.price ||
        typeof item.price !== "number" ||
        item.price < 0
      ) {
        throw new HttpsError(
          "invalid-argument",
          "Each cart item must have a valid productId, quantity, and price",
        );
      }
    }

    const db = admin.firestore();
    const stockFailures: string[] = [];
    let shippingCents = 0;
    const enrichedItems: EnrichedItem[] = [];

    for (const item of cartItems) {
      console.log("Looking up product:", item.productId);
      const productDoc = await db
        .collection("PRODUCTS")
        .doc(item.productId)
        .get();
      console.log("Product exists:", productDoc.exists);

      if (!productDoc.exists) {
        throw new HttpsError(
          "failed-precondition",
          `Product ${item.productTitle} not found`,
        );
      }
      const productData = productDoc.data();
      const stockQuantity: number = productData?.totalStockQuantity ?? 0;
      if (stockQuantity < item.quantity) {
        stockFailures.push(item.productTitle);
      }
      const shippingCost: number = productData?.shipping?.shippingCost ?? 0;
      shippingCents += Math.round(shippingCost * 100);

      enrichedItems.push({
        productId: item.productId,
        productTitle: item.productTitle,
        quantity: item.quantity,
        snapshotPrice: item.price,
        lineTotal: item.price * item.quantity,
        sellerId: productData?.sellerId ?? "",
        sellerName: productData?.sellerName ?? "",
        sellerLogoUrl: productData?.sellerLogoUrl ?? "",
      });
    }
    if (stockFailures.length > 0) {
      throw new HttpsError(
        "failed-precondition",
        `Insufficient stock for: ${stockFailures.join(", ")}`,
      );
    }

    const subtotalCents = cartItems.reduce(
      (sum, item) => sum + Math.round(item.price * 100) * item.quantity,
      0,
    );

    // Coupon validation
    let discountCents = 0;
    let appliedCouponCode = "";
    let couponTargetProductIds: string[] = [];

    const rawCouponCode = (request.data as { couponCode?: string }).couponCode;
    if (rawCouponCode && typeof rawCouponCode === "string" && rawCouponCode.trim().length > 0) {
      const normalized = rawCouponCode.trim().toUpperCase();
      const couponDoc = await db
        .collection("discountCodes")
        .doc(normalized)
        .get();
      if (!couponDoc.exists) {
        throw new HttpsError("not-found", "Code not found");
      }
      const couponData = couponDoc.data()!;
      validateCouponData(couponData, cartItems, subtotalCents);
      discountCents = computeDiscount(
        couponData,
        cartItems,
        subtotalCents,
        shippingCents,
      );
      appliedCouponCode = normalized;
      couponTargetProductIds = couponData.targetProductIds ?? [];
    }

    // Stripe's minimum charge is 50¢ (USD). We used to clamp the net total up
    // to 50, but the per-seller shares and the stored order totals were
    // computed on the UN-clamped net — so a sub-50¢ order charged 50¢ while
    // recording less, diverging line math and refunds. Reject instead: a
    // coherent order cannot cost less than the minimum chargeable amount.
    const netTotalCents = subtotalCents + shippingCents - discountCents;
    if (netTotalCents < 50) {
      throw new HttpsError(
        "failed-precondition",
        "Order total is below the minimum chargeable amount ($0.50).",
      );
    }
    const finalTotalCents = netTotalCents;

    // ── Phase 6: per-seller grouping + allocation ──────────────────
    const itemsBySeller = new Map<string, EnrichedItem[]>();
    for (const it of enrichedItems) {
      const list = itemsBySeller.get(it.sellerId) ?? [];
      list.push(it);
      itemsBySeller.set(it.sellerId, list);
    }
    const sellerSubtotals = new Map<string, number>();
    for (const [sid, list] of itemsBySeller) {
      const sub = list.reduce(
        (s, it) => s + Math.round(it.snapshotPrice * 100) * it.quantity,
        0,
      );
      sellerSubtotals.set(sid, sub);
    }
    const shippingShares = allocateProRata(
      sellerSubtotals,
      subtotalCents,
      shippingCents,
    );
    const discountShares = allocateDiscount(
      itemsBySeller,
      sellerSubtotals,
      subtotalCents,
      discountCents,
      couponTargetProductIds,
    );

    // Stripe PaymentIntent (single charge for the cart)
    const orderId = db.collection("orders").doc().id;
    const stripe = new Stripe(stripeSecretKey.value(), {
      apiVersion: "2025-02-24.acacia",
    });
    const paymentIntent = await stripe.paymentIntents.create({
      amount: finalTotalCents,
      currency: "usd",
      automatic_payment_methods: { enabled: true },
      metadata: {
        userId: request.auth.uid,
        orderId,
      },
    });

    // Payment-gated fan-out: DO NOT create /orders or /sellerOrders here.
    // Stash the computed payload in checkoutSessions/{orderId}; the Stripe
    // webhook materialises the real docs after payment_intent.succeeded, so
    // the seller is only notified once the customer has actually paid.
    const sellers: SellerBreakdown[] = [];
    for (const [sid, items] of itemsBySeller) {
      const subId = db.collection("sellerOrders").doc().id;
      sellers.push({
        sellerOrderId: subId,
        sellerId: sid,
        sellerName: items[0]?.sellerName ?? "",
        sellerLogoUrl: items[0]?.sellerLogoUrl ?? "",
        items: items.map((it) => ({
          productId: it.productId,
          productTitle: it.productTitle,
          quantity: it.quantity,
          snapshotPrice: it.snapshotPrice,
          lineTotal: it.lineTotal,
        })),
        subtotalCents: sellerSubtotals.get(sid) ?? 0,
        shippingShareCents: shippingShares.get(sid) ?? 0,
        discountShareCents: discountShares.get(sid) ?? 0,
      });
    }

    const session: CheckoutSession = {
      orderId,
      userId: request.auth.uid,
      shippingAddress,
      items: cartItems.map((item) => ({
        productId: item.productId,
        productTitle: item.productTitle,
        quantity: item.quantity,
        snapshotPrice: item.price,
        lineTotal: item.price * item.quantity,
      })),
      sellers,
      subtotalCents,
      shippingCents,
      finalTotalCents,
      discountCents,
      discountCode: appliedCouponCode,
      stripePaymentIntentId: paymentIntent.id,
    };

    await db.collection("checkoutSessions").doc(orderId).set({
      ...session,
      status: "AWAITING_PAYMENT",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      // L1: TTL anchor. The webhook deletes the session on success/failure;
      // this cleans up ABANDONED checkouts (never paid, no Stripe event) so
      // they don't linger with PII forever. A Firestore TTL policy on the
      // `expiresAt` field auto-deletes the doc after this instant.
      expiresAt: admin.firestore.Timestamp.fromMillis(
        Date.now() + CHECKOUT_SESSION_TTL_MS,
      ),
    });

    return {
      clientSecret: paymentIntent.client_secret,
      amountCents: finalTotalCents,
      orderId,
      discountAmountCents: discountCents,
    };
  },
);

// ─── stripeWebhook: payment-confirmation fan-out ───────────────────────
//
// Stripe → this endpoint on every configured event. We act on
// payment_intent.succeeded: load the stashed checkoutSessions/{orderId} and
// materialise /orders + /sellerOrders (idempotently). That creation is what
// fires onNewSellerOrder, so the seller is notified only after a real payment.
// On failure/cancellation we drop the session so it does not linger.
//
// Setup (one-time):
//   1. firebase deploy --only functions:stripeWebhook
//   2. Stripe Dashboard → Developers → Webhooks → Add endpoint →
//      URL = the function's URL, events = payment_intent.succeeded,
//      payment_intent.payment_failed, payment_intent.canceled
//   3. firebase functions:secrets:set STRIPE_WEBHOOK_SECRET  (paste whsec_…)

export const stripeWebhook = onRequest(
  { secrets: [stripeSecretKey, stripeWebhookSecret] },
  async (req, res) => {
    const stripe = new Stripe(stripeSecretKey.value(), {
      apiVersion: "2025-02-24.acacia",
    });

    let event: Stripe.Event;
    try {
      const signature = req.headers["stripe-signature"];
      event = stripe.webhooks.constructEvent(
        req.rawBody,
        signature as string,
        stripeWebhookSecret.value(),
      );
    } catch (err) {
      console.error("[stripeWebhook] signature verification failed", err);
      res.status(400).send("Invalid signature");
      return;
    }

    const db = admin.firestore();
    const pi = event.data.object as Stripe.PaymentIntent;
    const orderId = pi?.metadata?.orderId;

    try {
      // Firestore reads are only needed to decide the succeeded branch.
      let orderExists = false;
      let sessionSnap: admin.firestore.DocumentSnapshot | null = null;
      if (event.type === "payment_intent.succeeded" && orderId) {
        orderExists = (await db.collection("orders").doc(orderId).get()).exists;
        if (!orderExists) {
          sessionSnap = await db.collection("checkoutSessions").doc(orderId).get();
        }
      }
      const sessionExists = !!sessionSnap && sessionSnap.exists;

      const outcome = decideWebhookAction(
        event.type,
        orderId,
        orderExists,
        sessionExists,
      );

      switch (outcome.action) {
        case "materialise": {
          const session = sessionSnap!.data() as CheckoutSession;
          const docs = buildFanoutDocs(
            session,
            admin.firestore.FieldValue.serverTimestamp(),
            admin.firestore.Timestamp.now(),
          );
          const decrements = buildStockDecrements(session);

          // Pre-read the product + coupon docs so the atomic batch never
          // references a doc deleted mid-checkout — that would fail the whole
          // batch and block order creation for an already-charged customer.
          const productRefs = decrements.map((d) =>
            db.collection("PRODUCTS").doc(d.productId));
          const couponRef = session.discountCode
            ? db.collection("discountCodes").doc(session.discountCode)
            : null;
          const [productSnaps, couponSnap] = await Promise.all([
            Promise.all(productRefs.map((r) => r.get())),
            couponRef ? couponRef.get() : Promise.resolve(null),
          ]);

          const batch = db.batch();
          batch.set(db.collection("orders").doc(docs.order.id), docs.order.data);
          for (const so of docs.sellerOrders) {
            batch.set(db.collection("sellerOrders").doc(so.id), so.data);
          }
          // M2: decrement stock atomically with order creation. Guarded by the
          // order-exists idempotency check, so a retried delivery never
          // double-decrements. Skip products deleted since checkout.
          decrements.forEach((d, i) => {
            if (productSnaps[i].exists) {
              batch.update(productRefs[i], {
                totalStockQuantity:
                  admin.firestore.FieldValue.increment(-d.quantity),
              });
            } else {
              console.warn("[stripeWebhook] product missing — skip stock decrement", {
                productId: d.productId,
              });
            }
          });
          // X1: coupon usage is authoritative here now (was a client call that
          // could be lost on client death), same idempotency guarantee.
          if (couponRef && couponSnap && couponSnap.exists) {
            batch.update(couponRef, {
              usageCount: admin.firestore.FieldValue.increment(1),
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
          }
          // Consume the session so it cannot be replayed.
          batch.delete(db.collection("checkoutSessions").doc(orderId!));
          await batch.commit();
          console.log("[stripeWebhook] materialised order", {
            orderId,
            sellerOrders: docs.sellerOrders.length,
            stockDecremented: decrements.length,
            couponApplied: !!(couponRef && couponSnap && couponSnap.exists),
          });
          break;
        }
        case "retry-missing-session":
          // M1: charged but no session to build from — alert + let Stripe retry.
          console.error("[stripeWebhook] succeeded but no checkoutSession — returning 500 to retry", { orderId });
          break;
        case "drop-session":
          if (orderId) {
            await db.collection("checkoutSessions").doc(orderId).delete();
            console.log("[stripeWebhook] dropped session for failed/canceled payment", { orderId });
          }
          break;
        case "skip-already-done":
          console.log("[stripeWebhook] order already materialised — skipping", { orderId });
          break;
        case "skip-no-order-id":
          console.warn("[stripeWebhook] succeeded event without orderId metadata");
          break;
        case "ignore":
          break;
      }

      res.status(outcome.status).send(outcome.status === 200 ? "ok" : "retry");
    } catch (err) {
      console.error("[stripeWebhook] handler error", err);
      // 500 tells Stripe to retry — safe because materialisation is idempotent.
      res.status(500).send("handler error");
    }
  },
);

// ─── cancelSellerOrder callable (Phase 6) ──────────────────────────────

export const cancelSellerOrder = onCall(
  { secrets: [stripeSecretKey] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required");
    }
    const { sellerOrderId } = request.data as { sellerOrderId: string };
    if (!sellerOrderId || typeof sellerOrderId !== "string") {
      throw new HttpsError("invalid-argument", "sellerOrderId required");
    }

    const db = admin.firestore();
    const subRef = db.collection("sellerOrders").doc(sellerOrderId);
    const subSnap = await subRef.get();
    if (!subSnap.exists) {
      throw new HttpsError("not-found", "Sub-order not found");
    }
    const sub = subSnap.data()!;

    if (sub.sellerId !== request.auth.uid) {
      throw new HttpsError(
        "permission-denied",
        "Only the seller can cancel",
      );
    }
    if (["SHIPPED", "DELIVERED", "CANCELLED"].includes(sub.status)) {
      throw new HttpsError(
        "failed-precondition",
        "Cannot cancel post-shipping",
      );
    }

    const parentSnap = await db.collection("orders").doc(sub.parentOrderId).get();
    if (!parentSnap.exists) {
      throw new HttpsError("not-found", "Parent order not found");
    }
    const pi = parentSnap.data()!.stripePaymentIntentId as string;
    if (!pi) {
      throw new HttpsError(
        "failed-precondition",
        "No paymentIntent on parent order",
      );
    }

    const refundCents = Math.round(
      ((sub.subtotal ?? 0) + (sub.shippingShare ?? 0) - (sub.discountShare ?? 0)) * 100,
    );

    const stripe = new Stripe(stripeSecretKey.value(), {
      apiVersion: "2025-02-24.acacia",
    });

    // Idempotency key — stable per sub-order, prevents double refund on retry.
    const idempotencyKey = `cancel-${sellerOrderId}`;

    let refund: Stripe.Refund;
    try {
      refund = await stripe.refunds.create(
        {
          payment_intent: pi,
          amount: refundCents,
          metadata: { sellerOrderId },
        },
        { idempotencyKey },
      );
    } catch (err) {
      const code = (err as { code?: string }).code;
      if (code === "charge_already_refunded") {
        throw new HttpsError(
          "failed-precondition",
          "Refund exceeds available amount",
        );
      }
      throw err;
    }

    await subRef.update({
      status: "CANCELLED",
      statusHistory: admin.firestore.FieldValue.arrayUnion({
        status: "CANCELLED",
        timestamp: admin.firestore.Timestamp.now(),
        note: "Cancelled by seller",
        trackingNumber: null,
      }),
      refundId: refund.id,
      refundedAmount: refundCents / 100,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return {
      success: true,
      refundId: refund.id,
      refundedCents: refundCents,
    };
  },
);

// ─── Reviews & Ratings (Phase 7) ───────────────────────────────────────

interface SubmitReviewRequest {
  productId: string;
  rating: number;
  title: string;
  body: string;
  reviewerName: string;
  reviewerPhotoUrl: string;
}

interface ReviewData {
  id: string;
  productId: string;
  reviewerId: string;
  reviewerName: string;
  reviewerPhotoUrl: string;
  purchaseId: string;
  rating: number;
  title: string;
  body: string;
  isVerifiedPurchase: boolean;
  helpfulCount: number;
  isVisible: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Pure builder — produces the exact review doc shape written by submitReview.
 * Side-effect free (createdAt injected as epoch-millis string to match the
 * legacy client format `System.currentTimeMillis().toString()`; lexicographic
 * sort on fixed-width 13-digit strings is correct — see RESEARCH Pitfall 7).
 * On an edit the original createdAt + helpfulCount are preserved. Unit-testable
 * without Firestore, mirroring buildFanoutDocs.
 */
export function buildReviewData(
  uid: string,
  reviewId: string,
  productId: string,
  data: SubmitReviewRequest,
  purchaseId: string,
  existingDoc?: admin.firestore.DocumentData,
  isEdit?: boolean,
): ReviewData {
  const nowMillis = admin.firestore.Timestamp.now().toMillis().toString();
  return {
    id: reviewId,
    productId,
    reviewerId: uid,
    reviewerName: data.reviewerName ?? "",
    reviewerPhotoUrl: data.reviewerPhotoUrl ?? "",
    purchaseId,
    rating: data.rating,
    title: data.title ?? "",
    body: data.body ?? "",
    isVerifiedPurchase: true, // always — the function is the verification gate (REVW-05)
    helpfulCount: isEdit ? ((existingDoc?.helpfulCount as number) ?? 0) : 0,
    isVisible: true,
    createdAt: isEdit
      ? ((existingDoc?.createdAt as string) ?? nowMillis)
      : nowMillis,
    updatedAt: nowMillis,
  };
}

/**
 * Pure aggregate recompute — kept side-effect free so every branch is
 * unit-testable without Firestore (mirrors decideWebhookAction).
 *
 * New review:  count → count+1, avg → running mean.
 * Edit (isEdit): count UNCHANGED, avg delta-corrected by swapping oldRating for
 * newRating (RESEARCH Pitfall 1 — incrementing count on an edit is the classic
 * bug this guards against).
 */
export function computeNewAggregate(
  currentAvg: number,
  currentCount: number,
  newRating: number,
  oldRating?: number,
  isEdit?: boolean,
): { newAvg: number; newCount: number } {
  if (isEdit) {
    const newCount = currentCount; // count unchanged for an edit
    const newAvg = currentCount === 0
      ? newRating
      : ((currentAvg * currentCount) - (oldRating ?? 0) + newRating) / currentCount;
    return { newAvg, newCount };
  }
  const newCount = currentCount + 1;
  const newAvg = currentCount === 0
    ? newRating
    : ((currentAvg * currentCount) + newRating) / newCount;
  return { newAvg, newCount };
}

/**
 * submitReview — the ONLY write path for reviews (D-01/D-02). Verifies the
 * caller owns a DELIVERED sellerOrder containing the product (REVW-02),
 * enforces one review per (customer, product) with edit-in-place (REVW-03),
 * sets isVerifiedPurchase server-side (REVW-05), and recomputes the product
 * rating aggregate atomically in the same transaction (REVW-04). Mirrors the
 * cancelSellerOrder server-authoritative pattern.
 */
export const submitReview = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required");
  }
  const uid = request.auth.uid;
  const { productId, rating } = request.data as SubmitReviewRequest;
  const data = request.data as SubmitReviewRequest;

  if (!productId || typeof productId !== "string") {
    throw new HttpsError("invalid-argument", "productId required");
  }
  if (typeof rating !== "number" || rating < 1 || rating > 5) {
    throw new HttpsError("invalid-argument", "rating must be 1-5");
  }

  const db = admin.firestore();

  // REVW-02 — verified-purchase gate. Requires a DELIVERED sellerOrder owned by
  // this user that contains the product. (Composite index sellerOrders
  // userId ASC, status ASC — see firestore.indexes.json / RESEARCH Pitfall 2.)
  const sellerOrdersSnap = await db
    .collection("sellerOrders")
    .where("userId", "==", uid)
    .where("status", "==", "DELIVERED")
    .get();
  const qualifying = sellerOrdersSnap.docs.filter((doc) => {
    const items: { productId: string }[] = doc.data().items ?? [];
    return items.some((it) => it.productId === productId);
  });
  if (qualifying.length === 0) {
    throw new HttpsError(
      "failed-precondition",
      "No delivered order found for this product",
    );
  }

  // REVW-03 — one review per (reviewer, product); an edit REPLACES in place.
  const reviewsRef = db
    .collection("PRODUCTS")
    .doc(productId)
    .collection("REVIEWS");
  const existingSnap = await reviewsRef.where("reviewerId", "==", uid).get();
  const isEdit = !existingSnap.empty;
  const existingDoc = isEdit ? existingSnap.docs[0].data() : undefined;
  const reviewId = isEdit ? existingSnap.docs[0].id : reviewsRef.doc().id;
  const oldRating = isEdit
    ? ((existingSnap.docs[0].data().rating as number) ?? 0)
    : undefined;

  const reviewData = buildReviewData(
    uid,
    reviewId,
    productId,
    data,
    qualifying[0].id,
    existingDoc,
    isEdit,
  );

  const productRef = db.collection("PRODUCTS").doc(productId);
  const reviewRef = reviewsRef.doc(reviewId);

  // REVW-04 — write review + recompute product aggregate atomically.
  await db.runTransaction(async (tx) => {
    const productSnap = await tx.get(productRef);
    const currentCount = (productSnap.data()?.reviewCount as number) ?? 0;
    const currentAvg = (productSnap.data()?.averageRating as number) ?? 0;
    const { newAvg, newCount } = computeNewAggregate(
      currentAvg,
      currentCount,
      rating,
      oldRating,
      isEdit,
    );
    tx.set(reviewRef, reviewData);
    tx.update(productRef, {
      reviewCount: newCount,
      averageRating: newAvg,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });

  return { reviewId, isVerifiedPurchase: true };
});

/**
 * markReviewHelpful — idempotent per-user helpful vote (D-04). A transaction
 * reads helpfulVotes/{uid}; a repeat vote throws already-exists (Pitfall 5),
 * otherwise the vote doc is created and helpfulCount is incremented by one in
 * the same atomic write. helpfulVotes writes are server-only in firestore.rules.
 */
export const markReviewHelpful = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required");
  }
  const { productId, reviewId } = request.data as {
    productId: string;
    reviewId: string;
  };
  if (!productId || typeof productId !== "string") {
    throw new HttpsError("invalid-argument", "productId required");
  }
  if (!reviewId || typeof reviewId !== "string") {
    throw new HttpsError("invalid-argument", "reviewId required");
  }
  const uid = request.auth.uid;
  const db = admin.firestore();
  const reviewRef = db
    .collection("PRODUCTS")
    .doc(productId)
    .collection("REVIEWS")
    .doc(reviewId);
  const voteRef = reviewRef.collection("helpfulVotes").doc(uid);

  await db.runTransaction(async (tx) => {
    const voteSnap = await tx.get(voteRef);
    if (voteSnap.exists) {
      throw new HttpsError("already-exists", "Already marked helpful");
    }
    tx.set(voteRef, {
      votedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    tx.update(reviewRef, {
      helpfulCount: admin.firestore.FieldValue.increment(1),
    });
  });

  return { success: true };
});

// ─── Aggregate status helper (RESEARCH §2.8) ───────────────────────────

export function computeAggregateStatus(statuses: string[]): string {
  const order = ["PENDING", "CONFIRMED", "SHIPPED", "DELIVERED"];
  if (statuses.length === 0) return "PENDING";
  const hasCancelled = statuses.includes("CANCELLED");
  const nonCancelled = statuses.filter((s) => s !== "CANCELLED");
  if (nonCancelled.length === 0) return "CANCELLED";
  if (hasCancelled) return "PARTIALLY_CANCELLED";
  return nonCancelled.reduce(
    (min, s) => (order.indexOf(s) < order.indexOf(min) ? s : min),
    "DELIVERED",
  );
}

function titleFor(status: string): string {
  switch (status) {
    case "CONFIRMED": return "Order confirmed";
    case "SHIPPED":   return "Order shipped";
    case "DELIVERED": return "Order delivered";
    case "CANCELLED": return "Order cancelled";
    default:          return "Order update";
  }
}

// ─── onOrderStatusChange Firestore trigger (Phase 6) ───────────────────

export const onOrderStatusChange = onDocumentWritten(
  "sellerOrders/{sellerOrderId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    console.log("[trigger] fired", {
      sellerOrderId: event.params.sellerOrderId,
      beforeStatus: before?.status ?? null,
      afterStatus: after?.status ?? null,
    });
    if (!after) {
      console.log("[trigger] after=null (delete event) — skipping");
      return;
    }
    if (!before) {
      // Create event — onNewSellerOrder handles the seller push. Sending
      // a "your order is PENDING" push to the customer right after they
      // just placed the order is spammy; skip.
      console.log("[trigger] create event (no before) — deferring to onNewSellerOrder");
      return;
    }
    if (before.status === after.status) {
      console.log("[trigger] status unchanged — skipping FCM dispatch");
      return;
    }

    const db = admin.firestore();
    const parentId = after.parentOrderId as string;
    if (!parentId) {
      console.log("[trigger] no parentOrderId on sellerOrder doc — skipping");
      return;
    }
    console.log("[trigger] processing status change", {
      parentId,
      newStatus: after.status,
    });
    const parentRef = db.collection("orders").doc(parentId);

    // W5 race mitigation: reads-before-writes inside a transaction, with
    // monotonic aggregateVersion CAS guard on the parent doc.
    await db.runTransaction(async (tx) => {
      const subsQuery = db
        .collection("sellerOrders")
        .where("parentOrderId", "==", parentId);
      const subsSnap = await tx.get(subsQuery);
      const parentSnap = await tx.get(parentRef);
      const statuses = subsSnap.docs.map((d) => d.data().status as string);
      const aggregateStatus = computeAggregateStatus(statuses);
      const currentVersion =
        (parentSnap.data()?.aggregateVersion as number | undefined) ?? 0;
      tx.update(parentRef, {
        aggregateStatus,
        aggregateVersion: currentVersion + 1,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    });

    // FCM dispatch — outside the transaction; duplicates are far better than a
    // refused transaction (push is not transactional with the aggregate write).
    try {
      const parentSnap = await parentRef.get();
      const customerUid = parentSnap.data()?.userId as string | undefined;
      console.log("[fcm] customer lookup", { customerUid });
      if (!customerUid) {
        console.log("[fcm] no customerUid on parent — skipping");
        return;
      }
      const userSnap = await db.collection("USERS").doc(customerUid).get();
      const fcmToken = userSnap.data()?.fcmToken as string | undefined;
      console.log("[fcm] token lookup", {
        hasToken: !!fcmToken,
        tokenPrefix: fcmToken?.substring(0, 16) ?? null,
      });
      if (!fcmToken) {
        console.log("[fcm] no fcmToken on USERS doc — skipping send");
        return;
      }

      console.log("[fcm] sending message", {
        title: titleFor(after.status),
        status: after.status,
      });
      const messageId = await getMessaging().send({
        token: fcmToken,
        notification: {
          title: titleFor(after.status),
          body: `Your order is ${String(after.status).toLowerCase()}.`,
        },
        data: {
          type: "order_status",
          orderId: parentId,
          sellerOrderId: event.params.sellerOrderId,
          newStatus: String(after.status),
        },
        android: {
          priority: "high",
          notification: {
            channelId: "order_status_channel",
            // clickAction removed: without a matching <intent-filter> on
            // MainActivity, some Android versions silently no-op the tap.
            // Default launcher intent opens MainActivity with the `data`
            // payload as raw intent extras — MainActivity reads them.
          },
        },
      });
      console.log("[fcm] send SUCCESS", { messageId });
    } catch (err) {
      console.error("[fcm] dispatch FAILED for parentId", parentId, err);
      // Swallow — aggregate write is the contract, push is best-effort.
    }
  },
);

/**
 * Fires once when a sellerOrders/{id} doc is created. Since the fan-out now
 * runs from the Stripe webhook after payment_intent.succeeded, this fires only
 * for real, paid orders. Sends a "New order" FCM to the seller so their Orders
 * tab refreshes without pull-to-refresh.
 *
 * Client-side consumer: MessagingService routes `data.type == "new_order"`
 * onto SyncBus.NewOrder, which SellerOrdersViewModel collects and calls
 * OrderRepository.syncSellerOrders(sellerId).
 */
export const onNewSellerOrder = onDocumentCreated(
  "sellerOrders/{sellerOrderId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) {
      console.log("[new_order] no data on created doc — skipping");
      return;
    }
    const sellerUid = data.sellerId as string | undefined;
    const sellerOrderId = event.params.sellerOrderId;
    console.log("[new_order] fired", { sellerOrderId, sellerUid });
    if (!sellerUid) {
      console.log("[new_order] no sellerId on doc — skipping");
      return;
    }

    const db = admin.firestore();
    const userSnap = await db.collection("USERS").doc(sellerUid).get();
    const fcmToken = userSnap.data()?.fcmToken as string | undefined;
    console.log("[new_order] seller token lookup", {
      hasToken: !!fcmToken,
      tokenPrefix: fcmToken?.substring(0, 16) ?? null,
    });
    if (!fcmToken) {
      console.log("[new_order] no fcmToken on seller USERS doc — skipping");
      return;
    }

    try {
      const messageId = await getMessaging().send({
        token: fcmToken,
        notification: {
          title: "New order",
          body: "You have a new order to fulfill.",
        },
        data: {
          type: "new_order",
          sellerOrderId,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "order_status_channel",
          },
        },
      });
      console.log("[new_order] send SUCCESS", { messageId });
    } catch (err) {
      console.error("[new_order] dispatch FAILED for sellerOrderId", sellerOrderId, err);
    }
  },
);

// ─── onNewReview Firestore trigger (Phase 8 — NOTF-03, D-04) ───────────

/**
 * Pure helper — the notification body a seller sees when their product is
 * reviewed. Extracted so it can be unit-tested without Firestore (mirrors the
 * buildReviewData / computeAggregateStatus named-export testability idiom).
 */
export function buildNewReviewBody(productTitle: string): string {
  return `Your product "${productTitle}" received a new review.`;
}

/**
 * Fires once when a review doc is created under PRODUCTS/{id}/REVIEWS. Since
 * submitReview (the only writer) uses set() on a fixed reviewId and an edit
 * REPLACES in place, onDocumentCreated fires only on the FIRST review, never on
 * an edit (RESEARCH A2) — so a seller is notified once per new review.
 *
 * Resolves the recipient server-side from PRODUCTS/{productId}.sellerId (never
 * from client input — T-08-05/06), writes the notifications history doc BEFORE
 * the FCM send so history persists even when the seller has no fcmToken or the
 * send throws (T-08-08), then best-effort pushes FCM to the seller.
 */
export const onNewReview = onDocumentCreated(
  "PRODUCTS/{productId}/REVIEWS/{reviewId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) {
      console.log("[new_review] no data on created doc — skipping");
      return;
    }
    const productId = event.params.productId;
    const db = admin.firestore();

    // Resolve the seller from the product doc (server-authoritative recipient).
    const productSnap = await db.collection("PRODUCTS").doc(productId).get();
    const sellerId = productSnap.data()?.sellerId as string | undefined;
    const productTitle =
      (productSnap.data()?.title as string | undefined) ?? productId;
    console.log("[new_review] fired", { productId, sellerId });
    if (!sellerId) {
      console.log("[new_review] no sellerId on product — skipping");
      return;
    }

    const title = "New review";
    const body = buildNewReviewBody(productTitle);

    // Persist to seller notification history FIRST — unconditionally, before
    // the FCM send, so history survives a missing token or an FCM failure.
    const notifRef = db
      .collection("notifications")
      .doc(sellerId)
      .collection("items")
      .doc();
    await notifRef.set({
      id: notifRef.id,
      type: "new_review",
      title,
      body,
      orderId: "",
      sellerOrderId: "",
      productId,
      productTitle,
      read: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    const userSnap = await db.collection("USERS").doc(sellerId).get();
    const fcmToken = userSnap.data()?.fcmToken as string | undefined;
    console.log("[new_review] seller token lookup", {
      hasToken: !!fcmToken,
      tokenPrefix: fcmToken?.substring(0, 16) ?? null,
    });
    if (!fcmToken) {
      console.log("[new_review] no fcmToken — history written, push skipped");
      return;
    }

    try {
      const messageId = await getMessaging().send({
        token: fcmToken,
        notification: { title, body },
        data: {
          type: "new_review",
          productId,
          productTitle,
          notifId: notifRef.id,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "order_updates_channel",
          },
        },
      });
      console.log("[new_review] send SUCCESS", { messageId });
    } catch (err) {
      console.error("[new_review] dispatch FAILED for productId", productId, err);
    }
  },
);
