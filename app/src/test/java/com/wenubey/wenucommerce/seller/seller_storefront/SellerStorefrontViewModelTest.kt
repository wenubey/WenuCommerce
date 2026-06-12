package com.wenubey.wenucommerce.seller.seller_storefront

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeFirestoreRepository
import com.wenubey.wenucommerce.testing.fakes.FakeProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerStorefrontViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        sellerId: String? = "seller-1",
        productRepo: FakeProductRepository = FakeProductRepository(),
        firestoreRepo: FakeFirestoreRepository = FakeFirestoreRepository(),
    ): SellerStorefrontViewModel {
        val savedState = SavedStateHandle().apply { sellerId?.let { set("sellerId", it) } }
        return SellerStorefrontViewModel(productRepo, firestoreRepo, savedState, dispatcherProvider)
    }

    @Test
    fun `null sellerId is no-op — no load, no error, no loading`() = runTest {
        val productRepo = FakeProductRepository()
        val firestoreRepo = FakeFirestoreRepository()
        val vm = newViewModel(sellerId = null, productRepo = productRepo, firestoreRepo = firestoreRepo)
        advanceUntilIdle()

        assertThat(firestoreRepo.getUserCalls).isEmpty()
        assertThat(vm.state.value.products).isEmpty()
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.errorMessage).isNull()
    }

    @Test
    fun `init loads seller name from businessName when present`() = runTest {
        val seller = User(
            uuid = "seller-1",
            name = "Alice",
            businessInfo = BusinessInfo(businessName = "Acme Co"),
        )
        val firestoreRepo = FakeFirestoreRepository().apply {
            getUserResult = { Result.success(seller) }
        }
        val productRepo = FakeProductRepository().apply {
            emitSellerProducts("seller-1", listOf(Product(id = "p-1", title = "Hat")))
        }
        val vm = newViewModel(productRepo = productRepo, firestoreRepo = firestoreRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.sellerName).isEqualTo("Acme Co")
    }

    @Test
    fun `init falls back to user name when businessName is null`() = runTest {
        val seller = User(uuid = "seller-1", name = "Alice", businessInfo = null)
        val firestoreRepo = FakeFirestoreRepository().apply {
            getUserResult = { Result.success(seller) }
        }
        val vm = newViewModel(firestoreRepo = firestoreRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.sellerName).isEqualTo("Alice")
    }

    @Test
    fun `init populates products and clears loading on success`() = runTest {
        val productRepo = FakeProductRepository().apply {
            emitSellerProducts("seller-1", listOf(
                Product(id = "p-1", title = "Hat"),
                Product(id = "p-2", title = "Shirt"),
            ))
        }
        val vm = newViewModel(productRepo = productRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.products).hasSize(2)
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.errorMessage).isNull()
    }

    @Test
    fun `seller lookup failure does NOT block product load (sellerName stays empty)`() = runTest {
        val firestoreRepo = FakeFirestoreRepository().apply {
            getUserResult = { Result.failure(RuntimeException("not found")) }
        }
        val productRepo = FakeProductRepository().apply {
            emitSellerProducts("seller-1", listOf(Product(id = "p-1", title = "Hat")))
        }
        val vm = newViewModel(productRepo = productRepo, firestoreRepo = firestoreRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.sellerName).isEmpty()
        assertThat(vm.state.value.products).hasSize(1)
        assertThat(vm.state.value.errorMessage).isNull()
    }
}
