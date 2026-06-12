package com.wenubey.wenucommerce.seller.seller_dashboard

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerDashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = User(uuid = "s-1", role = UserRole.SELLER)),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = SellerDashboardViewModel(dispatcherProvider, auth, savedState)

    @Test
    fun `init populates state with the current user`() = runTest {
        val seller = User(uuid = "s-1", name = "Alice", role = UserRole.SELLER)
        val vm = newViewModel(auth = FakeAuthRepository(initialUser = seller))
        advanceUntilIdle()

        assertThat(vm.sellerDashboardState.value.user).isEqualTo(seller)
    }

    @Test
    fun `currentUser emissions propagate into state`() = runTest {
        val auth = FakeAuthRepository(initialUser = User(uuid = "s-1", name = "Old"))
        val vm = newViewModel(auth = auth)
        advanceUntilIdle()
        assertThat(vm.sellerDashboardState.value.user!!.name).isEqualTo("Old")

        auth.emitUser(User(uuid = "s-1", name = "New"))
        advanceUntilIdle()
        assertThat(vm.sellerDashboardState.value.user!!.name).isEqualTo("New")
    }

    @Test
    fun `default isBannerVisible is true when SavedStateHandle has no key`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        assertThat(vm.sellerDashboardState.value.isBannerVisible).isTrue()
    }

    @Test
    fun `SavedStateHandle restores isBannerVisible across recreation`() = runTest {
        val savedState = SavedStateHandle(mapOf("banner_visible" to false))
        val vm = newViewModel(savedState = savedState)
        advanceUntilIdle()
        assertThat(vm.sellerDashboardState.value.isBannerVisible).isFalse()
    }

    @Test
    fun `HideBanner sets state and persists into SavedStateHandle`() = runTest {
        val savedState = SavedStateHandle()
        val vm = newViewModel(savedState = savedState)
        advanceUntilIdle()

        vm.onAction(SellerDashboardAction.HideBanner)
        advanceUntilIdle()

        assertThat(vm.sellerDashboardState.value.isBannerVisible).isFalse()
        assertThat(savedState.get<Boolean>("banner_visible")).isFalse()
    }

    @Test
    fun `OnAddProduct is a no-op (TODO) and does not mutate state`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        val before = vm.sellerDashboardState.value
        vm.onAction(SellerDashboardAction.OnAddProduct)
        advanceUntilIdle()
        assertThat(vm.sellerDashboardState.value).isEqualTo(before)
    }
}
