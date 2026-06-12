package com.wenubey.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.wenubey.data.FirebaseEmulator
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for TagRepositoryImpl against the Firestore + Auth emulators.
 * resolveOrCreateTag requires a signed-in user (createdBy field), so the suite
 * relies on the Auth emulator's anonymous sign-in.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class TagRepositoryImplEmulatorTest {

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
    private val repo by lazy { TagRepositoryImpl(firestore, auth, dispatcherProvider) }

    @Before
    fun resetState() {
        // Only wipe Firestore between tests. The Auth account stays signed in
        // across tests in this class — none of our assertions depend on a
        // specific uid, only on a non-null currentUser. Recreating the auth
        // account every test causes the SDK to fight stale token cache races
        // (HTTP DELETE on the emulator deletes the account but the SDK retains
        // the in-memory user until the next auth state callback fires).
        FirebaseEmulator.clearFirestore()
        runBlocking { FirebaseEmulator.signInAnonymous() }
    }

    @Test
    fun resolveOrCreateTag_creates_new_tag_with_normalised_name_and_original_display_name(): Unit = runBlocking {
        val result = repo.resolveOrCreateTag("  Cotton  ")
        val tag = result.getOrThrow()
        assertThat(tag.name).isEqualTo("cotton")          // trimmed + lowercased
        assertThat(tag.displayName).isEqualTo("Cotton")   // trimmed, original case
        assertThat(tag.id).isNotEmpty()
        assertThat(tag.createdBy).isEqualTo(auth.currentUser?.uid)

        // The tag is reachable through the repository's own read path —
        // searchTagsByPrefix uses the same Firestore collection. Avoid raw
        // SDK reads here because the local cache and the emulator's
        // REST-deleted state can race within one test method.
        val found = repo.searchTagsByPrefix("cot").getOrThrow()
        assertThat(found.map { it.id }).contains(tag.id)
    }

    @Test
    fun resolveOrCreateTag_returns_existing_tag_when_a_normalised_match_already_exists(): Unit = runBlocking {
        val first = repo.resolveOrCreateTag("Summer").getOrThrow()
        val second = repo.resolveOrCreateTag("SUMMER").getOrThrow()
        val third = repo.resolveOrCreateTag("  summer  ").getOrThrow()

        // Same id => no duplicate document was created; the repo's lookup
        // path correctly normalises and reuses.
        assertThat(second.id).isEqualTo(first.id)
        assertThat(third.id).isEqualTo(first.id)
    }

    @Test
    fun resolveOrCreateTag_fails_when_no_user_is_signed_in(): Unit = runBlocking {
        auth.signOut()
        val result = repo.resolveOrCreateTag("Solo")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun searchTagsByPrefix_returns_tags_whose_name_starts_with_the_prefix(): Unit = runBlocking {
        repo.resolveOrCreateTag("Summer").getOrThrow()
        repo.resolveOrCreateTag("Sunset").getOrThrow()
        repo.resolveOrCreateTag("Winter").getOrThrow()

        val sResults = repo.searchTagsByPrefix("su").getOrThrow()
        assertThat(sResults.map { it.name }).containsExactly("summer", "sunset")

        val winterResults = repo.searchTagsByPrefix("wi").getOrThrow()
        assertThat(winterResults.map { it.name }).containsExactly("winter")
    }

    @Test
    fun searchTagsByPrefix_returns_empty_list_for_blank_prefix(): Unit = runBlocking {
        repo.resolveOrCreateTag("Summer").getOrThrow()
        val results = repo.searchTagsByPrefix("   ").getOrThrow()
        assertThat(results).isEmpty()
    }

    @Test
    fun searchTagsByPrefix_respects_the_limit_argument(): Unit = runBlocking {
        repo.resolveOrCreateTag("alpha").getOrThrow()
        repo.resolveOrCreateTag("alpine").getOrThrow()
        repo.resolveOrCreateTag("altitude").getOrThrow()

        val results = repo.searchTagsByPrefix("al", limit = 2).getOrThrow()
        assertThat(results).hasSize(2)
    }

    @Test
    fun getTagsByIds_returns_only_existing_tags_for_the_given_ids(): Unit = runBlocking {
        val a = repo.resolveOrCreateTag("alpha").getOrThrow()
        val b = repo.resolveOrCreateTag("beta").getOrThrow()
        // Third id does not exist in Firestore.
        val results = repo.getTagsByIds(listOf(a.id, b.id, "missing")).getOrThrow()
        assertThat(results.map { it.id }).containsExactly(a.id, b.id)
    }

    @Test
    fun getTagsByIds_empty_input_returns_empty_list(): Unit = runBlocking {
        val results = repo.getTagsByIds(emptyList()).getOrThrow()
        assertThat(results).isEmpty()
    }
}
