package com.wenubey.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.util.DeviceInfoProvider
import com.wenubey.data.util.USER_COLLECTION
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.util.AuthProvider
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for FirestoreRepositoryImpl against the Firestore + Auth
 * emulators. The CredentialManager-backed paths in AuthRepositoryImpl are not
 * involved here, so this class can construct a fresh repo per test without the
 * listener-leak workaround needed for AuthRepositoryImplEmulatorTest.
 *
 * Excluded paths:
 *   - updateSignedDevice (calls the 'handleDeviceLogin' Cloud Function which
 *     isn't deployed in the local Functions emulator setup).
 *   - profile photo upload inside onboardingComplete (covered by passing http
 *     URIs that skip Storage).
 *   - addUserToFirestore is a no-op in current production code (Result.success
 *     unconditionally) — pinned by a single assertion test below.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class FirestoreRepositoryImplEmulatorTest {

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
    private val functions: FirebaseFunctions by lazy { Firebase.functions }

    // DeviceInfoProvider is only used by updateSignedDevice (Cloud Function path),
    // which this test class does not exercise. Use a relaxed mockk so the
    // constructor is satisfied; any accidental call would throw via mockk's
    // default unmocked-call detection.
    private val deviceInfoProvider: DeviceInfoProvider = mockk(relaxed = true)
    private val repo by lazy {
        FirestoreRepositoryImpl(
            firestore = firestore,
            storage = storage,
            auth = auth,
            dispatcherProvider = dispatcherProvider,
            deviceInfoProvider = deviceInfoProvider,
            firebaseFunctions = functions,
        )
    }

    @Before
    fun resetState() {
        FirebaseEmulator.clearAuth()
        FirebaseEmulator.clearFirestore()
        auth.signOut()
    }

    private fun uid() = "uid-${UUID.randomUUID().toString().take(8)}"

    private suspend fun seedUserDoc(
        uid: String,
        email: String = "$uid@test.dev",
        role: UserRole = UserRole.CUSTOMER,
        businessInfo: BusinessInfo? = null,
    ): User {
        val user = User(
            uuid = uid,
            email = email,
            role = role,
            name = "Test",
            surname = "User",
            createdAt = "0",
            updatedAt = "0",
            businessInfo = businessInfo,
        )
        firestore.collection(USER_COLLECTION).document(uid).set(user).await()
        return user
    }

    private suspend fun seedAnonymousAuth(): String =
        FirebaseEmulator.signInAnonymous()

    // -------- addUserToFirestore (no-op pin) --------

    @Test
    fun addUserToFirestore_is_a_noop_success(): Unit = runBlocking {
        val result = repo.addUserToFirestore(firebaseUser = null, authProvider = AuthProvider.GOOGLE)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isTrue()
    }

    // -------- getUser --------

    @Test
    fun getUser_returns_user_when_doc_exists(): Unit = runBlocking {
        val id = uid()
        seedUserDoc(id, email = "alice@test.dev", role = UserRole.SELLER)

        val result = repo.getUser(id)

        assertThat(result.isSuccess).isTrue()
        val user = result.getOrThrow()
        assertThat(user.uuid).isEqualTo(id)
        assertThat(user.email).isEqualTo("alice@test.dev")
        assertThat(user.role).isEqualTo(UserRole.SELLER)
    }

    @Test
    fun getUser_returns_failure_when_doc_missing(): Unit = runBlocking {
        val result = repo.getUser("does-not-exist")

        assertThat(result.isFailure).isTrue()
    }

    // -------- onboardingComplete --------

    @Test
    fun onboardingComplete_writes_user_doc_when_profile_photo_is_http_url(): Unit = runBlocking {
        val id = seedAnonymousAuth()
        val onboarding = User(
            uuid = id,
            email = "buyer@test.dev",
            name = "Buyer",
            surname = "One",
            role = UserRole.CUSTOMER,
            // http(s) URIs short-circuit the Storage upload path in updateProfilePhoto
            profilePhotoUri = "https://example.com/photo.jpg",
            createdAt = "0",
            updatedAt = "0",
        )

        val result = repo.onboardingComplete(onboarding)

        assertThat(result.isSuccess).isTrue()
        val doc = firestore.collection(USER_COLLECTION).document(id).get().await()
        assertThat(doc.exists()).isTrue()
        assertThat(doc.getString("email")).isEqualTo("buyer@test.dev")
        assertThat(doc.getString("profilePhotoUri")).isEqualTo("https://example.com/photo.jpg")
        assertThat(doc.getString("role")).isEqualTo(UserRole.CUSTOMER.name)
    }

    @Test
    fun onboardingComplete_uploads_local_file_uri_to_profile_photos_storage(): Unit = runBlocking {
        val id = seedAnonymousAuth()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File.createTempFile("photo-", ".jpg", ctx.cacheDir).apply {
            writeBytes("local-photo-bytes".toByteArray())
        }
        val onboarding = User(
            uuid = id,
            email = "buyer@test.dev",
            name = "Local",
            surname = "Photo",
            role = UserRole.CUSTOMER,
            profilePhotoUri = file.toURI().toString(),
            createdAt = "0",
            updatedAt = "0",
        )

        try {
            val result = repo.onboardingComplete(onboarding)

            assertThat(result.isSuccess).isTrue()
            val doc = firestore.collection(USER_COLLECTION).document(id).get().await()
            // FirestoreRepositoryImpl.updateProfilePhoto uploads to
            // 'profile_images/{uid}_profile_image.jpeg' (note: this is a
            // different folder convention than ProfileRepositoryImpl, which
            // uses 'profile_photos/{uid}/profile_image_{timestamp}.jpg' —
            // see TB-9 in the backfill tracker).
            val photoUri = doc.getString("profilePhotoUri")
            assertThat(photoUri).contains("/o/profile_images%2F${id}_profile_image.jpeg")
        } finally {
            file.delete()
        }
    }

    @Test
    fun onboardingComplete_returns_failure_when_uuid_is_null(): Unit = runBlocking {
        val onboarding = User(
            uuid = null,
            email = "noid@test.dev",
            profilePhotoUri = "https://example.com/p.jpg",
            createdAt = "0",
            updatedAt = "0",
        )

        val result = repo.onboardingComplete(onboarding)

        assertThat(result.isFailure).isTrue()
    }

    // -------- updateSellerApprovalStatus --------

    @Test
    fun updateSellerApprovalStatus_sets_status_and_preserves_previous(): Unit = runBlocking {
        val id = uid()
        seedUserDoc(
            id,
            role = UserRole.SELLER,
            businessInfo = BusinessInfo(verificationStatus = VerificationStatus.PENDING),
        )

        repo.updateSellerApprovalStatus(
            sellerId = id,
            status = VerificationStatus.APPROVED,
            notes = "Looks good",
        ).getOrThrow()

        val doc = firestore.collection(USER_COLLECTION).document(id).get().await()
        assertThat(doc.getString("businessInfo.verificationStatus"))
            .isEqualTo(VerificationStatus.APPROVED.name)
        assertThat(doc.getString("businessInfo.verificationNotes")).isEqualTo("Looks good")
        assertThat(doc.getBoolean("businessInfo.isVerified")).isTrue()
        assertThat(doc.getString("businessInfo.previousStatus"))
            .isEqualTo(VerificationStatus.PENDING.name)
        assertThat(doc.getString("businessInfo.verificationDate")).isNotEmpty()
        assertThat(doc.getString("updatedAt")).isNotEmpty()
    }

    @Test
    fun updateSellerApprovalStatus_rejected_sets_isVerified_false(): Unit = runBlocking {
        val id = uid()
        seedUserDoc(
            id,
            role = UserRole.SELLER,
            businessInfo = BusinessInfo(verificationStatus = VerificationStatus.RESUBMITTED),
        )

        repo.updateSellerApprovalStatus(
            sellerId = id,
            status = VerificationStatus.REJECTED,
            notes = "Missing tax id",
        ).getOrThrow()

        val doc = firestore.collection(USER_COLLECTION).document(id).get().await()
        assertThat(doc.getString("businessInfo.verificationStatus"))
            .isEqualTo(VerificationStatus.REJECTED.name)
        assertThat(doc.getBoolean("businessInfo.isVerified")).isFalse()
        assertThat(doc.getString("businessInfo.previousStatus"))
            .isEqualTo(VerificationStatus.RESUBMITTED.name)
    }

    // -------- observeSellersByStatus --------

    @Test
    fun observeSellersByStatus_emits_only_sellers_with_matching_status(): Unit = runBlocking {
        val approved = uid()
        val pending = uid()
        val customer = uid()
        seedUserDoc(
            approved,
            role = UserRole.SELLER,
            businessInfo = BusinessInfo(verificationStatus = VerificationStatus.APPROVED),
        )
        seedUserDoc(
            pending,
            role = UserRole.SELLER,
            businessInfo = BusinessInfo(verificationStatus = VerificationStatus.PENDING),
        )
        seedUserDoc(customer, role = UserRole.CUSTOMER)

        val emitted = withTimeoutOrNull(5_000) {
            repo.observeSellersByStatus(VerificationStatus.APPROVED)
                .first { it.isNotEmpty() }
        }

        assertThat(emitted).isNotNull()
        assertThat(emitted!!.map { it.uuid }).containsExactly(approved)
    }

    @Test
    fun observeSellersByStatus_emits_empty_when_no_sellers_match(): Unit = runBlocking {
        seedUserDoc(
            uid(),
            role = UserRole.SELLER,
            businessInfo = BusinessInfo(verificationStatus = VerificationStatus.PENDING),
        )

        val emitted = withTimeoutOrNull(5_000) {
            repo.observeSellersByStatus(VerificationStatus.APPROVED).first()
        }

        assertThat(emitted).isEqualTo(emptyList<User>())
    }

    // -------- observePendingResubmittedSellerCount --------

    @Test
    fun observePendingResubmittedSellerCount_counts_pending_and_resubmitted(): Unit = runBlocking {
        seedUserDoc(
            uid(),
            role = UserRole.SELLER,
            businessInfo = BusinessInfo(verificationStatus = VerificationStatus.PENDING),
        )
        seedUserDoc(
            uid(),
            role = UserRole.SELLER,
            businessInfo = BusinessInfo(verificationStatus = VerificationStatus.RESUBMITTED),
        )
        seedUserDoc(
            uid(),
            role = UserRole.SELLER,
            businessInfo = BusinessInfo(verificationStatus = VerificationStatus.APPROVED),
        )
        seedUserDoc(uid(), role = UserRole.CUSTOMER)

        val emitted = withTimeoutOrNull(5_000) {
            repo.observePendingResubmittedSellerCount().first { it == 2 }
        }

        assertThat(emitted).isEqualTo(2)
    }

    @Test
    fun observePendingResubmittedSellerCount_emits_zero_when_no_matches(): Unit = runBlocking {
        seedUserDoc(uid(), role = UserRole.CUSTOMER)

        val emitted = withTimeoutOrNull(5_000) {
            repo.observePendingResubmittedSellerCount().first()
        }

        assertThat(emitted).isEqualTo(0)
    }

}
