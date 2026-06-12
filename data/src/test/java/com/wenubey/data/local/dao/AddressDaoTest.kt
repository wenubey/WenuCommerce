package com.wenubey.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.entity.AddressEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddressDaoTest {

    private lateinit var db: WenuCommerceDatabase
    private lateinit var dao: AddressDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.addressDao()
    }

    @After
    fun tearDown() = db.close()

    private fun addr(userId: String, addressId: String, fullName: String) =
        AddressEntity(
            userId = userId,
            addressId = addressId,
            fullName = fullName,
            line1 = "1 St",
            city = "Istanbul",
        )

    @Test
    fun `observeByUser emits empty when none stored`() = runTest {
        dao.observeByUser("u-1").test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert inserts a new address`() = runTest {
        dao.upsert(addr("u-1", "a-1", "Alice"))
        dao.observeByUser("u-1").test {
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert REPLACE replaces existing row on conflict`() = runTest {
        dao.upsert(addr("u-1", "a-1", "Alice"))
        dao.upsert(addr("u-1", "a-1", "Alicia"))

        dao.observeByUser("u-1").test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].fullName).isEqualTo("Alicia")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsertAll batches inserts`() = runTest {
        dao.upsertAll(listOf(
            addr("u-1", "a-1", "Alice"),
            addr("u-1", "a-2", "Bob"),
            addr("u-1", "a-3", "Carol"),
        ))
        dao.observeByUser("u-1").test {
            assertThat(awaitItem()).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeByUser orders by fullName ascending`() = runTest {
        dao.upsert(addr("u-1", "a-1", "Carol"))
        dao.upsert(addr("u-1", "a-2", "Alice"))
        dao.upsert(addr("u-1", "a-3", "Bob"))

        dao.observeByUser("u-1").test {
            val list = awaitItem()
            assertThat(list.map { it.fullName }).containsExactly("Alice", "Bob", "Carol").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeByUser isolates users`() = runTest {
        dao.upsert(addr("u-1", "a-1", "Alice"))
        dao.upsert(addr("u-2", "a-1", "Bob"))

        dao.observeByUser("u-1").test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].fullName).isEqualTo("Alice")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete removes one row by composite key`() = runTest {
        dao.upsert(addr("u-1", "a-1", "Alice"))
        dao.upsert(addr("u-1", "a-2", "Bob"))

        dao.delete("u-1", "a-1")

        dao.observeByUser("u-1").test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].addressId).isEqualTo("a-2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAllForUser removes only target user's rows`() = runTest {
        dao.upsert(addr("u-1", "a-1", "Alice"))
        dao.upsert(addr("u-1", "a-2", "Bob"))
        dao.upsert(addr("u-2", "a-1", "Other"))

        dao.deleteAllForUser("u-1")

        dao.observeByUser("u-1").test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
        dao.observeByUser("u-2").test {
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
