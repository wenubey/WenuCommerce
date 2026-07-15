# Phase 7: Reviews & Ratings — Pattern Map

**Mapped:** 2026-07-16
**Files analyzed:** 22 new/modified files
**Analogs found:** 21 / 22

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `functions/src/index.ts` (add `submitReview`) | service | request-response | `functions/src/index.ts` `cancelSellerOrder` (lines 838–933) | exact |
| `functions/src/index.ts` (add `markReviewHelpful`) | service | request-response | `functions/src/index.ts` `cancelSellerOrder` (lines 838–933) | exact |
| `functions/src/index.ts` (pure helpers extracted) | utility | transform | `functions/src/index.ts` `buildFanoutDocs` / `decideWebhookAction` (lines 314–440) | exact |
| `functions/test/submitReview.test.ts` | test | request-response | `functions/test/cancelSellerOrder.test.ts` | exact |
| `functions/test/markReviewHelpful.test.ts` | test | request-response | `functions/test/cancelSellerOrder.test.ts` | exact |
| `firestore.rules` (add REVIEWS + helpfulVotes blocks) | config | request-response | `firestore.rules` `/sellerOrders` + `/orders` blocks (lines 42–65) | exact |
| `functions/test/rules.test.ts` (extend for REVIEWS) | test | request-response | `functions/test/rules.test.ts` existing structure (lines 1–199) | exact |
| `firestore.indexes.json` | config | CRUD | none — new file | no analog |
| `data/.../entity/ReviewEntity.kt` | model | CRUD | `data/.../entity/SellerOrderEntity.kt` | exact |
| `data/.../dao/ReviewDao.kt` | service | CRUD | `data/.../dao/SellerOrderDao.kt` | exact |
| `data/.../mapper/ReviewMapper.kt` | utility | transform | `data/.../mapper/SellerOrderMapper.kt` | exact |
| `data/.../local/WenuCommerceDatabase.kt` (MIGRATION_8_9 + entity + DAO) | config | CRUD | `WenuCommerceDatabase.kt` `MIGRATION_6_7` (ADD column) + `MIGRATION_5_6` (CREATE table) | exact |
| `domain/.../repository/ProductReviewRepository.kt` (add `getMyReviewForProduct`) | service | CRUD | `domain/.../repository/ProductReviewRepository.kt` existing interface | role-match |
| `data/.../repository/ProductReviewRepositoryImpl.kt` (re-route `submitReview` + `markReviewHelpful` to callable) | service | request-response | `data/.../repository/OrderRepositoryImpl.kt` `cancelSellerOrder` (lines 154–176) | exact |
| `data/.../util/Constants.kt` (add `HELPFUL_VOTES_SUBCOLLECTION`) | config | — | `data/.../util/Constants.kt` existing constants | exact |
| `app/.../di/DataModule.kt` (add `reviewDao` + `MIGRATION_8_9`) | config | — | `DataModule.kt` `databaseModule` (lines 127–158) | exact |
| `app/.../customer/customer_products/CustomerProductDetailState.kt` (add review form fields) | model | — | `CustomerProductDetailState.kt` existing fields | exact |
| `app/.../customer/customer_products/CustomerProductDetailAction.kt` (add review actions) | model | — | `CustomerProductDetailAction.kt` existing sealed interface | exact |
| `app/.../customer/customer_products/CustomerProductDetailViewModel.kt` (add sort/submit/delivered-gate) | controller | request-response | `CustomerProductDetailViewModel.kt` existing ViewModel | exact |
| `app/.../customer/customer_products/CustomerProductDetailScreen.kt` (add sort toggle, badge, write-review affordance + sheet) | component | request-response | `CustomerProductDetailScreen.kt` existing `ReviewCard` + `CartActionSection` | exact |
| `app/.../customer/customer_reviews/WriteReviewScreen.kt` | component | request-response | `app/.../customer/customer_products/CustomerProductDetailScreen.kt` `CartActionSection` + existing full-screen composables (e.g. `SellerProductCreateScreen`) | role-match |
| `app/.../core/components/VerifiedPurchaseBadge.kt` | component | — | `app/.../core/components/OrderStatusBadge.kt` `BadgeChip` (lines 39–53) | exact |
| `data/.../worker/SyncWorker.kt` (wire `SUBMIT_REVIEW` case) | service | event-driven | `SyncWorker.kt` `ADD_TO_CART` branch (lines 106–113) | exact |
| `app/.../navigation/AppNavigationObjects.kt` (add `WriteReview` route) | config | — | `AppNavigationObjects.kt` `CustomerProductDetail` / `SellerOrderDetail` pattern (lines 57, 109) | exact |

---

## Pattern Assignments

### `functions/src/index.ts` — `submitReview` callable (NEW)

**Analog:** `functions/src/index.ts` — `cancelSellerOrder` (lines 838–933)

**Import / setup pattern** (lines 1–12):
```typescript
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
// admin.initializeApp() already called once at top of file — do NOT repeat
```

**Auth guard + input validation pattern** (lines 838–848, mirror exactly):
```typescript
export const submitReview = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required");
  }
  const { productId, rating, title, body, reviewerName, reviewerPhotoUrl }
    = request.data as SubmitReviewRequest;
  if (!productId || typeof productId !== "string") {
    throw new HttpsError("invalid-argument", "productId required");
  }
  if (typeof rating !== "number" || rating < 1 || rating > 5) {
    throw new HttpsError("invalid-argument", "rating must be 1–5");
  }
  // ...
});
```

**Ownership check pattern** (lines 850–870, `cancelSellerOrder` ownership check):
```typescript
// cancelSellerOrder checks sub.sellerId !== request.auth.uid
// submitReview: query sellerOrders WHERE userId==uid AND status==DELIVERED
const uid = request.auth.uid;
const sellerOrdersSnap = await db
  .collection("sellerOrders")
  .where("userId", "==", uid)
  .where("status", "==", "DELIVERED")
  .get();
const qualifying = sellerOrdersSnap.docs.filter(doc => {
  const items: {productId: string}[] = doc.data().items ?? [];
  return items.some(it => it.productId === productId);
});
if (qualifying.length === 0) {
  throw new HttpsError("failed-precondition", "No delivered order found for this product");
}
```

**Pure helper extraction pattern** (lines 314–381, `buildFanoutDocs` / `decideWebhookAction`):
```typescript
// Extract pure functions for testability — no Firestore side effects:
export function buildReviewData(
  uid: string, reviewId: string, productId: string,
  data: SubmitReviewRequest, purchaseId: string,
  existingDoc?: admin.firestore.DocumentData, isEdit?: boolean,
): ReviewData { ... }

export function computeNewAggregate(
  currentAvg: number, currentCount: number,
  newRating: number, oldRating?: number, isEdit?: boolean,
): { newAvg: number; newCount: number } { ... }
```

**Firestore transaction pattern** (lines 1002–1017, `onOrderStatusChange` transaction):
```typescript
await db.runTransaction(async (tx) => {
  const productSnap = await tx.get(productRef);
  // ... compute newAvg / newCount ...
  tx.set(reviewRef, reviewData);
  tx.update(productRef, {
    reviewCount: newCount,
    averageRating: newAvg,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
});
return { reviewId, isVerifiedPurchase: true };
```

**Field names to use** (from `ProductReviewRepositoryImpl.kt` lines 116–130 — verified codebase):
- Product doc: `"averageRating"`, `"reviewCount"`, `"updatedAt"`
- Review doc fields match `ProductReview.toMap()` shape

---

### `functions/src/index.ts` — `markReviewHelpful` callable (NEW)

**Analog:** `functions/src/index.ts` — `cancelSellerOrder` (lines 838–933)

**Core pattern** (transaction-based idempotency check — mirrors cancelSellerOrder's idempotency key):
```typescript
export const markReviewHelpful = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required");
  }
  const { productId, reviewId } = request.data as { productId: string; reviewId: string };
  const uid = request.auth.uid;
  const db = admin.firestore();
  const reviewRef = db.collection("PRODUCTS").doc(productId)
    .collection("REVIEWS").doc(reviewId);
  const voteRef = reviewRef.collection("helpfulVotes").doc(uid);

  await db.runTransaction(async (tx) => {
    const voteSnap = await tx.get(voteRef);
    if (voteSnap.exists) {
      throw new HttpsError("already-exists", "Already marked helpful");
    }
    tx.set(voteRef, { votedAt: admin.firestore.FieldValue.serverTimestamp() });
    tx.update(reviewRef, {
      helpfulCount: admin.firestore.FieldValue.increment(1),
    });
  });
  return { success: true };
});
```

---

### `functions/test/submitReview.test.ts` (NEW)

**Analog:** `functions/test/cancelSellerOrder.test.ts` (lines 1–62)

**Structure pattern** (entire file):
```typescript
import * as fs from "fs";
import * as path from "path";

const indexSrc = fs.readFileSync(
  path.resolve(__dirname, "../src/index.ts"),
  "utf-8",
);

describe("submitReview — structural contract", () => {
  it("rejects unauthenticated caller", () => {
    expect(indexSrc).toContain('unauthenticated"');
    expect(indexSrc).toContain("Sign in required");
  });

  it("rejects caller with no DELIVERED order (failed-precondition)", () => {
    expect(indexSrc).toContain("failed-precondition");
    expect(indexSrc).toContain("No delivered order found for this product");
    expect(indexSrc).toMatch(/status.*DELIVERED/);
  });

  it("sets isVerifiedPurchase: true on review doc", () => {
    expect(indexSrc).toMatch(/isVerifiedPurchase:\s*true/);
  });

  it("runs transaction writing review + updating product aggregate", () => {
    expect(indexSrc).toMatch(/db\.runTransaction/);
    expect(indexSrc).toMatch(/reviewCount/);
    expect(indexSrc).toMatch(/averageRating/);
  });

  it("edit path: count unchanged, average delta-corrected", () => {
    expect(indexSrc).toMatch(/isEdit/);
    expect(indexSrc).toMatch(/newCount = currentCount/);
  });
});
```

---

### `firestore.rules` — REVIEWS + helpfulVotes blocks (MODIFY)

**Analog:** `firestore.rules` `/sellerOrders` block (lines 42–56) and `/orders` block (lines 61–65)

**Server-only create/update/delete pattern** (lines 61–65):
```javascript
// /orders: client create/update/delete all false — same pattern for REVIEWS
match /orders/{orderId} {
  allow read: if request.auth != null && resource.data.userId == request.auth.uid;
  allow create, update, delete: if false;
}
```

**New rule block to add** inside `match /PRODUCTS/{productId}` (replacing the current wildcard `/{document=**}` block):
```javascript
match /PRODUCTS/{productId} {
  allow read, write: if request.auth != null;  // keep existing for product doc

  // REVIEWS subcollection: reads open, ALL writes server-only (Admin SDK)
  match /REVIEWS/{reviewId} {
    allow read: if request.auth != null;
    allow create, update, delete: if false;  // submitReview / setReviewVisibility callable

    // helpfulVotes subcollection: ALL writes via markReviewHelpful callable
    match /helpfulVotes/{voterId} {
      allow read: if request.auth != null;
      allow create, delete: if false;
    }
  }
}
```

---

### `functions/test/rules.test.ts` — REVIEWS section (EXTEND)

**Analog:** `functions/test/rules.test.ts` lines 81–199

**Seed + assert pattern** (lines 81–110 — copy this shape):
```typescript
// Seed via withSecurityRulesDisabled; test via authenticatedContext
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fstore = ctx.firestore();
    await fstore.doc("PRODUCTS/p-1/REVIEWS/rev-1").set(seedReview);
  });
});

describe("firestore.rules /PRODUCTS/{id}/REVIEWS", () => {
  it("(h) authenticated user can read a review", async () => {
    const ctx = env.authenticatedContext("any-user");
    await assertSucceeds(ctx.firestore().doc("PRODUCTS/p-1/REVIEWS/rev-1").get());
  });

  it("(i) client cannot create a review directly", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertFails(ctx.firestore().doc("PRODUCTS/p-1/REVIEWS/rev-99").set(seedReview));
  });

  it("(j) client cannot update a review directly", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertFails(ctx.firestore().doc("PRODUCTS/p-1/REVIEWS/rev-1").update({ rating: 1 }));
  });

  it("(k) client cannot write a helpfulVote directly", async () => {
    const ctx = env.authenticatedContext("customer-1");
    await assertFails(
      ctx.firestore().doc("PRODUCTS/p-1/REVIEWS/rev-1/helpfulVotes/customer-1").set({})
    );
  });
});
```

---

### `data/src/main/java/com/wenubey/data/local/entity/ReviewEntity.kt` (NEW)

**Analog:** `data/.../entity/SellerOrderEntity.kt` (lines 1–43)

**Entity declaration pattern** (lines 15–43):
```kotlin
@Entity(
    tableName = "seller_orders",
    indices = [
        Index("parentOrderId"),
        Index("sellerId")
    ]
)
data class SellerOrderEntity(
    @PrimaryKey val id: String,
    val parentOrderId: String = "",
    // ...scalar columns only, no @Relation...
    val itemsJson: String = "[]",  // NOTE: ReviewEntity has no JSON columns
    val createdAt: String = "",
    val updatedAt: String = ""
)
```

**Apply for ReviewEntity:**
```kotlin
package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reviews",
    indices = [Index("productId"), Index("reviewerId")]
)
data class ReviewEntity(
    @PrimaryKey val id: String,
    val productId: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val reviewerPhotoUrl: String = "",
    val purchaseId: String = "",
    val rating: Int = 0,
    val title: String = "",
    val body: String = "",
    val isVerifiedPurchase: Boolean = true,
    val helpfulCount: Int = 0,
    val isVisible: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
)
```

---

### `data/src/main/java/com/wenubey/data/local/dao/ReviewDao.kt` (NEW)

**Analog:** `data/.../dao/SellerOrderDao.kt` (lines 1–42)

**DAO pattern** (lines 12–42):
```kotlin
@Dao
interface SellerOrderDao {
    @Insert(onConflict = REPLACE)
    suspend fun upsert(entity: SellerOrderEntity)

    @Insert(onConflict = REPLACE)
    suspend fun upsertAll(entities: List<SellerOrderEntity>)

    @Query("SELECT * FROM seller_orders WHERE id = :id")
    fun observeById(id: String): Flow<SellerOrderEntity?>

    @Query("SELECT * FROM seller_orders WHERE sellerId = :sellerId ORDER BY createdAt DESC")
    fun observeBySeller(sellerId: String): Flow<List<SellerOrderEntity>>

    @Query("UPDATE seller_orders SET status = :status ... WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, ...)
}
```

**Apply for ReviewDao** (note: use `@Upsert` annotation, available in Room 2.5+, matching the design in RESEARCH.md):
```kotlin
@Dao
interface ReviewDao {
    @Upsert
    suspend fun upsert(review: ReviewEntity)

    @Upsert
    suspend fun upsertAll(reviews: List<ReviewEntity>)

    @Query("SELECT * FROM reviews WHERE productId = :productId AND isVisible = 1")
    fun observeByProduct(productId: String): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE reviewerId = :reviewerId AND productId = :productId LIMIT 1")
    suspend fun getByReviewerAndProduct(reviewerId: String, productId: String): ReviewEntity?

    @Query("DELETE FROM reviews WHERE productId = :productId")
    suspend fun deleteByProduct(productId: String)
}
```

---

### `data/src/main/java/com/wenubey/data/local/mapper/ReviewMapper.kt` (NEW)

**Analog:** `data/.../mapper/SellerOrderMapper.kt` (lines 1–63)

**Mapper pattern** (lines 1–63 — copy entire structure):
```kotlin
private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun SellerOrderEntity.toDomain(): SellerOrder = SellerOrder(
    id = id,
    // ... direct field mapping, no JSON columns in ReviewEntity ...
    status = runCatching { OrderStatus.valueOf(status) }.getOrElse { OrderStatus.PENDING },
)

fun SellerOrder.toEntity(): SellerOrderEntity = SellerOrderEntity(
    id = id,
    // ...
)
```

**Apply for ReviewMapper** (no JSON columns; direct field mapping):
```kotlin
fun ReviewEntity.toDomain(): ProductReview = ProductReview(
    id = id,
    productId = productId,
    reviewerId = reviewerId,
    reviewerName = reviewerName,
    reviewerPhotoUrl = reviewerPhotoUrl,
    purchaseId = purchaseId,
    rating = rating,
    title = title,
    body = body,
    isVerifiedPurchase = isVerifiedPurchase,
    helpfulCount = helpfulCount,
    isVisible = isVisible,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProductReview.toEntity(): ReviewEntity = ReviewEntity(
    id = id,
    productId = productId,
    reviewerId = reviewerId,
    // ...
)
```

---

### `data/src/main/java/com/wenubey/data/local/WenuCommerceDatabase.kt` — MIGRATION_8_9 (MODIFY)

**Analog — ADD column migration:** `WenuCommerceDatabase.kt` `MIGRATION_6_7` (lines 229–235):
```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `seller_orders` ADD COLUMN `userId` TEXT NOT NULL DEFAULT ''"
        )
    }
}
```

**Analog — CREATE TABLE migration:** `WenuCommerceDatabase.kt` `MIGRATION_5_6` (lines 183–222):
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `seller_orders` (
                `id` TEXT NOT NULL PRIMARY KEY,
                ...
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_seller_orders_parentOrderId` " +
                "ON `seller_orders` (`parentOrderId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_seller_orders_sellerId` " +
                "ON `seller_orders` (`sellerId`)"
        )
    }
}
```

**MIGRATION_8_9 to add** (CREATE TABLE + two indexes, same pattern as MIGRATION_5_6):
```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reviews` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `productId` TEXT NOT NULL DEFAULT '',
                `reviewerId` TEXT NOT NULL DEFAULT '',
                `reviewerName` TEXT NOT NULL DEFAULT '',
                `reviewerPhotoUrl` TEXT NOT NULL DEFAULT '',
                `purchaseId` TEXT NOT NULL DEFAULT '',
                `rating` INTEGER NOT NULL DEFAULT 0,
                `title` TEXT NOT NULL DEFAULT '',
                `body` TEXT NOT NULL DEFAULT '',
                `isVerifiedPurchase` INTEGER NOT NULL DEFAULT 1,
                `helpfulCount` INTEGER NOT NULL DEFAULT 0,
                `isVisible` INTEGER NOT NULL DEFAULT 1,
                `createdAt` TEXT NOT NULL DEFAULT '',
                `updatedAt` TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reviews_productId` ON `reviews` (`productId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reviews_reviewerId` ON `reviews` (`reviewerId`)"
        )
    }
}
```

**Database class header change** (lines 28–42 — add entity + bump version):
```kotlin
@Database(
    entities = [
        // ...existing...
        SellerOrderEntity::class,
        ReviewEntity::class,    // ADD
    ],
    version = 9,               // bump from 8
    exportSchema = true,
)
```

**databaseModule change** (`DataModule.kt` lines 127–158):
```kotlin
val databaseModule = module {
    single {
        Room.databaseBuilder(...)
            .addMigrations(
                // ... existing migrations ...
                WenuCommerceDatabase.MIGRATION_7_8,
                WenuCommerceDatabase.MIGRATION_8_9,  // ADD
            )
            ...
    }
    // ... existing DAOs ...
    single { get<WenuCommerceDatabase>().reviewDao() }  // ADD
}
```

---

### `data/.../repository/ProductReviewRepositoryImpl.kt` — re-route `submitReview` + `markReviewHelpful` (MODIFY)

**Analog:** `data/.../repository/OrderRepositoryImpl.kt` `cancelSellerOrder` (lines 154–176)

**Callable invocation + error handling pattern** (lines 154–176):
```kotlin
override suspend fun cancelSellerOrder(id: String): Result<Unit> =
    withContext(dispatcherProvider.io()) {
        runCatching {
            functions.getHttpsCallable("cancelSellerOrder")
                .call(mapOf("sellerOrderId" to id))
                .await()
            Unit
        }.recoverCatching { e ->
            val message = if (e is FirebaseFunctionsException) {
                when (e.code) {
                    FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                        "Only the seller can cancel this order"
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                        e.message ?: "Cannot cancel post-shipping"
                    else -> e.message ?: "Cancellation failed"
                }
            } else {
                e.message ?: "Cancellation failed"
            }
            Timber.e(e, "OrderRepository: cancelSellerOrder failed for $id")
            throw IllegalStateException(message, e)
        }
    }
```

**Alternative callable pattern (simpler)** from `PaymentRepositoryImpl.kt` (lines 53–78):
```kotlin
val result = Firebase.functions
    .getHttpsCallable("createPaymentIntent")
    .call(data)
    .await()
@Suppress("UNCHECKED_CAST")
val responseData = result.getData() as? Map<String, Any>
    ?: error("Invalid response from createPaymentIntent")
```

**Apply for `submitReview`** (replace the current Firestore transaction body):
```kotlin
override suspend fun submitReview(review: ProductReview): Result<ProductReview> =
    withContext(dispatcherProvider.io()) {
        runCatching {
            val data = mapOf(
                "productId" to review.productId,
                "rating" to review.rating,
                "title" to review.title,
                "body" to review.body,
                "reviewerName" to review.reviewerName,
                "reviewerPhotoUrl" to review.reviewerPhotoUrl,
            )
            val result = functions.getHttpsCallable("submitReview")
                .call(data)
                .await()
            @Suppress("UNCHECKED_CAST")
            val responseData = result.getData() as? Map<String, Any>
                ?: error("Invalid response from submitReview")
            val reviewId = responseData["reviewId"] as? String ?: error("Missing reviewId")
            review.copy(id = reviewId, isVerifiedPurchase = true)
        }.recoverCatching { e ->
            val message = if (e is FirebaseFunctionsException) {
                when (e.code) {
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                        e.message ?: "No delivered order found for this product"
                    FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                        "Sign in required"
                    else -> e.message ?: "Review submission failed"
                }
            } else { e.message ?: "Review submission failed" }
            Timber.e(e, "ProductReviewRepository: submitReview failed")
            throw IllegalStateException(message, e)
        }
    }
```

**Constructor change** (add `ReviewDao`, `FirebaseFunctions`):
```kotlin
class ProductReviewRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,   // ADD — already in firebaseModule
    private val reviewDao: ReviewDao,            // ADD — from databaseModule
    dispatcherProvider: DispatcherProvider,
) : ProductReviewRepository { ... }
```

**Koin binding change in `repositoryModule`** (switch from `singleOf` to explicit `single` when params grow):
```kotlin
// Before:
singleOf(::ProductReviewRepositoryImpl).bind<ProductReviewRepository>()

// After (if Koin param resolution is ambiguous):
single { ProductReviewRepositoryImpl(get(), get(), get(), get(), get()) }
    .bind<ProductReviewRepository>()
```

**`observeReviewsForProduct` — add Room DAO path** (mirrors OrderRepositoryImpl line 61–62):
```kotlin
override fun observeReviewsForProduct(productId: String): Flow<List<ProductReview>> =
    reviewDao.observeByProduct(productId)
        .map { list -> list.map { it.toDomain() } }
// Keep the callbackFlow Firestore listener in SyncManager (product-scoped, started on demand)
// Remove the direct callbackFlow from this method — Room is the source of truth
```

---

### `data/src/main/java/com/wenubey/data/util/Constants.kt` (MODIFY)

**Analog:** same file — existing constants (lines 18–19):
```kotlin
const val PRODUCTS_COLLECTION = "PRODUCTS"
const val REVIEWS_SUBCOLLECTION = "REVIEWS"
```

**Add:**
```kotlin
const val HELPFUL_VOTES_SUBCOLLECTION = "helpfulVotes"
```

---

### `app/.../customer/customer_products/CustomerProductDetailState.kt` (MODIFY)

**Analog:** `CustomerProductDetailState.kt` existing fields (lines 1–23)

**Current field pattern** (lines 7–23):
```kotlin
data class CustomerProductDetailState(
    val product: Product? = null,
    val reviews: List<ProductReview> = listOf(),
    val isLoading: Boolean = false,
    val isLoadingReviews: Boolean = false,
    val errorMessage: String? = null,
    val cartMessage: String? = null,
    val showLoginPrompt: Boolean = false,
    val isWishlisted: Boolean = false,
)
```

**Fields to add** (append after `isWishlisted`):
```kotlin
// Review form / eligibility state (Phase 7)
val hasDeliveredOrder: Boolean = false,
val existingReview: ProductReview? = null,
val reviewSortOrder: ReviewSortOrder = ReviewSortOrder.MOST_RECENT,
val isSubmittingReview: Boolean = false,
val showReviewForm: Boolean = false,
val reviewSubmitError: String? = null,
val helpfulVotedIds: Set<String> = emptySet(),
```

**Enum to add** (same file or a new file under `customer_products/`):
```kotlin
enum class ReviewSortOrder { MOST_RECENT, HIGHEST_RATED }
```

---

### `app/.../customer/customer_products/CustomerProductDetailAction.kt` (MODIFY)

**Analog:** `CustomerProductDetailAction.kt` existing sealed interface (lines 1–17)

**Current pattern** (lines 5–17):
```kotlin
sealed interface CustomerProductDetailAction {
    data class OnVariantSelected(val variant: ProductVariant) : CustomerProductDetailAction
    data class OnMarkReviewHelpful(val reviewId: String) : CustomerProductDetailAction
    data object AddToCart : CustomerProductDetailAction
    data object DismissLoginPrompt : CustomerProductDetailAction
    // ...
}
```

**Actions to add:**
```kotlin
// Review actions (Phase 7)
data object OpenReviewForm : CustomerProductDetailAction
data object DismissReviewForm : CustomerProductDetailAction
data class SubmitReview(
    val rating: Int,
    val title: String,
    val body: String,
) : CustomerProductDetailAction
data class OnSortOrderChanged(val order: ReviewSortOrder) : CustomerProductDetailAction
```

---

### `app/.../customer/customer_products/CustomerProductDetailViewModel.kt` (MODIFY)

**Analog:** `CustomerProductDetailViewModel.kt` existing structure (lines 1–243)

**`init` + suspend helper pattern** (lines 45–64, `observeWishlistState` pattern):
```kotlin
init {
    loadProduct(productId)
    observeReviews(productId)
    incrementViewCount(productId)
    observeWishlistState(productId)
    checkDeliveredOrderStatus(productId)  // ADD Phase 7
    loadExistingReview(productId)         // ADD Phase 7
}

private fun checkDeliveredOrderStatus(productId: String) {
    val userId = authRepository.currentUser.value?.uuid ?: return
    viewModelScope.launch(ioDispatcher) {
        // Query SellerOrderDao for DELIVERED orders containing this productId.
        // SellerOrderDao needs a new query: getByUserAndStatus(userId, "DELIVERED")
        // (see SellerOrderDao additions below)
        val deliveredOrders = sellerOrderDao.getByUserAndStatus(userId, "DELIVERED")
        val hasDelivered = deliveredOrders.any { entity ->
            runCatching {
                Json.decodeFromString<List<OrderItem>>(entity.itemsJson)
                    .any { it.productId == productId }
            }.getOrDefault(false)
        }
        _state.update { it.copy(hasDeliveredOrder = hasDelivered) }
    }
}
```

**Flow collect + state update pattern** (lines 112–130, `observeReviews`):
```kotlin
private fun observeReviews(productId: String) {
    reviewsJob?.cancel()
    reviewsJob = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(isLoadingReviews = true) }
        withContext(ioDispatcher) {
            reviewRepository.observeReviewsForProduct(productId)
                .catch { error ->
                    _state.update { it.copy(isLoadingReviews = false) }
                    Timber.e(error, "Failed to load reviews")
                }
                .collect { reviews ->
                    val sorted = sortedReviews(reviews, _state.value.reviewSortOrder)  // ADD sort
                    _state.update {
                        it.copy(reviews = sorted, isLoadingReviews = false)
                    }
                }
        }
    }
}
```

**`runCatching` action handler pattern** (lines 185–208, `addToCart`):
```kotlin
private fun submitReview(rating: Int, title: String, body: String) {
    val product = _state.value.product ?: return
    val user = authRepository.currentUser.value ?: return
    _state.update { it.copy(isSubmittingReview = true, reviewSubmitError = null) }
    viewModelScope.launch(ioDispatcher) {
        runCatching {
            reviewRepository.submitReview(
                ProductReview(
                    productId = product.id,
                    reviewerId = user.uuid,
                    reviewerName = "${user.name} ${user.surname}".trim(),
                    reviewerPhotoUrl = user.profilePhotoUri,
                    rating = rating, title = title, body = body,
                )
            )
        }.onSuccess {
            _state.update {
                it.copy(
                    isSubmittingReview = false,
                    showReviewForm = false,
                    cartMessage = "Review submitted",  // reuse snackbar
                )
            }
        }.onFailure { error ->
            Timber.e(error, "CustomerProductDetailViewModel: submitReview failed")
            _state.update {
                it.copy(
                    isSubmittingReview = false,
                    reviewSubmitError = error.message ?: "Couldn't submit review. Please try again.",
                )
            }
        }
    }
}
```

**Sort helper** (pure function, unit-testable without ViewModel):
```kotlin
private fun sortedReviews(reviews: List<ProductReview>, order: ReviewSortOrder) =
    when (order) {
        ReviewSortOrder.MOST_RECENT -> reviews.sortedByDescending { it.createdAt }
        ReviewSortOrder.HIGHEST_RATED -> reviews
            .sortedWith(compareByDescending<ProductReview> { it.rating }
                .thenByDescending { it.createdAt })
    }
```

**`SellerOrderDao` addition needed** (no analog — add a simple suspend query):
```kotlin
// In SellerOrderDao.kt
@Query("SELECT * FROM seller_orders WHERE userId = :userId AND status = :status")
suspend fun getByUserAndStatus(userId: String, status: String): List<SellerOrderEntity>
```

---

### `app/.../customer/customer_products/CustomerProductDetailScreen.kt` — sort toggle + badge + write-review affordance (MODIFY)

**Analog:** `CustomerProductDetailScreen.kt` existing reviews section (lines 459–501) and `ReviewCard` (lines 664–746)

**Reviews section item pattern** (lines 459–468):
```kotlin
item {
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Reviews (${state.reviews.size})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
    )
}
```

**Sort toggle — add after header item** (new `item` block; use `SingleChoiceSegmentedButtonRow` from M3):
```kotlin
item {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ReviewSortOrder.entries.forEachIndexed { index, order ->
            SegmentedButton(
                selected = state.reviewSortOrder == order,
                onClick = { viewModel.onAction(CustomerProductDetailAction.OnSortOrderChanged(order)) },
                shape = SegmentedButtonDefaults.itemShape(index, ReviewSortOrder.entries.size),
                label = { Text(if (order == ReviewSortOrder.MOST_RECENT) "Most recent" else "Highest rated") }
            )
        }
    }
}
```

**Write Review affordance** (new `item` block, gated on `hasDeliveredOrder`; mirrors `DismissLoginPrompt` auth gate pattern):
```kotlin
item {
    if (state.hasDeliveredOrder) {
        TextButton(
            onClick = { viewModel.onAction(CustomerProductDetailAction.OpenReviewForm) },
        ) {
            Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (state.existingReview != null) "Edit Your Review" else "Write a Review")
        }
    }
}
```

**ReviewCard enhancement** (lines 664–746 — add `VerifiedPurchaseBadge` in header row):
```kotlin
// In the existing Row(verticalAlignment = Alignment.CenterVertically) at line 673,
// add after the avatar+name column:
Spacer(modifier = Modifier.weight(1f))
if (review.isVerifiedPurchase) {
    VerifiedPurchaseBadge()
}
```

**Snackbar pattern for review submit result** (lines 83–96, `cartMessage` LaunchedEffect):
```kotlin
// Add a parallel LaunchedEffect for reviewSubmitError:
val reviewSubmitError = state.reviewSubmitError
LaunchedEffect(reviewSubmitError) {
    if (reviewSubmitError != null) {
        snackbarHostState.showSnackbar(
            message = reviewSubmitError,
            duration = SnackbarDuration.Long,
        )
        viewModel.onAction(CustomerProductDetailAction.DismissReviewForm)
    }
}
```

---

### `app/.../core/components/VerifiedPurchaseBadge.kt` (NEW)

**Analog:** `app/.../core/components/OrderStatusBadge.kt` `BadgeChip` (lines 39–53)

**BadgeChip pattern** (lines 39–53 — copy this exactly):
```kotlin
@Composable
private fun BadgeChip(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier
            .background(color = container, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
```

**Apply for VerifiedPurchaseBadge** (stateless — no parameters needed):
```kotlin
package com.wenubey.wenucommerce.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VerifiedPurchaseBadge(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = "Verified Purchase",
        style = MaterialTheme.typography.labelMedium,
        color = cs.onPrimaryContainer,
        modifier = modifier
            .background(cs.primaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
```

---

### `app/.../customer/customer_reviews/WriteReviewScreen.kt` (NEW)

**Analog:** `CustomerProductDetailScreen.kt` `CartActionSection` (lines 504–660) — spinner-in-button + disabled-state pattern

**Spinner-in-button pattern** (lines 530–542):
```kotlin
when {
    isOutOfStock -> {
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Out of Stock")
        }
    }
    isInCart -> { ... }
    else -> {
        Button(
            onClick = onAddToCart,
            enabled = !isAddingToCart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isAddingToCart) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Add to Cart")
            }
        }
    }
}
```

**Apply for WriteReviewScreen submit button:**
```kotlin
Button(
    onClick = { viewModel.onAction(CustomerProductDetailAction.SubmitReview(rating, title, body)) },
    enabled = selectedRating > 0 && !state.isSubmittingReview,
    modifier = Modifier.fillMaxWidth(),
) {
    if (state.isSubmittingReview) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        Text(if (state.existingReview != null) "Update Review" else "Submit Review")
    }
}
```

**Navigation scaffold pattern** (from `SellerProductCreateScreen` structure — full-screen with `TopAppBar` back nav; no type-safe route sample needed since the pattern for parameterized routes is already in `AppNavigationObjects.kt` lines 57, 109):
```kotlin
// Use Scaffold + TopAppBar with back navigation, no BottomBar
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(if (state.existingReview != null) "Edit Review" else "Write a Review") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }
) { padding -> ... }
```

---

### `app/.../navigation/AppNavigationObjects.kt` — `WriteReview` route (MODIFY)

**Analog:** `AppNavigationObjects.kt` lines 57 and 109

**Parameterized route pattern** (lines 57, 109):
```kotlin
@Serializable
data class CustomerProductDetail(val productId: String)

@Serializable
data class SellerOrderDetail(val sellerOrderId: String)
```

**Add:**
```kotlin
@Serializable
data class WriteReview(
    val productId: String,
    val existingReviewId: String? = null,
)
```

---

### `data/.../worker/SyncWorker.kt` — wire `SUBMIT_REVIEW` case (MODIFY)

**Analog:** `SyncWorker.kt` `ADD_TO_CART` branch (lines 106–113)

**Operation handler pattern** (lines 106–113):
```kotlin
OperationType.ADD_TO_CART -> {
    val payload = json.decodeFromString<AddToCartPayload>(operation.payloadJson)
    cartRepository.syncAddToCart(
        userId = operation.entityId,
        productId = payload.productId,
        quantity = payload.quantity,
        snapshotPrice = payload.snapshotPrice
    )
}
```

**Apply for `SUBMIT_REVIEW`** (replace `TODO`):
```kotlin
OperationType.SUBMIT_REVIEW -> {
    val review = json.decodeFromString<ProductReview>(operation.payloadJson)
    reviewRepository.submitReview(review).getOrThrow()
}
```

**Constructor injection change** (add `reviewRepository: ProductReviewRepository`):
```kotlin
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val pendingOperationDao: PendingOperationDao,
    private val cartRepository: CartRepository,
    private val reviewRepository: ProductReviewRepository,  // ADD
    private val dispatcherProvider: DispatcherProvider
) : CoroutineWorker(appContext, params)
```

**Koin worker binding change** (`DataModule.kt` line 169):
```kotlin
// Before:
worker { SyncWorker(get(), get(), get(), get(), get()) }

// After (6 params):
worker { SyncWorker(get(), get(), get(), get(), get(), get()) }
```

---

## Shared Patterns

### Authentication Guard (request.auth check)
**Source:** `functions/src/index.ts` lines 154–157 (`createPaymentIntent`), lines 443–449
**Apply to:** All new Cloud Functions (`submitReview`, `markReviewHelpful`, `setReviewVisibility`)
```typescript
if (!request.auth) {
  throw new HttpsError("unauthenticated", "Sign in required");
}
const uid = request.auth.uid;
```

### Firebase Callable — Kotlin client error handling
**Source:** `data/.../repository/OrderRepositoryImpl.kt` lines 154–176
**Apply to:** `ProductReviewRepositoryImpl.submitReview`, `ProductReviewRepositoryImpl.markReviewHelpful`
```kotlin
runCatching {
    functions.getHttpsCallable("functionName").call(data).await()
    Unit
}.recoverCatching { e ->
    val message = if (e is FirebaseFunctionsException) {
        when (e.code) {
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> e.message ?: "..."
            else -> e.message ?: "..."
        }
    } else { e.message ?: "..." }
    Timber.e(e, "...")
    throw IllegalStateException(message, e)
}
```

### Room-first Flow observation
**Source:** `data/.../repository/OrderRepositoryImpl.kt` lines 61–63
**Apply to:** `ProductReviewRepositoryImpl.observeReviewsForProduct` (change from callbackFlow to Room DAO)
```kotlin
override fun observeReviewsForProduct(productId: String): Flow<List<ProductReview>> =
    reviewDao.observeByProduct(productId)
        .map { list -> list.map { it.toDomain() } }
```

### ViewModel StateFlow UDF
**Source:** `CustomerProductDetailViewModel.kt` lines 36–37, 83–95 (state + update pattern)
**Apply to:** `CustomerProductDetailViewModel` extensions and `WriteReviewScreen` ViewModel (if separate)
```kotlin
private val _state = MutableStateFlow(CustomerProductDetailState())
val state: StateFlow<CustomerProductDetailState> = _state.asStateFlow()

// update with copy:
_state.update { it.copy(fieldName = newValue) }
```

### Snackbar feedback pattern
**Source:** `CustomerProductDetailScreen.kt` lines 83–97
**Apply to:** Review submit success/error, `WriteReviewScreen`
```kotlin
val cartMessage = state.cartMessage
LaunchedEffect(cartMessage) {
    if (cartMessage != null) {
        val result = snackbarHostState.showSnackbar(
            message = cartMessage,
            actionLabel = ...,
            duration = SnackbarDuration.Short,
        )
        viewModel.onAction(CustomerProductDetailAction.DismissCartMessage)
    }
}
```

### Pure helper extraction (testability)
**Source:** `functions/src/index.ts` lines 197–258 (`allocateProRata`, `allocateDiscount`), lines 314–440 (`buildFanoutDocs`, `decideWebhookAction`)
**Apply to:** `submitReview` review data builder and aggregate recompute logic
```typescript
// Extract as named export functions so Jest can import and test them directly
// without a running Cloud Function or Firestore instance
export function buildReviewData(...): ReviewData { ... }
export function computeNewAggregate(...): { newAvg: number; newCount: number } { ... }
```

### Amber star + outlined star pattern
**Source:** `CustomerProductDetailScreen.kt` lines 700–708, `CustomerHomeScreen.kt` lines 511–524
**Apply to:** `StarRatingDisplay`, `WriteReviewScreen` `StarRatingSelector`, `ReviewCard` star row
```kotlin
repeat(5) { index ->
    Icon(
        imageVector = if (index < review.rating) Icons.Filled.Star else Icons.Outlined.Star,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = Color(0xFFFFC107),
    )
}
// For interactive selector: swap tint to MaterialTheme.colorScheme.primary for selected stars
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `firestore.indexes.json` | config | CRUD | No existing `firestore.indexes.json` in the project root (research A6 confirmed: assumed absent) |

---

## Critical Implementation Notes for Planner

1. **`createdAt` format:** The existing `ProductReviewRepositoryImpl` uses `System.currentTimeMillis().toString()` (epoch millis string, line 103). The Cloud Function must match: use `admin.firestore.Timestamp.now().toMillis().toString()`. Lexicographic sort on 13-digit epoch strings is correct — do NOT switch to ISO-8601 without migrating existing reviews.

2. **SellerOrderDao gap:** `SellerOrderDao.kt` has no `getByUserAndStatus` query (open question A3 confirmed by reading the file). Add it in plan 07-01 alongside `ReviewDao`.

3. **Product card (REVW-07):** `CustomerProductCard` in `CustomerHomeScreen.kt` lines 511–525 already renders `reviewCount` and `averageRating` when `product.reviewCount > 0`. No new card code is required — REVW-07 activates automatically once the Cloud Function maintains the aggregate fields.

4. **`observeReviewsForProduct` listener scope:** Do NOT add a SyncManager-level Firestore listener for reviews (all products would be listened). Instead, keep the Firestore `callbackFlow` pattern from the current `ProductReviewRepositoryImpl.observeReviewsForProduct` as a product-scoped listener, and write-through to `ReviewDao` on each emission, so the ViewModel reads Room for offline-first but the Firestore listener keeps it live.

5. **Firestore rules:** The current `match /PRODUCTS/{productId}` block has a wildcard `/{document=**}` sub-match that allows any authenticated user to write reviews. Plan 07-01 must replace this with the scoped `REVIEWS` sub-match to lock writes server-only.

6. **`singleOf` vs `single`:** `ProductReviewRepositoryImpl` is currently registered as `singleOf(::ProductReviewRepositoryImpl)`. Adding `FirebaseFunctions` and `ReviewDao` constructor params will still work with `singleOf` as long as both are registered in Koin (`Firebase.functions` is in `firebaseModule` line 70; `reviewDao` must be added to `databaseModule`).

---

## Metadata

**Analog search scope:** `functions/src/`, `data/src/main/java/`, `app/src/main/java/`, `firestore.rules`, `functions/test/`
**Files scanned:** 22
**Pattern extraction date:** 2026-07-16
