package com.wenubey.wenucommerce.customer.customer_products

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.local.dao.SellerOrderDao
import com.wenubey.data.local.entity.SellerOrderEntity
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductReview
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeCartRepository
import com.wenubey.wenucommerce.testing.fakes.FakeProductRepository
import com.wenubey.wenucommerce.testing.fakes.FakeProductReviewRepository
import com.wenubey.wenucommerce.testing.fakes.FakeSellerOrderDao
import com.wenubey.wenucommerce.testing.fakes.FakeWishlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerProductDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private val productId = "p-1"
    private val testUserId = "u-1"

    private fun product(
        id: String = productId,
        totalStock: Int = 10,
        variants: List<ProductVariant> = listOf(
            ProductVariant(id = "v-1", label = "Default", isDefault = true),
        ),
    ) = Product(
        id = id,
        title = "Shirt",
        totalStockQuantity = totalStock,
        variants = variants,
    )

    /** Builds a DELIVERED seller-order entity whose itemsJson contains [productIds]. */
    private fun deliveredOrder(
        id: String,
        userId: String = testUserId,
        vararg productIds: String,
    ) = SellerOrderEntity(
        id = id,
        userId = userId,
        status = "DELIVERED",
        itemsJson = Json.encodeToString(productIds.map { OrderItem(productId = it) }),
    )

    private fun newViewModel(
        loadedProduct: Product? = product(),
        productRepo: FakeProductRepository = FakeProductRepository().apply {
            if (loadedProduct != null) getProductByIdResult = { Result.success(loadedProduct) }
        },
        reviewRepo: FakeProductReviewRepository = FakeProductReviewRepository(),
        cartRepo: FakeCartRepository = FakeCartRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = User(uuid = testUserId)),
        wishlist: FakeWishlistRepository = FakeWishlistRepository(),
        sellerOrderDao: SellerOrderDao = FakeSellerOrderDao(),
        savedState: SavedStateHandle = SavedStateHandle(mapOf("productId" to productId)),
    ) = CustomerProductDetailViewModel(
        productRepo, reviewRepo, cartRepo, auth, wishlist, sellerOrderDao, savedState,
        dispatcherProvider,
    )

    // --- load ---

    @Test
    fun `init loads product and increments view count`() = runTest {
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.success(product()) }
        }
        val vm = newViewModel(productRepo = productRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.product?.id).isEqualTo(productId)
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(productRepo.incrementViewCountCalls).containsExactly(productId)
    }

    @Test
    fun `init selects the variant flagged isDefault`() = runTest {
        val variants = listOf(
            ProductVariant(id = "v-1", label = "Red"),
            ProductVariant(id = "v-2", label = "Blue", isDefault = true),
            ProductVariant(id = "v-3", label = "Green"),
        )
        val vm = newViewModel(loadedProduct = product(variants = variants))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedVariant?.id).isEqualTo("v-2")
    }

    @Test
    fun `init falls back to first variant when none is marked default`() = runTest {
        val variants = listOf(
            ProductVariant(id = "v-1", label = "Red"),
            ProductVariant(id = "v-2", label = "Blue"),
        )
        val vm = newViewModel(loadedProduct = product(variants = variants))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedVariant?.id).isEqualTo("v-1")
    }

    @Test
    fun `load failure surfaces error and clears loading`() = runTest {
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.failure(RuntimeException("not found")) }
        }
        val vm = newViewModel(productRepo = productRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.product).isNull()
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.errorMessage).isEqualTo("not found")
    }

    @Test
    fun `init prefills isInCart, cartQuantity, selectedQuantity from existing cart item`() = runTest {
        val cartRepo = FakeCartRepository().apply {
            emitCartItems(testUserId, listOf(CartItem(productId = productId, quantity = 3)))
        }
        val vm = newViewModel(cartRepo = cartRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.isInCart).isTrue()
        assertThat(vm.state.value.cartQuantity).isEqualTo(3)
        assertThat(vm.state.value.selectedQuantity).isEqualTo(3)
    }

    // --- reviews ---

    @Test
    fun `reviews flow populates state and clears the review-loading flag`() = runTest {
        val reviewRepo = FakeProductReviewRepository()
        val vm = newViewModel(reviewRepo = reviewRepo)
        reviewRepo.emit(productId, listOf(
            ProductReview(id = "r-1", rating = 5),
            ProductReview(id = "r-2", rating = 4),
        ))
        advanceUntilIdle()

        assertThat(vm.state.value.reviews).hasSize(2)
        assertThat(vm.state.value.isLoadingReviews).isFalse()
    }

    @Test
    fun `reviews flow error clears loading flag without crashing`() = runTest {
        val reviewRepo = FakeProductReviewRepository().apply {
            observeFlow = flow { throw RuntimeException("offline") }
        }
        val vm = newViewModel(reviewRepo = reviewRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.isLoadingReviews).isFalse()
    }

    @Test
    fun `OnMarkReviewHelpful calls repository with the productId and reviewId`() = runTest {
        val reviewRepo = FakeProductReviewRepository()
        val vm = newViewModel(reviewRepo = reviewRepo)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.OnMarkReviewHelpful("r-1"))
        advanceUntilIdle()

        assertThat(reviewRepo.markHelpfulCalls).containsExactly(productId to "r-1")
    }

    @Test
    fun `OnMarkReviewHelpful before product loads is a no-op`() = runTest {
        val reviewRepo = FakeProductReviewRepository()
        val productRepo = FakeProductRepository().apply {
            // Never resolve — product never loads in time.
            getProductByIdResult = { Result.failure(RuntimeException()) }
        }
        val vm = newViewModel(productRepo = productRepo, reviewRepo = reviewRepo)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.OnMarkReviewHelpful("r-1"))
        advanceUntilIdle()

        assertThat(reviewRepo.markHelpfulCalls).isEmpty()
    }

    // --- delivered-order gate (D-07 / REVW-02 affordance) ---

    @Test
    fun `hasDeliveredOrder is true when a DELIVERED order contains this product`() = runTest {
        val dao = FakeSellerOrderDao().apply {
            seed(deliveredOrder(id = "so-1", productIds = arrayOf(productId, "other")))
        }
        val vm = newViewModel(sellerOrderDao = dao)
        advanceUntilIdle()

        assertThat(vm.state.value.hasDeliveredOrder).isTrue()
        assertThat(vm.state.value.isCheckingEligibility).isFalse()
    }

    @Test
    fun `hasDeliveredOrder is false when no DELIVERED order contains this product`() = runTest {
        val dao = FakeSellerOrderDao().apply {
            seed(deliveredOrder(id = "so-1", productIds = arrayOf("other-product")))
        }
        val vm = newViewModel(sellerOrderDao = dao)
        advanceUntilIdle()

        assertThat(vm.state.value.hasDeliveredOrder).isFalse()
    }

    @Test
    fun `hasDeliveredOrder is false when there are no delivered orders at all`() = runTest {
        val vm = newViewModel(sellerOrderDao = FakeSellerOrderDao())
        advanceUntilIdle()

        assertThat(vm.state.value.hasDeliveredOrder).isFalse()
    }

    @Test
    fun `hasDeliveredOrder ignores orders belonging to a different user`() = runTest {
        val dao = FakeSellerOrderDao().apply {
            seed(deliveredOrder(id = "so-1", userId = "someone-else", productIds = arrayOf(productId)))
        }
        val vm = newViewModel(sellerOrderDao = dao)
        advanceUntilIdle()

        assertThat(vm.state.value.hasDeliveredOrder).isFalse()
    }

    // --- existing review pre-fill (D-05) ---

    @Test
    fun `existingReview is populated from getMyReviewForProduct`() = runTest {
        val existing = ProductReview(id = "r-existing", rating = 4, title = "Good", body = "Nice")
        val reviewRepo = FakeProductReviewRepository().apply {
            myReviewResult = Result.success(existing)
        }
        val vm = newViewModel(reviewRepo = reviewRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.existingReview).isEqualTo(existing)
    }

    @Test
    fun `existingReview is null when the customer has not reviewed`() = runTest {
        val reviewRepo = FakeProductReviewRepository().apply {
            myReviewResult = Result.success(null)
        }
        val vm = newViewModel(reviewRepo = reviewRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.existingReview).isNull()
    }

    // --- sort (REVW-06) ---

    @Test
    fun `default sort orders reviews by createdAt descending (most recent first)`() = runTest {
        val reviewRepo = FakeProductReviewRepository()
        val vm = newViewModel(reviewRepo = reviewRepo)
        reviewRepo.emit(
            productId,
            listOf(
                ProductReview(id = "old", rating = 5, createdAt = "1000"),
                ProductReview(id = "new", rating = 3, createdAt = "3000"),
                ProductReview(id = "mid", rating = 4, createdAt = "2000"),
            ),
        )
        advanceUntilIdle()

        assertThat(vm.state.value.reviewSortOrder).isEqualTo(ReviewSortOrder.MOST_RECENT)
        assertThat(vm.state.value.reviews.map { it.id }).containsExactly("new", "mid", "old").inOrder()
    }

    @Test
    fun `HIGHEST_RATED sort orders by rating desc with recency tie-break`() = runTest {
        val reviewRepo = FakeProductReviewRepository()
        val vm = newViewModel(reviewRepo = reviewRepo)
        reviewRepo.emit(
            productId,
            listOf(
                // two 5-star reviews with different createdAt — recency breaks the tie
                ProductReview(id = "five-old", rating = 5, createdAt = "1000"),
                ProductReview(id = "five-new", rating = 5, createdAt = "2000"),
                ProductReview(id = "three", rating = 3, createdAt = "5000"),
            ),
        )
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.OnSortOrderChanged(ReviewSortOrder.HIGHEST_RATED))
        advanceUntilIdle()

        assertThat(vm.state.value.reviewSortOrder).isEqualTo(ReviewSortOrder.HIGHEST_RATED)
        // 5-star newest, then 5-star oldest, then 3-star
        assertThat(vm.state.value.reviews.map { it.id })
            .containsExactly("five-new", "five-old", "three").inOrder()
    }

    // --- submit review (REVW-01) ---

    @Test
    fun `SubmitReview success clears submitting, hides form, sets success message`() = runTest {
        val reviewRepo = FakeProductReviewRepository()
        val vm = newViewModel(reviewRepo = reviewRepo)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.SubmitReview(rating = 4, title = "Great", body = "Loved it"))
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.isSubmittingReview).isFalse()
        assertThat(s.showReviewForm).isFalse()
        assertThat(s.cartMessage).isEqualTo("Review submitted")
        assertThat(reviewRepo.submitReviewCalls).hasSize(1)
        assertThat(reviewRepo.submitReviewCalls[0].rating).isEqualTo(4)
    }

    @Test
    fun `SubmitReview success on an existing review sets the update message`() = runTest {
        val reviewRepo = FakeProductReviewRepository().apply {
            myReviewResult = Result.success(ProductReview(id = "r-existing", rating = 2))
        }
        val vm = newViewModel(reviewRepo = reviewRepo)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.SubmitReview(rating = 5, title = "Better", body = "Changed my mind"))
        advanceUntilIdle()

        assertThat(vm.state.value.cartMessage).isEqualTo("Review updated")
    }

    @Test
    fun `SubmitReview failure surfaces reviewSubmitError and clears submitting`() = runTest {
        val reviewRepo = FakeProductReviewRepository().apply {
            submitReviewResult = {
                Result.failure(IllegalStateException("No delivered order found for this product"))
            }
        }
        val vm = newViewModel(reviewRepo = reviewRepo)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.SubmitReview(rating = 3, title = "", body = ""))
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.isSubmittingReview).isFalse()
        assertThat(s.reviewSubmitError).isEqualTo("No delivered order found for this product")
    }

    // --- helpful optimistic disable (D-04) ---

    @Test
    fun `OnMarkReviewHelpful adds the id to helpfulVotedIds and calls the repository`() = runTest {
        val reviewRepo = FakeProductReviewRepository()
        val vm = newViewModel(reviewRepo = reviewRepo)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.OnMarkReviewHelpful("r-1"))
        advanceUntilIdle()

        assertThat(vm.state.value.helpfulVotedIds).contains("r-1")
        assertThat(reviewRepo.markHelpfulCalls).containsExactly(productId to "r-1")
    }

    @Test
    fun `OnMarkReviewHelpful on an already-voted review does not call the repository again`() = runTest {
        val reviewRepo = FakeProductReviewRepository()
        val vm = newViewModel(reviewRepo = reviewRepo)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.OnMarkReviewHelpful("r-1"))
        advanceUntilIdle()
        vm.onAction(CustomerProductDetailAction.OnMarkReviewHelpful("r-1"))
        advanceUntilIdle()

        assertThat(reviewRepo.markHelpfulCalls).hasSize(1)
    }

    // --- review form open/dismiss ---

    @Test
    fun `OpenReviewForm and DismissReviewForm toggle showReviewForm`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.OpenReviewForm)
        assertThat(vm.state.value.showReviewForm).isTrue()

        vm.onAction(CustomerProductDetailAction.DismissReviewForm)
        assertThat(vm.state.value.showReviewForm).isFalse()
    }

    // --- variants & quantity ---

    @Test
    fun `OnVariantSelected updates selectedVariant`() = runTest {
        val variants = listOf(
            ProductVariant(id = "v-1", label = "Red", isDefault = true),
            ProductVariant(id = "v-2", label = "Blue"),
        )
        val vm = newViewModel(loadedProduct = product(variants = variants))
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.OnVariantSelected(variants[1]))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedVariant?.id).isEqualTo("v-2")
    }

    @Test
    fun `SetQuantity clamps to total stock`() = runTest {
        val vm = newViewModel(loadedProduct = product(totalStock = 5))
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.SetQuantity(10))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedQuantity).isEqualTo(5)

        vm.onAction(CustomerProductDetailAction.SetQuantity(0))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedQuantity).isEqualTo(1) // clamped up from below 1
    }

    // --- cart ---

    @Test
    fun `AddToCart anonymous user shows login prompt and does not call cart`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val cart = FakeCartRepository()
        val vm = newViewModel(cartRepo = cart, auth = auth)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.AddToCart)
        advanceUntilIdle()

        assertThat(vm.state.value.showLoginPrompt).isTrue()
        assertThat(cart.addToCartCalls).isEmpty()
    }

    @Test
    fun `AddToCart with totalStock zero is silently ignored`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cartRepo = cart, loadedProduct = product(totalStock = 0))
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.AddToCart)
        advanceUntilIdle()

        assertThat(cart.addToCartCalls).isEmpty()
    }

    @Test
    fun `AddToCart success updates isInCart, cartQuantity, cartMessage`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cartRepo = cart)
        advanceUntilIdle()
        vm.onAction(CustomerProductDetailAction.SetQuantity(2))
        // Seed the cart repo so that the post-add lookup returns a value.
        cart.emitCartItems(testUserId, listOf(CartItem(productId = productId, quantity = 2)))
        vm.onAction(CustomerProductDetailAction.AddToCart)
        advanceUntilIdle()

        assertThat(cart.addToCartCalls).hasSize(1)
        val s = vm.state.value
        assertThat(s.isInCart).isTrue()
        assertThat(s.cartQuantity).isEqualTo(2)
        assertThat(s.cartMessage).isEqualTo("Added to cart")
        assertThat(s.isAddingToCart).isFalse()
    }

    @Test
    fun `AddToCart failure surfaces failure message`() = runTest {
        val cart = FakeCartRepository().apply {
            addToCartThrows = RuntimeException("server")
        }
        val vm = newViewModel(cartRepo = cart)
        advanceUntilIdle()
        vm.onAction(CustomerProductDetailAction.AddToCart)
        advanceUntilIdle()

        assertThat(vm.state.value.cartMessage).isEqualTo("Failed to add to cart")
        assertThat(vm.state.value.isAddingToCart).isFalse()
    }

    @Test
    fun `UpdateCartQuantity to positive updates cartQuantity and selectedQuantity, keeps isInCart true`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cartRepo = cart)
        advanceUntilIdle()
        vm.onAction(CustomerProductDetailAction.UpdateCartQuantity(4))
        advanceUntilIdle()

        assertThat(cart.updateQuantityCalls).containsExactly(Triple(testUserId, productId, 4))
        val s = vm.state.value
        assertThat(s.cartQuantity).isEqualTo(4)
        assertThat(s.selectedQuantity).isEqualTo(4)
        assertThat(s.isInCart).isTrue()
    }

    @Test
    fun `UpdateCartQuantity to zero flips isInCart to false`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cartRepo = cart)
        advanceUntilIdle()
        vm.onAction(CustomerProductDetailAction.UpdateCartQuantity(0))
        advanceUntilIdle()

        assertThat(vm.state.value.cartQuantity).isEqualTo(0)
        assertThat(vm.state.value.isInCart).isFalse()
    }

    @Test
    fun `UpdateCartQuantity for anonymous user is no-op`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val cart = FakeCartRepository()
        val vm = newViewModel(cartRepo = cart, auth = auth)
        advanceUntilIdle()
        vm.onAction(CustomerProductDetailAction.UpdateCartQuantity(2))
        advanceUntilIdle()

        assertThat(cart.updateQuantityCalls).isEmpty()
    }

    // --- wishlist ---

    @Test
    fun `wishlist flow populates isWishlisted`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        advanceUntilIdle()
        assertThat(vm.state.value.isWishlisted).isFalse()

        wishlist.emit(testUserId, listOf(
            com.wenubey.domain.model.WishlistItem(productId = productId),
        ))
        advanceUntilIdle()
        assertThat(vm.state.value.isWishlisted).isTrue()
    }

    @Test
    fun `ToggleWishlist before product loads is no-op`() = runTest {
        val wishlist = FakeWishlistRepository()
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.failure(RuntimeException()) }
        }
        val vm = newViewModel(productRepo = productRepo, wishlist = wishlist)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.ToggleWishlist)
        advanceUntilIdle()
        assertThat(wishlist.toggleCalls).isEmpty()
    }

    @Test
    fun `ToggleWishlist calls repository with current userId and product`() = runTest {
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.ToggleWishlist)
        advanceUntilIdle()

        assertThat(wishlist.toggleCalls).hasSize(1)
        assertThat(wishlist.toggleCalls[0].first).isEqualTo(testUserId)
        assertThat(wishlist.toggleCalls[0].second.id).isEqualTo(productId)
    }

    @Test
    fun `ToggleWishlist with anonymous user still calls repository with null userId`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val wishlist = FakeWishlistRepository()
        val vm = newViewModel(wishlist = wishlist, auth = auth)
        advanceUntilIdle()

        vm.onAction(CustomerProductDetailAction.ToggleWishlist)
        advanceUntilIdle()
        assertThat(wishlist.toggleCalls).hasSize(1)
        assertThat(wishlist.toggleCalls[0].first).isNull()
    }

    // --- dismiss actions ---

    @Test
    fun `DismissLoginPrompt clears showLoginPrompt`() = runTest {
        val vm = newViewModel(auth = FakeAuthRepository(initialUser = null))
        advanceUntilIdle()
        vm.onAction(CustomerProductDetailAction.AddToCart)
        advanceUntilIdle()
        assertThat(vm.state.value.showLoginPrompt).isTrue()

        vm.onAction(CustomerProductDetailAction.DismissLoginPrompt)
        advanceUntilIdle()
        assertThat(vm.state.value.showLoginPrompt).isFalse()
    }

    @Test
    fun `DismissCartMessage clears cartMessage`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cartRepo = cart)
        advanceUntilIdle()
        vm.onAction(CustomerProductDetailAction.AddToCart)
        advanceUntilIdle()
        assertThat(vm.state.value.cartMessage).isNotNull()

        vm.onAction(CustomerProductDetailAction.DismissCartMessage)
        advanceUntilIdle()
        assertThat(vm.state.value.cartMessage).isNull()
    }
}
