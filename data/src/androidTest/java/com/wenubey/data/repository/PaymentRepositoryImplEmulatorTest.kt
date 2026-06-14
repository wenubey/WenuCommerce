package com.wenubey.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.ktx.Firebase
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.ShippingAddress
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
 * Integration tests for PaymentRepositoryImpl against the Firestore + Auth +
 * Functions emulators.
 *
 * Coverage:
 *   - Room-coupled order paths: createOrderInRoom, getOrderById,
 *     observeOrderById, and updateOrderStatus (Room + Firestore dual-write).
 *   - createPaymentIntent failure paths that the Cloud Function rejects
 *     BEFORE reaching Stripe: unauthenticated, empty cart, invalid item,
 *     unknown product.
 *
 * The Stripe-coupled happy path is NOT exercised — it requires the
 * STRIPE_SECRET_KEY secret to be loaded into the Functions emulator and a
 * real (test) Stripe account. Adding that would couple the test suite to an
 * external paid service; the pre-Stripe validation layer is what this
 * repository can usefully assert against.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PaymentRepositoryImplEmulatorTest {

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

    private lateinit var db: WenuCommerceDatabase
    private lateinit var repo: PaymentRepositoryImpl

    @Before
    fun setUp() {
        FirebaseEmulator.clearAuth()
        FirebaseEmulator.clearFirestore()
        auth.signOut()

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PaymentRepositoryImpl(db.orderDao(), firestore, dispatcherProvider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun orderId() = "order-${UUID.randomUUID().toString().take(8)}"

    private fun sampleOrder(
        id: String = orderId(),
        status: OrderStatus = OrderStatus.PENDING,
        totalAmount: Double = 42.0,
    ) = Order(
        id = id,
        userId = "u-1",
        status = status,
        subtotal = 30.0,
        shippingTotal = 12.0,
        totalAmount = totalAmount,
        currency = "USD",
        stripePaymentIntentId = "pi_test",
        shippingAddress = ShippingAddress(
            id = "a-1",
            fullName = "Test",
            line1 = "1 Main",
            city = "Istanbul",
            country = "TR",
        ),
        items = listOf(
            OrderItem(productId = "p1", productTitle = "Widget", quantity = 1, snapshotPrice = 30.0, lineTotal = 30.0)
        ),
        createdAt = "0",
        updatedAt = "0",
    )

    // -------- Room-only paths --------

    @Test
    fun createOrderInRoom_persists_and_getOrderById_returns_it(): Unit = runBlocking {
        val order = sampleOrder()

        repo.createOrderInRoom(order)

        val read = repo.getOrderById(order.id)
        assertThat(read).isNotNull()
        assertThat(read!!.id).isEqualTo(order.id)
        assertThat(read.totalAmount).isEqualTo(42.0)
        assertThat(read.items.first().productTitle).isEqualTo("Widget")
    }

    @Test
    fun getOrderById_returns_null_when_missing(): Unit = runBlocking {
        val missing = repo.getOrderById("does-not-exist")

        assertThat(missing).isNull()
    }

    @Test
    fun observeOrderById_emits_room_updates(): Unit = runBlocking {
        val order = sampleOrder()
        repo.createOrderInRoom(order)

        val emitted = repo.observeOrderById(order.id).first()

        assertThat(emitted).isNotNull()
        assertThat(emitted!!.id).isEqualTo(order.id)
    }

    @Test
    fun updateOrderStatus_writes_room_and_firestore(): Unit = runBlocking {
        val order = sampleOrder(status = OrderStatus.PENDING)
        repo.createOrderInRoom(order)
        // Seed Firestore so the dotted-path update has a doc to mutate.
        firestore.collection("orders").document(order.id)
            .set(
                mapOf(
                    "id" to order.id,
                    "userId" to order.userId,
                    "status" to OrderStatus.PENDING.name,
                )
            )
            .await()

        repo.updateOrderStatus(order.id, OrderStatus.CONFIRMED).getOrThrow()

        val roomRead = repo.getOrderById(order.id)
        assertThat(roomRead!!.status).isEqualTo(OrderStatus.CONFIRMED)
        val doc = firestore.collection("orders").document(order.id).get().await()
        assertThat(doc.getString("status")).isEqualTo(OrderStatus.CONFIRMED.name)
    }

    @Test
    fun updateOrderStatus_returns_failure_when_firestore_doc_missing(): Unit = runBlocking {
        // Room write succeeds, but Firestore update on a non-existent doc fails.
        val order = sampleOrder()
        repo.createOrderInRoom(order)

        val result = repo.updateOrderStatus(order.id, OrderStatus.CONFIRMED)

        assertThat(result.isFailure).isTrue()
    }

    // -------- createPaymentIntent failure paths --------

    @Test
    fun createPaymentIntent_unauthenticated_returns_failure(): Unit = runBlocking {
        // No sign-in — request.auth is null on the function side.
        val result = repo.createPaymentIntent(
            userId = "anonymous",
            cartItems = listOf(
                CartItem(productId = "p1", productTitle = "T", quantity = 1, snapshotPrice = 5.0)
            ),
            shippingAddress = ShippingAddress(),
        )

        assertThat(result.isFailure).isTrue()
        val cause = result.exceptionOrNull()
        assertThat(cause).isInstanceOf(FirebaseFunctionsException::class.java)
        val code = (cause as FirebaseFunctionsException).code
        assertThat(code).isEqualTo(FirebaseFunctionsException.Code.UNAUTHENTICATED)
    }

    @Test
    fun createPaymentIntent_empty_cart_returns_invalid_argument(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()

        val result = repo.createPaymentIntent(
            userId = uid,
            cartItems = emptyList(),
            shippingAddress = ShippingAddress(),
        )

        assertThat(result.isFailure).isTrue()
        val cause = result.exceptionOrNull() as FirebaseFunctionsException
        assertThat(cause.code).isEqualTo(FirebaseFunctionsException.Code.INVALID_ARGUMENT)
    }

    @Test
    fun createPaymentIntent_invalid_item_returns_invalid_argument(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()
        // quantity = 0 fails the per-item validation in the function.
        val result = repo.createPaymentIntent(
            userId = uid,
            cartItems = listOf(
                CartItem(productId = "p1", productTitle = "T", quantity = 0, snapshotPrice = 5.0)
            ),
            shippingAddress = ShippingAddress(),
        )

        assertThat(result.isFailure).isTrue()
        val cause = result.exceptionOrNull() as FirebaseFunctionsException
        assertThat(cause.code).isEqualTo(FirebaseFunctionsException.Code.INVALID_ARGUMENT)
    }

    @Test
    fun createPaymentIntent_unknown_product_returns_failed_precondition(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()
        // No products seeded — the function's lookup misses and throws
        // failed-precondition before reaching Stripe.
        val result = repo.createPaymentIntent(
            userId = uid,
            cartItems = listOf(
                CartItem(productId = "no-such-product", productTitle = "Ghost", quantity = 1, snapshotPrice = 5.0)
            ),
            shippingAddress = ShippingAddress(),
        )

        assertThat(result.isFailure).isTrue()
        val cause = result.exceptionOrNull() as FirebaseFunctionsException
        assertThat(cause.code).isEqualTo(FirebaseFunctionsException.Code.FAILED_PRECONDITION)
    }
}
