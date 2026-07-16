package com.wenubey.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.util.PRODUCTS_COLLECTION
import com.wenubey.data.util.REVIEWS_SUBCOLLECTION
import com.wenubey.domain.model.product.ProductReview
import com.wenubey.domain.model.product.toMap
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for ProductReviewRepositoryImpl against the Firestore + Auth
 * emulators (Phase 7).
 *
 * NOTE: as of 07-01 the review WRITE path is server-only (submitReview /
 * markReviewHelpful Cloud Function callables). Those are exercised in
 * functions/test (jest) + firestore rules tests, NOT here — this suite has no
 * functions emulator wired. What remains valid to cover here is the READ +
 * Room-first OBSERVE path and setReviewVisibility, with reviews seeded directly
 * (simulating the Admin SDK server write).
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
    private val functions: FirebaseFunctions by lazy { Firebase.functions }

    private val db by lazy {
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WenuCommerceDatabase::class.java,
        ).build()
    }
    private val repo by lazy {
        ProductReviewRepositoryImpl(firestore, auth, functions, db.reviewDao(), dispatcherProvider)
    }

    private lateinit var uid: String

    @Before
    fun resetState() {
        FirebaseEmulator.clearAuth()
        FirebaseEmulator.clearFirestore()
        auth.signOut()
        uid = runBlocking { FirebaseEmulator.signInAnonymous() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun productId() = "prod-" + UUID.randomUUID().toString().take(8)

    /** Seeds a review doc directly (simulating the server Admin-SDK write path). */
    private suspend fun seedReview(
        productId: String,
        rating: Int = 5,
        isVisible: Boolean = true,
        reviewerId: String = uid,
    ): ProductReview {
        val id = "rev-" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis().toString()
        val review = ProductReview(
            id = id,
            productId = productId,
            reviewerId = reviewerId,
            reviewerName = "Tester",
            purchaseId = "purch-1",
            rating = rating,
            title = "Great",
            body = "Loved it",
            isVerifiedPurchase = true,
            isVisible = isVisible,
            createdAt = now,
            updatedAt = now,
        )
        firestore.collection(PRODUCTS_COLLECTION).document(productId)
            .collection(REVIEWS_SUBCOLLECTION).document(id)
            .set(review.toMap())
            .await()
        return review
    }

    @Test
    fun getReviewsForProduct_returns_only_visible(): Unit = runBlocking {
        val pid = productId()
        val r1 = seedReview(pid, isVisible = true)
        seedReview(pid, isVisible = false)

        val list = repo.getReviewsForProduct(pid).getOrThrow()

        assertThat(list.map { it.id }).containsExactly(r1.id)
    }

    @Test
    fun observeReviewsForProduct_writes_through_to_room_and_emits(): Unit = runBlocking {
        val pid = productId()

        // Room-first: empty until the product-scoped listener syncs the seed.
        val initial = repo.observeReviewsForProduct(pid).first()
        assertThat(initial).isEmpty()

        seedReview(pid)

        val emitted = repo.observeReviewsForProduct(pid).first { it.isNotEmpty() }
        assertThat(emitted).hasSize(1)
    }

    @Test
    fun getMyReviewForProduct_returns_callers_review(): Unit = runBlocking {
        val pid = productId()
        val mine = seedReview(pid, reviewerId = uid)
        seedReview(pid, reviewerId = "someone-else")

        val result = repo.getMyReviewForProduct(pid).getOrThrow()

        assertThat(result?.id).isEqualTo(mine.id)
    }

    @Test
    fun getMyReviewForProduct_returns_null_when_none(): Unit = runBlocking {
        val pid = productId()
        seedReview(pid, reviewerId = "someone-else")

        val result = repo.getMyReviewForProduct(pid).getOrThrow()

        assertThat(result).isNull()
    }

    @Test
    fun setReviewVisibility_toggles_flag(): Unit = runBlocking {
        val pid = productId()
        val saved = seedReview(pid)

        repo.setReviewVisibility(pid, saved.id, isVisible = false).getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid)
            .collection(REVIEWS_SUBCOLLECTION).document(saved.id).get().await()
        assertThat(doc.getBoolean("isVisible")).isEqualTo(false)
    }
}
