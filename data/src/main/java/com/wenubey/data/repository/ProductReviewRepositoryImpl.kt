package com.wenubey.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.wenubey.data.local.dao.ReviewDao
import com.wenubey.data.local.mapper.toDomain
import com.wenubey.data.local.mapper.toEntity
import com.wenubey.data.util.PRODUCTS_COLLECTION
import com.wenubey.data.util.REVIEWS_SUBCOLLECTION
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.product.ProductReview
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductReviewRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Phase 7 reviews repository (server-authoritative writes, Room-first reads):
 * - `submitReview` / `markReviewHelpful` invoke the Cloud Function callables
 *   (the ONLY write path — Firestore rules deny direct client writes, D-01/D-02).
 * - `observeReviewsForProduct` reads from Room (offline-first), kept live by a
 *   product-scoped Firestore listener that write-throughs each snapshot into the
 *   ReviewDao (Pitfall 4 — product-scoped, started on first collect).
 * - `getMyReviewForProduct` returns the caller's existing review for edit
 *   pre-fill (D-05).
 */
class ProductReviewRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val reviewDao: ReviewDao,
    private val dispatcherProvider: DispatcherProvider,
) : ProductReviewRepository {

    private val ioDispatcher = dispatcherProvider.io()

    private fun reviewsCollection(productId: String) =
        firestore.collection(PRODUCTS_COLLECTION)
            .document(productId)
            .collection(REVIEWS_SUBCOLLECTION)

    // ── Observe (Room-first + product-scoped write-through listener) ─────

    override fun observeReviewsForProduct(productId: String): Flow<List<ProductReview>> =
        channelFlow {
            // Product-scoped Firestore listener writing each snapshot through to
            // Room, launched in this flow's scope and torn down when collection
            // stops — never a global all-products listener (Pitfall 4).
            launch(ioDispatcher) {
                productReviewSnapshots(productId).collect { reviews ->
                    reviewDao.upsertAll(reviews.map { it.toEntity() })
                }
            }
            // Room is the source of truth the UI reads from (offline-first).
            reviewDao.observeByProduct(productId)
                .map { list -> list.map { it.toDomain() } }
                .collect { send(it) }
        }

    private fun productReviewSnapshots(productId: String): Flow<List<ProductReview>> =
        callbackFlow {
            val listener = reviewsCollection(productId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "Error observing reviews for product: $productId")
                        return@addSnapshotListener
                    }
                    val reviews = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(ProductReview::class.java)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to deserialize review: ${doc.id}")
                            null
                        }
                    } ?: emptyList()
                    trySend(reviews)
                }
            awaitClose {
                Timber.d("Removing reviews listener for product: $productId")
                listener.remove()
            }
        }

    override suspend fun getReviewsForProduct(productId: String): Result<List<ProductReview>> =
        safeApiCall(ioDispatcher) {
            val snapshot = reviewsCollection(productId)
                .whereEqualTo("isVisible", true)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(ProductReview::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize review: ${doc.id}")
                    null
                }
            }
        }

    override suspend fun getMyReviewForProduct(productId: String): Result<ProductReview?> =
        safeApiCall(ioDispatcher) {
            val uid = auth.currentUser?.uid ?: return@safeApiCall null
            val snapshot = reviewsCollection(productId)
                .whereEqualTo("reviewerId", uid)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.let { doc ->
                try {
                    doc.toObject(ProductReview::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize own review for product: $productId")
                    null
                }
            }
        }

    // ── Writes (server-authoritative via callables) ─────────────────────

    override suspend fun submitReview(review: ProductReview): Result<ProductReview> =
        withContext(ioDispatcher) {
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
                val reviewId = responseData["reviewId"] as? String
                    ?: error("Missing reviewId from submitReview")

                review.copy(id = reviewId, isVerifiedPurchase = true)
            }.recoverCatching { e ->
                val message = if (e is FirebaseFunctionsException) {
                    when (e.code) {
                        FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                            "No delivered order found for this product"
                        FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                            "Sign in required"
                        FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                            e.message ?: "Invalid review"
                        else -> e.message ?: "Review submission failed"
                    }
                } else {
                    e.message ?: "Review submission failed"
                }
                Timber.e(e, "ProductReviewRepository: submitReview failed")
                throw IllegalStateException(message, e)
            }
        }

    override suspend fun markReviewHelpful(
        productId: String,
        reviewId: String,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            functions.getHttpsCallable("markReviewHelpful")
                .call(mapOf("productId" to productId, "reviewId" to reviewId))
                .await()
            Unit
        }.recoverCatching { e ->
            // ALREADY_EXISTS = the user already voted; a benign no-op, not an error.
            if (e is FirebaseFunctionsException &&
                e.code == FirebaseFunctionsException.Code.ALREADY_EXISTS
            ) {
                Timber.d("markReviewHelpful: already voted for $reviewId — no-op")
                return@recoverCatching
            }
            val message = if (e is FirebaseFunctionsException) {
                when (e.code) {
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Sign in required"
                    else -> e.message ?: "Could not mark helpful"
                }
            } else {
                e.message ?: "Could not mark helpful"
            }
            Timber.e(e, "ProductReviewRepository: markReviewHelpful failed for $reviewId")
            throw IllegalStateException(message, e)
        }
    }

    override suspend fun setReviewVisibility(
        productId: String,
        reviewId: String,
        isVisible: Boolean,
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        // NOTE: 07-03 will route the seller visibility toggle through a callable
        // (review docs are server-only now). Left as the direct update for the
        // seller path pending that plan.
        reviewsCollection(productId).document(reviewId)
            .update("isVisible", isVisible)
            .await()

        Timber.d("Review visibility set to $isVisible: $reviewId")
    }
}
