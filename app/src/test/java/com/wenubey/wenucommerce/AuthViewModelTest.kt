package com.wenubey.wenucommerce

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.wenucommerce.navigation.AdminTab
import com.wenubey.wenucommerce.navigation.CustomerTab
import com.wenubey.wenucommerce.navigation.SellerTab
import com.wenubey.wenucommerce.navigation.SignUp
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeWishlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        auth: FakeAuthRepository = FakeAuthRepository(),
        wishlist: FakeWishlistRepository = FakeWishlistRepository(),
    ) = AuthViewModel(auth, dispatcherProvider, wishlist)

    @Test
    fun `initial start destination is SignUp before init completes`() = runTest {
        val vm = newViewModel()
        // No advance — observe the first state without letting coroutines run.
        assertThat(vm.startDestination.value).isEqualTo(SignUp)
        assertThat(vm.isInitialized.value).isFalse()
    }

    @Test
    fun `unauthenticated user routes to SignUp and marks initialized`() = runTest {
        val auth = FakeAuthRepository(initialUser = null).apply { hasFirebaseUser = false }
        val vm = newViewModel(auth = auth)

        advanceUntilIdle()

        assertThat(vm.startDestination.value).isEqualTo(SignUp)
        assertThat(vm.isInitialized.value).isTrue()
    }

    @Test
    fun `CUSTOMER user routes to CustomerTab`() = runTest {
        val customer = User(uuid = "u-1", role = UserRole.CUSTOMER)
        val vm = newViewModel(auth = FakeAuthRepository(initialUser = customer))

        advanceUntilIdle()

        assertThat(vm.startDestination.value).isEqualTo(CustomerTab(0))
        assertThat(vm.isInitialized.value).isTrue()
    }

    @Test
    fun `SELLER user routes to SellerTab`() = runTest {
        val seller = User(uuid = "u-1", role = UserRole.SELLER)
        val vm = newViewModel(auth = FakeAuthRepository(initialUser = seller))

        advanceUntilIdle()

        assertThat(vm.startDestination.value).isEqualTo(SellerTab(0))
    }

    @Test
    fun `ADMIN user routes to AdminTab`() = runTest {
        val admin = User(uuid = "u-1", role = UserRole.ADMIN)
        val vm = newViewModel(auth = FakeAuthRepository(initialUser = admin))

        advanceUntilIdle()

        assertThat(vm.startDestination.value).isEqualTo(AdminTab(0))
    }

    // NOTE: The "firebase user authenticated but profile not yet loaded" branch
    // (which trips the INIT_TIMEOUT_MS Onboarding fallback) cannot be exercised
    // here because AuthRepository.currentFirebaseUser exposes the platform
    // FirebaseUser type — a domain layer leak that isn't trivially instantiable
    // in a JVM test. Tracked in PRODUCT_BUGS_AND_GAPS.md (TB-2 AuthRepository
    // domain leak). The timeout path is therefore covered only by the
    // unauthenticated-and-no-profile case via the "SignUp" branch, which uses
    // the same _isInitialized = true outcome.

    @Test
    fun `login transition (null then user) triggers anonymous wishlist sync`() = runTest {
        val auth = FakeAuthRepository(initialUser = null).apply { hasFirebaseUser = false }
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(auth = auth, wishlist = wishlist)

        advanceUntilIdle()
        assertThat(wishlist.syncAnonymousOnLoginCalls).isEmpty()

        auth.emitUser(User(uuid = "u-1", role = UserRole.CUSTOMER))
        advanceUntilIdle()

        assertThat(wishlist.syncAnonymousOnLoginCalls).containsExactly("u-1")
    }

    @Test
    fun `wishlist sync failure does not block initialization`() = runTest {
        val auth = FakeAuthRepository(initialUser = null).apply { hasFirebaseUser = false }
        val wishlist = FakeWishlistRepository().apply {
            syncAnonymousThrows = RuntimeException("sync failed")
        }
        val vm = newViewModel(auth = auth, wishlist = wishlist)

        advanceUntilIdle()
        auth.emitUser(User(uuid = "u-1", role = UserRole.CUSTOMER))
        advanceUntilIdle()

        // Sync attempted, threw, but VM moved on.
        assertThat(wishlist.syncAnonymousOnLoginCalls).containsExactly("u-1")
        assertThat(vm.isInitialized.value).isTrue()
        assertThat(vm.startDestination.value).isEqualTo(CustomerTab(0))
    }

    @Test
    fun `wishlist sync only fires once on login transition, not on subsequent emits`() = runTest {
        val auth = FakeAuthRepository(initialUser = null).apply { hasFirebaseUser = false }
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(auth = auth, wishlist = wishlist)

        advanceUntilIdle()

        val user = User(uuid = "u-1", role = UserRole.CUSTOMER)
        auth.emitUser(user)
        advanceUntilIdle()

        // Re-emit the same user (e.g., profile field changed). Sync MUST NOT
        // run again — that's the previousUserId guard's whole purpose.
        auth.emitUser(user.copy(name = "Updated"))
        advanceUntilIdle()

        assertThat(wishlist.syncAnonymousOnLoginCalls).containsExactly("u-1")
    }

    @Test
    fun `currentUser exposes repository flow directly`() = runTest {
        val initial = User(uuid = "u-1", role = UserRole.CUSTOMER, name = "Alice")
        val auth = FakeAuthRepository(initialUser = initial)
        val vm = newViewModel(auth = auth)

        advanceUntilIdle()
        assertThat(vm.currentUser.value).isEqualTo(initial)

        auth.emitUser(initial.copy(name = "Alicia"))
        assertThat(vm.currentUser.value!!.name).isEqualTo("Alicia")
    }
}
