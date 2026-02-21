# Phase 4: Checkout & Payments - Research

**Researched:** 2026-02-21
**Domain:** Stripe Android PaymentSheet + Firebase Cloud Functions + Room Order entities
**Confidence:** HIGH (core Stripe SDK + Firebase), MEDIUM (Lottie version, auto-migration applicability)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Checkout flow structure**
- 3-step wizard: Address -> Review -> Payment
- Simple dots progress indicator (three dots, current step highlighted, no labels)
- Free back-navigation: user can tap any previous step dot to revisit earlier steps
- Cart screen has a bottom sticky bar with subtotal and "Proceed to Checkout" button
- Proceed button is disabled when cart has out-of-stock items — user must fix first
- No coupon/discount UI until Phase 5
- No minimum order amount — any non-empty in-stock cart can checkout
- Login is already required app-wide; no guest checkout flow needed

**Stock validation**
- Validate stock at checkout entry (client-side, immediate feedback)
- Validate again server-side when creating PaymentIntent (safety net)
- If server-side check fails during checkout, show warning on review step with affected items highlighted

**Review step**
- Compact item summary: product name, quantity, line total — no images
- Shipping cost: single "Shipping" total line (sum of per-product shipping costs set by sellers)
- Breakdown: Subtotal + Shipping = Total

**Payment step**
- Step 3 shows the total amount and a "Pay Now" button
- Tapping "Pay Now" launches Stripe PaymentSheet
- PaymentSheet handles all card entry — no card data passes through the app

**Address handling**
- Addresses are saved to user profile (Firestore + Room) for reuse across checkouts
- When user has saved addresses: dropdown selector with address labels, plus "Add new" option
- When user has no saved addresses: "Add Address" navigates to a separate address form screen
- Required fields: full name, street line 1, street line 2 (optional), city, state/province, postal code, country

**Order confirmation**
- Summary view: order ID, item count, total paid — minimal info
- Animated green checkmark on screen appearance
- Two action buttons: "Continue Shopping" (back to browsing) and "View Order"
- "View Order" navigates to a minimal order screen (order ID, status, items, total) — Phase 6 will enhance this

**Error handling**
- Payment failure: inline error message on step 3 with "Try Again" button — user stays on payment step, cart preserved
- Back-navigation during checkout: confirmation dialog "Leave checkout? Your progress will be lost"
- Checkout requires connectivity — show "You need internet to complete checkout" if offline

### Claude's Discretion
- Exact wizard transition animations
- Dropdown component styling for address selector
- Address form validation rules and error messages
- Loading states during PaymentIntent creation
- Checkmark animation implementation (Lottie vs custom)
- Minimal order screen layout details

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CHKT-01 | Firebase Cloud Function creates Stripe PaymentIntent server-side | Firebase Functions v2 onCall with defineSecret for STRIPE_SECRET_KEY; returns clientSecret to Android client |
| CHKT-02 | Customer sees checkout summary (items, totals, address) before payment | CheckoutScreen wizard step 2 (Review): CartItem list from Room, shipping sum from product.shipping.shippingCost, address from saved addresses |
| CHKT-03 | Stripe PaymentSheet presented for card entry (no custom form) | rememberPaymentSheet + presentWithPaymentIntent(clientSecret) — no card data ever in app memory |
| CHKT-04 | On payment success: Order document created in Firestore + Room, cart cleared | PaymentSheetResult.Completed triggers OrderRepositoryImpl.createOrder(); clearCart() already exists; no optimistic Room write for payments per STATE.md decision |
| CHKT-05 | On payment failure: error shown, cart preserved, retry allowed | PaymentSheetResult.Failed exposes error.message; cart NOT cleared; user stays on step 3 with "Try Again" |
| CHKT-06 | Order confirmation screen shows order ID, items, and link to order tracking | OrderConfirmationScreen receives orderId via navigation arg; loads Order from Room; "View Order" navigates to minimal order screen |
| CHKT-07 | Customer can enter or select shipping address at checkout | Step 1 of wizard: dropdown of saved addresses (Firestore + Room) + "Add new address" route to AddressFormScreen |
</phase_requirements>

---

## Summary

Phase 4 has two distinct technical domains: (1) a Firebase Cloud Function (`createPaymentIntent`) that creates a Stripe PaymentIntent server-side and returns only the `clientSecret` to the Android client, and (2) a Stripe Android PaymentSheet integration that presents a prebuilt payment UI using that `clientSecret`. Neither domain requires custom card handling — all sensitive data stays inside Stripe's infrastructure.

On the Android side, the integration point is `rememberPaymentSheet` (Compose API) paired with `presentWithPaymentIntent(clientSecret)`. After the sheet dismisses, `PaymentSheetResult` (sealed class: `Completed`, `Canceled`, `Failed`) drives the outcome logic: `Completed` triggers order creation + cart clear, `Canceled` is a no-op, `Failed` shows an inline error with the error message from `paymentSheetResult.error.message`.

The Order domain model and Room entity are net-new for this phase. The database migration from v3 to v4 adds `orders` and `order_items` tables. Room auto-migrations can handle simple new-table additions if `exportSchema = true` is set (already true in this project). The Firestore decision from STATE.md is clear: payments are Firestore-authoritative — no optimistic Room writes for payment state; create the Order document in Room only after the Cloud Function call succeeds and the server confirms.

**Primary recommendation:** Add `com.stripe:stripe-android:22.8.1` to `app/build.gradle.kts`, initialize `PaymentConfiguration` in `WenuCommerce.kt` (Application class) using the publishable key from `local.properties` / `BuildConfig`, and wire `createPaymentIntent` as a Firebase callable function using `defineSecret` for the Stripe secret key.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| com.stripe:stripe-android | 22.8.1 | PaymentSheet prebuilt UI, PaymentConfiguration | Stripe's recommended integration path; includes paymentsheet as transitive dep |
| firebase-functions-ktx | BOM-managed (33.8.0) | `Firebase.functions.getHttpsCallable()` to call `createPaymentIntent` | Already in project BOM; the SDK handles serialization and auth headers |
| stripe (npm) | ~17.x (2025-01-27.acacia API) | Server-side Stripe Node.js lib for Cloud Functions | Official Stripe Node library; required for PaymentIntent creation |
| com.airbnb.android:lottie-compose | 6.7.1 | Animated green checkmark on OrderConfirmationScreen | Standard Compose animation library; simpler than hand-rolled Animatable chain for complex paths |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Room (already in project) | 2.6.1 | OrderEntity, OrderItemEntity, OrderDao | New entities for order persistence; migration v3→v4 |
| kotlinx.serialization (already in project) | bundled with Kotlin 2.1.0 | Serialize address as JSON in Room, payloads for Cloud Function calls | Same pattern used by CartRepositoryImpl |
| Koin (already in project) | 4.0.1 | DI for PaymentRepository, CheckoutViewModel | Consistent with existing viewModelOf() pattern |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Lottie for checkmark | Custom Animatable draw path | Lottie is drop-in; custom draw takes significant time for a single-use animation |
| Firebase callable function | REST endpoint via Ktor | Callable functions handle Firebase Auth token automatically; Ktor requires manual auth header management |
| Room auto-migration | Manual Migration object | Auto-migration works for pure new-table additions; use manual only if column transforms are needed |

### Installation

Add to `app/build.gradle.kts` dependencies block:

```kotlin
// Stripe
implementation("com.stripe:stripe-android:22.8.1")

// Lottie Compose (for success checkmark — only if Claude's discretion chooses Lottie)
implementation("com.airbnb.android:lottie-compose:6.7.1")
```

Add to `libs.versions.toml`:

```toml
[versions]
stripe = "22.8.1"
lottie = "6.7.1"

[libraries]
stripe-android = { module = "com.stripe:stripe-android", version.ref = "stripe" }
lottie-compose = { module = "com.airbnb.android:lottie-compose", version.ref = "lottie" }
```

Add Stripe publishable key to `local.properties`:

```
STRIPE_PUBLISHABLE_KEY=pk_test_...
```

Add `BuildConfig` field in `app/build.gradle.kts` `defaultConfig` block (same pattern as `GOOGLE_ID_WEB_CLIENT`):

```kotlin
buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"${properties.getProperty("STRIPE_PUBLISHABLE_KEY")}\"")
```

Cloud Function dependencies (`functions/package.json`):

```json
{
  "dependencies": {
    "firebase-functions": "^6.x",
    "stripe": "^17.x"
  }
}
```

---

## Architecture Patterns

### Recommended Project Structure

```
domain/
└── model/
    ├── order/
    │   ├── Order.kt                    # domain model
    │   ├── OrderItem.kt                # domain model
    │   ├── OrderStatus.kt              # enum: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    │   └── ShippingAddress.kt          # value object (used by Order + checkout wizard)
└── repository/
    ├── PaymentRepository.kt            # interface: createPaymentIntent(), createOrder()
    └── AddressRepository.kt            # interface: getSavedAddresses(), saveAddress()

data/
└── local/
    ├── entity/
    │   ├── OrderEntity.kt
    │   └── OrderItemEntity.kt
    ├── dao/
    │   └── OrderDao.kt
    └── mapper/
        └── OrderMapper.kt
└── repository/
    ├── PaymentRepositoryImpl.kt        # calls Firebase function, creates Firestore doc, writes Room
    └── AddressRepositoryImpl.kt        # reads/writes addresses sub-collection on user doc

app/
└── customer/
    └── checkout/
        ├── CheckoutViewModel.kt        # manages wizard step, PaymentIntent creation, result handling
        ├── CheckoutState.kt
        ├── CheckoutAction.kt
        ├── CheckoutScreen.kt           # 3-step wizard host
        └── components/
            ├── AddressStepContent.kt
            ├── ReviewStepContent.kt
            ├── PaymentStepContent.kt
            ├── CheckoutProgressDots.kt
            └── AddressFormScreen.kt    # separate route for new address entry
    └── order_confirmation/
        ├── OrderConfirmationScreen.kt
        └── MinimalOrderScreen.kt       # placeholder for Phase 6 full order detail

functions/                              # Firebase Cloud Functions project (separate from Android)
└── src/
    └── index.ts                        # exports: createPaymentIntent
```

### Pattern 1: Stripe PaymentSheet in Compose (rememberPaymentSheet)

**What:** `rememberPaymentSheet` creates a PaymentSheet instance that survives recomposition. `presentWithPaymentIntent` launches the sheet. Results arrive via callback (not coroutine), so bridge to ViewModel via a Channel or event.

**When to use:** Any Compose screen that initiates a Stripe payment.

**Example:**

```kotlin
// Source: https://stripe.dev/stripe-android/paymentsheet/com.stripe.android.paymentsheet/index.html
// + verified via WebSearch (2026-02-21)

@Composable
fun PaymentStepContent(
    clientSecret: String,
    onResult: (PaymentSheetResult) -> Unit,
) {
    val paymentSheet = rememberPaymentSheet { result ->
        onResult(result)
    }

    Button(onClick = {
        paymentSheet.presentWithPaymentIntent(
            paymentIntentClientSecret = clientSecret,
            configuration = PaymentSheet.Configuration(
                merchantDisplayName = "WenuCommerce",
            )
        )
    }) {
        Text("Pay Now")
    }
}

// In CheckoutViewModel, handle result:
fun onPaymentResult(result: PaymentSheetResult) {
    when (result) {
        is PaymentSheetResult.Completed -> handlePaymentSuccess()
        is PaymentSheetResult.Canceled  -> { /* user dismissed, no-op */ }
        is PaymentSheetResult.Failed    -> {
            val message = result.error.message ?: "Payment failed"
            _state.update { it.copy(paymentError = message) }
        }
    }
}
```

### Pattern 2: PaymentConfiguration Initialization (Application class)

**What:** Must be called once at app startup before any PaymentSheet usage. Uses publishable key from `BuildConfig` (never hardcoded, never the secret key).

**When to use:** In `WenuCommerce.onCreate()`.

**Example:**

```kotlin
// Source: Stripe official docs + verified WebSearch (2026-02-21)

// In WenuCommerce.kt (Application)
PaymentConfiguration.init(
    applicationContext,
    BuildConfig.STRIPE_PUBLISHABLE_KEY  // pk_test_... from local.properties via BuildConfig
)
```

### Pattern 3: Firebase callable function (Android Kotlin)

**What:** `Firebase.functions.getHttpsCallable("createPaymentIntent")` returns a reference. Call `.call(data).await()` in a coroutine. Result is `HttpsCallableResult` — access `result.data` as a `Map<String, Any>`.

**When to use:** In `PaymentRepositoryImpl.createPaymentIntent()`.

**Example:**

```kotlin
// Source: https://firebase.google.com/docs/functions/callable?gen=2nd (verified 2026-02-21)

suspend fun createPaymentIntent(
    cartItems: List<CartItem>,
    shippingAddress: ShippingAddress,
    userId: String,
): Result<String> = withContext(dispatcherProvider.io()) {
    runCatching {
        val data = mapOf(
            "userId"       to userId,
            "cartItems"    to cartItems.map { mapOf("productId" to it.productId, "quantity" to it.quantity, "price" to it.snapshotPrice) },
            "shippingAddress" to mapOf(
                "fullName" to shippingAddress.fullName,
                "line1"    to shippingAddress.line1,
                // ...
            )
        )
        val result = Firebase.functions
            .getHttpsCallable("createPaymentIntent")
            .call(data)
            .await()
        @Suppress("UNCHECKED_CAST")
        val resultMap = result.data as Map<String, Any>
        resultMap["clientSecret"] as String
    }
}
```

### Pattern 4: Firebase Cloud Function (TypeScript v2)

**What:** `onCall` with `defineSecret` for Stripe secret key. Function receives `request.data`, validates, creates PaymentIntent, returns `{ clientSecret }`. Amount is computed in cents (Stripe uses smallest currency unit).

**When to use:** Backend function that the Android app calls via firebase-functions-ktx.

**Example:**

```typescript
// Source: https://codewithandrea.com/articles/api-keys-2ndgen-cloud-functions-firebase/ (verified 2026-02-21)

import { onCall, HttpsError } from "firebase-functions/v2/https"
import { defineSecret } from "firebase-functions/params"
import Stripe from "stripe"

const stripeSecretKey = defineSecret("STRIPE_SECRET_KEY")

export const createPaymentIntent = onCall(
  { secrets: [stripeSecretKey] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Must be authenticated")
    }

    const { cartItems, shippingAddress } = request.data as {
      cartItems: Array<{ productId: string; quantity: number; price: number }>
      shippingAddress: Record<string, string>
    }

    // Server-side stock validation goes here (Firestore reads)

    // Compute amount in cents
    const subtotal = cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0)
    // Shipping: fetch from Firestore product docs and sum
    const shippingTotal = 0 // compute from Firestore reads
    const totalCents = Math.round((subtotal + shippingTotal) * 100)

    const stripe = new Stripe(stripeSecretKey.value(), {
      apiVersion: "2025-01-27.acacia",
    })

    const paymentIntent = await stripe.paymentIntents.create({
      amount: totalCents,
      currency: "usd",
      automatic_payment_methods: { enabled: true },
      metadata: {
        userId: request.auth.uid,
      },
    })

    return { clientSecret: paymentIntent.client_secret }
  }
)
```

### Pattern 5: Room Migration v3 → v4 (new tables)

**What:** Add `orders` and `order_items` tables. Project already has `exportSchema = true`, so auto-migration may apply. However, given the project pattern uses explicit `Migration` objects (see `WenuCommerceDatabase`), continue the existing manual migration pattern for consistency.

**When to use:** When adding `OrderEntity` and `OrderItemEntity` to the `@Database` annotation.

**Example:**

```kotlin
// Manual migration — consistent with MIGRATION_2_3 pattern already in project

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `orders` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `userId` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'PENDING',
                `subtotal` REAL NOT NULL DEFAULT 0.0,
                `shippingTotal` REAL NOT NULL DEFAULT 0.0,
                `totalAmount` REAL NOT NULL DEFAULT 0.0,
                `currency` TEXT NOT NULL DEFAULT 'USD',
                `stripePaymentIntentId` TEXT NOT NULL DEFAULT '',
                `shippingAddressJson` TEXT NOT NULL DEFAULT '',
                `createdAt` TEXT NOT NULL DEFAULT '',
                `updatedAt` TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `order_items` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `orderId` TEXT NOT NULL,
                `productId` TEXT NOT NULL,
                `productTitle` TEXT NOT NULL DEFAULT '',
                `quantity` INTEGER NOT NULL DEFAULT 1,
                `snapshotPrice` REAL NOT NULL DEFAULT 0.0,
                `lineTotal` REAL NOT NULL DEFAULT 0.0
            )
        """.trimIndent())
    }
}
```

Register in `WenuCommerceDatabase`:

```kotlin
@Database(
    entities = [
        // existing...
        OrderEntity::class,
        OrderItemEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
```

And add `MIGRATION_3_4` to `databaseModule` in `DataModule.kt`.

### Pattern 6: Order domain model and Firestore structure

**What:** `Order` is the domain model. Firestore path: `orders/{orderId}`. OrderItems stored as a sub-collection `orders/{orderId}/items/{itemId}` OR as an embedded list in the order document. Embedded list is simpler for Phase 4 read-on-confirmation use case.

**Recommended Firestore shape:**

```
orders/{orderId}
  userId: string
  status: "PENDING"
  subtotal: number
  shippingTotal: number
  totalAmount: number
  currency: "USD"
  stripePaymentIntentId: string
  shippingAddress: { fullName, line1, line2, city, state, postalCode, country }
  items: [ { productId, productTitle, quantity, snapshotPrice, lineTotal } ]
  createdAt: Timestamp
  updatedAt: Timestamp
```

This flat-embedded structure avoids a sub-collection read on the confirmation screen and is simpler for Phase 4. Phase 6 can add a sub-collection if order item counts grow.

### Anti-Patterns to Avoid

- **Stripe secret key in Android app:** Never. It must ONLY exist in Cloud Functions environment via `defineSecret`. The Android app only receives `clientSecret` (which is a one-time, limited-scope token, safe to transmit).
- **Optimistic Room write for payment state:** Per STATE.md: "Payments = Firestore is source of truth / no optimistic Room writes". Write the Order to Room only after the Cloud Function succeeds and returns the clientSecret (i.e., after PaymentSheetResult.Completed, confirm via Firestore document existence, then persist to Room).
- **Custom card form:** Against CHKT-03 and PCI DSS. Use PaymentSheet exclusively.
- **clearCart() in PendingOperation queue for post-checkout:** Per STATE.md decision `[03-01]`: `clearCart()` does not queue a PendingOperation. Call it directly after order creation. Firestore cart will be cleared as part of the Cloud Function response or a separate Firestore write in `PaymentRepositoryImpl`.
- **Passing address as navigation arguments between wizard steps:** Wizard steps share a single `CheckoutViewModel` scoped to the checkout nav graph. Pass no data through navigation args between steps — state lives in ViewModel. Only `orderId` is passed to `OrderConfirmationScreen` as a nav arg.
- **Calling PaymentSheet from a non-Activity context:** `rememberPaymentSheet` must be called inside a Composable that is backed by a `ComponentActivity`. Since this project uses a single `MainActivity`, this is already satisfied.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Card input form | Custom TextFields for card number, CVV, expiry | Stripe PaymentSheet | PCI DSS scope, tokenization, 3DS, international formats, all handled by Stripe |
| Payment confirmation polling | Manual Firestore polling loop for PaymentIntent status | Trust `PaymentSheetResult.Completed` | PaymentSheet SDK confirms the intent before calling back; polling adds latency and complexity |
| Success checkmark animation | Frame-by-frame Canvas draw or Animatable chain | Lottie or simple animated Icon with scale+alpha | Pre-built LottieFiles checkmarks are free; Animatable scale+fade is acceptable for simple check icon per Claude's discretion |
| Secret key management | Storing Stripe secret in `local.properties` on Android | `defineSecret` in Cloud Functions v2 | Secret key must never leave the server; `defineSecret` integrates with Firebase Secret Manager |
| Address validation | Regex-only postal code checks | Simple non-empty checks + country-aware rules as needed | Phase 4 scope is basic form validation; full address verification (Google Places API) is overkill |

**Key insight:** Stripe's PaymentSheet handles the entire PCI DSS compliance burden. Any custom implementation would require quarterly security audits, network tokenization, and extensive testing across device configurations.

---

## Common Pitfalls

### Pitfall 1: PaymentSheet Not Initialized Before Presentation

**What goes wrong:** `PaymentConfiguration` is not initialized before `rememberPaymentSheet`/`presentWithPaymentIntent` is called. App crashes with an `IllegalStateException`.

**Why it happens:** Stripe SDK requires global initialization via `PaymentConfiguration.init()`. Developers forget this step or move it too late in lifecycle.

**How to avoid:** Add `PaymentConfiguration.init(applicationContext, BuildConfig.STRIPE_PUBLISHABLE_KEY)` in `WenuCommerce.onCreate()` alongside Koin initialization.

**Warning signs:** `IllegalStateException: PaymentConfiguration was not initialized` in logcat.

### Pitfall 2: Amount Mismatch (Client vs Server)

**What goes wrong:** Client displays one total but the server computes a different amount (e.g., due to shipping cost rounding, currency unit errors). Stripe processes the server amount, causing a confusing UX.

**Why it happens:** Amount is computed independently on client (for display) and server (for PaymentIntent). Divergence from floating-point rounding or forgotten shipping lines.

**How to avoid:** The Cloud Function is the single source of truth for amount. The Android client should display the breakdown (subtotal + shipping) computed locally from Room data, but the Payment step should also display the amount received from the Cloud Function response (or at minimum confirm it matches). Consider returning `{ clientSecret, amountCents, currency }` from the function.

**Warning signs:** Customer charged different amount than shown at checkout.

### Pitfall 3: PaymentSheetResult.Completed Does Not Mean Money Moved

**What goes wrong:** Developer calls `clearCart()` and creates the Order document on `Completed` but relies on this as final payment confirmation for fulfillment.

**Why it happens:** Stripe docs explicitly warn: "The payment may still be processing at this point; don't assume money has successfully moved."

**How to avoid:** For UX (cart clear + confirmation screen), trust `Completed`. For actual fulfillment (shipping, seller notification), use a Stripe webhook pointing at a Firebase Cloud Function that listens for `payment_intent.succeeded`. Phase 4 scope is UX flow only — webhook is a Phase 8 (Notifications) concern.

**Warning signs:** Orders marked "paid" before Stripe actually collects funds.

### Pitfall 4: `rememberPaymentSheet` Callback Leaks ViewModel State

**What goes wrong:** The `rememberPaymentSheet` callback lambda captures a stale reference to ViewModel state, causing incorrect behavior after recomposition.

**Why it happens:** The callback is passed at composition time. If it captures mutable state directly, it won't reflect later updates.

**How to avoid:** Call `viewModel.onPaymentResult(result)` from the callback — never read ViewModel state inside the lambda. The ViewModel's `onPaymentResult` method reads current state from `_state.value`.

### Pitfall 5: Firebase Callable Function Region

**What goes wrong:** `Firebase.functions` defaults to `us-central1`. If the Cloud Function is deployed to a different region, calls silently fail or go to the wrong endpoint.

**Why it happens:** Developers deploy to `europe-west1` for GDPR but forget to specify the region on the client.

**How to avoid:** Use `Firebase.functions("us-central1")` explicitly (or match the region where you deploy). Consistent region in both deploy config and client call.

### Pitfall 6: Checkout ViewModel Scoping

**What goes wrong:** `CheckoutViewModel` is scoped to the wrong nav graph owner. If scoped to the Activity, it persists after checkout completes and retains stale payment state. If scoped per-destination, each step gets its own instance and shared state is lost.

**Why it happens:** Koin `koinViewModel()` in Compose scopes to the nearest `ViewModelStoreOwner`. Navigation destinations have their own owners.

**How to avoid:** Scope `CheckoutViewModel` to the checkout nested nav graph (if implemented) or scope it to the Activity with explicit clearing on checkout completion. Simplest approach for Phase 4: use `koinViewModel()` with a shared nav-scoped approach. See Koin docs on `koinNavViewModel()` for navigation-scoped instances.

---

## Code Examples

Verified patterns from official sources and research:

### Cloud Function: createPaymentIntent (TypeScript v2)

```typescript
// Source: https://codewithandrea.com/articles/api-keys-2ndgen-cloud-functions-firebase/ (verified 2026-02-21)
// Pattern: defineSecret for STRIPE_SECRET_KEY, onCall with auth check

import { onCall, HttpsError } from "firebase-functions/v2/https"
import { defineSecret } from "firebase-functions/params"
import Stripe from "stripe"
import * as admin from "firebase-admin"

const stripeSecretKey = defineSecret("STRIPE_SECRET_KEY")

export const createPaymentIntent = onCall(
  { secrets: [stripeSecretKey] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Must be signed in to checkout")
    }

    const { cartItems, shippingAddress, userId } = request.data

    // Server-side stock check
    for (const item of cartItems) {
      const productDoc = await admin.firestore()
        .collection("products").doc(item.productId).get()
      const stock = productDoc.data()?.totalStockQuantity ?? 0
      if (stock < item.quantity) {
        throw new HttpsError("failed-precondition", `${item.productTitle} is out of stock`)
      }
    }

    // Compute total in cents
    const subtotalCents = cartItems.reduce(
      (sum: number, item: { price: number; quantity: number }) =>
        sum + Math.round(item.price * 100) * item.quantity,
      0
    )
    // Fetch shipping costs from Firestore and sum
    let shippingCents = 0
    for (const item of cartItems) {
      const productDoc = await admin.firestore()
        .collection("products").doc(item.productId).get()
      const shippingCost = productDoc.data()?.shipping?.shippingCost ?? 0
      shippingCents += Math.round(shippingCost * 100) * item.quantity
    }
    const totalCents = subtotalCents + shippingCents

    const stripe = new Stripe(stripeSecretKey.value(), {
      apiVersion: "2025-01-27.acacia",
    })

    const paymentIntent = await stripe.paymentIntents.create({
      amount: totalCents,
      currency: "usd",
      automatic_payment_methods: { enabled: true },
      metadata: { userId: request.auth.uid },
    })

    return {
      clientSecret: paymentIntent.client_secret,
      amountCents: totalCents,
    }
  }
)
```

### Android: PaymentConfiguration init (WenuCommerce.kt)

```kotlin
// Source: Stripe official docs, verified WebSearch 2026-02-21
// Add to WenuCommerce.onCreate() after FirebaseApp.initializeApp()

import com.stripe.android.PaymentConfiguration

class WenuCommerce : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        PaymentConfiguration.init(applicationContext, BuildConfig.STRIPE_PUBLISHABLE_KEY)
        startKoin { /* ... existing koin setup ... */ }
        // ...
    }
}
```

### Android: PaymentSheet in Compose

```kotlin
// Source: Stripe docs + verified WebSearch 2026-02-21

@Composable
fun PaymentStepContent(
    total: Double,
    clientSecret: String,
    paymentError: String?,
    isLoading: Boolean,
    onPaymentResult: (PaymentSheetResult) -> Unit,
) {
    val paymentSheet = rememberPaymentSheet { result ->
        onPaymentResult(result)
    }

    Column(/* ... */) {
        Text("Total: ${"$%.2f".format(total)}", style = MaterialTheme.typography.titleLarge)

        if (paymentError != null) {
            Text(
                text = paymentError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = {
                paymentSheet.presentWithPaymentIntent(
                    paymentIntentClientSecret = clientSecret,
                    configuration = PaymentSheet.Configuration(
                        merchantDisplayName = "WenuCommerce",
                    )
                )
            },
            enabled = !isLoading && clientSecret.isNotEmpty(),
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp))
            else Text("Pay Now")
        }
    }
}
```

### Android: PaymentSheetResult handling in ViewModel

```kotlin
// Source: Stripe PaymentSheetResult sealed class docs, verified 2026-02-21

fun onPaymentResult(result: PaymentSheetResult) {
    when (result) {
        is PaymentSheetResult.Completed -> {
            // IMPORTANT: payment may still be processing — treat as UX signal only
            viewModelScope.launch(ioDispatcher) {
                createOrderAndClearCart()
            }
        }
        is PaymentSheetResult.Canceled -> {
            // User dismissed — no-op, cart preserved, stay on step 3
        }
        is PaymentSheetResult.Failed -> {
            val message = result.error.message ?: "Payment failed. Please try again."
            _state.update { it.copy(paymentError = message, isProcessingPayment = false) }
        }
    }
}
```

### Android: Firebase Functions callable

```kotlin
// Source: https://firebase.google.com/docs/functions/callable?gen=2nd (verified 2026-02-21)

private suspend fun callCreatePaymentIntent(
    userId: String,
    cartItems: List<CartItem>,
    shippingAddress: ShippingAddress,
): Result<Pair<String, Int>> = withContext(dispatcherProvider.io()) {
    runCatching {
        val data = mapOf(
            "userId" to userId,
            "cartItems" to cartItems.map {
                mapOf(
                    "productId" to it.productId,
                    "productTitle" to it.productTitle,
                    "quantity" to it.quantity,
                    "price" to it.snapshotPrice,
                )
            },
            "shippingAddress" to mapOf(
                "fullName" to shippingAddress.fullName,
                "line1" to shippingAddress.line1,
                "line2" to (shippingAddress.line2 ?: ""),
                "city" to shippingAddress.city,
                "state" to shippingAddress.state,
                "postalCode" to shippingAddress.postalCode,
                "country" to shippingAddress.country,
            )
        )
        val result = Firebase.functions
            .getHttpsCallable("createPaymentIntent")
            .call(data)
            .await()

        @Suppress("UNCHECKED_CAST")
        val resultMap = result.data as Map<String, Any>
        val clientSecret = resultMap["clientSecret"] as String
        val amountCents = (resultMap["amountCents"] as Number).toInt()
        Pair(clientSecret, amountCents)
    }
}
```

### Lottie Success Checkmark

```kotlin
// Source: https://github.com/airbnb/lottie/blob/master/android-compose.md (verified 2026-02-21)
// Download a checkmark Lottie JSON from lottiefiles.com and place in res/raw/success_checkmark.json

@Composable
fun SuccessCheckmark(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.success_checkmark)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,  // plays once
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
    )
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Stripe Android `Card` / `CardInputWidget` (custom form) | PaymentSheet prebuilt UI (`rememberPaymentSheet`) | 2021+ | Eliminates PCI scope; PaymentSheet now Stripe's "recommended integration path" |
| Firebase Functions v1 `functions.config()` for secrets | Firebase Functions v2 `defineSecret` from `firebase-functions/params` | 2022+ | Secrets stored in Google Secret Manager; never in environment variables or config files |
| Activity-based PaymentSheet constructor | Compose `rememberPaymentSheet` | 2022+ | Proper Compose lifecycle management; no Activity reference needed |
| Stripe API version `2022-11-15` | `2025-01-27.acacia` (current) | Jan 2025 | Latest stable API; Stripe recommends pinning to specific version |

**Deprecated/outdated:**
- `stripe-android` `PaymentIntent.confirm()` directly in Android: deprecated, replaced by PaymentSheet which handles 3DS, SCA, redirect flows automatically.
- Firebase Functions v1 `onCall` without secrets: replaced by v2 `onCall` with `defineSecret` for production use.

---

## Open Questions

1. **Stripe publishable key storage for production**
   - What we know: publishable key goes in `local.properties` → `BuildConfig` (same as `GOOGLE_ID_WEB_CLIENT`)
   - What's unclear: Whether the project has a CI/CD pipeline that would inject it differently for release builds
   - Recommendation: Follow existing `local.properties` pattern for now; document the key name clearly

2. **Cloud Function server-side shipping cost calculation**
   - What we know: Per-product shipping cost is in `ProductShipping.shippingCost` in Firestore. Cloud Function must read each product doc to sum shipping.
   - What's unclear: Whether shipping is per-item or per-product (i.e., does buying 3 units of X multiply the shipping cost by 3?)
   - Recommendation: Per-product shipping (not per-unit) is the simpler and more common model — charge `shippingCost` once per product line regardless of quantity. Planner should decide definitively.

3. **Address persistence model in Room**
   - What we know: User.address is currently a String in domain model and UserEntity. Addresses need to be a List to support multiple saved addresses.
   - What's unclear: Whether to add a separate `addresses` table or store as a JSON list on the user entity (consistent with `purchaseHistory` JSON column pattern).
   - Recommendation: JSON list on UserEntity is the fastest path (follows existing `purchaseHistoryJson` pattern). A separate table is cleaner for querying. Given Phase 10 (Profile Management) adds PROF-04 (manage shipping addresses), a separate `addresses` table is more future-proof. However, for Phase 4 scope, JSON list on the user is sufficient.

4. **Order Firestore write: pre-payment vs post-payment**
   - What we know: STATE.md: "Payments = Firestore is source of truth / no optimistic Room writes"
   - What's unclear: Should `createPaymentIntent` also write a pending Order document server-side, or should the Android client write the Order to Firestore after `PaymentSheetResult.Completed`?
   - Recommendation: Have the Cloud Function create a pending Order document in Firestore at PaymentIntent creation time (with status PENDING). On `Completed`, the Android client updates status to CONFIRMED and writes to Room. This gives a fallback if the user kills the app between PaymentSheet dismissal and Room write.

---

## Sources

### Primary (HIGH confidence)
- Stripe Android SDK GitHub (stripe-android) — latest version 22.8.1 confirmed from CHANGELOG
- `https://stripe.dev/stripe-android/paymentsheet/com.stripe.android.paymentsheet/-payment-sheet-result/index.html` — PaymentSheetResult sealed class (Completed, Canceled, Failed)
- `https://stripe.dev/stripe-android/paymentsheet/com.stripe.android.paymentsheet/index.html` — rememberPaymentSheet API, presentWithPaymentIntent signature
- `https://firebase.google.com/docs/functions/callable?gen=2nd` — Firebase callable functions Android Kotlin API
- `https://codewithandrea.com/articles/api-keys-2ndgen-cloud-functions-firebase/` — defineSecret pattern for Cloud Functions v2, verified 2026-02-21
- `https://github.com/airbnb/lottie/blob/master/android-compose.md` — Lottie Compose API (rememberLottieComposition, animateLottieCompositionAsState)
- Project codebase: WenuCommerceDatabase.kt, CartRepositoryImpl.kt, DataModule.kt, STATE.md — architectural patterns

### Secondary (MEDIUM confidence)
- WebSearch result for Stripe Android 22.8.1 (confirmed via GitHub CHANGELOG fetch)
- WebSearch: `com.stripe:stripe-android` includes `paymentsheet` as transitive dep — verified via GitHub issue thread
- WebSearch: lottie-compose 6.7.1 latest version (from lottie-android GitHub releases, verified 2026-02-21)
- WebSearch: `PaymentSheetResult.Failed.error.message` access pattern — confirmed by multiple search results citing same pattern

### Tertiary (LOW confidence)
- Shipping cost per-unit vs per-product interpretation — not verified against explicit product requirements, marked as open question
- Cloud Function region for WenuCommerce deployment — assumed us-central1 (default); not confirmed in project config

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Stripe 22.8.1 version confirmed from GitHub CHANGELOG; Firebase callable API confirmed from official docs
- Architecture: HIGH — patterns follow directly from existing project conventions (CartRepositoryImpl, DataModule, WenuCommerceDatabase)
- Pitfalls: HIGH — Stripe-specific pitfalls confirmed from official SDK docs and GitHub issues; migration patterns from existing code
- Lottie version: MEDIUM — 6.7.1 from GitHub releases page (October 2025); verify at Maven Central before adding

**Research date:** 2026-02-21
**Valid until:** 2026-03-21 (Stripe SDK updates frequently; verify version before implementing)
