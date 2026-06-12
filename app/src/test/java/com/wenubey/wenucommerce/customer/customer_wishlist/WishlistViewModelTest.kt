package com.wenubey.wenucommerce.customer.customer_wishlist

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.WishlistItem
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeCartRepository
import com.wenubey.wenucommerce.testing.fakes.FakeWishlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private val testUserId = "u-1"
    private val testUser = User(uuid = testUserId)

    private fun newViewModel(
        wishlist: FakeWishlistRepository = FakeWishlistRepository(),
        cart: FakeCartRepository = FakeCartRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = testUser),
    ) = WishlistViewModel(wishlist, cart, auth, dispatcherProvider)

    private fun item(
        productId: String,
        title: String = "Title $productId",
        price: Double = 10.0,
        stock: Int = 5,
        deleted: Boolean = false,
    ) = WishlistItem(
        productId = productId,
        productTitle = title,
        productPrice = price,
        availableStock = stock,
        isProductDeleted = deleted,
    )

    // --- observe ---

    @Test
    fun `observed items populate state and clear loading`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        wishlist.emit(testUserId, listOf(item("p-1"), item("p-2")))
        advanceUntilIdle()

        assertThat(vm.state.value.wishlistItems).hasSize(2)
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `observeWishlistItems error surfaces error and stops loading`() = runTest {
        val wishlist = FakeWishlistRepository().apply {
            observeFlow = flow { throw RuntimeException("network") }
        }
        val vm = newViewModel(wishlist = wishlist)
        advanceUntilIdle()

        assertThat(vm.state.value.error).isEqualTo("network")
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `null user observes wishlist under empty userId (anonymous)`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist, auth = auth)
        wishlist.emit("", listOf(item("p-anon")))
        advanceUntilIdle()

        assertThat(vm.state.value.wishlistItems.map { it.productId }).containsExactly("p-anon")
    }

    // --- remove ---

    @Test
    fun `RemoveFromWishlist stores undoItem and calls repository`() = runTest {
        val wishlist = FakeWishlistRepository()
        val target = item("p-1")
        val vm = newViewModel(wishlist = wishlist)
        wishlist.emit(testUserId, listOf(target))
        advanceUntilIdle()

        vm.onAction(WishlistAction.RemoveFromWishlist("p-1"))
        advanceUntilIdle()

        assertThat(vm.state.value.undoItem).isEqualTo(target)
        assertThat(wishlist.removeCalls).containsExactly(testUserId to "p-1")
    }

    @Test
    fun `RemoveFromWishlist for missing productId is no-op`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        wishlist.emit(testUserId, emptyList())
        advanceUntilIdle()

        vm.onAction(WishlistAction.RemoveFromWishlist("missing"))
        advanceUntilIdle()

        assertThat(wishlist.removeCalls).isEmpty()
        assertThat(vm.state.value.undoItem).isNull()
    }

    @Test
    fun `RemoveFromWishlist failure clears undoItem`() = runTest {
        val wishlist = FakeWishlistRepository().apply { removeThrows = RuntimeException("server") }
        val vm = newViewModel(wishlist = wishlist)
        wishlist.emit(testUserId, listOf(item("p-1")))
        advanceUntilIdle()

        vm.onAction(WishlistAction.RemoveFromWishlist("p-1"))
        advanceUntilIdle()

        assertThat(vm.state.value.undoItem).isNull()
    }

    @Test
    fun `UndoRemove clears undoItem and calls toggleWishlist with a minimal product`() = runTest {
        val wishlist = FakeWishlistRepository()
        val target = item("p-1", title = "Hat", price = 9.99, stock = 3)
        val vm = newViewModel(wishlist = wishlist)
        wishlist.emit(testUserId, listOf(target))
        advanceUntilIdle()
        vm.onAction(WishlistAction.RemoveFromWishlist("p-1"))
        advanceUntilIdle()

        vm.onAction(WishlistAction.UndoRemove(target))
        advanceUntilIdle()

        assertThat(vm.state.value.undoItem).isNull()
        assertThat(wishlist.toggleCalls).hasSize(1)
        val (uid, product) = wishlist.toggleCalls.single()
        assertThat(uid).isEqualTo(testUserId)
        assertThat(product.id).isEqualTo("p-1")
        assertThat(product.title).isEqualTo("Hat")
        assertThat(product.basePrice).isEqualTo(9.99)
        assertThat(product.totalStockQuantity).isEqualTo(3)
    }

    // --- add to cart (single) ---

    @Test
    fun `AddItemToCart anonymous user surfaces sign-in error and does not call cart`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart, auth = auth)
        advanceUntilIdle()

        vm.onAction(WishlistAction.AddItemToCart(item("p-1")))
        advanceUntilIdle()

        assertThat(vm.state.value.error).contains("sign in")
        assertThat(cart.addToCartCalls).isEmpty()
    }

    @Test
    fun `AddItemToCart skips deleted or out-of-stock items silently`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        advanceUntilIdle()

        vm.onAction(WishlistAction.AddItemToCart(item("p-1", deleted = true)))
        vm.onAction(WishlistAction.AddItemToCart(item("p-2", stock = 0)))
        advanceUntilIdle()

        assertThat(cart.addToCartCalls).isEmpty()
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `AddItemToCart available item calls cart with minimal product and quantity 1`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        advanceUntilIdle()

        val target = item("p-1", title = "Hat", price = 9.99, stock = 3)
        vm.onAction(WishlistAction.AddItemToCart(target))
        advanceUntilIdle()

        assertThat(cart.addToCartCalls).hasSize(1)
        val (uid, product, qty) = cart.addToCartCalls.single()
        assertThat(uid).isEqualTo(testUserId)
        assertThat(product.id).isEqualTo("p-1")
        assertThat(product.title).isEqualTo("Hat")
        assertThat(qty).isEqualTo(1)
    }

    // --- add all to cart ---

    @Test
    fun `AddAllToCart anonymous user surfaces sign-in error and does not call cart`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart, auth = auth)
        advanceUntilIdle()

        vm.onAction(WishlistAction.AddAllToCart)
        advanceUntilIdle()

        assertThat(vm.state.value.error).contains("sign in")
        assertThat(cart.addToCartCalls).isEmpty()
    }

    @Test
    fun `AddAllToCart adds only available items`() = runTest {
        val wishlist = FakeWishlistRepository()
        val cart = FakeCartRepository()
        val vm = newViewModel(wishlist = wishlist, cart = cart)
        wishlist.emit(testUserId, listOf(
            item("ok-1"),
            item("ok-2"),
            item("deleted", deleted = true),
            item("oos", stock = 0),
        ))
        advanceUntilIdle()

        vm.onAction(WishlistAction.AddAllToCart)
        advanceUntilIdle()

        assertThat(cart.addToCartCalls.map { it.second.id }).containsExactly("ok-1", "ok-2")
    }

    // --- selection ---

    @Test
    fun `ToggleSelection enters selection mode on first add and exits when set becomes empty`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        wishlist.emit(testUserId, listOf(item("p-1"), item("p-2")))
        advanceUntilIdle()

        vm.onAction(WishlistAction.ToggleSelection("p-1"))
        advanceUntilIdle()
        assertThat(vm.state.value.isSelectionMode).isTrue()
        assertThat(vm.state.value.selectedItems).containsExactly("p-1")

        vm.onAction(WishlistAction.ToggleSelection("p-2"))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedItems).containsExactly("p-1", "p-2")

        vm.onAction(WishlistAction.ToggleSelection("p-1"))
        vm.onAction(WishlistAction.ToggleSelection("p-2"))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedItems).isEmpty()
        assertThat(vm.state.value.isSelectionMode).isFalse()
    }

    @Test
    fun `ClearSelection empties selection set and exits selection mode`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        wishlist.emit(testUserId, listOf(item("p-1")))
        advanceUntilIdle()
        vm.onAction(WishlistAction.ToggleSelection("p-1"))
        advanceUntilIdle()

        vm.onAction(WishlistAction.ClearSelection)
        advanceUntilIdle()

        assertThat(vm.state.value.selectedItems).isEmpty()
        assertThat(vm.state.value.isSelectionMode).isFalse()
    }

    @Test
    fun `AddSelectedToCart adds only selected and available items, then clears selection`() = runTest {
        val wishlist = FakeWishlistRepository()
        val cart = FakeCartRepository()
        val vm = newViewModel(wishlist = wishlist, cart = cart)
        wishlist.emit(testUserId, listOf(
            item("sel-ok"),
            item("sel-deleted", deleted = true),
            item("not-selected"),
        ))
        advanceUntilIdle()

        vm.onAction(WishlistAction.ToggleSelection("sel-ok"))
        vm.onAction(WishlistAction.ToggleSelection("sel-deleted"))
        vm.onAction(WishlistAction.AddSelectedToCart)
        advanceUntilIdle()

        assertThat(cart.addToCartCalls.map { it.second.id }).containsExactly("sel-ok")
        assertThat(vm.state.value.selectedItems).isEmpty()
        assertThat(vm.state.value.isSelectionMode).isFalse()
    }

    @Test
    fun `AddSelectedToCart anonymous user surfaces sign-in error`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart, auth = auth)
        advanceUntilIdle()

        vm.onAction(WishlistAction.AddSelectedToCart)
        advanceUntilIdle()

        assertThat(vm.state.value.error).contains("sign in")
        assertThat(cart.addToCartCalls).isEmpty()
    }

    @Test
    fun `enterSelectionMode flips flag and seeds the selection with one productId`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        wishlist.emit(testUserId, listOf(item("p-1")))
        advanceUntilIdle()

        vm.enterSelectionMode("p-1")
        advanceUntilIdle()

        assertThat(vm.state.value.isSelectionMode).isTrue()
        assertThat(vm.state.value.selectedItems).containsExactly("p-1")
    }

    @Test
    fun `clearUndoItem nulls the undo item`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        wishlist.emit(testUserId, listOf(item("p-1")))
        advanceUntilIdle()
        vm.onAction(WishlistAction.RemoveFromWishlist("p-1"))
        advanceUntilIdle()
        assertThat(vm.state.value.undoItem).isNotNull()

        vm.clearUndoItem()
        advanceUntilIdle()
        assertThat(vm.state.value.undoItem).isNull()
    }
}
