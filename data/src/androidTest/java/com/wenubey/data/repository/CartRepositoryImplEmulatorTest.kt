package com.wenubey.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.local.SyncManager
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.entity.OperationStatus
import com.wenubey.data.local.entity.OperationType
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.repository.DispatcherProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for CartRepositoryImpl against the Firestore emulator + a
 * real in-memory Room database + a test WorkManager.
 *
 * Coverage focuses on:
 *   - Room-first writes for add/update/remove/clear/restore
 *   - PendingOperationDao queue entries per mutation (with payload assertions)
 *   - Firestore-side syncAddToCart / syncUpdateQuantity / syncRemoveFromCart
 *
 * SyncWorker.enqueue is exercised through WorkManagerTestInitHelper —
 * WorkManager is initialized so the enqueue calls don't crash, but the worker
 * itself does not run in tests.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CartRepositoryImplEmulatorTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun configureSdk() {
            FirebaseEmulator.useEmulator()
            // Initialize WorkManager once for the JVM so SyncWorker.enqueue()
            // can resolve WorkManager.getInstance(context) inside the repo.
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()
            WorkManagerTestInitHelper.initializeTestWorkManager(ctx, config)
        }
    }

    private val dispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun io(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun default(): CoroutineDispatcher = Dispatchers.Unconfined
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val syncManager: SyncManager = mockk(relaxed = true)

    private lateinit var db: WenuCommerceDatabase
    private lateinit var repo: CartRepositoryImpl

    @Before
    fun setUp() {
        FirebaseEmulator.clearFirestore()
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            // Force Room's query + transaction executors to run inline. Without
            // this, suspend DAO calls dispatch through Room's background
            // executor and the *next* DAO call in the same test (running on
            // Dispatchers.Unconfined) can observe stale state from before the
            // previous commit landed. Inline execution makes the whole repo
            // operation block until Room has actually committed.
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        coEvery { syncManager.emitOfflineWriteQueued() } returns Unit
        repo = CartRepositoryImpl(
            cartItemDao = db.cartItemDao(),
            pendingOperationDao = db.pendingOperationDao(),
            syncManager = syncManager,
            firestore = firestore,
            dispatcherProvider = dispatcherProvider,
            context = ctx,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun userId() = "user-${UUID.randomUUID().toString().take(8)}"
    private fun productId() = "p-${UUID.randomUUID().toString().take(6)}"

    private fun sampleProduct(
        id: String = productId(),
        title: String = "Widget",
        price: Double = 9.99,
        stock: Int = 10,
    ) = Product(
        id = id,
        title = title,
        basePrice = price,
        totalStockQuantity = stock,
    )

    private fun cartDoc(uid: String, productId: String) =
        firestore.collection("users").document(uid)
            .collection("cart").document(productId)

    // -------- addToCart --------

    @Test
    fun addToCart_new_item_writes_room_and_queues_ADD_TO_CART(): Unit = runBlocking {
        val uid = userId()
        val product = sampleProduct(title = "Lamp", price = 25.0)

        repo.addToCart(uid, product, quantity = 2)

        val cart = repo.observeCartItems(uid).first()
        assertThat(cart).hasSize(1)
        val item = cart.first()
        assertThat(item.productId).isEqualTo(product.id)
        assertThat(item.quantity).isEqualTo(2)
        assertThat(item.snapshotPrice).isEqualTo(25.0)
        assertThat(item.productTitle).isEqualTo("Lamp")

        val pending = db.pendingOperationDao().observeAllOperations().first()
        assertThat(pending).hasSize(1)
        val op = pending.first()
        assertThat(op.operationType).isEqualTo(OperationType.ADD_TO_CART.name)
        assertThat(op.entityId).isEqualTo(uid)
        assertThat(op.status).isEqualTo(OperationStatus.PENDING.name)
        val payload = json.parseToJsonElement(op.payloadJson).jsonObject
        assertThat(payload["productId"]?.jsonPrimitive?.content).isEqualTo(product.id)
        assertThat(payload["quantity"]?.jsonPrimitive?.content).isEqualTo("2")
        assertThat(payload["snapshotPrice"]?.jsonPrimitive?.content).isEqualTo("25.0")
    }

    @Test
    fun addToCart_existing_item_increments_quantity_and_queues_UPDATE(): Unit = runBlocking {
        val uid = userId()
        val product = sampleProduct()
        repo.addToCart(uid, product, quantity = 1)

        repo.addToCart(uid, product, quantity = 3)

        val item = repo.getCartItem(uid, product.id)
        assertThat(item).isNotNull()
        assertThat(item!!.quantity).isEqualTo(4)

        // observeAllOperations() is ORDER BY createdAt DESC — newest first.
        val ops = db.pendingOperationDao().observeAllOperations().first()
        assertThat(ops).hasSize(2)
        assertThat(ops.first().operationType).isEqualTo(OperationType.UPDATE_CART_QUANTITY.name)
        assertThat(ops.last().operationType).isEqualTo(OperationType.ADD_TO_CART.name)
        val updatePayload = json.parseToJsonElement(ops.first().payloadJson).jsonObject
        assertThat(updatePayload["quantity"]?.jsonPrimitive?.content).isEqualTo("4")
    }

    // -------- updateQuantity --------

    @Test
    fun updateQuantity_positive_updates_room_and_queues_UPDATE(): Unit = runBlocking {
        val uid = userId()
        val product = sampleProduct()
        repo.addToCart(uid, product, quantity = 1)

        repo.updateQuantity(uid, product.id, newQuantity = 7)

        val item = repo.getCartItem(uid, product.id)
        assertThat(item!!.quantity).isEqualTo(7)
        val ops = db.pendingOperationDao().observeAllOperations().first()
        // Newest first (DESC); UPDATE was queued last.
        assertThat(ops.first().operationType).isEqualTo(OperationType.UPDATE_CART_QUANTITY.name)
        val payload = json.parseToJsonElement(ops.first().payloadJson).jsonObject
        assertThat(payload["quantity"]?.jsonPrimitive?.content).isEqualTo("7")
    }

    @Test
    fun updateQuantity_zero_or_negative_falls_through_to_remove(): Unit = runBlocking {
        val uid = userId()
        val product = sampleProduct()
        repo.addToCart(uid, product, quantity = 2)

        repo.updateQuantity(uid, product.id, newQuantity = 0)

        assertThat(repo.getCartItem(uid, product.id)).isNull()
        val ops = db.pendingOperationDao().observeAllOperations().first()
        assertThat(ops.first().operationType).isEqualTo(OperationType.REMOVE_FROM_CART.name)
        assertThat(ops.first().payloadJson).isEqualTo(product.id)
    }

    // -------- removeFromCart --------

    @Test
    fun removeFromCart_deletes_room_row_and_queues_REMOVE(): Unit = runBlocking {
        val uid = userId()
        val a = sampleProduct()
        val b = sampleProduct()
        repo.addToCart(uid, a, quantity = 1)
        repo.addToCart(uid, b, quantity = 2)

        repo.removeFromCart(uid, a.id)

        assertThat(repo.getCartItem(uid, a.id)).isNull()
        assertThat(repo.getCartItem(uid, b.id)).isNotNull()
        val newestOp = db.pendingOperationDao().observeAllOperations().first().first()
        assertThat(newestOp.operationType).isEqualTo(OperationType.REMOVE_FROM_CART.name)
        assertThat(newestOp.payloadJson).isEqualTo(a.id)
    }

    // -------- clearCart --------

    @Test
    fun clearCart_empties_room_without_queueing_operations(): Unit = runBlocking {
        val uid = userId()
        repo.addToCart(uid, sampleProduct(), quantity = 1)
        repo.addToCart(uid, sampleProduct(), quantity = 2)
        val opsBefore = db.pendingOperationDao().observeAllOperations().first().size

        repo.clearCart(uid)

        assertThat(repo.observeCartItems(uid).first()).isEmpty()
        // No new ops queued by clearCart (used on checkout success).
        val opsAfter = db.pendingOperationDao().observeAllOperations().first().size
        assertThat(opsAfter).isEqualTo(opsBefore)
    }

    // -------- restoreCartItem --------

    @Test
    fun restoreCartItem_writes_room_and_queues_ADD_TO_CART(): Unit = runBlocking {
        val uid = userId()
        val restored = CartItem(
            productId = productId(),
            productTitle = "Restored",
            productImageUrl = "",
            quantity = 4,
            snapshotPrice = 11.0,
            availableStock = 10,
            isProductDeleted = false,
            addedAt = "2026-01-01T00:00:00Z",
        )

        repo.restoreCartItem(uid, restored)

        val cart = repo.observeCartItems(uid).first()
        assertThat(cart).hasSize(1)
        assertThat(cart.first().productTitle).isEqualTo("Restored")
        assertThat(cart.first().addedAt).isEqualTo("2026-01-01T00:00:00Z")

        val op = db.pendingOperationDao().observeAllOperations().first().first()
        assertThat(op.operationType).isEqualTo(OperationType.ADD_TO_CART.name)
        val payload = json.parseToJsonElement(op.payloadJson).jsonObject
        assertThat(payload["productId"]?.jsonPrimitive?.content).isEqualTo(restored.productId)
        assertThat(payload["quantity"]?.jsonPrimitive?.content).isEqualTo("4")
    }

    // -------- observeUniqueProductCount --------

    @Test
    fun observeUniqueProductCount_tracks_distinct_products(): Unit = runBlocking {
        val uid = userId()
        assertThat(repo.observeUniqueProductCount(uid).first()).isEqualTo(0)

        repo.addToCart(uid, sampleProduct(), quantity = 1)
        repo.addToCart(uid, sampleProduct(), quantity = 1)
        assertThat(repo.observeUniqueProductCount(uid).first()).isEqualTo(2)

        repo.addToCart(uid, sampleProduct().copy(), quantity = 1) // new product
        assertThat(repo.observeUniqueProductCount(uid).first()).isEqualTo(3)
    }

    // -------- getCartItem --------

    @Test
    fun getCartItem_returns_existing_and_null_for_missing(): Unit = runBlocking {
        val uid = userId()
        val product = sampleProduct(title = "Saved")
        repo.addToCart(uid, product, quantity = 2)

        val found = repo.getCartItem(uid, product.id)
        assertThat(found).isNotNull()
        assertThat(found!!.productTitle).isEqualTo("Saved")
        assertThat(found.quantity).isEqualTo(2)

        val missing = repo.getCartItem(uid, "nope")
        assertThat(missing).isNull()
    }

    // -------- sync* (Firestore round-trip) --------

    @Test
    fun syncAddToCart_writes_cart_doc_under_user_subcollection(): Unit = runBlocking {
        val uid = userId()
        val pid = productId()

        repo.syncAddToCart(uid, pid, quantity = 3, snapshotPrice = 8.0)

        val doc = cartDoc(uid, pid).get().await()
        assertThat(doc.exists()).isTrue()
        assertThat(doc.getString("productId")).isEqualTo(pid)
        assertThat(doc.getLong("quantity")).isEqualTo(3L)
        assertThat(doc.getDouble("snapshotPrice")).isEqualTo(8.0)
        assertThat(doc.getString("updatedAt")).isNotEmpty()
    }

    @Test
    fun syncUpdateQuantity_patches_existing_doc(): Unit = runBlocking {
        val uid = userId()
        val pid = productId()
        repo.syncAddToCart(uid, pid, quantity = 1, snapshotPrice = 5.0)

        repo.syncUpdateQuantity(uid, pid, quantity = 9)

        val doc = cartDoc(uid, pid).get().await()
        assertThat(doc.getLong("quantity")).isEqualTo(9L)
        // snapshotPrice from initial add must be preserved by the update.
        assertThat(doc.getDouble("snapshotPrice")).isEqualTo(5.0)
    }

    @Test
    fun syncRemoveFromCart_deletes_doc(): Unit = runBlocking {
        val uid = userId()
        val pid = productId()
        repo.syncAddToCart(uid, pid, quantity = 1, snapshotPrice = 3.0)

        repo.syncRemoveFromCart(uid, pid)

        val doc = cartDoc(uid, pid).get().await()
        assertThat(doc.exists()).isFalse()
    }
}
