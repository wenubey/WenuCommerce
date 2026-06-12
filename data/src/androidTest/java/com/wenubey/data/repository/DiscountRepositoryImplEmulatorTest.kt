package com.wenubey.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.FirebaseEmulator
import com.wenubey.domain.model.discount.DiscountCode
import com.wenubey.domain.model.discount.DiscountType
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for DiscountRepositoryImpl against the Firestore emulator.
 *
 * **Prerequisites** (the runner is responsible — these tests fail loudly otherwise):
 *  1. Firebase emulators running:  firebase emulators:start --only firestore
 *  2. Connected AVD or device      ./gradlew :data:connectedDebugAndroidTest
 *
 * Each test isolates state by clearing the Firestore emulator in @Before. Tests
 * here exercise the SDK boundary that JVM unit tests cannot reach: real
 * snapshot listeners, real serverTimestamp updates, real document IDs.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DiscountRepositoryImplEmulatorTest {

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
    private val repo by lazy { DiscountRepositoryImpl(firestore, dispatcherProvider) }

    @Before
    fun resetState() {
        FirebaseEmulator.clearFirestore()
    }

    private fun sampleCode(code: String = "SAVE20", seller: String = "seller-1") = DiscountCode(
        code = code,
        type = DiscountType.PERCENTAGE,
        value = 20.0,
        maxDiscountCap = 50.0,
        minimumOrderAmount = 25.0,
        targetProductIds = listOf("p-1", "p-2"),
        sellerId = seller,
        usageLimit = 100,
        isActive = true,
    )

    @Test
    fun create_writes_document_with_uppercased_code_and_zero_usage_count(): Unit = runBlocking {
        val result = repo.createDiscountCode(sampleCode(code = "save20"))
        assertThat(result.isSuccess).isTrue()

        val doc = withTimeout(5_000) {
            firestore.collection("discountCodes").document("SAVE20").get().await()
        }
        assertThat(doc.exists()).isTrue()
        assertThat(doc.getString("code")).isEqualTo("SAVE20")
        assertThat(doc.getString("type")).isEqualTo("PERCENTAGE")
        assertThat(doc.getDouble("value")).isEqualTo(20.0)
        assertThat(doc.getLong("usageCount")).isEqualTo(0)
        assertThat(doc.getBoolean("isActive")).isTrue()
        assertThat(doc.getString("sellerId")).isEqualTo("seller-1")
    }

    @Test
    fun observe_emits_created_documents_filtered_by_seller_id(): Unit = runBlocking {
        repo.createDiscountCode(sampleCode(code = "ONE", seller = "seller-A")).getOrThrow()
        repo.createDiscountCode(sampleCode(code = "TWO", seller = "seller-A")).getOrThrow()
        repo.createDiscountCode(sampleCode(code = "OTHER", seller = "seller-B")).getOrThrow()

        val emissions = withTimeout(5_000) {
            // Wait for an emission that contains both seller-A codes (the
            // emulator may emit transiently with fewer documents while the
            // upserts are being applied).
            repo.observeDiscountCodes("seller-A")
                .first { it.size >= 2 }
        }
        assertThat(emissions.map { it.code }).containsExactly("ONE", "TWO")
    }

    @Test
    fun observe_with_empty_seller_id_returns_all_codes(): Unit = runBlocking {
        repo.createDiscountCode(sampleCode(code = "A", seller = "seller-A")).getOrThrow()
        repo.createDiscountCode(sampleCode(code = "B", seller = "seller-B")).getOrThrow()

        val emissions = withTimeout(5_000) {
            repo.observeDiscountCodes("").first { it.size >= 2 }
        }
        assertThat(emissions.map { it.code }).containsExactly("A", "B")
    }

    @Test
    fun update_overwrites_mutable_fields_but_preserves_usage_count_and_created_at(): Unit = runBlocking {
        repo.createDiscountCode(sampleCode(code = "EDIT")).getOrThrow()
        val before = firestore.collection("discountCodes").document("EDIT").get().await()
        val originalCreatedAt = before.getString("createdAt")
        assertThat(before.getLong("usageCount")).isEqualTo(0)

        val updated = sampleCode(code = "EDIT").copy(
            value = 30.0,
            usageLimit = 50,
            isActive = false,
        )
        repo.updateDiscountCode(updated).getOrThrow()

        val after = firestore.collection("discountCodes").document("EDIT").get().await()
        assertThat(after.getDouble("value")).isEqualTo(30.0)
        assertThat(after.getLong("usageLimit")).isEqualTo(50)
        assertThat(after.getBoolean("isActive")).isFalse()
        // usageCount must not be reset
        assertThat(after.getLong("usageCount")).isEqualTo(0)
        // createdAt must not be overwritten by updateDiscountCode (only updatedAt changes)
        assertThat(after.getString("createdAt")).isEqualTo(originalCreatedAt)
        assertThat(after.getString("updatedAt")).isNotEqualTo(originalCreatedAt)
    }

    @Test
    fun delete_removes_the_document(): Unit = runBlocking {
        repo.createDiscountCode(sampleCode(code = "DEL")).getOrThrow()
        val before = firestore.collection("discountCodes").document("DEL").get().await()
        assertThat(before.exists()).isTrue()

        repo.deleteDiscountCode("del").getOrThrow() // case-insensitive

        val after = firestore.collection("discountCodes").document("DEL").get().await()
        assertThat(after.exists()).isFalse()
    }

    @Test
    fun deactivate_flips_isActive_to_false_without_other_field_changes(): Unit = runBlocking {
        repo.createDiscountCode(sampleCode(code = "OFF")).getOrThrow()
        val before = firestore.collection("discountCodes").document("OFF").get().await()
        val originalValue = before.getDouble("value")
        assertThat(before.getBoolean("isActive")).isTrue()

        repo.deactivateDiscountCode("off").getOrThrow() // case-insensitive

        val after = firestore.collection("discountCodes").document("OFF").get().await()
        assertThat(after.getBoolean("isActive")).isFalse()
        assertThat(after.getDouble("value")).isEqualTo(originalValue) // untouched
    }

    @Test
    fun snapshot_listener_emits_updates_after_external_change(): Unit = runBlocking {
        repo.createDiscountCode(sampleCode(code = "WATCH", seller = "seller-X")).getOrThrow()

        // First emission: the initial state.
        val initial = withTimeout(5_000) {
            repo.observeDiscountCodes("seller-X").first { it.isNotEmpty() }
        }
        assertThat(initial.single().isActive).isTrue()

        // External mutation through a different path.
        repo.deactivateDiscountCode("WATCH").getOrThrow()

        // Subsequent emission reflects the change.
        val after = withTimeout(5_000) {
            repo.observeDiscountCodes("seller-X").first { snapshot ->
                snapshot.isNotEmpty() && !snapshot.single().isActive
            }
        }
        assertThat(after.single().isActive).isFalse()
    }
}
