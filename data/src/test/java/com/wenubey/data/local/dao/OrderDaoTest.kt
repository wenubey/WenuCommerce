package com.wenubey.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OrderDaoTest {

    private lateinit var db: WenuCommerceDatabase
    private lateinit var dao: OrderDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.orderDao()
    }

    @After
    fun tearDown() = db.close()

    private fun order(
        id: String,
        userId: String = "u-1",
        status: String = "PENDING",
        createdAt: String = "2026-01-01",
    ) = OrderEntity(
        id = id,
        userId = userId,
        status = status,
        subtotal = 100.0,
        totalAmount = 100.0,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `upsert REPLACE inserts new row`() = runTest {
        dao.upsert(order("o-1"))
        assertThat(dao.getOrderById("o-1")).isNotNull()
    }

    @Test
    fun `upsert REPLACE replaces existing row on id conflict`() = runTest {
        dao.upsert(order("o-1", status = "PENDING"))
        dao.upsert(order("o-1", status = "DELIVERED"))

        assertThat(dao.getOrderById("o-1")!!.status).isEqualTo("DELIVERED")
    }

    @Test
    fun `getOrderById returns null when missing`() = runTest {
        assertThat(dao.getOrderById("missing")).isNull()
    }

    @Test
    fun `observeOrderById emits the current row and null when not present`() = runTest {
        dao.observeOrderById("o-1").test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        dao.upsert(order("o-1"))

        dao.observeOrderById("o-1").test {
            val row = awaitItem()
            assertThat(row).isNotNull()
            assertThat(row!!.id).isEqualTo("o-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeOrdersByUser orders by createdAt descending`() = runTest {
        dao.upsert(order("older", createdAt = "2026-01-01"))
        dao.upsert(order("newer", createdAt = "2026-02-01"))

        val list = dao.observeOrdersByUser("u-1").first()
        assertThat(list.map { it.id }).containsExactly("newer", "older").inOrder()
    }

    @Test
    fun `observeOrdersByUser isolates users`() = runTest {
        dao.upsert(order("o-1", userId = "u-1"))
        dao.upsert(order("o-2", userId = "u-2"))

        val list = dao.observeOrdersByUser("u-1").first()
        assertThat(list).hasSize(1)
        assertThat(list[0].id).isEqualTo("o-1")
    }

    @Test
    fun `updateOrderStatus changes status and updatedAt without touching other columns`() = runTest {
        dao.upsert(order("o-1", status = "PENDING"))

        dao.updateOrderStatus("o-1", "SHIPPED", "2026-03-01")

        val row = dao.getOrderById("o-1")!!
        assertThat(row.status).isEqualTo("SHIPPED")
        assertThat(row.updatedAt).isEqualTo("2026-03-01")
        assertThat(row.subtotal).isEqualTo(100.0) // unchanged
    }
}
