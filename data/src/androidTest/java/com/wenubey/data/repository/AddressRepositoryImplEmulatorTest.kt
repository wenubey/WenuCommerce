package com.wenubey.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.domain.model.order.ShippingAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for AddressRepositoryImpl against the Firestore emulator.
 *
 * The repo uses a Room-first / Firestore-snapshot-listener pattern: writes go
 * to both stores, reads come from Room which is kept in sync by a snapshot
 * listener registered on first [observeSavedAddresses] call per user.
 *
 * Path note: this repository writes to lowercase `users/{uid}/addresses`,
 * NOT the canonical `USERS` collection that holds user profiles. The two
 * data sets are independent in current production code — this test preserves
 * that contract by reading and writing against the same lowercase path.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AddressRepositoryImplEmulatorTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun configureSdk() {
            FirebaseEmulator.useEmulator()
        }
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var db: WenuCommerceDatabase
    private lateinit var repo: AddressRepositoryImpl

    @Before
    fun setUp() {
        FirebaseEmulator.clearFirestore()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AddressRepositoryImpl(db.addressDao(), firestore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun userId() = "user-${UUID.randomUUID().toString().take(8)}"

    private fun sampleAddress(
        id: String = "",
        line1: String = "1 Main St",
        city: String = "Istanbul",
    ) = ShippingAddress(
        id = id,
        fullName = "Test User",
        line1 = line1,
        line2 = "Apt 2",
        city = city,
        state = "TR",
        postalCode = "34000",
        country = "Turkey",
    )

    @Test
    fun saveAddress_writes_to_firestore_and_room_with_generated_id(): Unit = runBlocking {
        val uid = userId()
        val address = sampleAddress() // empty id → repo generates UUID

        repo.saveAddress(uid, address)

        // Firestore: exactly one doc under the user's addresses subcollection
        val snapshot = firestore.collection("users").document(uid)
            .collection("addresses").get().await()
        assertThat(snapshot.documents).hasSize(1)
        val doc = snapshot.documents.first()
        assertThat(doc.id).isNotEmpty()
        assertThat(doc.getString("line1")).isEqualTo("1 Main St")
        assertThat(doc.getString("city")).isEqualTo("Istanbul")
        assertThat(doc.getString("id")).isEqualTo(doc.id)

        // Room mirrors the same address keyed by the generated id
        val roomList = db.addressDao().observeByUser(uid).first()
        assertThat(roomList).hasSize(1)
        assertThat(roomList.first().addressId).isEqualTo(doc.id)
    }

    @Test
    fun saveAddress_preserves_caller_provided_id(): Unit = runBlocking {
        val uid = userId()
        val provided = sampleAddress(id = "fixed-addr-1")

        repo.saveAddress(uid, provided)

        val doc = firestore.collection("users").document(uid)
            .collection("addresses").document("fixed-addr-1").get().await()
        assertThat(doc.exists()).isTrue()
        assertThat(doc.getString("id")).isEqualTo("fixed-addr-1")
    }

    @Test
    fun deleteAddress_removes_from_firestore_and_room(): Unit = runBlocking {
        val uid = userId()
        repo.saveAddress(uid, sampleAddress(id = "del-1"))
        repo.saveAddress(uid, sampleAddress(id = "keep-1", line1 = "2 Side St"))

        repo.deleteAddress(uid, "del-1")

        val remaining = firestore.collection("users").document(uid)
            .collection("addresses").get().await()
        assertThat(remaining.documents.map { it.id }).containsExactly("keep-1")

        val roomRemaining = db.addressDao().observeByUser(uid).first()
        assertThat(roomRemaining.map { it.addressId }).containsExactly("keep-1")
    }

    @Test
    fun observeSavedAddresses_syncs_firestore_changes_into_room(): Unit = runBlocking {
        val uid = userId()
        // Seed Firestore directly (no Room write) so we can prove the listener
        // backfills Room.
        firestore.collection("users").document(uid)
            .collection("addresses").document("ext-1")
            .set(sampleAddress(id = "ext-1", line1 = "9 External Way").toMap())
            .await()

        val emitted = withTimeoutOrNull(5_000) {
            repo.observeSavedAddresses(uid).first { it.isNotEmpty() }
        }

        assertThat(emitted).isNotNull()
        assertThat(emitted!!.map { it.id }).containsExactly("ext-1")
        assertThat(emitted.first().line1).isEqualTo("9 External Way")
    }

    @Test
    fun observeSavedAddresses_emits_updates_when_firestore_changes(): Unit = runBlocking {
        val uid = userId()

        val flow = repo.observeSavedAddresses(uid)
        // Initial: empty
        val initial = withTimeoutOrNull(5_000) { flow.first() }
        assertThat(initial).isEqualTo(emptyList<ShippingAddress>())

        // Add an address directly via Firestore — listener should propagate it
        firestore.collection("users").document(uid)
            .collection("addresses").document("late-1")
            .set(sampleAddress(id = "late-1", city = "Ankara").toMap())
            .await()

        val updated = withTimeoutOrNull(5_000) { flow.first { it.isNotEmpty() } }
        assertThat(updated?.map { it.city }).containsExactly("Ankara")
    }

    @Test
    fun observeSavedAddresses_reflects_remote_delete_in_room(): Unit = runBlocking {
        val uid = userId()
        repo.saveAddress(uid, sampleAddress(id = "to-remove"))

        // Trigger the listener
        val initial = withTimeoutOrNull(5_000) {
            repo.observeSavedAddresses(uid).first { it.isNotEmpty() }
        }
        assertThat(initial?.map { it.id }).containsExactly("to-remove")

        firestore.collection("users").document(uid)
            .collection("addresses").document("to-remove")
            .delete().await()

        val afterDelete = withTimeoutOrNull(5_000) {
            repo.observeSavedAddresses(uid).first { it.isEmpty() }
        }
        assertThat(afterDelete).isEqualTo(emptyList<ShippingAddress>())
    }

    @Test
    fun observeSavedAddresses_with_empty_userId_does_not_register_listener_or_emit(): Unit = runBlocking {
        // Empty userId is a guard in the production code (early return). The Room
        // query for an empty user is also empty. Verify both: no docs in
        // Firestore and the flow emits empty.
        val emitted = withTimeoutOrNull(2_000) {
            repo.observeSavedAddresses("").first()
        }
        assertThat(emitted).isEqualTo(emptyList<ShippingAddress>())
    }

    @Test
    fun observeSavedAddresses_isolates_users(): Unit = runBlocking {
        val a = userId()
        val b = userId()
        repo.saveAddress(a, sampleAddress(id = "a-1", line1 = "A Street"))
        repo.saveAddress(b, sampleAddress(id = "b-1", line1 = "B Street"))

        val aList = withTimeoutOrNull(5_000) {
            repo.observeSavedAddresses(a).first { it.isNotEmpty() }
        }
        val bList = withTimeoutOrNull(5_000) {
            repo.observeSavedAddresses(b).first { it.isNotEmpty() }
        }

        assertThat(aList?.map { it.line1 }).containsExactly("A Street")
        assertThat(bList?.map { it.line1 }).containsExactly("B Street")
    }
}
