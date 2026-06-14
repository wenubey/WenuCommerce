package com.wenubey.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.util.PRODUCTS_COLLECTION
import com.wenubey.data.util.REVIEWS_SUBCOLLECTION
import com.wenubey.domain.model.product.ProductReview
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for ProductReviewRepositoryImpl against the Firestore + Auth emulators.
 *
 * Prereqs (see [FirebaseEmulator]):
 *   firebase emulators:start --only firestore,auth
 *   AVD or device connected.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ProductReviewRepositoryImplEmulatorTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun configureSdk() {
            FirebaseEmulator.useEmulator()
        }
    }

    private val dispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun io(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun default(): CoroutineDispatcher = Dispatchers.Unconfined
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val repo by lazy {
        ProductReviewRepositoryImpl(firestore, auth, dispatcherProvider)
    }

    private lateinit var uid: String

    @Before
    fun resetState() {
        FirebaseEmulator.clearAuth()
        FirebaseEmulator.clearFirestore()
        auth.signOut()
        uid = runBlocking { FirebaseEmulator.signInAnonymous() }
    }

    private fun productId() = "prod-" + UUID.randomUUID().toString().take(8)

    /**
     * Seeds a product document with the aggregation fields the review transaction
     * reads/updates. Returns the productId.
     */
    private suspend fun seedProduct(
        averageRating: Double = 0.0,
        reviewCount: Int = 0,
    ): String {
        val pid = productId()
        firestore.collection(PRODUCTS_COLLECTION)
            .document(pid)
            .set(
                mapOf(
                    "id" to pid,
                    "averageRating" to averageRating,
                    "reviewCount" to reviewCount,
                    "createdAt" to "0",
                    "updatedAt" to "0",
                )
            )
            .await()
        return pid
    }

    private fun review(
        productId: String,
        rating: Int = 5,
        purchaseId: String = "purch-${UUID.randomUUID()}",
    ) = ProductReview(
        productId = productId,
        reviewerId = uid,
        reviewerName = "Tester",
        purchaseId = purchaseId,
        rating = rating,
        title = "Great",
        body = "Loved it",
    )

    @Test
    fun submitReview_writes_review_and_updates_product_aggregation(): Unit = runBlocking {
        val pid = seedProduct()

        val result = repo.submitReview(review(pid, rating = 4))

        assertThat(result.isSuccess).isTrue()
        val saved = result.getOrThrow()
        assertThat(saved.id).isNotEmpty()
        assertThat(saved.createdAt).isNotEmpty()
        assertThat(saved.updatedAt).isEqualTo(saved.createdAt)

        val product = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(product.getDouble("averageRating")).isEqualTo(4.0)
        assertThat(product.getLong("reviewCount")).isEqualTo(1L)

        val reviewDoc = firestore.collection(PRODUCTS_COLLECTION).document(pid)
            .collection(REVIEWS_SUBCOLLECTION).document(saved.id).get().await()
        assertThat(reviewDoc.exists()).isTrue()
        assertThat(reviewDoc.getLong("rating")).isEqualTo(4L)
    }

    @Test
    fun submitReview_second_review_averages_correctly(): Unit = runBlocking {
        val pid = seedProduct()

        repo.submitReview(review(pid, rating = 5, purchaseId = "p1")).getOrThrow()
        repo.submitReview(review(pid, rating = 3, purchaseId = "p2")).getOrThrow()

        val product = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(product.getDouble("averageRating")).isEqualTo(4.0)
        assertThat(product.getLong("reviewCount")).isEqualTo(2L)
    }

    @Test
    fun submitReview_rejects_duplicate_same_purchase(): Unit = runBlocking {
        val pid = seedProduct()
        repo.submitReview(review(pid, purchaseId = "purch-x")).getOrThrow()

        val result = repo.submitReview(review(pid, purchaseId = "purch-x"))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun submitReview_rejects_mismatched_reviewerId(): Unit = runBlocking {
        val pid = seedProduct()
        val foreign = review(pid).copy(reviewerId = "someone-else")

        val result = repo.submitReview(foreign)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun getReviewsForProduct_returns_only_visible(): Unit = runBlocking {
        val pid = seedProduct()
        val r1 = repo.submitReview(review(pid, purchaseId = "p1")).getOrThrow()
        val r2 = repo.submitReview(review(pid, purchaseId = "p2")).getOrThrow()
        repo.setReviewVisibility(pid, r2.id, isVisible = false).getOrThrow()

        val list = repo.getReviewsForProduct(pid).getOrThrow()

        assertThat(list.map { it.id }).containsExactly(r1.id)
    }

    @Test
    fun observeReviewsForProduct_emits_on_new_submission(): Unit = runBlocking {
        val pid = seedProduct()

        val initial = repo.observeReviewsForProduct(pid).first()
        assertThat(initial).isEmpty()

        repo.submitReview(review(pid)).getOrThrow()

        val flow = repo.observeReviewsForProduct(pid).first { it.isNotEmpty() }
        assertThat(flow).hasSize(1)
    }

    @Test
    fun markReviewHelpful_increments_count(): Unit = runBlocking {
        val pid = seedProduct()
        val saved = repo.submitReview(review(pid)).getOrThrow()

        repo.markReviewHelpful(pid, saved.id).getOrThrow()
        repo.markReviewHelpful(pid, saved.id).getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid)
            .collection(REVIEWS_SUBCOLLECTION).document(saved.id).get().await()
        assertThat(doc.getLong("helpfulCount")).isEqualTo(2L)
    }

    @Test
    fun setReviewVisibility_toggles_flag(): Unit = runBlocking {
        val pid = seedProduct()
        val saved = repo.submitReview(review(pid)).getOrThrow()

        repo.setReviewVisibility(pid, saved.id, isVisible = false).getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid)
            .collection(REVIEWS_SUBCOLLECTION).document(saved.id).get().await()
        assertThat(doc.getBoolean("isVisible")).isEqualTo(false)
    }
}
