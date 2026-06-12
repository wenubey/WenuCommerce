package com.wenubey.wenucommerce.seller.seller_products

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductImage
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.ShippingType
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.domain.model.product.Tag
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeProductRepository
import com.wenubey.wenucommerce.testing.fakes.FakeTagRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerProductEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun draftProduct() = Product(
        id = "p-1",
        title = "Old Title",
        basePrice = 10.0,
        status = ProductStatus.DRAFT,
        categoryId = "c-1",
        categoryName = "Clothing",
        subcategoryId = "s-1",
        subcategoryName = "Tops",
        tagNames = listOf("Cotton"),
        images = emptyList(),
        variants = listOf(ProductVariant(id = "v-1", label = "Default", stockQuantity = 5)),
    )

    private fun newViewModel(
        product: Product? = draftProduct(),
        productRepo: FakeProductRepository = FakeProductRepository().apply {
            if (product != null) getProductByIdResult = { Result.success(product) }
        },
        tag: FakeTagRepository = FakeTagRepository(),
        savedState: SavedStateHandle = SavedStateHandle(mapOf("productId" to (product?.id ?: ""))),
    ) = SellerProductEditViewModel(productRepo, tag, savedState, dispatcherProvider)

    // --- init / load ---

    @Test
    fun `null productId skips load and state stays empty`() = runTest {
        val productRepo = FakeProductRepository()
        val vm = SellerProductEditViewModel(
            productRepo,
            FakeTagRepository(),
            SavedStateHandle(),
            dispatcherProvider,
        )
        advanceUntilIdle()

        assertThat(vm.state.value.product).isNull()
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(productRepo.getProductByIdCalls).isEmpty()
    }

    @Test
    fun `init loads the product and seeds selectedCategory and subcategory`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.product?.id).isEqualTo("p-1")
        assertThat(state.isLoading).isFalse()
        assertThat(state.selectedCategory).isEqualTo(Category(id = "c-1", name = "Clothing"))
        assertThat(state.selectedSubcategory).isEqualTo(Subcategory(id = "s-1", name = "Tops"))
    }

    @Test
    fun `DRAFT and SUSPENDED products are editable, others are not`() = runTest {
        val draftVm = newViewModel(product = draftProduct().copy(status = ProductStatus.DRAFT))
        advanceUntilIdle()
        assertThat(draftVm.state.value.isEditable).isTrue()

        val suspendedVm = newViewModel(product = draftProduct().copy(status = ProductStatus.SUSPENDED))
        advanceUntilIdle()
        assertThat(suspendedVm.state.value.isEditable).isTrue()

        val activeVm = newViewModel(product = draftProduct().copy(status = ProductStatus.ACTIVE))
        advanceUntilIdle()
        assertThat(activeVm.state.value.isEditable).isFalse()
    }

    @Test
    fun `loadProduct failure surfaces error and clears loading`() = runTest {
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.failure(RuntimeException("not found")) }
        }
        val vm = newViewModel(productRepo = productRepo)
        advanceUntilIdle()

        assertThat(vm.state.value.product).isNull()
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.errorMessage).isEqualTo("not found")
    }

    // --- category / subcategory ---

    @Test
    fun `OnCategorySelected updates both selectedCategory and product fields, clearing subcategory`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnCategorySelected(Category(id = "c-2", name = "Home")))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedCategory?.id).isEqualTo("c-2")
        assertThat(vm.state.value.selectedSubcategory).isNull()
        assertThat(vm.state.value.product?.categoryId).isEqualTo("c-2")
        assertThat(vm.state.value.product?.categoryName).isEqualTo("Home")
        assertThat(vm.state.value.product?.subcategoryId).isEmpty()
        assertThat(vm.state.value.product?.subcategoryName).isEmpty()
    }

    @Test
    fun `OnSubcategorySelected propagates to product fields`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnSubcategorySelected(Subcategory(id = "s-9", name = "Bottoms")))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedSubcategory?.id).isEqualTo("s-9")
        assertThat(vm.state.value.product?.subcategoryId).isEqualTo("s-9")
        assertThat(vm.state.value.product?.subcategoryName).isEqualTo("Bottoms")
    }

    // --- tags ---

    @Test
    fun `OnTagAdded trims and deduplicates case-insensitively`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnTagAdded("  Summer  "))
        vm.onAction(SellerProductEditAction.OnTagAdded("cotton")) // dup vs initial "Cotton"
        vm.onAction(SellerProductEditAction.OnTagAdded("   "))     // blank
        advanceUntilIdle()

        assertThat(vm.state.value.product?.tagNames).containsExactly("Cotton", "Summer").inOrder()
    }

    @Test
    fun `OnTagRemoved drops the tag`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnTagRemoved("Cotton"))
        advanceUntilIdle()

        assertThat(vm.state.value.product?.tagNames).isEmpty()
    }

    @Test
    fun `OnTagInputChanged debounces 300ms and filters out already-added tags`() = runTest {
        val tag = FakeTagRepository().apply {
            searchByPrefixResult = Result.success(listOf(
                Tag(displayName = "Cotton"),
                Tag(displayName = "Summer"),
            ))
        }
        val vm = newViewModel(tag = tag)
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnTagInputChanged("co"))
        advanceTimeBy(350)
        advanceUntilIdle()

        // Cotton already on the product, only Summer remains as suggestion.
        assertThat(vm.state.value.tagSuggestions).containsExactly("Summer")
        assertThat(vm.state.value.isLoadingTagSuggestions).isFalse()
    }

    @Test
    fun `OnTagInputChanged blank input clears suggestions without fetching`() = runTest {
        val tag = FakeTagRepository()
        val vm = newViewModel(tag = tag)
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnTagInputChanged(""))
        advanceTimeBy(400)
        advanceUntilIdle()

        assertThat(tag.searchByPrefixCalls).isEmpty()
        assertThat(vm.state.value.tagSuggestions).isEmpty()
    }

    // --- images ---

    @Test
    fun `OnImagesSelected caps total (remote+local) at 8`() = runTest {
        val product = draftProduct().copy(images = List(5) {
            ProductImage(id = "img-$it", downloadUrl = "u-$it")
        })
        val vm = newViewModel(product = product)
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnImagesSelected(List(10) { "new-$it" }))
        advanceUntilIdle()

        // 5 remote + 3 local = 8 cap
        assertThat(vm.state.value.localImageUris).hasSize(3)
    }

    @Test
    fun `OnImageRemoved with remote index removes the image and triggers delete`() = runTest {
        val remote = ProductImage(id = "img-1", downloadUrl = "u", storagePath = "p/img-1.jpg")
        val product = draftProduct().copy(images = listOf(remote))
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.success(product) }
        }
        val vm = newViewModel(product = product, productRepo = productRepo)
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnImageRemoved(0))
        advanceUntilIdle()

        assertThat(vm.state.value.product?.images).isEmpty()
    }

    @Test
    fun `OnImageRemoved with local-region index drops only that local uri`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnImagesSelected(listOf("a", "b", "c")))
        // Remote count = 0, so indices 0..2 are local.
        vm.onAction(SellerProductEditAction.OnImageRemoved(1))
        advanceUntilIdle()

        assertThat(vm.state.value.localImageUris).containsExactly("a", "c").inOrder()
    }

    // --- variants ---

    @Test
    fun `OnVariantAdded appends with a fresh id and timestamps`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnVariantAdded(ProductVariant(label = "New", stockQuantity = 2)))
        advanceUntilIdle()

        val variants = vm.state.value.product!!.variants
        assertThat(variants).hasSize(2)
        val added = variants.last()
        assertThat(added.id).isNotEmpty()
        assertThat(added.createdAt).isNotEmpty()
        assertThat(added.updatedAt).isNotEmpty()
    }

    @Test
    fun `OnVariantAdded caps at 20`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        repeat(25) { vm.onAction(SellerProductEditAction.OnVariantAdded(ProductVariant(label = "v-$it"))) }
        advanceUntilIdle()
        assertThat(vm.state.value.product!!.variants).hasSize(20)
    }

    @Test
    fun `OnVariantUpdated replaces by id`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnVariantUpdated(
            ProductVariant(id = "v-1", label = "Updated", stockQuantity = 42),
        ))
        advanceUntilIdle()

        val updated = vm.state.value.product!!.variants.first { it.id == "v-1" }
        assertThat(updated.label).isEqualTo("Updated")
        assertThat(updated.stockQuantity).isEqualTo(42)
    }

    @Test
    fun `OnVariantRemoved drops by id`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnVariantRemoved("v-1"))
        advanceUntilIdle()
        assertThat(vm.state.value.product!!.variants).isEmpty()
    }

    @Test
    fun `OnHasVariantsToggled off resets variants to a single Default`() = runTest {
        val product = draftProduct().copy(hasVariants = true)
        val vm = newViewModel(product = product)
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnHasVariantsToggled)
        advanceUntilIdle()

        assertThat(vm.state.value.product!!.hasVariants).isFalse()
        assertThat(vm.state.value.product!!.variants).hasSize(1)
        assertThat(vm.state.value.product!!.variants[0].label).isEqualTo("Default")
        assertThat(vm.state.value.product!!.variants[0].isDefault).isTrue()
    }

    // --- shipping ---

    @Test
    fun `OnShippingUpdated stores the new shipping value`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        val shipping = ProductShipping(shippingType = ShippingType.FREE_SHIPPING)
        vm.onAction(SellerProductEditAction.OnShippingUpdated(shipping))
        advanceUntilIdle()
        assertThat(vm.state.value.product!!.shipping).isEqualTo(shipping)
    }

    // --- save / submit ---

    @Test
    fun `OnSave is no-op when product is non-editable (ACTIVE)`() = runTest {
        val product = draftProduct().copy(status = ProductStatus.ACTIVE)
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.success(product) }
        }
        val vm = newViewModel(product = product, productRepo = productRepo)
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnSave)
        advanceUntilIdle()

        assertThat(productRepo.updateProductCalls).isEmpty()
    }

    @Test
    fun `OnSave resolves tags, recomputes totalStockQuantity, persists update`() = runTest {
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.success(draftProduct()) }
        }
        val vm = newViewModel(productRepo = productRepo)
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnTagAdded("Summer"))
        vm.onAction(SellerProductEditAction.OnVariantUpdated(
            ProductVariant(id = "v-1", label = "Default", stockQuantity = 10),
        ))
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnSave)
        advanceUntilIdle()

        assertThat(productRepo.updateProductCalls).hasSize(1)
        val saved = productRepo.updateProductCalls[0]
        assertThat(saved.tagNames).containsExactly("Cotton", "Summer")
        assertThat(saved.tags).containsExactly("tag-cotton", "tag-summer")
        assertThat(saved.totalStockQuantity).isEqualTo(10)
        assertThat(vm.state.value.savedSuccessfully).isTrue()
        assertThat(vm.state.value.isSaving).isFalse()
    }

    @Test
    fun `OnSave appends uploaded local images preserving remote ones`() = runTest {
        val remote = ProductImage(id = "img-1", downloadUrl = "remote-url", sortOrder = 0)
        val product = draftProduct().copy(images = listOf(remote))
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.success(product) }
        }
        val vm = newViewModel(product = product, productRepo = productRepo)
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnImagesSelected(listOf("u1", "u2")))
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnSave)
        advanceUntilIdle()

        val saved = productRepo.updateProductCalls.single()
        assertThat(saved.images).hasSize(3)
        assertThat(saved.images[0].id).isEqualTo("img-1")          // remote first
        assertThat(saved.images[1].downloadUrl).startsWith("https://fake/")
        assertThat(saved.images[2].downloadUrl).startsWith("https://fake/")
        // Clear local URIs after successful save.
        assertThat(vm.state.value.localImageUris).isEmpty()
    }

    @Test
    fun `OnSave failure surfaces error and clears isSaving`() = runTest {
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.success(draftProduct()) }
            updateProductResult = Result.failure(RuntimeException("server"))
        }
        val vm = newViewModel(productRepo = productRepo)
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnSave)
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isEqualTo("server")
        assertThat(vm.state.value.isSaving).isFalse()
        assertThat(vm.state.value.savedSuccessfully).isFalse()
    }

    @Test
    fun `OnSubmitForReview saves then submits with the product id`() = runTest {
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.success(draftProduct()) }
        }
        val vm = newViewModel(productRepo = productRepo)
        advanceUntilIdle()

        vm.onAction(SellerProductEditAction.OnSubmitForReview)
        advanceUntilIdle()

        assertThat(productRepo.updateProductCalls).hasSize(1)
        assertThat(productRepo.submitForReviewCalls).containsExactly("p-1")
        assertThat(vm.state.value.savedSuccessfully).isTrue()
    }

    @Test
    fun `OnSubmitForReview save-failure does NOT call submitForReview`() = runTest {
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.success(draftProduct()) }
            updateProductResult = Result.failure(RuntimeException("server"))
        }
        val vm = newViewModel(productRepo = productRepo)
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnSubmitForReview)
        advanceUntilIdle()

        assertThat(productRepo.submitForReviewCalls).isEmpty()
        assertThat(vm.state.value.errorMessage).isEqualTo("server")
    }

    @Test
    fun `OnDismissError clears errorMessage`() = runTest {
        val productRepo = FakeProductRepository().apply {
            getProductByIdResult = { Result.success(draftProduct()) }
            updateProductResult = Result.failure(RuntimeException("boom"))
        }
        val vm = newViewModel(productRepo = productRepo)
        advanceUntilIdle()
        vm.onAction(SellerProductEditAction.OnSave)
        advanceUntilIdle()
        assertThat(vm.state.value.errorMessage).isEqualTo("boom")

        vm.onAction(SellerProductEditAction.OnDismissError)
        advanceUntilIdle()
        assertThat(vm.state.value.errorMessage).isNull()
    }
}
