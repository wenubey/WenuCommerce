package com.wenubey.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.entity.CategoryEntity
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
class CategoryDaoTest {

    private lateinit var db: WenuCommerceDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.categoryDao()
    }

    @After
    fun tearDown() = db.close()

    private fun cat(id: String, name: String = id, active: Boolean = true) =
        CategoryEntity(id = id, name = name, isActive = active)

    @Test
    fun `observeActiveCategories filters out inactive rows`() = runTest {
        dao.upsertAll(listOf(
            cat("c-1", active = true),
            cat("c-2", active = false),
            cat("c-3", active = true),
        ))

        dao.observeActiveCategories().test {
            val ids = awaitItem().map { it.id }
            assertThat(ids).containsExactly("c-1", "c-3")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAllCategories returns inactive too`() = runTest {
        dao.upsertAll(listOf(cat("c-1", active = true), cat("c-2", active = false)))

        dao.observeAllCategories().test {
            assertThat(awaitItem()).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert single row stores and replaces on id conflict`() = runTest {
        dao.upsert(cat("c-1", name = "Old"))
        dao.upsert(cat("c-1", name = "New"))

        dao.observeAllCategories().test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].name).isEqualTo("New")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteById removes the row`() = runTest {
        dao.upsertAll(listOf(cat("c-1"), cat("c-2")))
        dao.deleteById("c-1")

        dao.observeAllCategories().test {
            val ids = awaitItem().map { it.id }
            assertThat(ids).containsExactly("c-2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearAll empties the table`() = runTest {
        dao.upsertAll(listOf(cat("c-1"), cat("c-2")))
        dao.clearAll()

        dao.observeAllCategories().test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default subcategoriesJson is empty json array`() = runTest {
        dao.upsert(cat("c-1"))
        val list = dao.observeAllCategories().first()
        assertThat(list[0].subcategoriesJson).isEqualTo("[]")
    }
}
