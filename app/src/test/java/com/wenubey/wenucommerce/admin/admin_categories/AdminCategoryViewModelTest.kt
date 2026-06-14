package com.wenubey.wenucommerce.admin.admin_categories

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeCategoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminCategoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        category: FakeCategoryRepository = FakeCategoryRepository(),
    ): Pair<AdminCategoryViewModel, FakeCategoryRepository> =
        AdminCategoryViewModel(category, dispatcherProvider) to category

    // -------- observe --------

    @Test
    fun `initial state is empty and not loading`() = runTest {
        val (vm, _) = newViewModel()

        // before the init's flow emits we briefly mark loading=true; drain it
        advanceUntilIdle()
        val state = vm.categoryState.value
        assertThat(state.categories).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `categories emission populates state and clears loading`() = runTest {
        val category = FakeCategoryRepository()
        val (vm, _) = newViewModel(category)

        category.setCategories(
            listOf(
                Category(id = "c-1", name = "Tools"),
                Category(id = "c-2", name = "Clothing"),
            )
        )
        advanceUntilIdle()

        val state = vm.categoryState.value
        assertThat(state.categories.map { it.name }).containsExactly("Tools", "Clothing")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `selected category refreshes when same id reappears with new data`() = runTest {
        val category = FakeCategoryRepository()
        val (vm, _) = newViewModel(category)
        val original = Category(id = "c-1", name = "Old Name")
        category.setCategories(listOf(original))
        advanceUntilIdle()
        vm.onAction(AdminCategoryAction.OnCategorySelected(original))
        advanceUntilIdle()

        // Simulate Firestore push with a renamed category at the same id.
        category.setCategories(listOf(original.copy(name = "New Name")))
        advanceUntilIdle()

        assertThat(vm.categoryState.value.selectedCategory?.name).isEqualTo("New Name")
    }

    @Test
    fun `selected category is cleared if removed from the stream`() = runTest {
        val category = FakeCategoryRepository()
        val (vm, _) = newViewModel(category)
        val keep = Category(id = "c-1", name = "Keep")
        category.setCategories(listOf(keep, Category(id = "c-2", name = "Gone")))
        advanceUntilIdle()
        vm.onAction(AdminCategoryAction.OnCategorySelected(Category(id = "c-2", name = "Gone")))
        advanceUntilIdle()

        category.setCategories(listOf(keep))
        advanceUntilIdle()

        assertThat(vm.categoryState.value.selectedCategory).isNull()
    }

    // -------- create --------

    @Test
    fun `createCategory without image skips upload and writes category`() = runTest {
        val category = FakeCategoryRepository()
        val (vm, _) = newViewModel(category)

        vm.onAction(AdminCategoryAction.OnCreateCategory("Books", "All books", imageUri = ""))
        advanceUntilIdle()

        assertThat(category.uploadImageCalls).isEmpty()
        assertThat(category.createCalls).hasSize(1)
        assertThat(category.createCalls.first().name).isEqualTo("Books")
        assertThat(vm.categoryState.value.showCreateDialog).isFalse()
        assertThat(vm.categoryState.value.errorMessage).isNull()
    }

    @Test
    fun `createCategory with image uploads first then writes category`() = runTest {
        val category = FakeCategoryRepository()
        category.uploadImageResult = Result.success("https://cdn/img.jpg")
        val (vm, _) = newViewModel(category)

        vm.onAction(AdminCategoryAction.OnCreateCategory("Books", "All", imageUri = "file://x.jpg"))
        advanceUntilIdle()

        assertThat(category.uploadImageCalls).hasSize(1)
        assertThat(category.createCalls.single().imageUrl).isEqualTo("https://cdn/img.jpg")
    }

    @Test
    fun `createCategory aborts when image upload fails and surfaces error`() = runTest {
        val category = FakeCategoryRepository()
        category.uploadImageResult = Result.failure(RuntimeException("storage down"))
        val (vm, _) = newViewModel(category)

        vm.onAction(AdminCategoryAction.OnCreateCategory("X", "d", imageUri = "file://x"))
        advanceUntilIdle()

        assertThat(category.createCalls).isEmpty()
        assertThat(vm.categoryState.value.errorMessage).contains("Image upload failed")
        assertThat(vm.categoryState.value.isLoading).isFalse()
    }

    @Test
    fun `createCategory surfaces firestore error and keeps dialog open`() = runTest {
        val category = FakeCategoryRepository()
        category.createCategoryResult = { Result.failure(RuntimeException("db error")) }
        val (vm, _) = newViewModel(category)
        vm.onAction(AdminCategoryAction.OnShowCreateDialog)
        advanceUntilIdle()

        vm.onAction(AdminCategoryAction.OnCreateCategory("X", "d", imageUri = ""))
        advanceUntilIdle()

        assertThat(vm.categoryState.value.errorMessage).isEqualTo("db error")
        assertThat(vm.categoryState.value.showCreateDialog).isTrue()
    }

    // -------- update --------

    @Test
    fun `updateCategory without new image reuses existing imageUrl`() = runTest {
        val category = FakeCategoryRepository()
        val (vm, _) = newViewModel(category)
        val existing = Category(id = "c-1", name = "Old", imageUrl = "https://old.jpg")

        vm.onAction(AdminCategoryAction.OnEditCategory(existing.copy(name = "New"), newImageUri = null))
        advanceUntilIdle()

        assertThat(category.uploadImageCalls).isEmpty()
        assertThat(category.updateCalls.single().name).isEqualTo("New")
        assertThat(category.updateCalls.single().imageUrl).isEqualTo("https://old.jpg")
    }

    @Test
    fun `updateCategory uploads new image and stores new url`() = runTest {
        val category = FakeCategoryRepository()
        category.uploadImageResult = Result.success("https://new.jpg")
        val (vm, _) = newViewModel(category)

        vm.onAction(
            AdminCategoryAction.OnEditCategory(
                category = Category(id = "c-1", name = "X", imageUrl = "https://old.jpg"),
                newImageUri = "file://new.jpg",
            )
        )
        advanceUntilIdle()

        assertThat(category.updateCalls.single().imageUrl).isEqualTo("https://new.jpg")
        assertThat(vm.categoryState.value.showEditDialog).isFalse()
        assertThat(vm.categoryState.value.selectedCategory).isNull()
    }

    @Test
    fun `updateCategory surfaces error from repo failure`() = runTest {
        val category = FakeCategoryRepository()
        category.updateCategoryResult = Result.failure(RuntimeException("oops"))
        val (vm, _) = newViewModel(category)

        vm.onAction(
            AdminCategoryAction.OnEditCategory(
                category = Category(id = "c-1", name = "X"),
                newImageUri = null,
            )
        )
        advanceUntilIdle()

        assertThat(vm.categoryState.value.errorMessage).isEqualTo("oops")
    }

    // -------- delete --------

    @Test
    fun `deleteCategory routes to repo and clears loading`() = runTest {
        val category = FakeCategoryRepository()
        val (vm, _) = newViewModel(category)

        vm.onAction(AdminCategoryAction.OnDeleteCategory("c-1"))
        advanceUntilIdle()

        assertThat(category.deleteCalls).containsExactly("c-1")
        assertThat(vm.categoryState.value.isLoading).isFalse()
        assertThat(vm.categoryState.value.errorMessage).isNull()
    }

    @Test
    fun `deleteCategory surfaces error from repo failure`() = runTest {
        val category = FakeCategoryRepository()
        category.deleteCategoryResult = Result.failure(RuntimeException("nope"))
        val (vm, _) = newViewModel(category)

        vm.onAction(AdminCategoryAction.OnDeleteCategory("c-1"))
        advanceUntilIdle()

        assertThat(vm.categoryState.value.errorMessage).isEqualTo("nope")
    }

    // -------- addSubcategory --------

    @Test
    fun `addSubcategory routes to repo and appends to selectedCategory when ids match`() = runTest {
        val category = FakeCategoryRepository()
        val (vm, _) = newViewModel(category)
        val selected = Category(id = "c-1", name = "X", subcategories = emptyList())
        category.setCategories(listOf(selected))
        advanceUntilIdle()
        vm.onAction(AdminCategoryAction.OnCategorySelected(selected))
        advanceUntilIdle()

        val sub = Subcategory(id = "s-1", name = "Sub 1")
        vm.onAction(AdminCategoryAction.OnAddSubcategory("c-1", sub))
        advanceUntilIdle()

        assertThat(category.addSubcategoryCalls).containsExactly("c-1" to sub)
        assertThat(vm.categoryState.value.selectedCategory?.subcategories?.map { it.id })
            .containsExactly("s-1")
    }

    @Test
    fun `addSubcategory ignores selected when ids differ`() = runTest {
        val category = FakeCategoryRepository()
        val (vm, _) = newViewModel(category)
        val selected = Category(id = "c-1", name = "X")
        category.setCategories(listOf(selected))
        advanceUntilIdle()
        vm.onAction(AdminCategoryAction.OnCategorySelected(selected))
        advanceUntilIdle()

        val sub = Subcategory(id = "s-1", name = "Sub 1")
        vm.onAction(AdminCategoryAction.OnAddSubcategory("c-OTHER", sub))
        advanceUntilIdle()

        assertThat(category.addSubcategoryCalls).containsExactly("c-OTHER" to sub)
        assertThat(vm.categoryState.value.selectedCategory?.subcategories ?: emptyList<Subcategory>()).isEmpty()
    }

    @Test
    fun `addSubcategory surfaces error and does not mutate selected`() = runTest {
        val category = FakeCategoryRepository()
        category.addSubcategoryResult = Result.failure(RuntimeException("boom"))
        val (vm, _) = newViewModel(category)
        val selected = Category(id = "c-1", name = "X")
        category.setCategories(listOf(selected))
        advanceUntilIdle()
        vm.onAction(AdminCategoryAction.OnCategorySelected(selected))
        advanceUntilIdle()

        vm.onAction(AdminCategoryAction.OnAddSubcategory("c-1", Subcategory(id = "s-1", name = "Sub 1")))
        advanceUntilIdle()

        assertThat(vm.categoryState.value.errorMessage).isEqualTo("boom")
        assertThat(vm.categoryState.value.selectedCategory?.subcategories ?: emptyList<Subcategory>()).isEmpty()
    }

    // -------- dialogs / selection --------

    @Test
    fun `OnShowCreateDialog flips showCreateDialog`() = runTest {
        val (vm, _) = newViewModel()

        vm.onAction(AdminCategoryAction.OnShowCreateDialog)
        advanceUntilIdle()

        assertThat(vm.categoryState.value.showCreateDialog).isTrue()
    }

    @Test
    fun `OnCategorySelected opens edit dialog with selection`() = runTest {
        val (vm, _) = newViewModel()
        val c = Category(id = "c-1", name = "X")

        vm.onAction(AdminCategoryAction.OnCategorySelected(c))
        advanceUntilIdle()

        assertThat(vm.categoryState.value.selectedCategory).isEqualTo(c)
        assertThat(vm.categoryState.value.showEditDialog).isTrue()
    }

    @Test
    fun `OnDismissDialog clears every dialog flag and selection`() = runTest {
        val (vm, _) = newViewModel()
        vm.onAction(AdminCategoryAction.OnCategorySelected(Category(id = "c-1", name = "X")))
        vm.onAction(AdminCategoryAction.OnShowCreateDialog)
        advanceUntilIdle()

        vm.onAction(AdminCategoryAction.OnDismissDialog)
        advanceUntilIdle()

        val state = vm.categoryState.value
        assertThat(state.showCreateDialog).isFalse()
        assertThat(state.showEditDialog).isFalse()
        assertThat(state.selectedCategory).isNull()
    }
}
