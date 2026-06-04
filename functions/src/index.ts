import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import Stripe from "stripe";
import * as admin from "firebase-admin";

admin.initializeApp();

const stripeSecretKey = defineSecret("STRIPE_SECRET_KEY");

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

// ─── Shared discount helpers ───────────────────────────────────────────

/**
 * Validates a coupon document data against cart contents.
 * Throws HttpsError with specific codes on failure.
 */
function validateCouponData(
  data: admin.firestore.DocumentData,
  cartItems: CartItem[],
  subtotalCents: number,
): void {
  // Active check — treat inactive as not found for security
  if (!data.isActive) {
    throw new HttpsError("not-found", "Code not found");
  }

  // Expiry check
  if (data.expiresAt && data.expiresAt.toDate() < new Date()) {
    throw new HttpsError("failed-precondition", "This code has expired");
  }

  // Usage limit check
  if (data.usageLimit != null && data.usageCount >= data.usageLimit) {
    throw new HttpsError("resource-exhausted", "Usage limit reached");
  }

  // Eligible items check (for product-scoped coupons)
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

  // Minimum order check
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

/**
 * Computes the discount amount in cents based on coupon type.
 * For product-scoped coupons, only eligible items contribute to the discount base.
 */
function computeDiscount(
  couponData: admin.firestore.DocumentData,
  cartItems: CartItem[],
  subtotalCents: number,
  shippingCents: number,
): number {
  const type: string = couponData.type;
  const value: number = couponData.value ?? 0;
  const targetProductIds: string[] = couponData.targetProductIds ?? [];

  // For product-scoped coupons, compute eligible subtotal
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

/**
 * Builds a human-readable description for a coupon.
 */
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
    // Auth check
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

    // Normalize
    const normalized = couponCode.trim().toUpperCase();

    // Fetch document (O(1) lookup by doc ID)
    const db = admin.firestore();
    const doc = await db.collection("discountCodes").doc(normalized).get();

    if (!doc.exists) {
      throw new HttpsError("not-found", "Code not found");
    }

    const data = doc.data()!;

    // Validate
    validateCouponData(data, cartItems, subtotalCents);

    // Compute discount (shippingCents = 0 for preview)
    const discountCents = computeDiscount(data, cartItems, subtotalCents, 0);

    return {
      code: normalized,
      type: data.type,
      discountCents,
      description: buildDiscountDescription(data),
    };
  },
);

// ─── decrementCouponUsage Cloud Function ───────────────────────────────

export const decrementCouponUsage = onCall(
  { secrets: [stripeSecretKey] },
  async (request) => {
    // Auth check
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Must be signed in");
    }

    const { couponCode } = request.data as { couponCode: string };

    if (!couponCode || typeof couponCode !== "string") {
      throw new HttpsError("invalid-argument", "Coupon code is required");
    }

    const normalized = couponCode.trim().toUpperCase();
    const db = admin.firestore();

    // Atomic increment — race-condition-free
    await db.collection("discountCodes").doc(normalized).update({
      usageCount: admin.firestore.FieldValue.increment(1),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { success: true };
  },
);

// ─── createPaymentIntent Cloud Function ────────────────────────────────

export const createPaymentIntent = onCall(
  { secrets: [stripeSecretKey] },
  async (request) => {
    // 1. Auth check
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Must be signed in to checkout",
      );
    }

    // 2. Extract data
    console.log("RAW request.data:", JSON.stringify(request.data));
    const { cartItems, shippingAddress } = request.data as {
      cartItems: CartItem[];
      shippingAddress: ShippingAddress;
      userId: string;
    };
    console.log("Parsed cartItems:", JSON.stringify(cartItems));

    // 3. Input validation
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

    // 4. Server-side stock validation and shipping cost fetch
    const db = admin.firestore();
    const stockFailures: string[] = [];
    let shippingCents = 0;

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

      // Stock validation
      const stockQuantity: number = productData?.totalStockQuantity ?? 0;
      if (stockQuantity < item.quantity) {
        stockFailures.push(item.productTitle);
      }

      // 6. Compute shipping per product line (not per unit)
      const shippingCost: number = productData?.shipping?.shippingCost ?? 0;
      shippingCents += Math.round(shippingCost * 100);
    }

    if (stockFailures.length > 0) {
      throw new HttpsError(
        "failed-precondition",
        `Insufficient stock for: ${stockFailures.join(", ")}`,
      );
    }

    // 5. Compute subtotal in cents
    const subtotalCents = cartItems.reduce(
      (sum, item) => sum + Math.round(item.price * 100) * item.quantity,
      0,
    );

    // 7. Coupon validation and discount computation
    let discountCents = 0;
    let appliedCouponCode = "";

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

      // Re-validate coupon (authoritative check — never trust client)
      validateCouponData(couponData, cartItems, subtotalCents);

      // Compute discount with actual shipping
      discountCents = computeDiscount(
        couponData,
        cartItems,
        subtotalCents,
        shippingCents,
      );
      appliedCouponCode = normalized;
    }

    // 8. Final total (Stripe minimum is 50 cents)
    const finalTotalCents = Math.max(
      50,
      subtotalCents + shippingCents - discountCents,
    );

    // 9. Create Stripe PaymentIntent
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

    // 10. Create pending Order in Firestore
    await db.collection("orders").doc(orderId).set({
      userId: request.auth.uid,
      status: "PENDING",
      subtotal: subtotalCents / 100,
      shippingTotal: shippingCents / 100,
      totalAmount: finalTotalCents / 100,
      discountAmount: discountCents / 100,
      discountCode: appliedCouponCode,
      currency: "USD",
      stripePaymentIntentId: paymentIntent.id,
      shippingAddress: shippingAddress,
      items: cartItems.map((item) => ({
        productId: item.productId,
        productTitle: item.productTitle,
        quantity: item.quantity,
        snapshotPrice: item.price,
        lineTotal: item.price * item.quantity,
      })),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // 11. Return clientSecret, amountCents, orderId, and discountAmountCents
    return {
      clientSecret: paymentIntent.client_secret,
      amountCents: finalTotalCents,
      orderId,
      discountAmountCents: discountCents,
    };
  },
);
