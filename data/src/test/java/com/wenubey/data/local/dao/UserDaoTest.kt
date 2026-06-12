package com.wenubey.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.entity.UserEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserDaoTest {

    private lateinit var db: WenuCommerceDatabase
    private lateinit var dao: UserDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.userDao()
    }

    @After
    fun tearDown() = db.close()

    private fun user(id: String, name: String = "Alice", role: String = "CUSTOMER") = UserEntity(
        id = id,
        role = role,
        name = name,
        email = "$id@example.com",
    )

    @Test
    fun `observeCurrentUser emits null when no user stored`() = runTest {
        dao.observeCurrentUser().test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCurrentUser returns null when no user stored`() = runTest {
        assertThat(dao.getCurrentUser()).isNull()
    }

    @Test
    fun `upsert stores a single user and emits it`() = runTest {
        dao.upsert(user("u-1"))

        dao.observeCurrentUser().test {
            val u = awaitItem()
            assertThat(u).isNotNull()
            assertThat(u!!.id).isEqualTo("u-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert with same id replaces the existing row`() = runTest {
        dao.upsert(user("u-1", name = "Alice"))
        dao.upsert(user("u-1", name = "Alicia"))

        val u = dao.getCurrentUser()
        assertThat(u!!.name).isEqualTo("Alicia")
    }

    @Test
    fun `clearAll removes the stored user`() = runTest {
        dao.upsert(user("u-1"))
        dao.clearAll()

        assertThat(dao.getCurrentUser()).isNull()
    }

    @Test
    fun `default json columns hold valid empty representations`() = runTest {
        // Pin the wire format the converter side relies on: nullable BusinessInfo
        // defaults to null, list columns to "[]". A schema migration that
        // changes these defaults to "" would break json parsing on read.
        dao.upsert(UserEntity(id = "u-1"))
        val row = dao.getCurrentUser()!!
        assertThat(row.purchaseHistoryJson).isEqualTo("[]")
        assertThat(row.signedDevicesJson).isEqualTo("[]")
        assertThat(row.productsJson).isEqualTo("[]")
        assertThat(row.businessInfoJson).isNull()
    }
}
