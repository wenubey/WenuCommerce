package com.wenubey.data.repository

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.util.DeviceInfoProvider
import com.wenubey.data.util.USER_COLLECTION
import com.wenubey.domain.model.Device
import com.wenubey.domain.model.Gender
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.util.AuthProvider
import com.wenubey.domain.util.DocumentType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
 * Storage-emulator integration tests for ProfileRepositoryImpl. Exercises the
 * paths that ProfileRepositoryImplEmulatorTest had to skip:
 *   - onboarding with a non-EMPTY photo URI triggers uploadProfilePhoto
 *   - onboarding for SELLER with non-EMPTY document URIs uploads each
 *     document to its organized folder
 *   - updateSellerDocument deletes existing files of the same type and
 *     uploads a new one, then writes the new URL into the user doc
 *   - deleteSellerData removes every file under seller_info/{uid}
 *
 * Prereq: `firebase emulators:start --only firestore,auth,functions,storage`
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ProfileRepositoryImplStorageEmulatorTest {

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

    private val deviceInfoProvider: DeviceInfoProvider = mockk(relaxed = true)
    private lateinit var fakeFirestoreRepo: CapturingFirestoreRepository
    private lateinit var repo: ProfileRepositoryImpl
    private lateinit var tempFiles: MutableList<File>

    @Before
    fun resetState() = runBlocking {
        FirebaseEmulator.clearAuth()
        FirebaseEmulator.clearFirestore()
        FirebaseEmulator.clearStorage()
        auth.signOut()

        coEvery { deviceInfoProvider.getDeviceData() } returns Device(
            deviceId = "device-test",
            deviceName = "Pixel Test",
            osVersion = "Android 14",
            timeStamp = "0",
            fcmToken = "fcm-test",
            location = "Istanbul, Turkey",
        )
        fakeFirestoreRepo = CapturingFirestoreRepository(firestore)
        tempFiles = mutableListOf()
    }

    @After
    fun tearDown() {
        tempFiles.forEach { runCatching { it.delete() } }
    }

    private fun construct(): ProfileRepositoryImpl = ProfileRepositoryImpl(
        firestoreRepository = fakeFirestoreRepo,
        auth = auth,
        storage = storage,
        dispatcherProvider = dispatcherProvider,
        deviceInfoProvider = deviceInfoProvider,
        firestore = firestore,
    )

    private fun tempFile(bytes: ByteArray, extension: String): Uri {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("seller-", ".$extension", ctx.cacheDir).apply {
            writeBytes(bytes)
        }
        tempFiles += file
        return file.toUri()
    }

    // -------- onboarding with profile photo --------

    @Test
    fun onboarding_customer_with_photo_uploads_to_profile_photos_folder(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()
        val repo = construct()
        val photo = tempFile("png-bytes".toByteArray(), "png")

        val user = repo.onboarding(
            name = "Ada",
            surname = "Lovelace",
            phoneNumber = "",
            dateOfBirth = "",
            address = "",
            gender = Gender.FEMALE,
            photoUrl = photo,
            role = UserRole.CUSTOMER,
        ).getOrThrow()

        assertThat(user.profilePhotoUri).isNotEmpty()
        assertThat(user.profilePhotoUri).contains("/o/profile_photos%2F$uid%2F")
    }

    // -------- onboarding seller with documents --------

    @Test
    fun onboarding_seller_with_documents_uploads_each_to_its_folder(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()
        val repo = construct()
        val taxDoc = tempFile("tax-pdf-bytes".toByteArray(), "pdf")
        val licenseDoc = tempFile("license-bytes".toByteArray(), "pdf")
        val identityDoc = tempFile("id-bytes".toByteArray(), "jpg")

        val user = repo.onboarding(
            name = "Grace",
            surname = "Hopper",
            phoneNumber = "",
            dateOfBirth = "",
            address = "",
            gender = Gender.FEMALE,
            photoUrl = Uri.EMPTY,
            role = UserRole.SELLER,
            businessName = "Hopper Tools",
            taxId = "TX-1",
            businessLicense = "BL-1",
            businessAddress = "",
            businessPhone = "",
            businessEmail = "",
            bankAccountNumber = "",
            routingNumber = "",
            businessType = BusinessType.LLC,
            businessDescription = "",
            taxDocumentUri = taxDoc,
            businessLicenseDocumentUri = licenseDoc,
            identityDocumentUri = identityDoc,
        ).getOrThrow()

        val biz = user.businessInfo!!
        assertThat(biz.taxDocumentUri).contains("/o/seller_info%2F$uid%2Ftax_documents%2F")
        assertThat(biz.businessLicenseDocumentUri).contains("/o/seller_info%2F$uid%2Fbusiness_license%2F")
        assertThat(biz.identityDocumentUri).contains("/o/seller_info%2F$uid%2Fidentity_documents%2F")
        assertThat(biz.verificationStatus).isEqualTo(VerificationStatus.PENDING)
    }

    // -------- updateSellerDocument --------

    @Test
    fun updateSellerDocument_replaces_existing_file_and_patches_firestore(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()
        val repo = construct()

        // First upload (initial seller onboarding)
        repo.onboarding(
            name = "S",
            surname = "Eller",
            phoneNumber = "",
            dateOfBirth = "",
            address = "",
            gender = Gender.NOT_SPECIFIED,
            photoUrl = Uri.EMPTY,
            role = UserRole.SELLER,
            businessName = "Co",
            taxId = "TX",
            businessLicense = "BL",
            businessAddress = "",
            businessPhone = "",
            businessEmail = "",
            bankAccountNumber = "",
            routingNumber = "",
            businessType = BusinessType.INDIVIDUAL,
            businessDescription = "",
            taxDocumentUri = tempFile("old-tax".toByteArray(), "pdf"),
            businessLicenseDocumentUri = Uri.EMPTY,
            identityDocumentUri = Uri.EMPTY,
        ).getOrThrow()

        // Capture old folder size
        val taxFolder = storage.reference.child("seller_info/$uid/tax_documents")
        val before = taxFolder.listAll().await()
        assertThat(before.items).hasSize(1)
        val oldName = before.items.first().name

        // Replace the tax document
        val newUrl = repo.updateSellerDocument(
            userUid = uid,
            documentType = DocumentType.TAX_DOCUMENTS,
            newDocumentUri = tempFile("new-tax".toByteArray(), "pdf"),
        ).getOrThrow()

        assertThat(newUrl).isNotEmpty()
        // After: only the new file remains
        val after = taxFolder.listAll().await()
        assertThat(after.items.map { it.name }).doesNotContain(oldName)
        assertThat(after.items).hasSize(1)

        // TB-8 (production bug pinned): updateSellerDocument writes to
        // 'businessInfo.${DocumentType.name.lowercase()}' (e.g.
        // 'businessInfo.tax_documents'), but onboarding stores the same URL
        // under the camel-cased field 'businessInfo.taxDocumentUri'. So the
        // refresh does NOT replace the original — it spawns a parallel field
        // and the old URL remains live. This test pins both observable
        // outcomes so we notice when the bug is fixed.
        val doc = firestore.collection(USER_COLLECTION).document(uid).get().await()
        assertThat(doc.getString("businessInfo.tax_documents")).isEqualTo(newUrl)
        // Original field is untouched by the update.
        assertThat(doc.getString("businessInfo.taxDocumentUri")).isNotEqualTo(newUrl)
    }

    // -------- deleteSellerData --------

    @Test
    fun deleteSellerData_wipes_every_file_under_seller_info_uid(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()
        val repo = construct()
        repo.onboarding(
            name = "S",
            surname = "Eller",
            phoneNumber = "",
            dateOfBirth = "",
            address = "",
            gender = Gender.NOT_SPECIFIED,
            photoUrl = Uri.EMPTY,
            role = UserRole.SELLER,
            businessName = "Co",
            taxId = "TX",
            businessLicense = "BL",
            businessAddress = "",
            businessPhone = "",
            businessEmail = "",
            bankAccountNumber = "",
            routingNumber = "",
            businessType = BusinessType.INDIVIDUAL,
            businessDescription = "",
            taxDocumentUri = tempFile("tax".toByteArray(), "pdf"),
            businessLicenseDocumentUri = tempFile("license".toByteArray(), "pdf"),
            identityDocumentUri = tempFile("id".toByteArray(), "jpg"),
        ).getOrThrow()

        // Pre: each of the three subfolders has one file.
        val sellerFolder = storage.reference.child("seller_info/$uid")
        val pre = sellerFolder.listAll().await()
        assertThat(pre.prefixes.map { it.name })
            .containsExactly("tax_documents", "business_license", "identity_documents")

        repo.deleteSellerData(uid).getOrThrow()

        // After: every subfolder is empty.
        val post = sellerFolder.listAll().await()
        val remaining = post.prefixes.sumOf { it.listAll().await().items.size }
        assertThat(remaining).isEqualTo(0)
    }

    /**
     * Captures the User passed to onboardingComplete and writes it through to
     * Firestore so the user-document update path (updateSellerDocumentUri)
     * has a real target to mutate.
     */
    private class CapturingFirestoreRepository(
        private val firestore: FirebaseFirestore,
    ) : FirestoreRepository {

        var captured: User? = null
            private set

        override suspend fun addUserToFirestore(
            firebaseUser: FirebaseUser?,
            authProvider: AuthProvider,
        ): Result<Boolean> = Result.success(true)

        override suspend fun getUser(uid: String): Result<User> =
            Result.failure(NoSuchElementException("not used"))

        override suspend fun updateSignedDevice(userUid: String?): Result<Unit> =
            Result.success(Unit)

        override suspend fun onboardingComplete(user: User): Result<Unit> {
            captured = user
            return runCatching {
                val uid = user.uuid ?: error("missing uuid")
                firestore.collection(USER_COLLECTION).document(uid).set(user).await()
            }
        }

        override fun updateFcmToken(token: String): Result<Unit> = Result.success(Unit)

        override fun observeSellersByStatus(status: VerificationStatus): Flow<List<User>> =
            emptyFlow()

        override fun observePendingResubmittedSellerCount(): Flow<Int> = emptyFlow()

        override suspend fun updateSellerApprovalStatus(
            sellerId: String,
            status: VerificationStatus,
            notes: String,
        ): Result<Unit> = Result.success(Unit)
    }
}
