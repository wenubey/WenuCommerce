package com.wenubey.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.entity.ProductEntity
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
class ProductDaoTest {

    private lateinit var db: WenuCommerceDatabase
    private lateinit var dao: ProductDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
    }

    @After
    fun tearDown() = db.close()

    private fun product(
        id: String,
        title: String = "Title $id",
        status: String = "ACTIVE",
        categoryId: String = "cat-1",
        subcategoryId: String = "sub-1",
        sellerId: String = "seller-1",
        categoryName: String = "Clothing",
        searchKeywordsJson: String = "[]",
    ) = ProductEntity(
        id = id,
        title = title,
        status = status,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        sellerId = sellerId,
        categoryName = categoryName,
        searchKeywordsJson = searchKeywordsJson,
    )

    @Test
    fun `observeAllActiveProducts excludes DRAFT and PENDING and SUSPENDED rows`() = runTest {
        dao.upsertAll(listOf(
            product("p-1", status = "ACTIVE"),
            product("p-2", status = "DRAFT"),
            product("p-3", status = "PENDING_REVIEW"),
            product("p-4", status = "SUSPENDED"),
            product("p-5", status = "ACTIVE"),
        ))

        val list = dao.observeAllActiveProducts().first()
        assertThat(list.map { it.id }).containsExactly("p-1", "p-5")
    }

    @Test
    fun `observeActiveProductsByCategory filters by category and status`() = runTest {
        dao.upsertAll(listOf(
            product("p-1", categoryId = "cat-A", status = "ACTIVE"),
            product("p-2", categoryId = "cat-A", status = "DRAFT"),
            product("p-3", categoryId = "cat-B", status = "ACTIVE"),
        ))

        val list = dao.observeActiveProductsByCategory("cat-A").first()
        assertThat(list.map { it.id }).containsExactly("p-1")
    }

    @Test
    fun `observeActiveProductsByCategoryAndSubcategory narrows further`() = runTest {
        dao.upsertAll(listOf(
            product("p-1", categoryId = "cat-A", subcategoryId = "sub-1"),
            product("p-2", categoryId = "cat-A", subcategoryId = "sub-2"),
            product("p-3", categoryId = "cat-B", subcategoryId = "sub-1"),
        ))

        val list = dao.observeActiveProductsByCategoryAndSubcategory("cat-A", "sub-1").first()
        assertThat(list.map { it.id }).containsExactly("p-1")
    }

    @Test
    fun `observeSellerProducts ignores status filter (returns all statuses)`() = runTest {
        dao.upsertAll(listOf(
            product("p-1", sellerId = "s-1", status = "ACTIVE"),
            product("p-2", sellerId = "s-1", status = "DRAFT"),
            product("p-3", sellerId = "s-2", status = "ACTIVE"),
        ))

        val list = dao.observeSellerProducts("s-1").first()
        assertThat(list.map { it.id }).containsExactly("p-1", "p-2")
    }

    @Test
    fun `observeProductsByStatus filters strictly by status`() = runTest {
        dao.upsertAll(listOf(
            product("p-1", status = "DRAFT"),
            product("p-2", status = "ACTIVE"),
            product("p-3", status = "DRAFT"),
        ))

        val list = dao.observeProductsByStatus("DRAFT").first()
        assertThat(list.map { it.id }).containsExactly("p-1", "p-3")
    }

    @Test
    fun `searchActiveProducts matches title category and search keywords`() = runTest {
        dao.upsertAll(listOf(
            product("p-1", title = "Red Shirt", categoryName = "Clothing"),
            product("p-2", title = "Blue Hat", categoryName = "Accessories"),
            product("p-3", title = "Sock", categoryName = "Accessories", searchKeywordsJson = "[\"red\",\"warm\"]"),
            product("p-4", title = "Pen", categoryName = "Stationery", status = "DRAFT"),
        ))

        val list = dao.searchActiveProducts("red")
        assertThat(list.map { it.id }).containsExactly("p-1", "p-3")
    }

    @Test
    fun `searchActiveProducts respects status filter`() = runTest {
        dao.upsertAll(listOf(
            product("p-1", title = "Red Shirt", status = "ACTIVE"),
            product("p-2", title = "Red Hat", status = "DRAFT"),
        ))

        val list = dao.searchActiveProducts("Red")
        assertThat(list.map { it.id }).containsExactly("p-1")
    }

    @Test
    fun `searchAllProducts returns matches regardless of status`() = runTest {
        dao.upsertAll(listOf(
            product("p-1", title = "Red Shirt", status = "ACTIVE"),
            product("p-2", title = "Red Hat", status = "DRAFT"),
        ))

        val list = dao.searchAllProducts("Red")
        assertThat(list.map { it.id }).containsExactly("p-1", "p-2")
    }

    @Test
    fun `getProductById returns null when missing`() = runTest {
        assertThat(dao.getProductById("missing")).isNull()
    }

    @Test
    fun `upsert single product replaces on id conflict`() = runTest {
        dao.upsert(product("p-1", title = "Old"))
        dao.upsert(product("p-1", title = "New"))

        assertThat(dao.getProductById("p-1")!!.title).isEqualTo("New")
    }

    @Test
    fun `deleteById removes the row`() = runTest {
        dao.upsert(product("p-1"))
        dao.deleteById("p-1")
        assertThat(dao.getProductById("p-1")).isNull()
    }

    @Test
    fun `clearAll empties the table`() = runTest {
        dao.upsertAll(listOf(product("p-1"), product("p-2")))
        dao.clearAll()
        val list = dao.observeAllActiveProducts().first()
        assertThat(list).isEmpty()
    }

    @Test
    fun `default json columns hold empty representations`() = runTest {
        // Pin wire format the converter side relies on.
        dao.upsert(ProductEntity(id = "p-1"))
        val row = dao.getProductById("p-1")!!
        assertThat(row.imagesJson).isEqualTo("[]")
        assertThat(row.variantsJson).isEqualTo("[]")
        assertThat(row.shippingJson).isEqualTo("{}")
        assertThat(row.tagsJson).isEqualTo("[]")
        assertThat(row.tagNamesJson).isEqualTo("[]")
        assertThat(row.searchKeywordsJson).isEqualTo("[]")
    }
}
