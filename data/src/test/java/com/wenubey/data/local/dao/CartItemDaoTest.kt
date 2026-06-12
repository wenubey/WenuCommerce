package com.wenubey.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.entity.CartItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CartItemDaoTest {

    private lateinit var db: WenuCommerceDatabase
    private lateinit var dao: CartItemDao

    private val userA = "user-A"
    private val userB = "user-B"

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cartItemDao()
    }

    @After
    fun tearDown() = db.close()

    private fun item(
        userId: String = userA,
        productId: String,
        quantity: Int = 1,
        addedAt: String = "2026-01-01T00:00:00Z",
    ) = CartItemEntity(
        userId = userId,
        productId = productId,
        productTitle = "Title $productId",
        productImageUrl = "",
        quantity = quantity,
        snapshotPrice = 9.99,
        availableStock = 10,
        isProductDeleted = false,
        addedAt = addedAt,
        updatedAt = addedAt,
    )

    @Test
    fun `observeCartItems emits empty list when user has no items`() = runTest {
        dao.observeCartItems(userA).test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert inserts a new item and emits it`() = runTest {
        dao.upsert(item(productId = "p-1"))
        dao.observeCartItems(userA).test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].productId).isEqualTo("p-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert with same userId and productId replaces the existing row`() = runTest {
        dao.upsert(item(productId = "p-1", quantity = 1))
        dao.upsert(item(productId = "p-1", quantity = 5))

        val stored = dao.getCartItem(userA, "p-1")
        assertThat(stored).isNotNull()
        assertThat(stored!!.quantity).isEqualTo(5)
    }

    @Test
    fun `observeCartItems orders by addedAt ascending`() = runTest {
        dao.upsert(item(productId = "later", addedAt = "2026-02-01"))
        dao.upsert(item(productId = "earlier", addedAt = "2026-01-01"))

        dao.observeCartItems(userA).test {
            val list = awaitItem()
            assertThat(list.map { it.productId }).containsExactly("earlier", "later").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeCartItems isolates users`() = runTest {
        dao.upsert(item(userId = userA, productId = "a-1"))
        dao.upsert(item(userId = userB, productId = "b-1"))

        dao.observeCartItems(userA).test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].productId).isEqualTo("a-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeUniqueProductCount reflects distinct productIds`() = runTest {
        dao.upsert(item(productId = "p-1"))
        dao.upsert(item(productId = "p-2"))
        dao.upsert(item(productId = "p-1", quantity = 7)) // same key as first

        dao.observeUniqueProductCount(userA).test {
            assertThat(awaitItem()).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCartItem returns null when no row matches`() = runTest {
        assertThat(dao.getCartItem(userA, "missing")).isNull()
    }

    @Test
    fun `deleteItem removes exactly one row`() = runTest {
        dao.upsert(item(productId = "p-1"))
        dao.upsert(item(productId = "p-2"))

        dao.deleteItem(userA, "p-1")

        assertThat(dao.getCartItem(userA, "p-1")).isNull()
        assertThat(dao.getCartItem(userA, "p-2")).isNotNull()
    }

    @Test
    fun `clearCart removes only the target user's rows`() = runTest {
        dao.upsert(item(userId = userA, productId = "a-1"))
        dao.upsert(item(userId = userA, productId = "a-2"))
        dao.upsert(item(userId = userB, productId = "b-1"))

        dao.clearCart(userA)

        dao.observeCartItems(userA).test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
        dao.observeCartItems(userB).test {
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateQuantity changes quantity and updatedAt without touching other fields`() = runTest {
        dao.upsert(item(productId = "p-1", quantity = 1))

        dao.updateQuantity(userA, "p-1", quantity = 9, updatedAt = "2026-03-01")

        val row = dao.getCartItem(userA, "p-1")!!
        assertThat(row.quantity).isEqualTo(9)
        assertThat(row.updatedAt).isEqualTo("2026-03-01")
        assertThat(row.snapshotPrice).isEqualTo(9.99) // unchanged
    }

    @Test
    fun `updateProductStatus toggles isProductDeleted and availableStock`() = runTest {
        dao.upsert(item(productId = "p-1"))

        dao.updateProductStatus(userA, "p-1", deleted = true, stock = 0)

        val row = dao.getCartItem(userA, "p-1")!!
        assertThat(row.isProductDeleted).isTrue()
        assertThat(row.availableStock).isEqualTo(0)
    }
}
