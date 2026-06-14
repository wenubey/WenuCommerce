package com.wenubey.data.repository

import androidx.core.net.toUri
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
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Storage-emulator integration tests for ProductRepositoryImpl. Kept in a
 * separate class from ProductRepositoryImplEmulatorTest so the Firestore-only
 * suite can run without the Storage emulator available.
 *
 * Prereq: `firebase emulators:start --only firestore,auth,functions,storage`
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ProductRepositoryImplStorageEmulatorTest {

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
    private lateinit var tempFiles: MutableList<File>

    @Before
    fun setUp() {
        FirebaseEmulator.clearStorage()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ProductRepositoryImpl(firestore, auth, storage, dispatcherProvider, db.productDao())
        tempFiles = mutableListOf()
    }

    @After
    fun tearDown() {
        tempFiles.forEach { runCatching { it.delete() } }
        db.close()
    }

    /** Writes a temp file with the given bytes and returns its file:// URI string. */
    private fun tempFile(bytes: ByteArray, extension: String = "jpg"): String {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("upload-", ".$extension", ctx.cacheDir).apply {
            writeBytes(bytes)
        }
        tempFiles += file
        return file.toUri().toString()
    }

    @Test
    fun uploadProductImage_uploads_file_and_returns_https_download_url(): Unit = runBlocking {
        val productId = "p-${UUID.randomUUID().toString().take(6)}"
        val imageId = "img-${UUID.randomUUID().toString().take(6)}"
        val payload = "fake-jpeg-bytes-${UUID.randomUUID()}".toByteArray()

        val downloadUrl = repo.uploadProductImage(
            localUri = tempFile(payload),
            productId = productId,
            imageId = imageId,
        ).getOrThrow()

        assertThat(downloadUrl).isNotEmpty()
        // The emulator's downloadUrl points to the emulator host endpoint.
        assertThat(downloadUrl).contains("/o/product_images%2F$productId%2F$imageId.jpg")

        // The same path under the bucket should hold the bytes we just wrote.
        val ref = storage.reference.child("product_images/$productId/$imageId.jpg")
        val readBack = ref.getBytes(1024 * 1024).await()
        assertThat(readBack).isEqualTo(payload)
    }

    @Test
    fun uploadProductImage_overwrites_when_called_twice_with_same_imageId(): Unit = runBlocking {
        val productId = "p-${UUID.randomUUID().toString().take(6)}"
        val imageId = "fixed-img"

        repo.uploadProductImage(tempFile("v1".toByteArray()), productId, imageId).getOrThrow()
        repo.uploadProductImage(tempFile("v2-overwrite".toByteArray()), productId, imageId).getOrThrow()

        val ref = storage.reference.child("product_images/$productId/$imageId.jpg")
        val bytes = ref.getBytes(1024 * 1024).await()
        assertThat(String(bytes)).isEqualTo("v2-overwrite")
    }

    @Test
    fun deleteProductImage_removes_object_at_given_path(): Unit = runBlocking {
        val productId = "p-${UUID.randomUUID().toString().take(6)}"
        val imageId = "img-del"
        repo.uploadProductImage(tempFile("to-delete".toByteArray()), productId, imageId).getOrThrow()
        val path = "product_images/$productId/$imageId.jpg"

        repo.deleteProductImage(path).getOrThrow()

        val result = runCatching {
            storage.reference.child(path).getBytes(1024).await()
        }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun deleteProductImage_on_missing_path_returns_failure(): Unit = runBlocking {
        val result = repo.deleteProductImage("product_images/nope/nothing.jpg")

        assertThat(result.isFailure).isTrue()
    }
}
