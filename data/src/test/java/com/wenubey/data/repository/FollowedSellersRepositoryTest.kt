package com.wenubey.data.repository

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.local.dao.FollowedSellerDao
import com.wenubey.data.local.entity.FollowedSellerEntity
import com.wenubey.domain.repository.DispatcherProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [FollowedSellersRepositoryImpl].
 *
 * - DAO is a fake backed by an in-memory MutableStateFlow so observe/isFollowing
 *   behave like real Room queries.
 * - Firestore is a MockK relaxed double — no real Firestore contact per CLAUDE.md.
 * - The fire-and-forget contract (CD-02) is validated by a test that makes the
 *   Firestore .set() task throw and asserts Room state is still correct.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowedSellersRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = object : DispatcherProvider {
        override fun io() = testDispatcher
        override fun main() = testDispatcher
        override fun default() = testDispatcher
    }

    /** Fake DAO — in-memory map keyed by (userId, sellerId). */
    private class FakeFollowedSellerDao : FollowedSellerDao {
        private val rows = mutableMapOf<Pair<String, String>, FollowedSellerEntity>()
        private val flow = MutableStateFlow<List<FollowedSellerEntity>>(emptyList())

        override fun observeFollowedSellers(userId: String) =
            MutableStateFlow(rows.values.filter { it.userId == userId })
                .also { flow.value = rows.values.toList() }

        override suspend fun getFollowedSeller(userId: String, sellerId: String) =
            rows[userId to sellerId]

        override suspend fun upsert(item: FollowedSellerEntity) {
            rows[item.userId to item.sellerId] = item
            flow.value = rows.values.toList()
        }

        override suspend fun deleteItem(userId: String, sellerId: String) {
            rows.remove(userId to sellerId)
            flow.value = rows.values.toList()
        }

        override fun isFollowing(userId: String, sellerId: String) =
            MutableStateFlow(rows.containsKey(userId to sellerId))

        fun size() = rows.size
        fun get(userId: String, sellerId: String) = rows[userId to sellerId]
    }

    private fun firestoreThatSucceeds(
        setSlot: io.mockk.CapturingSlot<Map<String, Any>>? = null,
    ): FirebaseFirestore {
        val firestore: FirebaseFirestore = mockk(relaxed = true)
        val docRef: DocumentReference = mockk(relaxed = true)
        val collectionRef: CollectionReference = mockk(relaxed = true)
        every { firestore.collection(any()) } returns collectionRef
        every { collectionRef.document(any()) } returns docRef
        every { docRef.collection(any()) } returns collectionRef
        val task = com.google.android.gms.tasks.Tasks.forResult<Void?>(null)
        if (setSlot != null) {
            every { docRef.set(capture(setSlot)) } returns task
        } else {
            every { docRef.set(any<Map<String, Any>>()) } returns task
        }
        every { docRef.delete() } returns task
        return firestore
    }

    private fun firestoreThatThrowsOnSet(): FirebaseFirestore {
        val firestore: FirebaseFirestore = mockk(relaxed = true)
        val docRef: DocumentReference = mockk(relaxed = true)
        val collectionRef: CollectionReference = mockk(relaxed = true)
        every { firestore.collection(any()) } returns collectionRef
        every { collectionRef.document(any()) } returns docRef
        every { docRef.collection(any()) } returns collectionRef
        val failed = com.google.android.gms.tasks.Tasks.forException<Void?>(
            RuntimeException("firestore offline")
        )
        every { docRef.set(any<Map<String, Any>>()) } returns failed
        every { docRef.delete() } returns failed
        return firestore
    }

    @Test
    fun `followSeller upserts entity into DAO with expected fields`() = runTest(testDispatcher) {
        val dao = FakeFollowedSellerDao()
        val setPayload = slot<Map<String, Any>>()
        val repo = FollowedSellersRepositoryImpl(
            dao,
            firestoreThatSucceeds(setPayload),
            dispatcherProvider,
        )

        repo.followSeller(
            userId = "u1",
            sellerId = "s1",
            sellerName = "Acme",
            sellerLogoUrl = "https://x/y.png",
        )

        val stored = dao.get("u1", "s1")
        assertThat(stored).isNotNull()
        assertThat(stored!!.userId).isEqualTo("u1")
        assertThat(stored.sellerId).isEqualTo("s1")
        assertThat(stored.sellerName).isEqualTo("Acme")
        assertThat(stored.sellerLogoUrl).isEqualTo("https://x/y.png")
        assertThat(stored.followedAt).isNotEmpty()
    }

    @Test
    fun `followSeller keeps Room correct when Firestore write throws (fire-and-forget)`() =
        runTest(testDispatcher) {
            val dao = FakeFollowedSellerDao()
            val repo = FollowedSellersRepositoryImpl(
                dao,
                firestoreThatThrowsOnSet(),
                dispatcherProvider,
            )

            repo.followSeller("u1", "s1", "Acme", "")

            // Exception was swallowed; Room upsert already happened.
            assertThat(dao.get("u1", "s1")).isNotNull()
        }

    @Test
    fun `unfollowSeller deletes the row from DAO`() = runTest(testDispatcher) {
        val dao = FakeFollowedSellerDao()
        val repo = FollowedSellersRepositoryImpl(
            dao,
            firestoreThatSucceeds(),
            dispatcherProvider,
        )
        repo.followSeller("u1", "s1", "Acme", "")
        assertThat(dao.size()).isEqualTo(1)

        repo.unfollowSeller("u1", "s1")

        assertThat(dao.get("u1", "s1")).isNull()
        assertThat(dao.size()).isEqualTo(0)
    }

    @Test
    fun `observeFollowedSellers maps entities to domain models`() = runTest(testDispatcher) {
        val dao = FakeFollowedSellerDao()
        val repo = FollowedSellersRepositoryImpl(
            dao,
            firestoreThatSucceeds(),
            dispatcherProvider,
        )
        repo.followSeller("u1", "s1", "Acme", "logo")

        val items = repo.observeFollowedSellers("u1").first()

        assertThat(items).hasSize(1)
        assertThat(items[0].sellerId).isEqualTo("s1")
        assertThat(items[0].sellerName).isEqualTo("Acme")
        assertThat(items[0].sellerLogoUrl).isEqualTo("logo")
    }

    @Test
    fun `isFollowing reflects DAO EXISTS flow (true after follow, false after unfollow)`() =
        runTest(testDispatcher) {
            val dao = FakeFollowedSellerDao()
            val repo = FollowedSellersRepositoryImpl(
                dao,
                firestoreThatSucceeds(),
                dispatcherProvider,
            )

            assertThat(repo.isFollowing("u1", "s1").first()).isFalse()
            repo.followSeller("u1", "s1", "Acme", "")
            assertThat(repo.isFollowing("u1", "s1").first()).isTrue()
            repo.unfollowSeller("u1", "s1")
            assertThat(repo.isFollowing("u1", "s1").first()).isFalse()
        }
}
