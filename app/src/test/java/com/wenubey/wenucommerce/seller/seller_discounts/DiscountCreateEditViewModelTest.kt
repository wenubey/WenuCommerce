package com.wenubey.wenucommerce.seller.seller_discounts

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.discount.DiscountCode
import com.wenubey.domain.model.discount.DiscountType
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeDiscountRepository
import com.wenubey.wenucommerce.testing.fakes.FakeProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscountCreateEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Suppress("unused")
    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        savedState: SavedStateHandle = SavedStateHandle(mapOf("isSeller" to true)),
        discount: FakeDiscountRepository = FakeDiscountRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(
            initialUser = User(uuid = "seller-1", role = UserRole.SELLER),
        ),
        product: FakeProductRepository = FakeProductRepository(),
    ) = DiscountCreateEditViewModel(discount, auth, product, savedState)

    // --- Field updates ---

    @Test
    fun `UpdateCode uppercases the input`() = runTest {
        val vm = newViewModel()
        vm.onAction(DiscountCreateEditAction.UpdateCode("save20"))
        advanceUntilIdle()
        assertThat(vm.state.value.code).isEqualTo("SAVE20")
    }

    @Test
    fun `GenerateCode produces an 8-character uppercase alphanumeric without easily-confused chars`() = runTest {
        val vm = newViewModel()
        vm.onAction(DiscountCreateEditAction.GenerateCode)
        advanceUntilIdle()

        val code = vm.state.value.code
        assertThat(code).hasLength(8)
        assertThat(code).matches("^[A-HJ-NP-Z2-9]+$") // no I, O, 0, 1
    }

    @Test
    fun `UpdateType updates state type`() = runTest {
        val vm = newViewModel()
        vm.onAction(DiscountCreateEditAction.UpdateType(DiscountType.FREE_SHIPPING))
        advanceUntilIdle()
        assertThat(vm.state.value.type).isEqualTo(DiscountType.FREE_SHIPPING)
    }

    @Test
    fun `simple field updates flow through unchanged`() = runTest {
        val vm = newViewModel()
        vm.onAction(DiscountCreateEditAction.UpdateValue("25"))
        vm.onAction(DiscountCreateEditAction.UpdateMaxCap("50"))
        vm.onAction(DiscountCreateEditAction.UpdateMinOrder("10"))
        vm.onAction(DiscountCreateEditAction.UpdateUsageLimit("100"))
        vm.onAction(DiscountCreateEditAction.UpdateExpiryDate(1_700_000_000_000L))
        vm.onAction(DiscountCreateEditAction.UpdateProductSearch("blue"))
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.value).isEqualTo("25")
        assertThat(s.maxDiscountCap).isEqualTo("50")
        assertThat(s.minimumOrderAmount).isEqualTo("10")
        assertThat(s.usageLimit).isEqualTo("100")
        assertThat(s.expiresAt).isEqualTo(1_700_000_000_000L)
        assertThat(s.productSearchQuery).isEqualTo("blue")
    }

    @Test
    fun `ToggleProduct adds, then removes a productId and flips picker isSelected`() = runTest {
        val product = FakeProductRepository()
        product.emitSellerProducts("seller-1", listOf(
            Product(id = "p-1", title = "Red"),
            Product(id = "p-2", title = "Blue"),
        ))
        val vm = newViewModel(product = product)
        advanceUntilIdle()

        vm.onAction(DiscountCreateEditAction.ToggleProduct("p-1"))
        advanceUntilIdle()
        assertThat(vm.state.value.targetProductIds).containsExactly("p-1")
        assertThat(vm.state.value.availableProducts.first { it.productId == "p-1" }.isSelected).isTrue()

        vm.onAction(DiscountCreateEditAction.ToggleProduct("p-1"))
        advanceUntilIdle()
        assertThat(vm.state.value.targetProductIds).isEmpty()
        assertThat(vm.state.value.availableProducts.first { it.productId == "p-1" }.isSelected).isFalse()
    }

    // --- Validation ---

    @Test
    fun `Save with blank code surfaces saveError and does not call repository`() = runTest {
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount)
        advanceUntilIdle()

        vm.onAction(DiscountCreateEditAction.Save)
        advanceUntilIdle()

        assertThat(vm.state.value.saveError).isEqualTo("Coupon code is required")
        assertThat(discount.createCalls).isEmpty()
    }

    @Test
    fun `Save percentage above 100 surfaces saveError`() = runTest {
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount)
        vm.onAction(DiscountCreateEditAction.UpdateCode("SAVE"))
        vm.onAction(DiscountCreateEditAction.UpdateType(DiscountType.PERCENTAGE))
        vm.onAction(DiscountCreateEditAction.UpdateValue("150"))
        vm.onAction(DiscountCreateEditAction.Save)
        advanceUntilIdle()

        assertThat(vm.state.value.saveError).isEqualTo("Percentage must be between 0 and 100")
        assertThat(discount.createCalls).isEmpty()
    }

    @Test
    fun `Save with non-positive value surfaces saveError`() = runTest {
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount)
        vm.onAction(DiscountCreateEditAction.UpdateCode("SAVE"))
        vm.onAction(DiscountCreateEditAction.UpdateValue("0"))
        vm.onAction(DiscountCreateEditAction.Save)
        advanceUntilIdle()

        assertThat(vm.state.value.saveError).isEqualTo("Value must be greater than 0")
    }

    @Test
    fun `Save with FREE_SHIPPING skips value validation`() = runTest {
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount)
        vm.onAction(DiscountCreateEditAction.UpdateCode("SHIP"))
        vm.onAction(DiscountCreateEditAction.UpdateType(DiscountType.FREE_SHIPPING))
        // value left blank intentionally
        vm.onAction(DiscountCreateEditAction.Save)
        advanceUntilIdle()

        assertThat(vm.state.value.saveSuccess).isTrue()
        assertThat(discount.createCalls).hasSize(1)
        assertThat(discount.createCalls[0].type).isEqualTo(DiscountType.FREE_SHIPPING)
        assertThat(discount.createCalls[0].value).isEqualTo(0.0)
    }

    // --- Create / update flows ---

    @Test
    fun `Save in create mode trims and uppercases code, attaches sellerId, calls createDiscountCode`() = runTest {
        val auth = FakeAuthRepository(
            initialUser = User(uuid = "seller-1", role = UserRole.SELLER),
        )
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount, auth = auth)

        vm.onAction(DiscountCreateEditAction.UpdateCode("  save20  "))
        vm.onAction(DiscountCreateEditAction.UpdateValue("20"))
        vm.onAction(DiscountCreateEditAction.UpdateMaxCap("100"))
        vm.onAction(DiscountCreateEditAction.UpdateMinOrder("50"))
        vm.onAction(DiscountCreateEditAction.UpdateUsageLimit("10"))
        vm.onAction(DiscountCreateEditAction.UpdateExpiryDate(1_700_000_000_000L))
        vm.onAction(DiscountCreateEditAction.Save)
        advanceUntilIdle()

        assertThat(vm.state.value.saveSuccess).isTrue()
        assertThat(vm.state.value.isSaving).isFalse()
        assertThat(discount.updateCalls).isEmpty()
        assertThat(discount.createCalls).hasSize(1)
        val saved = discount.createCalls[0]
        assertThat(saved.code).isEqualTo("SAVE20")
        assertThat(saved.value).isEqualTo(20.0)
        assertThat(saved.maxDiscountCap).isEqualTo(100.0)
        assertThat(saved.minimumOrderAmount).isEqualTo(50.0)
        assertThat(saved.usageLimit).isEqualTo(10)
        assertThat(saved.sellerId).isEqualTo("seller-1")
        assertThat(saved.isActive).isTrue()
    }

    @Test
    fun `Save as ADMIN attaches empty sellerId (cross-seller code)`() = runTest {
        val auth = FakeAuthRepository(
            initialUser = User(uuid = "admin-1", role = UserRole.ADMIN),
        )
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount, auth = auth)

        vm.onAction(DiscountCreateEditAction.UpdateCode("GLOBAL"))
        vm.onAction(DiscountCreateEditAction.UpdateValue("10"))
        vm.onAction(DiscountCreateEditAction.Save)
        advanceUntilIdle()

        assertThat(discount.createCalls[0].sellerId).isEmpty()
    }

    @Test
    fun `Save in edit mode calls updateDiscountCode, not create`() = runTest {
        // Pre-load an existing discount.
        val existing = DiscountCode(
            code = "OLD",
            type = DiscountType.PERCENTAGE,
            value = 10.0,
            sellerId = "seller-1",
        )
        val discount = FakeDiscountRepository().apply {
            emit("seller-1", listOf(existing))
        }
        val savedState = SavedStateHandle(mapOf("isSeller" to true, "code" to "OLD"))
        val vm = newViewModel(savedState = savedState, discount = discount)
        advanceUntilIdle()

        // Should have loaded into edit mode with the existing fields.
        assertThat(vm.state.value.isEditMode).isTrue()
        assertThat(vm.state.value.code).isEqualTo("OLD")
        assertThat(vm.state.value.value).isEqualTo("10.0")

        // Save should hit update, not create.
        vm.onAction(DiscountCreateEditAction.Save)
        advanceUntilIdle()

        assertThat(discount.createCalls).isEmpty()
        assertThat(discount.updateCalls).hasSize(1)
        assertThat(discount.updateCalls[0].code).isEqualTo("OLD")
    }

    @Test
    fun `Save repository failure surfaces saveError and clears isSaving`() = runTest {
        val discount = FakeDiscountRepository().apply {
            createResult = Result.failure(RuntimeException("server down"))
        }
        val vm = newViewModel(discount = discount)

        vm.onAction(DiscountCreateEditAction.UpdateCode("SAVE"))
        vm.onAction(DiscountCreateEditAction.UpdateValue("20"))
        vm.onAction(DiscountCreateEditAction.Save)
        advanceUntilIdle()

        assertThat(vm.state.value.saveError).isEqualTo("server down")
        assertThat(vm.state.value.isSaving).isFalse()
        assertThat(vm.state.value.saveSuccess).isFalse()
    }

    @Test
    fun `DismissError clears saveError`() = runTest {
        val discount = FakeDiscountRepository().apply {
            createResult = Result.failure(RuntimeException("boom"))
        }
        val vm = newViewModel(discount = discount)
        vm.onAction(DiscountCreateEditAction.UpdateCode("SAVE"))
        vm.onAction(DiscountCreateEditAction.UpdateValue("20"))
        vm.onAction(DiscountCreateEditAction.Save)
        advanceUntilIdle()
        assertThat(vm.state.value.saveError).isEqualTo("boom")

        vm.onAction(DiscountCreateEditAction.DismissError)
        advanceUntilIdle()
        assertThat(vm.state.value.saveError).isNull()
    }

    @Test
    fun `availableProducts populates from seller products and preserves selection state`() = runTest {
        val product = FakeProductRepository()
        product.emitSellerProducts("seller-1", listOf(
            Product(id = "p-1", title = "Red"),
            Product(id = "p-2", title = "Blue"),
        ))
        val vm = newViewModel(product = product)
        advanceUntilIdle()

        val items = vm.state.value.availableProducts
        assertThat(items.map { it.productId }).containsExactly("p-1", "p-2")
        assertThat(items.all { !it.isSelected }).isTrue()
    }
}
