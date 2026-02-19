package com.wenubey.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.util.PRODUCTS_COLLECTION
import com.wenubey.data.util.REVIEWS_SUBCOLLECTION
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.product.ProductReview
import com.wenubey.domain.model.product.toMap
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductReviewRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID

class ProductReviewRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    dispatcherProvider: DispatcherProvider,
) : ProductReviewRepository {

    private val ioDispatcher = dispatcherProvider.io()

    private fun reviewsCollection(productId: String) =
        firestore.collection(PRODUCTS_COLLECTION)
            .document(productId)
            .collection(REVIEWS_SUBCOLLECTION)

    override fun observeReviewsForProduct(productId: String): Flow<List<ProductReview>> =
        callbackFlow {
            val query = reviewsCollection(productId)
                .whereEqualTo("isVisible", true)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error observing reviews for product: $productId")
                    close(error)
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

                Timber.d("Reviews updated for $productId: ${reviews.size} reviews")
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

    override suspend fun submitReview(review: ProductReview): Result<ProductReview> =
        safeApiCall(ioDispatcher) {
            val callerUid = auth.currentUser?.uid
                ?: throw Exception("User not authenticated")

            if (callerUid != review.reviewerId) {
                throw Exception("Reviewer ID mismatch")
            }

            // Check for duplicate review
            val existing = reviewsCollection(review.productId)
                .whereEqualTo("reviewerId", callerUid)
                .whereEqualTo("purchaseId", review.purchaseId)
                .get()
                .await()

            if (!existing.isEmpty) {
                throw IllegalStateException("Review already submitted for this purchase")
            }

            val reviewId = UUID.randomUUID().toString()
            val currentTime = System.currentTimeMillis().toString()

            val newReview = review.copy(
                id = reviewId,
                createdAt = currentTime,
                updatedAt = currentTime,
            )

            // Write review and update product aggregation in a transaction
            val productRef = firestore.collection(PRODUCTS_COLLECTION)
                .document(review.productId)

            firestore.runTransaction { transaction ->
                val productSnapshot = transaction.get(productRef)
                val currentAvg = productSnapshot.getDouble("averageRating") ?: 0.0
                val currentCount = productSnapshot.getLong("reviewCount")?.toInt() ?: 0

                val newCount = currentCount + 1
                val newAvg = ((currentAvg * currentCount) + newReview.rating) / newCount

                val reviewRef = reviewsCollection(review.productId).document(reviewId)
                transaction.set(reviewRef, newReview.toMap())
                transaction.update(
                    productRef, mapOf(
                        "averageRating" to newAvg,
                        "reviewCount" to newCount,
                        "updatedAt" to currentTime,
                    )
                )
            }.await()

            Timber.d("Review submitted: $reviewId for product: ${review.productId}")
            newReview
        }

    override suspend fun markReviewHelpful(
        productId: String,
        reviewId: String,
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        reviewsCollection(productId).document(reviewId)
            .update("helpfulCount", FieldValue.increment(1))
            .await()

        Timber.d("Review marked helpful: $reviewId")
    }

    override suspend fun setReviewVisibility(
        productId: String,
        reviewId: String,
        isVisible: Boolean,
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        reviewsCollection(productId).document(reviewId)
            .update("isVisible", isVisible)
            .await()

        Timber.d("Review visibility set to $isVisible: $reviewId")
    }
}
