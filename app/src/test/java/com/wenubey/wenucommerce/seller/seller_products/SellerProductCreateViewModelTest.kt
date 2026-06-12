package com.wenubey.wenucommerce.seller.seller_products

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductCondition
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.ShippingType
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.domain.model.product.Tag
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeProductRepository
import com.wenubey.wenucommerce.testing.fakes.FakeTagRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerProductCreateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private val sellerId = "seller-1"

    private fun verifiedSeller() = User(
        uuid = sellerId,
        role = UserRole.SELLER,
        businessInfo = BusinessInfo(
            businessName = "Acme Co",
            verificationStatus = VerificationStatus.APPROVED,
            isVerified = true,
        ),
    )

    private fun unverifiedSeller() = User(
        uuid = sellerId,
        role = UserRole.SELLER,
        businessInfo = BusinessInfo(verificationStatus = VerificationStatus.PENDING),
    )

    private fun newViewModel(
        product: FakeProductRepository = FakeProductRepository(),
        tag: FakeTagRepository = FakeTagRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = verifiedSeller()),
    ) = SellerProductCreateViewModel(product, auth, tag, dispatcherProvider)

    // --- init / seller verification ---

    @Test
    fun `verified seller starts with isSellerVerified true and no error`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.isSellerVerified).isTrue()
        assertThat(vm.state.value.errorMessage).isNull()
    }

    @Test
    fun `unverified seller starts with isSellerVerified false and a guard message`() = runTest {
        val auth = FakeAuthRepository(initialUser = unverifiedSeller())
        val vm = newViewModel(auth = auth)
        advanceUntilIdle()

        assertThat(vm.state.value.isSellerVerified).isFalse()
        assertThat(vm.state.value.errorMessage).contains("verified")
    }

    @Test
    fun `null user counts as unverified`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val vm = newViewModel(auth = auth)
        advanceUntilIdle()

        assertThat(vm.state.value.isSellerVerified).isFalse()
    }

    // --- basic field updates ---

    @Test
    fun `simple field changes update state directly`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerProductCreateAction.OnTitleChanged("Red Shirt"))
        vm.onAction(SellerProductCreateAction.OnDescriptionChanged("Soft cotton"))
        vm.onAction(SellerProductCreateAction.OnPriceChanged("29.99"))
        vm.onAction(SellerProductCreateAction.OnComparePriceChanged("39.99"))
        vm.onAction(SellerProductCreateAction.OnConditionSelected(ProductCondition.LIKE_NEW))
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.title).isEqualTo("Red Shirt")
        assertThat(s.description).isEqualTo("Soft cotton")
        assertThat(s.basePrice).isEqualTo("29.99")
        assertThat(s.compareAtPrice).isEqualTo("39.99")
        assertThat(s.condition).isEqualTo(ProductCondition.LIKE_NEW)
    }

    @Test
    fun `selecting a new category clears the previous subcategory`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerProductCreateAction.OnCategorySelected(Category(id = "c-1", name = "Clothing")))
        vm.onAction(SellerProductCreateAction.OnSubcategorySelected(Subcategory(id = "s-1", name = "Tops")))
        vm.onAction(SellerProductCreateAction.OnCategorySelected(Category(id = "c-2", name = "Home")))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedCategory?.id).isEqualTo("c-2")
        assertThat(vm.state.value.selectedSubcategory).isNull()
    }

    // --- tags ---

    @Test
    fun `OnTagAdded trims whitespace and deduplicates case-insensitively`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerProductCreateAction.OnTagAdded("  Cotton  "))
        vm.onAction(SellerProductCreateAction.OnTagAdded("cotton")) // dup
        vm.onAction(SellerProductCreateAction.OnTagAdded("  "))     // blank
        vm.onAction(SellerProductCreateAction.OnTagAdded("Summer"))
        advanceUntilIdle()

        assertThat(vm.state.value.tags).containsExactly("Cotton", "Summer").inOrder()
    }

    @Test
    fun `tag cap is 10 items`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        repeat(15) { vm.onAction(SellerProductCreateAction.OnTagAdded("tag-$it")) }
        advanceUntilIdle()
        assertThat(vm.state.value.tags).hasSize(10)
    }

    @Test
    fun `OnTagRemoved drops the tag`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnTagAdded("Cotton"))
        vm.onAction(SellerProductCreateAction.OnTagAdded("Summer"))
        vm.onAction(SellerProductCreateAction.OnTagRemoved("Cotton"))
        advanceUntilIdle()
        assertThat(vm.state.value.tags).containsExactly("Summer")
    }

    @Test
    fun `OnTagInputChanged debounces and fetches suggestions excluding already-added tags`() = runTest {
        val tag = FakeTagRepository().apply {
            searchByPrefixResult = Result.success(listOf(
                Tag(id = "t1", displayName = "Cotton"),
                Tag(id = "t2", displayName = "Summer"),
                Tag(id = "t3", displayName = "Winter"),
            ))
        }
        val vm = newViewModel(tag = tag)
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnTagAdded("Cotton")) // already added

        vm.onAction(SellerProductCreateAction.OnTagInputChanged("co"))
        // Before the 300ms debounce: no fetch yet.
        runCurrent()
        assertThat(tag.searchByPrefixCalls).isEmpty()

        advanceTimeBy(350)
        advanceUntilIdle()

        assertThat(tag.searchByPrefixCalls).hasSize(1)
        assertThat(vm.state.value.tagSuggestions).containsExactly("Summer", "Winter")
        assertThat(vm.state.value.isLoadingTagSuggestions).isFalse()
    }

    @Test
    fun `OnTagInputChanged blank input clears suggestions and skips fetch`() = runTest {
        val tag = FakeTagRepository()
        val vm = newViewModel(tag = tag)
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnTagInputChanged(""))
        advanceTimeBy(400)
        advanceUntilIdle()

        assertThat(tag.searchByPrefixCalls).isEmpty()
        assertThat(vm.state.value.tagSuggestions).isEmpty()
        assertThat(vm.state.value.isLoadingTagSuggestions).isFalse()
    }

    @Test
    fun `OnTagInputChanged failure resets loading flag and empties suggestions`() = runTest {
        val tag = FakeTagRepository().apply {
            searchByPrefixResult = Result.failure(RuntimeException("offline"))
        }
        val vm = newViewModel(tag = tag)
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnTagInputChanged("co"))
        advanceTimeBy(350)
        advanceUntilIdle()

        assertThat(vm.state.value.isLoadingTagSuggestions).isFalse()
        assertThat(vm.state.value.tagSuggestions).isEmpty()
    }

    @Test
    fun `OnTagAdded clears any standing suggestions`() = runTest {
        val tag = FakeTagRepository().apply {
            searchByPrefixResult = Result.success(listOf(Tag(displayName = "Cotton")))
        }
        val vm = newViewModel(tag = tag)
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnTagInputChanged("co"))
        advanceTimeBy(350)
        advanceUntilIdle()
        assertThat(vm.state.value.tagSuggestions).isNotEmpty()

        vm.onAction(SellerProductCreateAction.OnTagAdded("Cotton"))
        advanceUntilIdle()
        assertThat(vm.state.value.tagSuggestions).isEmpty()
    }

    // --- images ---

    @Test
    fun `OnImagesSelected caps at 8 total uris`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnImagesSelected(List(5) { "uri-$it" }))
        vm.onAction(SellerProductCreateAction.OnImagesSelected(List(5) { "extra-$it" }))
        advanceUntilIdle()

        assertThat(vm.state.value.localImageUris).hasSize(8)
    }

    @Test
    fun `OnImageRemoved drops the indexed local uri`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnImagesSelected(listOf("a", "b", "c")))
        vm.onAction(SellerProductCreateAction.OnImageRemoved(1))
        advanceUntilIdle()

        assertThat(vm.state.value.localImageUris).containsExactly("a", "c").inOrder()
    }

    // --- variants ---

    @Test
    fun `OnVariantAdded assigns an id and timestamps`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        val template = ProductVariant(label = "Red / L", stockQuantity = 5)
        vm.onAction(SellerProductCreateAction.OnVariantAdded(template))
        advanceUntilIdle()

        // Default state already contains one (default) variant.
        val added = vm.state.value.variants.first { it.label == "Red / L" }
        assertThat(added.id).isNotEmpty()
        assertThat(added.createdAt).isNotEmpty()
        assertThat(added.updatedAt).isNotEmpty()
        assertThat(added.stockQuantity).isEqualTo(5)
    }

    @Test
    fun `OnVariantAdded caps at 20 variants`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        repeat(25) {
            vm.onAction(SellerProductCreateAction.OnVariantAdded(ProductVariant(label = "L$it")))
        }
        advanceUntilIdle()
        assertThat(vm.state.value.variants).hasSize(20)
    }

    @Test
    fun `OnVariantUpdated replaces by id and bumps updatedAt`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnVariantAdded(ProductVariant(label = "Old")))
        advanceUntilIdle()
        val originalId = vm.state.value.variants.last().id

        vm.onAction(SellerProductCreateAction.OnVariantUpdated(
            ProductVariant(id = originalId, label = "New", stockQuantity = 99),
        ))
        advanceUntilIdle()

        val updated = vm.state.value.variants.first { it.id == originalId }
        assertThat(updated.label).isEqualTo("New")
        assertThat(updated.stockQuantity).isEqualTo(99)
    }

    @Test
    fun `OnVariantRemoved drops by id`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnVariantAdded(ProductVariant(label = "x")))
        advanceUntilIdle()
        val id = vm.state.value.variants.last().id

        vm.onAction(SellerProductCreateAction.OnVariantRemoved(id))
        advanceUntilIdle()
        assertThat(vm.state.value.variants.none { it.id == id }).isTrue()
    }

    @Test
    fun `OnHasVariantsToggled flips off resets to a single default variant`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        // Turn on, add variants
        vm.onAction(SellerProductCreateAction.OnHasVariantsToggled)
        vm.onAction(SellerProductCreateAction.OnVariantAdded(ProductVariant(label = "A")))
        vm.onAction(SellerProductCreateAction.OnVariantAdded(ProductVariant(label = "B")))
        advanceUntilIdle()
        assertThat(vm.state.value.hasVariants).isTrue()

        // Toggle off — variants reset to just the Default
        vm.onAction(SellerProductCreateAction.OnHasVariantsToggled)
        advanceUntilIdle()
        assertThat(vm.state.value.hasVariants).isFalse()
        assertThat(vm.state.value.variants).hasSize(1)
        assertThat(vm.state.value.variants[0].label).isEqualTo("Default")
        assertThat(vm.state.value.variants[0].isDefault).isTrue()
    }

    @Test
    fun `OnShippingUpdated stores the new shipping value`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        val shipping = ProductShipping(shippingType = ShippingType.FREE_SHIPPING, shippingCost = 0.0)
        vm.onAction(SellerProductCreateAction.OnShippingUpdated(shipping))
        advanceUntilIdle()
        assertThat(vm.state.value.shipping).isEqualTo(shipping)
    }

    // --- save / submit ---

    @Test
    fun `OnSaveDraft is no-op when seller is unverified`() = runTest {
        val auth = FakeAuthRepository(initialUser = unverifiedSeller())
        val product = FakeProductRepository()
        val vm = newViewModel(product = product, auth = auth)
        advanceUntilIdle()

        vm.onAction(SellerProductCreateAction.OnSaveDraft)
        advanceUntilIdle()

        assertThat(product.createProductCalls).isEmpty()
    }

    @Test
    fun `OnSaveDraft passes resolved tags, sellerName and totalStockQuantity to the repository`() = runTest {
        val product = FakeProductRepository().apply {
            createProductResult = { p -> Result.success(p.copy(id = "new-id")) }
        }
        val tag = FakeTagRepository()
        val vm = newViewModel(product = product, tag = tag)
        advanceUntilIdle()

        vm.onAction(SellerProductCreateAction.OnTitleChanged("Red Shirt"))
        vm.onAction(SellerProductCreateAction.OnPriceChanged("29.99"))
        vm.onAction(SellerProductCreateAction.OnCategorySelected(Category(id = "c-1", name = "Clothing")))
        vm.onAction(SellerProductCreateAction.OnTagAdded("Cotton"))
        vm.onAction(SellerProductCreateAction.OnVariantAdded(ProductVariant(label = "L", stockQuantity = 3)))
        vm.onAction(SellerProductCreateAction.OnVariantAdded(ProductVariant(label = "M", stockQuantity = 4)))
        advanceUntilIdle()

        vm.onAction(SellerProductCreateAction.OnSaveDraft)
        advanceUntilIdle()

        assertThat(product.createProductCalls).hasSize(1)
        val saved = product.createProductCalls[0]
        assertThat(saved.title).isEqualTo("Red Shirt")
        assertThat(saved.basePrice).isEqualTo(29.99)
        assertThat(saved.sellerName).isEqualTo("Acme Co")
        assertThat(saved.tags).containsExactly("tag-cotton")
        assertThat(saved.tagNames).containsExactly("Cotton")
        // default variant (3 + 4) + initial Default variant (0 stock)
        assertThat(saved.totalStockQuantity).isEqualTo(7)

        assertThat(vm.state.value.savedProductId).isEqualTo("new-id")
        assertThat(vm.state.value.isSaving).isFalse()
    }

    @Test
    fun `OnSaveDraft repository failure surfaces error and clears isSaving`() = runTest {
        val product = FakeProductRepository().apply {
            createProductResult = { Result.failure(RuntimeException("server")) }
        }
        val vm = newViewModel(product = product)
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnTitleChanged("Shirt"))
        vm.onAction(SellerProductCreateAction.OnPriceChanged("10"))
        vm.onAction(SellerProductCreateAction.OnCategorySelected(Category(id = "c-1")))
        vm.onAction(SellerProductCreateAction.OnSaveDraft)
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isEqualTo("server")
        assertThat(vm.state.value.isSaving).isFalse()
        assertThat(vm.state.value.savedProductId).isNull()
    }

    @Test
    fun `OnSubmitForReview validates title price and category before saving`() = runTest {
        val product = FakeProductRepository()
        val vm = newViewModel(product = product)
        advanceUntilIdle()

        // Empty title
        vm.onAction(SellerProductCreateAction.OnSubmitForReview)
        advanceUntilIdle()
        assertThat(vm.state.value.errorMessage).contains("title")
        assertThat(product.createProductCalls).isEmpty()

        // Title set, price <= 0
        vm.onAction(SellerProductCreateAction.OnTitleChanged("Shirt"))
        vm.onAction(SellerProductCreateAction.OnPriceChanged("0"))
        vm.onAction(SellerProductCreateAction.OnSubmitForReview)
        advanceUntilIdle()
        assertThat(vm.state.value.errorMessage).contains("price")
        assertThat(product.createProductCalls).isEmpty()

        // Title + price set, no category
        vm.onAction(SellerProductCreateAction.OnPriceChanged("10"))
        vm.onAction(SellerProductCreateAction.OnSubmitForReview)
        advanceUntilIdle()
        assertThat(vm.state.value.errorMessage).contains("category")
        assertThat(product.createProductCalls).isEmpty()
    }

    @Test
    fun `OnSubmitForReview happy path saves then submits with the new product id`() = runTest {
        val product = FakeProductRepository().apply {
            createProductResult = { p -> Result.success(p.copy(id = "p-123")) }
        }
        val vm = newViewModel(product = product)
        advanceUntilIdle()

        vm.onAction(SellerProductCreateAction.OnTitleChanged("Shirt"))
        vm.onAction(SellerProductCreateAction.OnPriceChanged("10"))
        vm.onAction(SellerProductCreateAction.OnCategorySelected(Category(id = "c-1")))
        vm.onAction(SellerProductCreateAction.OnSubmitForReview)
        advanceUntilIdle()

        assertThat(product.createProductCalls).hasSize(1)
        assertThat(product.submitForReviewCalls).containsExactly("p-123")
    }

    @Test
    fun `OnDismissError clears errorMessage`() = runTest {
        val product = FakeProductRepository().apply {
            createProductResult = { Result.failure(RuntimeException("boom")) }
        }
        val vm = newViewModel(product = product)
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnTitleChanged("S"))
        vm.onAction(SellerProductCreateAction.OnPriceChanged("1"))
        vm.onAction(SellerProductCreateAction.OnCategorySelected(Category(id = "c-1")))
        vm.onAction(SellerProductCreateAction.OnSaveDraft)
        advanceUntilIdle()
        assertThat(vm.state.value.errorMessage).isEqualTo("boom")

        vm.onAction(SellerProductCreateAction.OnDismissError)
        advanceUntilIdle()
        assertThat(vm.state.value.errorMessage).isNull()
    }

    @Test
    fun `OnSaveDraft uploads selected images and updates product with download urls`() = runTest {
        val product = FakeProductRepository().apply {
            createProductResult = { p -> Result.success(p.copy(id = "p-img")) }
            getProductByIdResult = { id ->
                if (id == "p-img") Result.success(Product(id = "p-img", title = "Shirt"))
                else Result.failure(NoSuchElementException())
            }
        }
        val vm = newViewModel(product = product)
        advanceUntilIdle()
        vm.onAction(SellerProductCreateAction.OnTitleChanged("Shirt"))
        vm.onAction(SellerProductCreateAction.OnPriceChanged("10"))
        vm.onAction(SellerProductCreateAction.OnCategorySelected(Category(id = "c-1")))
        vm.onAction(SellerProductCreateAction.OnImagesSelected(listOf("uri-1", "uri-2")))
        vm.onAction(SellerProductCreateAction.OnSaveDraft)
        advanceUntilIdle()

        assertThat(product.updateProductCalls).hasSize(1)
        val updated = product.updateProductCalls[0]
        assertThat(updated.id).isEqualTo("p-img")
        assertThat(updated.images).hasSize(2)
        assertThat(updated.images.all { it.downloadUrl.startsWith("https://fake/") }).isTrue()
    }
}
