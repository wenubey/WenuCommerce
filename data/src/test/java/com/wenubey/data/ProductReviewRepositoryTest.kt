package com.wenubey.data

import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.wenubey.data.local.dao.ReviewDao
import com.wenubey.data.local.entity.ReviewEntity
import com.wenubey.data.repository.ProductReviewRepositoryImpl
import com.wenubey.domain.model.product.ProductReview
import com.wenubey.domain.repository.DispatcherProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for ProductReviewRepositoryImpl. Uses MockK for the DAO + Firebase
 * clients — no real Firestore. Mirrors OrderRepositoryTest's callable-mocking
 * pattern (Tasks.forResult + HttpsCallableReference).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductReviewRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = object : DispatcherProvider {
        override fun io() = testDispatcher
        override fun main() = testDispatcher
        override fun default() = testDispatcher
    }

    private fun newRepo(
        firestore: FirebaseFirestore = mockk(relaxed = true),
        auth: FirebaseAuth = mockk(relaxed = true),
        functions: FirebaseFunctions = mockk(relaxed = true),
        reviewDao: ReviewDao = mockk(relaxed = true),
    ) = ProductReviewRepositoryImpl(firestore, auth, functions, reviewDao, dispatcherProvider)

    private fun sampleReview() = ProductReview(
        productId = "p-1",
        reviewerId = "cust-1",
        reviewerName = "Ada",
        rating = 4,
        title = "Nice",
        body = "Works",
    )

    @Test
    fun `submitReview delegates to callable and returns review with server id + verified flag`() =
        runTest(testDispatcher) {
            val functions: FirebaseFunctions = mockk()
            val callable: HttpsCallableReference = mockk()
            val callResult: HttpsCallableResult = mockk()
            every { functions.getHttpsCallable("submitReview") } returns callable
            coEvery { callable.call(any<Map<String, Any?>>()) } returns Tasks.forResult(callResult)
            every { callResult.getData() } returns mapOf("reviewId" to "r-1", "isVerifiedPurchase" to true)

            val repo = newRepo(functions = functions)
            val result = repo.submitReview(sampleReview())

            assertThat(result.isSuccess).isTrue()
            val saved = result.getOrThrow()
            assertThat(saved.id).isEqualTo("r-1")
            assertThat(saved.isVerifiedPurchase).isTrue()
            coVerify {
                callable.call(
                    match<Map<String, Any?>> {
                        it["productId"] == "p-1" && it["rating"] == 4
                    }
                )
            }
        }

    @Test
    fun `submitReview maps FAILED_PRECONDITION to no-delivered-order message`() =
        runTest(testDispatcher) {
            val functions: FirebaseFunctions = mockk()
            val callable: HttpsCallableReference = mockk()
            every { functions.getHttpsCallable("submitReview") } returns callable
            val fnException: FirebaseFunctionsException = mockk(relaxed = true)
            every { fnException.code } returns FirebaseFunctionsException.Code.FAILED_PRECONDITION
            coEvery { callable.call(any<Map<String, Any?>>()) } returns Tasks.forException(fnException)

            val repo = newRepo(functions = functions)
            val result = repo.submitReview(sampleReview())

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
            assertThat(result.exceptionOrNull()?.message)
                .isEqualTo("No delivered order found for this product")
        }

    @Test
    fun `markReviewHelpful invokes callable with productId + reviewId map`() =
        runTest(testDispatcher) {
            val functions: FirebaseFunctions = mockk()
            val callable: HttpsCallableReference = mockk()
            val callResult: HttpsCallableResult = mockk(relaxed = true)
            every { functions.getHttpsCallable("markReviewHelpful") } returns callable
            coEvery { callable.call(any<Map<String, Any?>>()) } returns Tasks.forResult(callResult)

            val repo = newRepo(functions = functions)
            val result = repo.markReviewHelpful("p-1", "r-1")

            assertThat(result.isSuccess).isTrue()
            coVerify { callable.call(mapOf("productId" to "p-1", "reviewId" to "r-1")) }
        }

    @Test
    fun `markReviewHelpful treats ALREADY_EXISTS as a benign success`() =
        runTest(testDispatcher) {
            val functions: FirebaseFunctions = mockk()
            val callable: HttpsCallableReference = mockk()
            every { functions.getHttpsCallable("markReviewHelpful") } returns callable
            val fnException: FirebaseFunctionsException = mockk(relaxed = true)
            every { fnException.code } returns FirebaseFunctionsException.Code.ALREADY_EXISTS
            coEvery { callable.call(any<Map<String, Any?>>()) } returns Tasks.forException(fnException)

            val repo = newRepo(functions = functions)
            val result = repo.markReviewHelpful("p-1", "r-1")

            assertThat(result.isSuccess).isTrue()
        }

    @Test
    fun `observeReviewsForProduct maps ReviewDao flow through toDomain`() =
        runTest(testDispatcher) {
            val reviewDao: ReviewDao = mockk(relaxed = true)
            every { reviewDao.observeByProduct("p-1") } returns flowOf(
                listOf(
                    ReviewEntity(
                        id = "r-1",
                        productId = "p-1",
                        reviewerId = "cust-1",
                        rating = 5,
                        isVisible = true,
                    )
                )
            )
            val repo = newRepo(reviewDao = reviewDao)

            val reviews = repo.observeReviewsForProduct("p-1").first()

            assertThat(reviews).hasSize(1)
            assertThat(reviews.first().id).isEqualTo("r-1")
            assertThat(reviews.first().rating).isEqualTo(5)
        }
}
