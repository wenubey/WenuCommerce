package com.wenubey.wenucommerce.customer.customer_cart

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeCartRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private val testUserId = "u-1"
    private val testUser = User(uuid = testUserId)

    private fun newViewModel(
        cart: FakeCartRepository = FakeCartRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = testUser),
    ) = CartViewModel(cart, auth, dispatcherProvider)

    private fun item(
        productId: String,
        quantity: Int = 1,
        snapshotPrice: Double = 10.0,
        availableStock: Int = 10,
        isProductDeleted: Boolean = false,
    ) = CartItem(
        productId = productId,
        productTitle = "Title $productId",
        quantity = quantity,
        snapshotPrice = snapshotPrice,
        availableStock = availableStock,
        isProductDeleted = isProductDeleted,
    )

    @Test
    fun `null userId shows please-log-in error and stops loading`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val vm = newViewModel(auth = auth)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.error).isEqualTo("Please log in to view your cart")
        assertThat(state.isLoading).isFalse()
        assertThat(state.cartItems).isEmpty()
    }

    @Test
    fun `observed cart items populate state and clear loading`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)

        cart.emitCartItems(testUserId, listOf(item("p-1"), item("p-2")))
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.cartItems).hasSize(2)
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun `observeCartItems error surfaces error message and stops loading`() = runTest {
        val cart = FakeCartRepository().apply {
            observeCartItemsFlow = flow { throw RuntimeException("network") }
        }
        val vm = newViewModel(cart = cart)
        advanceUntilIdle()

        assertThat(vm.state.value.error).isEqualTo("network")
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `subtotal sums available items only and ignores deleted or out-of-stock`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(
            item("p-1", quantity = 2, snapshotPrice = 5.0),                       // 10
            item("p-2", quantity = 3, snapshotPrice = 4.0),                       // 12
            item("p-3", quantity = 1, snapshotPrice = 100.0, isProductDeleted = true), // ignored
            item("p-4", quantity = 1, snapshotPrice = 50.0, availableStock = 0),   // ignored
        ))
        advanceUntilIdle()

        assertThat(vm.state.value.subtotal).isEqualTo(22.0)
        assertThat(vm.state.value.availableItemCount).isEqualTo(2)
        assertThat(vm.state.value.hasUnavailableItems).isTrue()
        assertThat(vm.state.value.canCheckout).isFalse()
    }

    @Test
    fun `canCheckout is true only when all items are available`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(item("p-1"), item("p-2")))
        advanceUntilIdle()

        assertThat(vm.state.value.canCheckout).isTrue()
    }

    @Test
    fun `IncrementQuantity below stock calls repository updateQuantity`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(item("p-1", quantity = 2, availableStock = 10)))
        advanceUntilIdle()

        vm.onAction(CartAction.IncrementQuantity("p-1"))
        advanceUntilIdle()

        assertThat(cart.updateQuantityCalls).containsExactly(Triple(testUserId, "p-1", 3))
    }

    @Test
    fun `IncrementQuantity at stock limit is no-op`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(item("p-1", quantity = 5, availableStock = 5)))
        advanceUntilIdle()

        vm.onAction(CartAction.IncrementQuantity("p-1"))
        advanceUntilIdle()

        assertThat(cart.updateQuantityCalls).isEmpty()
    }

    @Test
    fun `IncrementQuantity for missing productId is silently ignored`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, emptyList())
        advanceUntilIdle()

        vm.onAction(CartAction.IncrementQuantity("missing"))
        advanceUntilIdle()

        assertThat(cart.updateQuantityCalls).isEmpty()
    }

    @Test
    fun `DecrementQuantity above 1 calls repository updateQuantity with reduced value`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(item("p-1", quantity = 3)))
        advanceUntilIdle()

        vm.onAction(CartAction.DecrementQuantity("p-1"))
        advanceUntilIdle()

        assertThat(cart.updateQuantityCalls).containsExactly(Triple(testUserId, "p-1", 2))
        assertThat(cart.removeFromCartCalls).isEmpty()
    }

    @Test
    fun `DecrementQuantity at 1 removes item and stores undo item`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        val target = item("p-1", quantity = 1)
        cart.emitCartItems(testUserId, listOf(target))
        advanceUntilIdle()

        vm.onAction(CartAction.DecrementQuantity("p-1"))
        advanceUntilIdle()

        assertThat(cart.removeFromCartCalls).containsExactly(testUserId to "p-1")
        assertThat(vm.state.value.undoItem).isEqualTo(target)
    }

    @Test
    fun `DecrementQuantity at 1 with failed remove clears undo item`() = runTest {
        val cart = FakeCartRepository().apply {
            removeFromCartThrows = RuntimeException("server")
        }
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(item("p-1", quantity = 1)))
        advanceUntilIdle()

        vm.onAction(CartAction.DecrementQuantity("p-1"))
        advanceUntilIdle()

        assertThat(vm.state.value.undoItem).isNull()
    }

    @Test
    fun `RemoveItem stores undo item and calls remove`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        val target = item("p-1")
        cart.emitCartItems(testUserId, listOf(target))
        advanceUntilIdle()

        vm.onAction(CartAction.RemoveItem("p-1"))
        advanceUntilIdle()

        assertThat(vm.state.value.undoItem).isEqualTo(target)
        assertThat(cart.removeFromCartCalls).containsExactly(testUserId to "p-1")
    }

    @Test
    fun `UndoRemove clears undo item and calls restoreCartItem`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        val target = item("p-1")
        cart.emitCartItems(testUserId, listOf(target))
        advanceUntilIdle()

        vm.onAction(CartAction.UndoRemove(target))
        advanceUntilIdle()

        assertThat(vm.state.value.undoItem).isNull()
        assertThat(cart.restoreCartItemCalls).containsExactly(testUserId to target)
    }

    @Test
    fun `ToggleSelection adds and removes from selectedItems`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(item("p-1"), item("p-2")))
        advanceUntilIdle()

        vm.onAction(CartAction.ToggleSelection("p-1"))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedItems).containsExactly("p-1")

        vm.onAction(CartAction.ToggleSelection("p-2"))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedItems).containsExactly("p-1", "p-2")

        vm.onAction(CartAction.ToggleSelection("p-1"))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedItems).containsExactly("p-2")
    }

    @Test
    fun `DeleteSelected removes all selected items and exits selection mode`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(item("p-1"), item("p-2"), item("p-3")))
        advanceUntilIdle()

        vm.onAction(CartAction.ToggleSelectionMode)
        vm.onAction(CartAction.ToggleSelection("p-1"))
        vm.onAction(CartAction.ToggleSelection("p-3"))
        advanceUntilIdle()
        assertThat(vm.state.value.isSelectionMode).isTrue()

        vm.onAction(CartAction.DeleteSelected)
        advanceUntilIdle()

        assertThat(cart.removeFromCartCalls.map { it.second }).containsExactly("p-1", "p-3")
        assertThat(vm.state.value.selectedItems).isEmpty()
        assertThat(vm.state.value.isSelectionMode).isFalse()
    }

    @Test
    fun `ToggleSelectionMode flips mode and clears selection`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(item("p-1")))
        advanceUntilIdle()

        vm.onAction(CartAction.ToggleSelectionMode)
        vm.onAction(CartAction.ToggleSelection("p-1"))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedItems).containsExactly("p-1")

        // Flipping mode off must clear the selection.
        vm.onAction(CartAction.ToggleSelectionMode)
        advanceUntilIdle()
        assertThat(vm.state.value.isSelectionMode).isFalse()
        assertThat(vm.state.value.selectedItems).isEmpty()
    }

    @Test
    fun `ClearSelection empties selectedItems but keeps mode`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(item("p-1")))
        advanceUntilIdle()

        vm.onAction(CartAction.ToggleSelectionMode)
        vm.onAction(CartAction.ToggleSelection("p-1"))
        vm.onAction(CartAction.ClearSelection)
        advanceUntilIdle()

        assertThat(vm.state.value.selectedItems).isEmpty()
        assertThat(vm.state.value.isSelectionMode).isTrue()
    }

    @Test
    fun `clearUndoItem nulls the undo item`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        val target = item("p-1")
        cart.emitCartItems(testUserId, listOf(target))
        advanceUntilIdle()

        vm.onAction(CartAction.RemoveItem("p-1"))
        advanceUntilIdle()
        assertThat(vm.state.value.undoItem).isNotNull()

        vm.clearUndoItem()
        advanceUntilIdle()
        assertThat(vm.state.value.undoItem).isNull()
    }

    @Test
    fun `actions are no-op when user is null`() = runTest {
        val cart = FakeCartRepository()
        val auth = FakeAuthRepository(initialUser = null)
        val vm = newViewModel(cart = cart, auth = auth)
        advanceUntilIdle()

        vm.onAction(CartAction.RemoveItem("p-1"))
        vm.onAction(CartAction.IncrementQuantity("p-1"))
        advanceUntilIdle()

        assertThat(cart.removeFromCartCalls).isEmpty()
        assertThat(cart.updateQuantityCalls).isEmpty()
    }
}
