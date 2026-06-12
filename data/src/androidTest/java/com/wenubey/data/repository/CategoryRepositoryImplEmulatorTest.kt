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
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class CategoryRepositoryImplEmulatorTest {

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
    private lateinit var repo: CategoryRepositoryImpl

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CategoryRepositoryImpl(firestore, auth, storage, dispatcherProvider, db.categoryDao())

        FirebaseEmulator.clearFirestore()
        runBlocking { FirebaseEmulator.signInAnonymous() }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createCategory_assigns_id_and_createdBy_and_isActive(): Unit = runBlocking {
        val created = repo.createCategory(
            Category(name = "Clothing", description = "All clothing")
        ).getOrThrow()

        assertThat(created.id).isNotEmpty()
        assertThat(created.name).isEqualTo("Clothing")
        assertThat(created.description).isEqualTo("All clothing")
        assertThat(created.createdBy).isEqualTo(auth.currentUser?.uid)
        assertThat(created.isActive).isTrue()
        assertThat(created.createdAt).isNotEmpty()
        assertThat(created.updatedAt).isEqualTo(created.createdAt)
    }

    @Test
    fun createCategory_preserves_a_caller_supplied_id(): Unit = runBlocking {
        val created = repo.createCategory(
            Category(id = "explicit-id", name = "Custom"),
        ).getOrThrow()
        assertThat(created.id).isEqualTo("explicit-id")
    }

    @Test
    fun createCategory_fails_when_no_user_is_signed_in(): Unit = runBlocking {
        auth.signOut()
        val result = repo.createCategory(Category(name = "Nope"))
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun getCategories_returns_only_active_ones_and_caches_to_room(): Unit = runBlocking {
        val active = repo.createCategory(Category(name = "Active1")).getOrThrow()
        val soft = repo.createCategory(Category(name = "ToDelete")).getOrThrow()
        repo.deleteCategory(soft.id).getOrThrow() // flips isActive to false in Firestore

        val list = repo.getCategories().getOrThrow()
        // Returned list reflects Firestore: only Active1 should be present.
        val names = list.map { it.name }
        assertThat(names).contains("Active1")
        assertThat(names).doesNotContain("ToDelete")

        // Room cache was populated for active entries.
        val cached = db.categoryDao().observeAllCategories()
        // Using a one-shot first() collect — Room emits the current set.
        val cachedList = cached.first()
        assertThat(cachedList.map { it.id }).contains(active.id)
    }

    @Test
    fun updateCategory_overwrites_fields_and_bumps_updatedAt(): Unit = runBlocking {
        val created = repo.createCategory(Category(name = "Original")).getOrThrow()
        val originalUpdatedAt = created.updatedAt

        val edited = created.copy(name = "Renamed", description = "new desc")
        repo.updateCategory(edited).getOrThrow()

        // Verify via repo's read path so we don't hit cache vs server races.
        // updateCategory writes through to Room synchronously, so observe is
        // safe to read immediately.
        val cached = db.categoryDao().observeAllCategories().first()
        val row = cached.single { it.id == created.id }
        assertThat(row.name).isEqualTo("Renamed")
        assertThat(row.description).isEqualTo("new desc")
        assertThat(row.updatedAt).isNotEqualTo(originalUpdatedAt)
    }

    @Test
    fun deleteCategory_soft_deletes_via_isActive_false_and_drops_from_Room_cache(): Unit = runBlocking {
        val created = repo.createCategory(Category(name = "Doomed")).getOrThrow()
        repo.deleteCategory(created.id).getOrThrow()

        // Room cache: the entry is removed.
        val cached = db.categoryDao().observeAllCategories().first()
        assertThat(cached.none { it.id == created.id }).isTrue()

        // Firestore-side: refetch through getCategories should now exclude it
        // because the query filters where isActive == true.
        val active = repo.getCategories().getOrThrow()
        assertThat(active.none { it.id == created.id }).isTrue()
    }

    @Test
    fun addSubcategory_appends_to_subcategories_list(): Unit = runBlocking {
        val created = repo.createCategory(Category(name = "Tops")).getOrThrow()

        repo.addSubcategory(created.id, Subcategory(id = "sub-1", name = "T-Shirts")).getOrThrow()
        repo.addSubcategory(created.id, Subcategory(id = "sub-2", name = "Sweaters")).getOrThrow()

        // Read back via getCategories to verify Firestore state.
        val refetched = repo.getCategories().getOrThrow()
        val target = refetched.single { it.id == created.id }
        assertThat(target.subcategories.map { it.id }).containsExactly("sub-1", "sub-2")
    }

    @Test
    fun addSubcategory_is_idempotent_for_repeated_ids(): Unit = runBlocking {
        val created = repo.createCategory(Category(name = "X")).getOrThrow()
        val sub = Subcategory(id = "sub-X", name = "X-Sub")

        repo.addSubcategory(created.id, sub).getOrThrow()
        repo.addSubcategory(created.id, sub).getOrThrow() // duplicate — should be skipped

        val refetched = repo.getCategories().getOrThrow()
        val target = refetched.single { it.id == created.id }
        assertThat(target.subcategories).hasSize(1)
    }
}
