package com.wenubey.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.local.mapper.toEntity
import com.wenubey.data.util.PRODUCTS_COLLECTION
import com.wenubey.data.util.USER_COLLECTION
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.toMap
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
 * Integration tests for ProductRepositoryImpl against the Firestore + Auth
 * emulators plus a real in-memory Room database.
 *
 * Coverage focus:
 *   - Seller CRUD: createProduct (auth + slug + searchKeywords), updateProduct
 *     (recomputes searchKeywords + totalStockQuantity), submitForReview,
 *     archive / unarchive
 *   - Seller queries: observeSellerProducts (Room), getSellerProducts
 *     (Firestore -> Room cache)
 *   - Customer queries: observeActiveProductsByCategory (with + without
 *     subcategory), getProductById (Room hit, Firestore fallback)
 *   - Storefront: getStorefrontProducts (filters by sellerId + ACTIVE)
 *   - Admin: observeProductsByStatus, approveProduct (transaction),
 *     suspendProduct, adminUpdateProduct
 *   - Counters / stock: incrementViewCount (FieldValue.increment),
 *     decrementStock (transaction with variant manipulation +
 *     out-of-stock failure)
 *   - addProductToSellerDocument (arrayUnion on USERS/{uid})
 *   - searchActiveProducts / searchAllProducts (token filtering + ACTIVE
 *     guard for active variant)
 *
 * Excluded: uploadProductImage / deleteProductImage (Storage emulator is
 * not configured for this suite).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ProductRepositoryImplEmulatorTest {

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
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    private lateinit var db: WenuCommerceDatabase
    private lateinit var repo: ProductRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        FirebaseEmulator.clearAuth()
        FirebaseEmulator.clearFirestore()
        auth.signOut()

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        repo = ProductRepositoryImpl(firestore, auth, storage, dispatcherProvider, db.productDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun productId() = "prod-${UUID.randomUUID().toString().take(8)}"

    private fun sampleProduct(
        id: String = productId(),
        sellerId: String = "seller-1",
        title: String = "Cool Widget",
        categoryId: String = "cat-1",
        categoryName: String = "Tools",
        subcategoryId: String = "sub-1",
        subcategoryName: String = "Hand Tools",
        tagNames: List<String> = listOf("durable", "metal"),
        basePrice: Double = 10.0,
        status: ProductStatus = ProductStatus.ACTIVE,
        variants: List<ProductVariant> = emptyList(),
        searchKeywords: List<String> = listOf("cool", "widget", "tools", "hand", "durable", "metal"),
        totalStockQuantity: Int = 5,
    ) = Product(
        id = id,
        sellerId = sellerId,
        title = title,
        slug = title.lowercase().replace(" ", "-"),
        categoryId = categoryId,
        categoryName = categoryName,
        subcategoryId = subcategoryId,
        subcategoryName = subcategoryName,
        tagNames = tagNames,
        basePrice = basePrice,
        status = status,
        variants = variants,
        searchKeywords = searchKeywords,
        totalStockQuantity = totalStockQuantity,
        createdAt = "0",
        updatedAt = "0",
        viewCount = 0,
        purchaseCount = 0,
    )

    /** Seeds Firestore and Room with the given product. */
    private suspend fun seedProduct(product: Product): Product {
        firestore.collection(PRODUCTS_COLLECTION).document(product.id)
            .set(product.toMap()).await()
        db.productDao().upsert(product.toEntity())
        return product
    }

    // -------- createProduct --------

    @Test
    fun createProduct_assigns_id_seller_slug_and_searchKeywords(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()

        val created = repo.createProduct(
            Product(
                title = "Bright Lamp",
                categoryName = "Home",
                tagNames = listOf("led", "warm"),
            )
        ).getOrThrow()

        assertThat(created.id).isNotEmpty()
        assertThat(created.sellerId).isEqualTo(uid)
        assertThat(created.slug).isEqualTo("bright-lamp")
        assertThat(created.status).isEqualTo(ProductStatus.DRAFT)
        assertThat(created.searchKeywords).containsAtLeast("bright", "lamp", "home", "led", "warm")

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(created.id).get().await()
        assertThat(doc.exists()).isTrue()
        assertThat(doc.getString("title")).isEqualTo("Bright Lamp")

        val cached = db.productDao().getProductById(created.id)
        assertThat(cached).isNotNull()
        assertThat(cached!!.sellerId).isEqualTo(uid)
    }

    @Test
    fun createProduct_returns_failure_when_user_not_authenticated(): Unit = runBlocking {
        val result = repo.createProduct(Product(title = "Anon Item"))

        assertThat(result.isFailure).isTrue()
    }

    // -------- updateProduct --------

    @Test
    fun updateProduct_recomputes_searchKeywords_and_totalStockQuantity(): Unit = runBlocking {
        FirebaseEmulator.signInAnonymous()
        val pid = productId()
        seedProduct(
            sampleProduct(
                id = pid,
                title = "Old Title",
                tagNames = listOf("old"),
                searchKeywords = listOf("old", "title"),
                totalStockQuantity = 0,
            )
        )

        val updated = sampleProduct(id = pid).copy(
            title = "Shiny New Title",
            categoryName = "Updated",
            tagNames = listOf("new"),
            searchKeywords = emptyList(),
            variants = listOf(
                ProductVariant(id = "v1", stockQuantity = 3),
                ProductVariant(id = "v2", stockQuantity = 7),
            ),
        )

        repo.updateProduct(updated).getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(doc.getString("title")).isEqualTo("Shiny New Title")
        @Suppress("UNCHECKED_CAST")
        val storedKeywords = doc.get("searchKeywords") as List<String>
        assertThat(storedKeywords).containsAtLeast("shiny", "new", "title", "updated")
        assertThat(doc.getLong("totalStockQuantity")).isEqualTo(10L)
    }

    // -------- submitForReview / archive / unarchive --------

    @Test
    fun submitForReview_sets_status_PENDING_REVIEW(): Unit = runBlocking {
        val pid = productId()
        seedProduct(sampleProduct(id = pid, status = ProductStatus.DRAFT))

        repo.submitForReview(pid).getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(doc.getString("status")).isEqualTo(ProductStatus.PENDING_REVIEW.name)
    }

    @Test
    fun archive_then_unarchive_round_trips_status(): Unit = runBlocking {
        val pid = productId()
        seedProduct(sampleProduct(id = pid, status = ProductStatus.ACTIVE))

        repo.archiveProduct(pid).getOrThrow()
        val archived = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(archived.getString("status")).isEqualTo(ProductStatus.ARCHIVED.name)
        assertThat(archived.getString("archivedAt")).isNotEmpty()

        repo.unarchiveProduct(pid).getOrThrow()
        val unarchived = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(unarchived.getString("status")).isEqualTo(ProductStatus.DRAFT.name)
        assertThat(unarchived.getString("archivedAt")).isEmpty()
    }

    // -------- seller queries --------

    @Test
    fun observeSellerProducts_emits_room_state(): Unit = runBlocking {
        seedProduct(sampleProduct(sellerId = "s-A", title = "A1"))
        seedProduct(sampleProduct(sellerId = "s-A", title = "A2"))
        seedProduct(sampleProduct(sellerId = "s-B", title = "B1"))

        val a = repo.observeSellerProducts("s-A").first()
        assertThat(a.map { it.title }).containsExactly("A1", "A2")
    }

    @Test
    fun getSellerProducts_pulls_from_firestore_and_caches_to_room(): Unit = runBlocking {
        val sellerId = "seller-fetch"
        // Seed Firestore only (skip Room) to prove the fetch + cache path
        firestore.collection(PRODUCTS_COLLECTION).document(productId())
            .set(sampleProduct(sellerId = sellerId, title = "Remote 1").toMap()).await()
        firestore.collection(PRODUCTS_COLLECTION).document(productId())
            .set(sampleProduct(sellerId = sellerId, title = "Remote 2").toMap()).await()

        val result = repo.getSellerProducts(sellerId).getOrThrow()

        assertThat(result.map { it.title }).containsExactly("Remote 1", "Remote 2")
        val cached = db.productDao().observeSellerProducts(sellerId).first()
        assertThat(cached).hasSize(2)
    }

    // -------- customer / observe paths --------

    @Test
    fun observeActiveProductsByCategory_filters_by_categoryId_and_ACTIVE(): Unit = runBlocking {
        seedProduct(sampleProduct(categoryId = "cat-X", status = ProductStatus.ACTIVE, title = "ActiveX"))
        seedProduct(sampleProduct(categoryId = "cat-X", status = ProductStatus.DRAFT, title = "DraftX"))
        seedProduct(sampleProduct(categoryId = "cat-Y", status = ProductStatus.ACTIVE, title = "ActiveY"))

        val xs = repo.observeActiveProductsByCategory("cat-X").first()

        assertThat(xs.map { it.title }).containsExactly("ActiveX")
    }

    @Test
    fun observeActiveProductsByCategoryAndSubcategory_falls_back_when_subcategory_blank(): Unit = runBlocking {
        seedProduct(
            sampleProduct(
                categoryId = "cat-Z",
                subcategoryId = "sub-1",
                status = ProductStatus.ACTIVE,
                title = "Sub1Item",
            )
        )
        seedProduct(
            sampleProduct(
                categoryId = "cat-Z",
                subcategoryId = "sub-2",
                status = ProductStatus.ACTIVE,
                title = "Sub2Item",
            )
        )

        val all = repo.observeActiveProductsByCategoryAndSubcategory("cat-Z", null).first()
        assertThat(all.map { it.title }).containsExactly("Sub1Item", "Sub2Item")

        val onlySub1 = repo.observeActiveProductsByCategoryAndSubcategory("cat-Z", "sub-1").first()
        assertThat(onlySub1.map { it.title }).containsExactly("Sub1Item")
    }

    @Test
    fun getProductById_returns_room_cached_without_firestore_hit(): Unit = runBlocking {
        val pid = productId()
        val cachedOnly = sampleProduct(id = pid, title = "From Cache")
        db.productDao().upsert(cachedOnly.toEntity()) // not in Firestore

        val result = repo.getProductById(pid).getOrThrow()

        assertThat(result.title).isEqualTo("From Cache")
    }

    @Test
    fun getProductById_falls_back_to_firestore_when_room_misses(): Unit = runBlocking {
        val pid = productId()
        firestore.collection(PRODUCTS_COLLECTION).document(pid)
            .set(sampleProduct(id = pid, title = "Remote Only").toMap()).await()

        val result = repo.getProductById(pid).getOrThrow()

        assertThat(result.title).isEqualTo("Remote Only")
        // Should have populated Room cache.
        val cached = db.productDao().getProductById(pid)
        assertThat(cached).isNotNull()
    }

    @Test
    fun getProductById_returns_failure_when_missing_everywhere(): Unit = runBlocking {
        val result = repo.getProductById("no-such-id")

        assertThat(result.isFailure).isTrue()
    }

    // -------- storefront --------

    @Test
    fun getStorefrontProducts_returns_only_ACTIVE_for_seller(): Unit = runBlocking {
        val sellerId = "store-seller"
        firestore.collection(PRODUCTS_COLLECTION).document(productId())
            .set(sampleProduct(sellerId = sellerId, status = ProductStatus.ACTIVE, title = "Live").toMap()).await()
        firestore.collection(PRODUCTS_COLLECTION).document(productId())
            .set(sampleProduct(sellerId = sellerId, status = ProductStatus.DRAFT, title = "Draft").toMap()).await()
        firestore.collection(PRODUCTS_COLLECTION).document(productId())
            .set(sampleProduct(sellerId = "other", status = ProductStatus.ACTIVE, title = "Other").toMap()).await()

        val result = repo.getStorefrontProducts(sellerId).getOrThrow()

        assertThat(result.map { it.title }).containsExactly("Live")
    }

    // -------- admin --------

    @Test
    fun observeProductsByStatus_filters_room_by_status(): Unit = runBlocking {
        seedProduct(sampleProduct(status = ProductStatus.PENDING_REVIEW, title = "Pending1"))
        seedProduct(sampleProduct(status = ProductStatus.PENDING_REVIEW, title = "Pending2"))
        seedProduct(sampleProduct(status = ProductStatus.ACTIVE, title = "Active1"))

        val pending = repo.observeProductsByStatus(ProductStatus.PENDING_REVIEW).first()

        assertThat(pending.map { it.title }).containsExactly("Pending1", "Pending2")
    }

    @Test
    fun approveProduct_transitions_to_ACTIVE_and_clears_moderationNotes(): Unit = runBlocking {
        val pid = productId()
        seedProduct(
            sampleProduct(
                id = pid,
                status = ProductStatus.PENDING_REVIEW,
            ).copy(moderationNotes = "previously rejected")
        )

        repo.approveProduct(pid).getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(doc.getString("status")).isEqualTo(ProductStatus.ACTIVE.name)
        assertThat(doc.getString("moderationNotes")).isEmpty()
        assertThat(doc.getString("publishedAt")).isNotEmpty()
        // Room cache also updated
        val cached = db.productDao().getProductById(pid)
        assertThat(cached!!.status).isEqualTo(ProductStatus.ACTIVE.name)
    }

    @Test
    fun suspendProduct_records_reason_and_admin(): Unit = runBlocking {
        val pid = productId()
        seedProduct(sampleProduct(id = pid, status = ProductStatus.ACTIVE))

        repo.suspendProduct(pid, reason = "Counterfeit", adminId = "admin-9").getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(doc.getString("status")).isEqualTo(ProductStatus.SUSPENDED.name)
        assertThat(doc.getString("moderationNotes")).isEqualTo("Counterfeit")
        assertThat(doc.getString("suspendedBy")).isEqualTo("admin-9")
        assertThat(doc.getString("suspendedAt")).isNotEmpty()
    }

    @Test
    fun adminUpdateProduct_writes_firestore_with_recomputed_keywords(): Unit = runBlocking {
        val pid = productId()
        seedProduct(sampleProduct(id = pid))

        repo.adminUpdateProduct(
            sampleProduct(id = pid).copy(
                title = "Edited By Admin",
                tagNames = listOf("admin", "edit"),
                searchKeywords = emptyList(),
            )
        ).getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        @Suppress("UNCHECKED_CAST")
        val storedKeywords = doc.get("searchKeywords") as List<String>
        assertThat(storedKeywords).containsAtLeast("edited", "admin", "edit")
    }

    // -------- counters / stock --------

    @Test
    fun incrementViewCount_bumps_the_field(): Unit = runBlocking {
        val pid = productId()
        seedProduct(sampleProduct(id = pid))

        repo.incrementViewCount(pid).getOrThrow()
        repo.incrementViewCount(pid).getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(doc.getLong("viewCount")).isEqualTo(2L)
    }

    @Test
    fun decrementStock_updates_variant_total_and_purchase_count(): Unit = runBlocking {
        val pid = productId()
        seedProduct(
            sampleProduct(
                id = pid,
                variants = listOf(
                    ProductVariant(id = "v1", stockQuantity = 5),
                    ProductVariant(id = "v2", stockQuantity = 3),
                ),
                totalStockQuantity = 8,
            )
        )

        repo.decrementStock(pid, variantId = "v1", quantity = 2).getOrThrow()

        val doc = firestore.collection(PRODUCTS_COLLECTION).document(pid).get().await()
        assertThat(doc.getLong("totalStockQuantity")).isEqualTo(6L)
        assertThat(doc.getLong("purchaseCount")).isEqualTo(1L)
        @Suppress("UNCHECKED_CAST")
        val variants = doc.get("variants") as List<Map<String, Any?>>
        val v1 = variants.first { it["id"] == "v1" }
        assertThat((v1["stockQuantity"] as Long).toInt()).isEqualTo(3)
        assertThat(v1["inStock"]).isEqualTo(true)
    }

    @Test
    fun decrementStock_fails_when_quantity_exceeds_variant_stock(): Unit = runBlocking {
        val pid = productId()
        seedProduct(
            sampleProduct(
                id = pid,
                variants = listOf(ProductVariant(id = "v1", stockQuantity = 1)),
                totalStockQuantity = 1,
            )
        )

        val result = repo.decrementStock(pid, variantId = "v1", quantity = 5)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun decrementStock_fails_when_variantId_unknown(): Unit = runBlocking {
        val pid = productId()
        seedProduct(
            sampleProduct(
                id = pid,
                variants = listOf(ProductVariant(id = "v1", stockQuantity = 5)),
            )
        )

        val result = repo.decrementStock(pid, variantId = "v-unknown", quantity = 1)

        assertThat(result.isFailure).isTrue()
    }

    // -------- seller user-doc --------

    @Test
    fun addProductToSellerDocument_unions_product_id_into_user_array(): Unit = runBlocking {
        val sellerId = "seller-doc"
        firestore.collection(USER_COLLECTION).document(sellerId)
            .set(mapOf("uuid" to sellerId, "products" to listOf("existing-1"))).await()

        repo.addProductToSellerDocument(sellerId, "new-prod-1").getOrThrow()
        // arrayUnion is idempotent — adding the same id twice should leave the
        // array unchanged.
        repo.addProductToSellerDocument(sellerId, "new-prod-1").getOrThrow()

        val doc = firestore.collection(USER_COLLECTION).document(sellerId).get().await()
        @Suppress("UNCHECKED_CAST")
        val products = doc.get("products") as List<String>
        assertThat(products).containsExactly("existing-1", "new-prod-1")
    }

    // -------- search --------

    @Test
    fun searchActiveProducts_blank_query_returns_empty(): Unit = runBlocking {
        seedProduct(sampleProduct(title = "Anything"))

        assertThat(repo.searchActiveProducts("").getOrThrow()).isEmpty()
        assertThat(repo.searchActiveProducts("   ").getOrThrow()).isEmpty()
    }

    @Test
    fun searchActiveProducts_returns_ACTIVE_only_and_requires_all_tokens(): Unit = runBlocking {
        seedProduct(
            sampleProduct(
                title = "Cozy Wool Blanket",
                searchKeywords = listOf("cozy", "wool", "blanket"),
                status = ProductStatus.ACTIVE,
            )
        )
        // Draft product also matching the primary token — must be excluded.
        seedProduct(
            sampleProduct(
                title = "Cozy Throw Pillow",
                searchKeywords = listOf("cozy", "throw", "pillow"),
                status = ProductStatus.DRAFT,
            )
        )
        // Active but only matching the primary token — must be excluded by the
        // multi-token filter.
        seedProduct(
            sampleProduct(
                title = "Cozy Coffee Mug",
                searchKeywords = listOf("cozy", "coffee", "mug"),
                status = ProductStatus.ACTIVE,
            )
        )

        val result = repo.searchActiveProducts("cozy wool").getOrThrow()

        assertThat(result.map { it.title }).containsExactly("Cozy Wool Blanket")
    }

    @Test
    fun searchAllProducts_includes_DRAFT_and_PENDING(): Unit = runBlocking {
        seedProduct(
            sampleProduct(
                title = "Holiday Mug",
                searchKeywords = listOf("holiday", "mug"),
                status = ProductStatus.DRAFT,
            )
        )
        seedProduct(
            sampleProduct(
                title = "Holiday Tee",
                searchKeywords = listOf("holiday", "tee"),
                status = ProductStatus.PENDING_REVIEW,
            )
        )

        val result = repo.searchAllProducts("holiday").getOrThrow()

        assertThat(result.map { it.title }).containsAtLeast("Holiday Mug", "Holiday Tee")
    }

    @Test
    fun searchActiveProducts_categoryId_filter_narrows_results(): Unit = runBlocking {
        seedProduct(
            sampleProduct(
                title = "Boot",
                categoryId = "shoes",
                searchKeywords = listOf("boot"),
            )
        )
        seedProduct(
            sampleProduct(
                title = "Boot Sandwich",
                categoryId = "food",
                searchKeywords = listOf("boot", "sandwich"),
            )
        )

        val onlyShoes = repo.searchActiveProducts("boot", categoryId = "shoes").getOrThrow()

        assertThat(onlyShoes.map { it.title }).containsExactly("Boot")
    }
}
