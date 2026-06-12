package com.wenubey.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.entity.WishlistItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WishlistItemDaoTest {

    private lateinit var db: WenuCommerceDatabase
    private lateinit var dao: WishlistItemDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.wishlistItemDao()
    }

    @After
    fun tearDown() = db.close()

    private fun item(
        userId: String,
        productId: String,
        addedAt: String = "2026-01-01",
    ) = WishlistItemEntity(
        userId = userId,
        productId = productId,
        productTitle = "Title $productId",
        productPrice = 9.99,
        availableStock = 5,
        addedAt = addedAt,
    )

    @Test
    fun `observeWishlistItems emits empty when user has none`() = runTest {
        dao.observeWishlistItems("u-1").test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert inserts and replaces on conflict`() = runTest {
        dao.upsert(item("u-1", "p-1", "2026-01-01"))
        dao.upsert(item("u-1", "p-1", "2026-02-01"))
        val list = dao.getItemsForUser("u-1")
        assertThat(list).hasSize(1)
        assertThat(list[0].addedAt).isEqualTo("2026-02-01")
    }

    @Test
    fun `observeWishlistItems orders by addedAt descending newest first`() = runTest {
        dao.upsert(item("u-1", "older", addedAt = "2026-01-01"))
        dao.upsert(item("u-1", "newer", addedAt = "2026-02-01"))

        dao.observeWishlistItems("u-1").test {
            val list = awaitItem()
            assertThat(list.map { it.productId }).containsExactly("newer", "older").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeWishlistItems isolates users including the anonymous empty string user`() = runTest {
        dao.upsert(item("", "anon-1"))
        dao.upsert(item("u-1", "user-1"))

        dao.observeWishlistItems("u-1").test {
            assertThat(awaitItem().map { it.productId }).containsExactly("user-1")
            cancelAndIgnoreRemainingEvents()
        }
        dao.observeWishlistItems("").test {
            assertThat(awaitItem().map { it.productId }).containsExactly("anon-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getWishlistItem returns null when missing`() = runTest {
        assertThat(dao.getWishlistItem("u-1", "missing")).isNull()
    }

    @Test
    fun `deleteItem removes one row by composite key`() = runTest {
        dao.upsert(item("u-1", "p-1"))
        dao.upsert(item("u-1", "p-2"))

        dao.deleteItem("u-1", "p-1")

        assertThat(dao.getWishlistItem("u-1", "p-1")).isNull()
        assertThat(dao.getWishlistItem("u-1", "p-2")).isNotNull()
    }

    @Test
    fun `deleteAllForUser clears only target user`() = runTest {
        dao.upsert(item("u-1", "p-1"))
        dao.upsert(item("u-2", "p-1"))

        dao.deleteAllForUser("u-1")

        assertThat(dao.getItemsForUser("u-1")).isEmpty()
        assertThat(dao.getItemsForUser("u-2")).hasSize(1)
    }

    @Test
    fun `isWishlisted reflects current state reactively`() = runTest {
        dao.isWishlisted("u-1", "p-1").test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }

        dao.upsert(item("u-1", "p-1"))

        dao.isWishlisted("u-1", "p-1").test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
