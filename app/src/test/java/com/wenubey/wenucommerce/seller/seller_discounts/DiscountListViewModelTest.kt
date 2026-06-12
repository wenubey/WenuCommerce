package com.wenubey.wenucommerce.seller.seller_discounts

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.discount.DiscountCode
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeDiscountRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscountListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Suppress("unused")
    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        discount: FakeDiscountRepository = FakeDiscountRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(
            initialUser = User(uuid = "seller-1", role = UserRole.SELLER),
        ),
    ) = DiscountListViewModel(discount, auth)

    @Test
    fun `seller observes only their own discount codes`() = runTest {
        val auth = FakeAuthRepository(
            initialUser = User(uuid = "seller-1", role = UserRole.SELLER),
        )
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount, auth = auth)

        discount.emit("seller-1", listOf(
            DiscountCode(code = "S1", sellerId = "seller-1"),
        ))
        advanceUntilIdle()

        assertThat(vm.state.value.discounts.map { it.code }).containsExactly("S1")
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `admin observes ALL discount codes via empty seller id`() = runTest {
        val auth = FakeAuthRepository(
            initialUser = User(uuid = "admin-1", role = UserRole.ADMIN),
        )
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount, auth = auth)

        // Admin queries with empty sellerId — emit there.
        discount.emit("", listOf(
            DiscountCode(code = "G1", sellerId = "seller-1"),
            DiscountCode(code = "G2", sellerId = "seller-2"),
        ))
        advanceUntilIdle()

        assertThat(vm.state.value.discounts.map { it.code }).containsExactly("G1", "G2")
    }

    @Test
    fun `null user defaults sellerId to empty (no codes, no crash)`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount, auth = auth)

        discount.emit("", emptyList())
        advanceUntilIdle()

        assertThat(vm.state.value.discounts).isEmpty()
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `observe error surfaces error message and stops loading`() = runTest {
        val discount = FakeDiscountRepository().apply {
            observeFlow = flow { throw RuntimeException("network") }
        }
        val vm = newViewModel(discount = discount)
        advanceUntilIdle()

        assertThat(vm.state.value.error).isEqualTo("network")
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `Delete success keeps error null`() = runTest {
        val discount = FakeDiscountRepository().apply { deleteResult = Result.success(Unit) }
        val vm = newViewModel(discount = discount)
        advanceUntilIdle()

        vm.onAction(DiscountListAction.Delete("ABC"))
        advanceUntilIdle()

        assertThat(discount.deleteCalls).containsExactly("ABC")
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `Delete failure surfaces error message`() = runTest {
        val discount = FakeDiscountRepository().apply {
            deleteResult = Result.failure(RuntimeException("perm denied"))
        }
        val vm = newViewModel(discount = discount)
        advanceUntilIdle()

        vm.onAction(DiscountListAction.Delete("ABC"))
        advanceUntilIdle()

        assertThat(vm.state.value.error).isEqualTo("perm denied")
    }

    @Test
    fun `Deactivate success keeps error null`() = runTest {
        val discount = FakeDiscountRepository().apply { deactivateResult = Result.success(Unit) }
        val vm = newViewModel(discount = discount)
        advanceUntilIdle()

        vm.onAction(DiscountListAction.Deactivate("ABC"))
        advanceUntilIdle()

        assertThat(discount.deactivateCalls).containsExactly("ABC")
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `Deactivate failure surfaces error message`() = runTest {
        val discount = FakeDiscountRepository().apply {
            deactivateResult = Result.failure(RuntimeException("nope"))
        }
        val vm = newViewModel(discount = discount)
        advanceUntilIdle()

        vm.onAction(DiscountListAction.Deactivate("ABC"))
        advanceUntilIdle()

        assertThat(vm.state.value.error).isEqualTo("nope")
    }

    @Test
    fun `DismissError clears error to null`() = runTest {
        val discount = FakeDiscountRepository().apply {
            deleteResult = Result.failure(RuntimeException("boom"))
        }
        val vm = newViewModel(discount = discount)
        advanceUntilIdle()

        vm.onAction(DiscountListAction.Delete("ABC"))
        advanceUntilIdle()
        assertThat(vm.state.value.error).isEqualTo("boom")

        vm.onAction(DiscountListAction.DismissError)
        advanceUntilIdle()
        assertThat(vm.state.value.error).isNull()
    }
}
