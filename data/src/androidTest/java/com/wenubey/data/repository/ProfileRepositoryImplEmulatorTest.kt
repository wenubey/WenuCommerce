package com.wenubey.data.repository

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
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
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.util.AuthProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for ProfileRepositoryImpl against the Firebase Auth +
 * Firestore emulators.
 *
 * Coverage focus (Storage-independent paths):
 *   - onboarding constructs the User correctly for CUSTOMER and SELLER roles.
 *     Storage uploads are skipped (all document URIs set to Uri.EMPTY) and the
 *     downstream firestoreRepository.onboardingComplete call is captured by a
 *     fake so we can inspect the exact User instance handed off.
 *   - onboarding returns failure when no Firebase user is signed in.
 *   - updateSellerBusinessInfo writes the nested businessInfo map to Firestore.
 *   - cancelSellerApplication flips status to CANCELLED and preserves
 *     previousStatus.
 *
 * Excluded paths (require Storage emulator):
 *   - uploadProfilePhoto / uploadSellerDocument
 *   - deleteSellerData
 *   - updateSellerDocument (does list/delete/upload through Storage)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ProfileRepositoryImplEmulatorTest {

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

    @Before
    fun resetState() = runBlocking {
        FirebaseEmulator.clearAuth()
        FirebaseEmulator.clearFirestore()
        auth.signOut()

        // The repo captures auth.currentUser in a property at construction time.
        // We must sign in BEFORE constructing the repo for onboarding tests.
        // Tests that don't care about onboarding call construct() themselves
        // after they decide on auth state.

        coEvery { deviceInfoProvider.getDeviceData() } returns Device(
            deviceId = "device-test",
            deviceName = "Pixel Test",
            osVersion = "Android 14",
            timeStamp = "0",
            fcmToken = "fcm-test",
            location = "Istanbul, Turkey",
        )
        fakeFirestoreRepo = CapturingFirestoreRepository(firestore)
    }

    private fun construct(): ProfileRepositoryImpl {
        return ProfileRepositoryImpl(
            firestoreRepository = fakeFirestoreRepo,
            auth = auth,
            storage = storage,
            dispatcherProvider = dispatcherProvider,
            deviceInfoProvider = deviceInfoProvider,
            firestore = firestore,
        )
    }

    // -------- onboarding (CUSTOMER) --------

    @Test
    fun onboarding_customer_role_builds_user_without_businessInfo(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()
        repo = construct()

        val result = repo.onboarding(
            name = "Ada",
            surname = "Lovelace",
            phoneNumber = "5550101",
            dateOfBirth = "1815-12-10",
            address = "London",
            gender = Gender.FEMALE,
            photoUrl = Uri.EMPTY,
            role = UserRole.CUSTOMER,
        )

        assertThat(result.isSuccess).isTrue()
        val user = result.getOrThrow()
        assertThat(user.uuid).isEqualTo(uid)
        assertThat(user.name).isEqualTo("Ada")
        assertThat(user.role).isEqualTo(UserRole.CUSTOMER)
        assertThat(user.businessInfo).isNull()
        assertThat(user.profilePhotoUri).isEmpty()
        assertThat(user.signedDevices.map { it.deviceId }).containsExactly("device-test")

        // Production code hands the constructed user to onboardingComplete.
        val captured = fakeFirestoreRepo.captured
        assertThat(captured?.uuid).isEqualTo(uid)
        assertThat(captured?.businessInfo).isNull()
    }

    // -------- onboarding (SELLER) --------

    @Test
    fun onboarding_seller_role_builds_user_with_pending_businessInfo(): Unit = runBlocking {
        val uid = FirebaseEmulator.signInAnonymous()
        repo = construct()

        val result = repo.onboarding(
            name = "Grace",
            surname = "Hopper",
            phoneNumber = "5550199",
            dateOfBirth = "1906-12-09",
            address = "NY",
            gender = Gender.FEMALE,
            photoUrl = Uri.EMPTY,
            role = UserRole.SELLER,
            businessName = "Hopper Tools",
            taxId = "TX-1234",
            businessLicense = "BL-9",
            businessAddress = "1 Compiler Lane",
            businessPhone = "5550200",
            businessEmail = "biz@hopper.dev",
            bankAccountNumber = "ACC-001",
            routingNumber = "ROU-001",
            businessType = BusinessType.LLC,
            businessDescription = "Maker of pioneering tooling",
            // All document URIs left as Uri.EMPTY → Storage uploads are skipped.
        )

        assertThat(result.isSuccess).isTrue()
        val user = result.getOrThrow()
        assertThat(user.role).isEqualTo(UserRole.SELLER)

        val biz = user.businessInfo
        assertThat(biz).isNotNull()
        biz!!
        assertThat(biz.businessName).isEqualTo("Hopper Tools")
        assertThat(biz.businessType).isEqualTo(BusinessType.LLC)
        assertThat(biz.taxId).isEqualTo("TX-1234")
        assertThat(biz.verificationStatus).isEqualTo(VerificationStatus.PENDING)
        assertThat(biz.isVerified).isFalse()
        assertThat(biz.taxDocumentUri).isEmpty()
        assertThat(biz.businessLicenseDocumentUri).isEmpty()
        assertThat(biz.identityDocumentUri).isEmpty()

        assertThat(fakeFirestoreRepo.captured?.uuid).isEqualTo(uid)
        assertThat(fakeFirestoreRepo.captured?.businessInfo?.businessName).isEqualTo("Hopper Tools")
    }

    @Test
    fun onboarding_returns_failure_when_no_auth_user(): Unit = runBlocking {
        // No signInAnonymous before constructing — auth.currentUser is null.
        repo = construct()

        val result = repo.onboarding(
            name = "X",
            surname = "Y",
            phoneNumber = "",
            dateOfBirth = "",
            address = "",
            gender = Gender.NOT_SPECIFIED,
            photoUrl = Uri.EMPTY,
            role = UserRole.CUSTOMER,
        )

        assertThat(result.isFailure).isTrue()
    }

    // -------- updateSellerBusinessInfo --------

    @Test
    fun updateSellerBusinessInfo_writes_nested_businessInfo_map(): Unit = runBlocking {
        repo = construct()
        val uid = "seller-${UUID.randomUUID().toString().take(6)}"
        // Seed an empty user doc so update has something to mutate.
        firestore.collection(USER_COLLECTION).document(uid)
            .set(mapOf("uuid" to uid)).await()

        val newBiz = BusinessInfo(
            businessName = "Patched Co",
            businessType = BusinessType.CORPORATION,
            taxId = "TX-NEW",
            verificationStatus = VerificationStatus.PENDING,
        )

        repo.updateSellerBusinessInfo(uid, newBiz).getOrThrow()

        val doc = firestore.collection(USER_COLLECTION).document(uid).get().await()
        assertThat(doc.getString("businessInfo.businessName")).isEqualTo("Patched Co")
        assertThat(doc.getString("businessInfo.businessType")).isEqualTo(BusinessType.CORPORATION.name)
        assertThat(doc.getString("businessInfo.taxId")).isEqualTo("TX-NEW")
        assertThat(doc.getString("businessInfo.verificationStatus"))
            .isEqualTo(VerificationStatus.PENDING.name)
    }

    // -------- cancelSellerApplication --------

    @Test
    fun cancelSellerApplication_flips_status_to_CANCELLED_and_preserves_previousStatus(): Unit = runBlocking {
        repo = construct()
        val uid = "seller-${UUID.randomUUID().toString().take(6)}"
        // Seed with PENDING businessInfo
        firestore.collection(USER_COLLECTION).document(uid)
            .set(
                mapOf(
                    "uuid" to uid,
                    "businessInfo" to mapOf(
                        "verificationStatus" to VerificationStatus.PENDING.name,
                        "isVerified" to false,
                    ),
                )
            ).await()

        repo.cancelSellerApplication(uid).getOrThrow()

        val doc = firestore.collection(USER_COLLECTION).document(uid).get().await()
        assertThat(doc.getString("businessInfo.verificationStatus"))
            .isEqualTo(VerificationStatus.CANCELLED.name)
        assertThat(doc.getBoolean("businessInfo.isVerified")).isFalse()
        assertThat(doc.getString("businessInfo.previousStatus"))
            .isEqualTo(VerificationStatus.PENDING.name)
        assertThat(doc.getString("updatedAt")).isNotEmpty()
    }

    @Test
    fun cancelSellerApplication_without_prior_status_does_not_set_previousStatus(): Unit = runBlocking {
        repo = construct()
        val uid = "seller-${UUID.randomUUID().toString().take(6)}"
        // Seed with no businessInfo block — emulates a customer cancelling a
        // half-filled seller application.
        firestore.collection(USER_COLLECTION).document(uid)
            .set(mapOf("uuid" to uid)).await()

        repo.cancelSellerApplication(uid).getOrThrow()

        val doc = firestore.collection(USER_COLLECTION).document(uid).get().await()
        assertThat(doc.getString("businessInfo.verificationStatus"))
            .isEqualTo(VerificationStatus.CANCELLED.name)
        assertThat(doc.getString("businessInfo.previousStatus")).isNull()
    }

    /**
     * Captures the User passed to onboardingComplete and writes it to Firestore
     * (mirroring the relevant production behaviour we want to observe). Every
     * other interface method is a benign no-op.
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
