package com.wenubey.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.domain.model.product.Product
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
 * Integration tests for WishlistRepositoryImpl against the Firestore emulator.
 *
 * The repo writes Room-first and best-effort to Firestore. Anonymous wishlist
 * (empty userId) is held only in Room until [syncAnonymousOnLogin] migrates
 * it to a real account.
 *
 * Path note: this repo writes to lowercase 'users/{uid}/wishlist' — same
 * casing divergence as AddressRepositoryImpl (TB-7). Tests match the actual
 * production path.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class WishlistRepositoryImplEmulatorTest {

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

    private lateinit var db: WenuCommerceDatabase
    private lateinit var repo: WishlistRepositoryImpl

    @Before
    fun setUp() {
        FirebaseEmulator.clearFirestore()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = WishlistRepositoryImpl(db.wishlistItemDao(), firestore, dispatcherProvider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun userId() = "user-${UUID.randomUUID().toString().take(8)}"
    private fun productId() = "p-${UUID.randomUUID().toString().take(6)}"

    private fun sampleProduct(
        id: String = productId(),
        title: String = "Widget",
        price: Double = 19.99,
    ): Product = Product(
        id = id,
        title = title,
        basePrice = price,
        totalStockQuantity = 5,
    )

    private fun wishlistDoc(uid: String, productId: String) =
        firestore.collection("users").document(uid)
            .collection("wishlist").document(productId)

    // -------- toggleWishlist --------

    @Test
    fun toggleWishlist_authenticated_adds_to_room_and_firestore(): Unit = runBlocking {
        val uid = userId()
        val product = sampleProduct(title = "Mug", price = 12.5)

        repo.toggleWishlist(uid, product)

        val room = repo.observeWishlistItems(uid).first()
        assertThat(room.map { it.productId }).containsExactly(product.id)
        assertThat(room.first().productTitle).isEqualTo("Mug")
        assertThat(room.first().productPrice).isEqualTo(12.5)

        val doc = wishlistDoc(uid, product.id).get().await()
        assertThat(doc.exists()).isTrue()
        assertThat(doc.getString("productTitle")).isEqualTo("Mug")
        assertThat(doc.getDouble("productPrice")).isEqualTo(12.5)
    }

    @Test
    fun toggleWishlist_existing_item_removes_from_room_and_firestore(): Unit = runBlocking {
        val uid = userId()
        val product = sampleProduct()
        repo.toggleWishlist(uid, product)

        repo.toggleWishlist(uid, product)

        val room = repo.observeWishlistItems(uid).first()
        assertThat(room).isEmpty()
        val doc = wishlistDoc(uid, product.id).get().await()
        assertThat(doc.exists()).isFalse()
    }

    @Test
    fun toggleWishlist_null_userId_writes_to_room_only(): Unit = runBlocking {
        val product = sampleProduct()

        repo.toggleWishlist(null, product)

        // Anonymous rows live under the empty userId in Room and never reach
        // Firestore — the production guard (`if (effectiveUserId.isNotEmpty())`)
        // skips the remote write for anonymous toggles.
        val room = repo.observeWishlistItems("").first()
        assertThat(room.map { it.productId }).containsExactly(product.id)
    }

    // -------- isWishlisted --------

    @Test
    fun isWishlisted_reflects_room_state(): Unit = runBlocking {
        val uid = userId()
        val product = sampleProduct()
        assertThat(repo.isWishlisted(uid, product.id).first()).isFalse()

        repo.toggleWishlist(uid, product)
        assertThat(repo.isWishlisted(uid, product.id).first()).isTrue()

        repo.toggleWishlist(uid, product)
        assertThat(repo.isWishlisted(uid, product.id).first()).isFalse()
    }

    // -------- removeFromWishlist --------

    @Test
    fun removeFromWishlist_deletes_from_both_stores(): Unit = runBlocking {
        val uid = userId()
        val p1 = sampleProduct()
        val p2 = sampleProduct()
        repo.toggleWishlist(uid, p1)
        repo.toggleWishlist(uid, p2)

        repo.removeFromWishlist(uid, p1.id)

        val room = repo.observeWishlistItems(uid).first()
        assertThat(room.map { it.productId }).containsExactly(p2.id)
        assertThat(wishlistDoc(uid, p1.id).get().await().exists()).isFalse()
        assertThat(wishlistDoc(uid, p2.id).get().await().exists()).isTrue()
    }

    @Test
    fun removeFromWishlist_empty_userId_only_touches_room(): Unit = runBlocking {
        val product = sampleProduct()
        repo.toggleWishlist(null, product) // anonymous, Room only

        repo.removeFromWishlist("", product.id)

        val room = repo.observeWishlistItems("").first()
        assertThat(room).isEmpty()
    }

    // -------- syncAnonymousOnLogin --------

    @Test
    fun syncAnonymousOnLogin_migrates_anonymous_room_rows_to_user(): Unit = runBlocking {
        val anon1 = sampleProduct(title = "Anon-A")
        val anon2 = sampleProduct(title = "Anon-B")
        repo.toggleWishlist(null, anon1)
        repo.toggleWishlist(null, anon2)

        val uid = userId()
        repo.syncAnonymousOnLogin(uid)

        // Anonymous rows gone from Room
        val anonAfter = repo.observeWishlistItems("").first()
        assertThat(anonAfter).isEmpty()

        // Items now exist under the new uid in Room
        val migrated = repo.observeWishlistItems(uid).first()
        assertThat(migrated.map { it.productTitle }).containsExactly("Anon-A", "Anon-B")

        // And they were written to Firestore
        val snapshot = firestore.collection("users").document(uid)
            .collection("wishlist").get().await()
        assertThat(snapshot.documents.map { it.id })
            .containsExactly(anon1.id, anon2.id)
    }

    @Test
    fun syncAnonymousOnLogin_no_anonymous_rows_pulls_firestore_into_room(): Unit = runBlocking {
        val uid = userId()
        val pid = productId()
        // Seed a remote wishlist entry as if added from another device
        wishlistDoc(uid, pid).set(
            mapOf(
                "productId" to pid,
                "productTitle" to "Remote Item",
                "productPrice" to 7.5,
                "addedAt" to "2026-01-01T00:00:00Z",
            )
        ).await()

        // Pre: Room has nothing for this user
        assertThat(repo.observeWishlistItems(uid).first()).isEmpty()

        repo.syncAnonymousOnLogin(uid)

        val merged = repo.observeWishlistItems(uid).first()
        assertThat(merged.map { it.productId }).containsExactly(pid)
        assertThat(merged.first().productTitle).isEqualTo("Remote Item")
        assertThat(merged.first().productPrice).isEqualTo(7.5)
    }

    @Test
    fun syncAnonymousOnLogin_merges_anonymous_with_existing_firestore_items(): Unit = runBlocking {
        val uid = userId()
        val anonProduct = sampleProduct(title = "Local Pick")
        repo.toggleWishlist(null, anonProduct)

        val remoteId = productId()
        wishlistDoc(uid, remoteId).set(
            mapOf(
                "productId" to remoteId,
                "productTitle" to "From Other Device",
                "productPrice" to 25.0,
                "addedAt" to "2026-01-01T00:00:00Z",
            )
        ).await()

        repo.syncAnonymousOnLogin(uid)

        val merged = repo.observeWishlistItems(uid).first()
        assertThat(merged.map { it.productTitle })
            .containsExactly("Local Pick", "From Other Device")
        // Anonymous bucket is clean
        assertThat(repo.observeWishlistItems("").first()).isEmpty()
        // Firestore now contains both
        val snapshot = firestore.collection("users").document(uid)
            .collection("wishlist").get().await()
        assertThat(snapshot.documents).hasSize(2)
    }
}
