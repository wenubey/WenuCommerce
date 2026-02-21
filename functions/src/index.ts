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

export const createPaymentIntent = onCall(
  { secrets: [stripeSecretKey] },
  async (request) => {
    // 1. Auth check
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Must be signed in to checkout"
      );
    }

    // 2. Extract data
    const { cartItems, shippingAddress } = request.data as {
      cartItems: CartItem[];
      shippingAddress: ShippingAddress;
      userId: string;
    };

    // 3. Input validation
    if (!Array.isArray(cartItems) || cartItems.length === 0) {
      throw new HttpsError(
        "invalid-argument",
        "Cart must contain at least one item"
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
          "Each cart item must have a valid productId, quantity, and price"
        );
      }
    }

    // 4. Server-side stock validation and shipping cost fetch
    const db = admin.firestore();
    const stockFailures: string[] = [];
    let shippingCents = 0;

    for (const item of cartItems) {
      const productDoc = await db.collection("products").doc(item.productId).get();

      if (!productDoc.exists) {
        throw new HttpsError(
          "failed-precondition",
          `Product ${item.productTitle} not found`
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
        `Insufficient stock for: ${stockFailures.join(", ")}`
      );
    }

    // 5. Compute subtotal in cents
    const subtotalCents = cartItems.reduce(
      (sum, item) => sum + Math.round(item.price * 100) * item.quantity,
      0
    );

    // 7. Total
    const totalCents = subtotalCents + shippingCents;

    // 8. Create Stripe PaymentIntent
    const orderId = db.collection("orders").doc().id;

    const stripe = new Stripe(stripeSecretKey.value(), {
      apiVersion: "2025-02-24.acacia",
    });

    const paymentIntent = await stripe.paymentIntents.create({
      amount: totalCents,
      currency: "usd",
      automatic_payment_methods: { enabled: true },
      metadata: {
        userId: request.auth.uid,
        orderId,
      },
    });

    // 9. Create pending Order in Firestore
    await db.collection("orders").doc(orderId).set({
      userId: request.auth.uid,
      status: "PENDING",
      subtotal: subtotalCents / 100,
      shippingTotal: shippingCents / 100,
      totalAmount: totalCents / 100,
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

    // 10. Return clientSecret, amountCents, and orderId
    return {
      clientSecret: paymentIntent.client_secret,
      amountCents: totalCents,
      orderId,
    };
  }
);
