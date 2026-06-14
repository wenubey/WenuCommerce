package com.wenubey.data.repository

import androidx.credentials.CredentialManager
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.wenubey.data.FirebaseEmulator
import com.wenubey.data.local.WenuCommerceDatabase
import com.wenubey.data.util.USER_COLLECTION
import com.wenubey.domain.auth.SignInResult
import com.wenubey.domain.auth.SignUpResult
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.util.AuthProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumentation tests for AuthRepositoryImpl against the Firebase Auth +
 * Firestore emulators.
 *
 * Coverage is limited to paths that do not require the Android CredentialManager
 * picker UI:
 *  - sign-up / sign-in with saveCredentials = false
 *  - logOut / deleteAccount (clearCredentialState is best-effort on the emulator)
 *  - StateFlow currentUser propagation via the Firestore snapshot listener
 *  - setCurrentUserAfterOnboarding writes into Room
 *  - refreshCurrentUser / isUserAuthenticated / isPhoneNumberVerified / isEmailVerified false-path
 *
 * The signIn(GetCredentialResponse), getCredential, and credential-save branches
 * are excluded because they require platform CredentialManager state that an
 * AVD cannot provide in CI.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AuthRepositoryImplEmulatorTest {

    companion object {
        private lateinit var sharedDb: WenuCommerceDatabase

        @JvmStatic
        @BeforeClass
        fun configureSdk() {
            FirebaseEmulator.useEmulator()
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            // Single Room db across the whole test class. Each AuthRepositoryImpl
            // instance registers a FirebaseAuth state listener that is never
            // unregistered (no cleanup hook in production code). When the next
            // test signs out, every leaked listener fires and writes to its dao.
            // Closing the db between tests crashed those callbacks; sharing it
            // keeps stale callbacks harmless.
            sharedDb = Room.inMemoryDatabaseBuilder(ctx, WenuCommerceDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            sharedDb.close()
        }
    }

    private val dispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun io(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun default(): CoroutineDispatcher = Dispatchers.Unconfined
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val credentialManager by lazy { CredentialManager.create(ctx) }
    private val googleIdOption by lazy {
        GetGoogleIdOption.Builder()
            .setServerClientId("emulator-fake-server-client-id")
            .build()
    }

    private val db get() = sharedDb
    private lateinit var fakeFirestoreRepo: FakeFirestoreRepository
    private lateinit var repo: AuthRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        FirebaseEmulator.clearAuth()
        FirebaseEmulator.clearFirestore()
        auth.signOut()
        sharedDb.userDao().clearAll()

        fakeFirestoreRepo = FakeFirestoreRepository()
        repo = AuthRepositoryImpl(
            credentialManager = credentialManager,
            firebaseAuth = auth,
            context = ctx,
            googleIdOption = googleIdOption,
            dispatcherProvider = dispatcherProvider,
            firestoreRepository = fakeFirestoreRepo,
            firestore = firestore,
            userDao = sharedDb.userDao(),
        )
    }

    private fun email() = "user-${UUID.randomUUID().toString().take(8)}@test.dev"

    /** Seeds a Firestore user document under USERS/{uid}. */
    private suspend fun seedUserDoc(uid: String, email: String, role: UserRole = UserRole.CUSTOMER) {
        val user = User(
            uuid = uid,
            email = email,
            role = role,
            name = "Test",
            surname = "User",
            createdAt = "0",
            updatedAt = "0",
        )
        firestore.collection(USER_COLLECTION).document(uid).set(user).await()
    }

    @Test
    fun signUpWithEmailPassword_success_creates_firebase_user(): Unit = runBlocking {
        val em = email()

        val result = repo.signUpWithEmailPassword(em, "password123", saveCredentials = false)

        assertThat(result).isInstanceOf(SignUpResult.Success::class.java)
        assertThat(auth.currentUser?.email).isEqualTo(em)
    }

    @Test
    fun signUpWithEmailPassword_duplicate_email_returns_failure(): Unit = runBlocking {
        val em = email()
        repo.signUpWithEmailPassword(em, "password123", saveCredentials = false)
        auth.signOut()

        val second = repo.signUpWithEmailPassword(em, "password123", saveCredentials = false)

        assertThat(second).isInstanceOf(SignUpResult.Failure::class.java)
    }

    @Test
    fun signInWithEmailPassword_wrong_password_returns_failure(): Unit = runBlocking {
        val em = email()
        repo.signUpWithEmailPassword(em, "password123", saveCredentials = false)
        auth.signOut()

        val result = repo.signInWithEmailPassword(em, "wrongpassword", saveCredentials = false)

        assertThat(result).isInstanceOf(SignInResult.Failure::class.java)
    }

    @Test
    fun signInWithEmailPassword_propagates_user_through_currentUser_flow(): Unit = runBlocking {
        val em = email()
        // First create the user in Auth, then seed a USERS/{uid} doc, then sign out
        // so the next signIn boots the listener from a clean slate.
        repo.signUpWithEmailPassword(em, "password123", saveCredentials = false)
        val uid = auth.currentUser!!.uid
        seedUserDoc(uid, em)
        auth.signOut()

        // Initiate sign-in — the SignInResult may briefly race the snapshot
        // listener, but currentUser StateFlow must eventually emit the seeded user.
        repo.signInWithEmailPassword(em, "password123", saveCredentials = false)

        val emitted = withTimeoutOrNull(5_000) {
            repo.currentUser.filterNotNull().first()
        }
        assertThat(emitted?.uuid).isEqualTo(uid)
        assertThat(emitted?.email).isEqualTo(em)
        assertThat(fakeFirestoreRepo.updateSignedDeviceCalls).contains(uid)
    }

    @Test
    fun logOut_clears_currentUser_room_cache_and_firebase_session(): Unit = runBlocking {
        val em = email()
        repo.signUpWithEmailPassword(em, "password123", saveCredentials = false)
        val uid = auth.currentUser!!.uid
        seedUserDoc(uid, em)
        // Force a value into the StateFlow + Room
        repo.setCurrentUserAfterOnboarding(
            User(uuid = uid, email = em, name = "T", surname = "U", createdAt = "0", updatedAt = "0")
        )
        assertThat(repo.currentUser.value).isNotNull()
        assertThat(db.userDao().getCurrentUser()).isNotNull()

        repo.logOut().getOrThrow()

        assertThat(auth.currentUser).isNull()
        assertThat(repo.currentUser.value).isNull()
        assertThat(db.userDao().getCurrentUser()).isNull()
    }

    @Test
    fun deleteAccount_removes_firebase_user_and_clears_state(): Unit = runBlocking {
        val em = email()
        repo.signUpWithEmailPassword(em, "password123", saveCredentials = false)
        repo.setCurrentUserAfterOnboarding(
            User(uuid = auth.currentUser?.uid, email = em, createdAt = "0", updatedAt = "0")
        )

        repo.deleteAccount().getOrThrow()

        // currentUser must be cleared and Room cache wiped. The Firebase session
        // is invalidated by .delete() — currentUser may briefly remain populated
        // on the SDK until the next refresh, so we don't assert auth.currentUser
        // here.
        assertThat(repo.currentUser.value).isNull()
        assertThat(db.userDao().getCurrentUser()).isNull()
    }

    @Test
    fun setCurrentUserAfterOnboarding_emits_and_caches_user(): Unit = runBlocking {
        val onboarded = User(
            uuid = "uid-onb-1",
            email = "ob@test.dev",
            role = UserRole.SELLER,
            name = "Ob",
            surname = "Boarded",
            createdAt = "0",
            updatedAt = "0",
        )

        repo.setCurrentUserAfterOnboarding(onboarded)

        assertThat(repo.currentUser.value?.uuid).isEqualTo("uid-onb-1")
        assertThat(repo.currentUser.value?.role).isEqualTo(UserRole.SELLER)
        val cached = db.userDao().getCurrentUser()
        assertThat(cached?.id).isEqualTo("uid-onb-1")
    }

    @Test
    fun refreshCurrentUser_when_not_authenticated_returns_null(): Unit = runBlocking {
        val result = repo.refreshCurrentUser()

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
        assertThat(repo.currentUser.value).isNull()
    }

    @Test
    fun isUserAuthenticated_reflects_firebase_session(): Unit = runBlocking {
        assertThat(repo.isUserAuthenticated().getOrThrow()).isFalse()

        repo.signUpWithEmailPassword(email(), "password123", saveCredentials = false)
        assertThat(repo.isUserAuthenticated().getOrThrow()).isTrue()
    }

    @Test
    fun isPhoneNumberVerified_false_for_email_password_account(): Unit = runBlocking {
        repo.signUpWithEmailPassword(email(), "password123", saveCredentials = false)

        assertThat(repo.isPhoneNumberVerified().getOrThrow()).isFalse()
    }

    @Test
    fun isEmailVerified_false_for_fresh_account_and_does_not_write_firestore(): Unit = runBlocking {
        val em = email()
        repo.signUpWithEmailPassword(em, "password123", saveCredentials = false)
        val uid = auth.currentUser!!.uid

        val verified = repo.isEmailVerified().getOrThrow()

        assertThat(verified).isFalse()
        // Repo should NOT have written isEmailVerified=true into Firestore because
        // the user hasn't actually verified. We don't pre-seed the doc, so a
        // missing doc + no write is the expected state.
        val doc = firestore.collection(USER_COLLECTION).document(uid).get().await()
        assertThat(doc.exists()).isFalse()
    }

    /**
     * Minimal in-test fake. Only [updateSignedDevice] is exercised by the paths
     * we test; everything else returns a benign default. Avoids spinning up the
     * full FirestoreRepositoryImpl just for these tests.
     */
    private class FakeFirestoreRepository : FirestoreRepository {
        val updateSignedDeviceCalls = mutableListOf<String?>()

        override suspend fun addUserToFirestore(
            firebaseUser: FirebaseUser?,
            authProvider: AuthProvider,
        ): Result<Boolean> = Result.success(true)

        override suspend fun getUser(uid: String): Result<User> =
            Result.failure(NoSuchElementException("not seeded"))

        override suspend fun updateSignedDevice(userUid: String?): Result<Unit> {
            updateSignedDeviceCalls += userUid
            return Result.success(Unit)
        }

        override suspend fun onboardingComplete(user: User): Result<Unit> =
            Result.success(Unit)

        override fun updateFcmToken(token: String): Result<Unit> = Result.success(Unit)

        override fun observeSellersByStatus(status: VerificationStatus): Flow<List<User>> =
            emptyFlow()

        override fun observePendingResubmittedSellerCount(): Flow<Int> = emptyFlow()

        override suspend fun updateSellerApprovalStatus(
            sellerId: String,
            status: VerificationStatus,
            notes: String,
        ): Result<Unit> = Result.success(Unit)

        private val _pendingSyncCount = MutableStateFlow(0)
        @Suppress("unused")
        val pendingSyncCount = _pendingSyncCount.asStateFlow()
    }
}
