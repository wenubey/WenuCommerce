package com.wenubey.wenucommerce.admin.admin_products

import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeProductRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminProductModerationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun newViewModel(
        product: FakeProductRepository = FakeProductRepository(),
        adminUid: String? = "admin-1",
    ): Triple<AdminProductModerationViewModel, FakeProductRepository, FirebaseAuth> {
        val auth: FirebaseAuth = mockk(relaxed = true)
        if (adminUid != null) {
            val user: FirebaseUser = mockk(relaxed = true)
            every { user.uid } returns adminUid
            every { auth.currentUser } returns user
        } else {
            every { auth.currentUser } returns null
        }
        val vm = AdminProductModerationViewModel(product, auth, dispatcherProvider)
        return Triple(vm, product, auth)
    }

    private fun pendingProduct(id: String, sellerId: String = "s-1") = Product(
        id = id,
        sellerId = sellerId,
        title = "P-$id",
        status = ProductStatus.PENDING_REVIEW,
    )

    @Test
    fun `observes pending review products and clears loading`() = runTest {
        val product = FakeProductRepository()
        val (vm, _) = newViewModel(product).let { it.first to it.second }

        product.emitSellerProducts("s-1", listOf(pendingProduct("p1"), pendingProduct("p2")))
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.pendingProducts.map { it.id }).containsExactly("p1", "p2")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `observe filters out non-PENDING_REVIEW products`() = runTest {
        val product = FakeProductRepository()
        val (vm, _) = newViewModel(product).let { it.first to it.second }

        product.emitSellerProducts(
            "s-1",
            listOf(
                pendingProduct("p1"),
                Product(id = "p2", sellerId = "s-1", status = ProductStatus.ACTIVE, title = "Live"),
            )
        )
        advanceUntilIdle()

        assertThat(vm.state.value.pendingProducts.map { it.id }).containsExactly("p1")
    }

    @Test
    fun `OnProductSelected stores selection`() = runTest {
        val (vm, _) = newViewModel().let { it.first to it.second }
        val p = pendingProduct("p1")

        vm.onAction(AdminProductModerationAction.OnProductSelected(p))

        assertThat(vm.state.value.selectedProduct).isEqualTo(p)
    }

    @Test
    fun `OnShowApproveDialog and OnShowSuspendDialog and OnShowDetailDialog flip flags`() = runTest {
        val (vm, _) = newViewModel().let { it.first to it.second }

        vm.onAction(AdminProductModerationAction.OnShowApproveDialog)
        vm.onAction(AdminProductModerationAction.OnShowSuspendDialog)
        vm.onAction(AdminProductModerationAction.OnShowDetailDialog)

        val state = vm.state.value
        assertThat(state.showApproveDialog).isTrue()
        assertThat(state.showSuspendDialog).isTrue()
        assertThat(state.showDetailDialog).isTrue()
    }

    @Test
    fun `OnDismissDialog clears every dialog flag and suspendReason`() = runTest {
        val (vm, _) = newViewModel().let { it.first to it.second }
        vm.onAction(AdminProductModerationAction.OnShowApproveDialog)
        vm.onAction(AdminProductModerationAction.OnShowSuspendDialog)
        vm.onAction(AdminProductModerationAction.OnShowDetailDialog)
        vm.onAction(AdminProductModerationAction.OnSuspendReasonChanged("bad"))

        vm.onAction(AdminProductModerationAction.OnDismissDialog)

        val state = vm.state.value
        assertThat(state.showApproveDialog).isFalse()
        assertThat(state.showSuspendDialog).isFalse()
        assertThat(state.showDetailDialog).isFalse()
        assertThat(state.suspendReason).isEmpty()
    }

    // -------- approve --------

    @Test
    fun `approveProduct without selection is a no-op`() = runTest {
        val product = FakeProductRepository()
        val (vm, _) = newViewModel(product).let { it.first to it.second }

        vm.onAction(AdminProductModerationAction.OnConfirmApprove)
        advanceUntilIdle()

        assertThat(product.approveCalls).isEmpty()
    }

    @Test
    fun `approveProduct calls repo and clears selection on success`() = runTest {
        val product = FakeProductRepository()
        val (vm, _) = newViewModel(product).let { it.first to it.second }
        val p = pendingProduct("p1")
        vm.onAction(AdminProductModerationAction.OnProductSelected(p))
        vm.onAction(AdminProductModerationAction.OnShowApproveDialog)

        vm.onAction(AdminProductModerationAction.OnConfirmApprove)
        advanceUntilIdle()

        assertThat(product.approveCalls).containsExactly("p1")
        val state = vm.state.value
        assertThat(state.selectedProduct).isNull()
        assertThat(state.showApproveDialog).isFalse()
        assertThat(state.isActing).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `approveProduct surfaces error and keeps selection`() = runTest {
        val product = FakeProductRepository()
        product.approveProductResult = Result.failure(RuntimeException("nope"))
        val (vm, _) = newViewModel(product).let { it.first to it.second }
        val p = pendingProduct("p1")
        vm.onAction(AdminProductModerationAction.OnProductSelected(p))

        vm.onAction(AdminProductModerationAction.OnConfirmApprove)
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isEqualTo("nope")
        assertThat(vm.state.value.selectedProduct).isEqualTo(p)
    }

    // -------- suspend --------

    @Test
    fun `suspendProduct without selection is a no-op`() = runTest {
        val product = FakeProductRepository()
        val (vm, _) = newViewModel(product).let { it.first to it.second }

        vm.onAction(AdminProductModerationAction.OnConfirmSuspend)
        advanceUntilIdle()

        assertThat(product.suspendCalls).isEmpty()
    }

    @Test
    fun `suspendProduct without auth uid is a no-op`() = runTest {
        val product = FakeProductRepository()
        val (vm, _, _) = newViewModel(product, adminUid = null)
        vm.onAction(AdminProductModerationAction.OnProductSelected(pendingProduct("p1")))
        vm.onAction(AdminProductModerationAction.OnSuspendReasonChanged("counterfeit"))

        vm.onAction(AdminProductModerationAction.OnConfirmSuspend)
        advanceUntilIdle()

        assertThat(product.suspendCalls).isEmpty()
    }

    @Test
    fun `suspendProduct with blank reason surfaces error without calling repo`() = runTest {
        val product = FakeProductRepository()
        val (vm, _) = newViewModel(product).let { it.first to it.second }
        // Drain the init observer first — its collect emits with
        // errorMessage = null and would clobber the synchronous error we
        // expect from the blank-reason guard below.
        advanceUntilIdle()
        vm.onAction(AdminProductModerationAction.OnProductSelected(pendingProduct("p1")))
        vm.onAction(AdminProductModerationAction.OnSuspendReasonChanged("   "))

        vm.onAction(AdminProductModerationAction.OnConfirmSuspend)
        // The guard sets errorMessage synchronously and returns before any
        // launch — no advanceUntilIdle needed (and skipping it avoids a
        // late observer emission overwriting errorMessage with null).

        assertThat(product.suspendCalls).isEmpty()
        assertThat(vm.state.value.errorMessage).contains("suspension reason")
    }

    @Test
    fun `suspendProduct routes reason and adminId and resets state on success`() = runTest {
        val product = FakeProductRepository()
        val (vm, _) = newViewModel(product, adminUid = "admin-7").let { it.first to it.second }
        vm.onAction(AdminProductModerationAction.OnProductSelected(pendingProduct("p1")))
        vm.onAction(AdminProductModerationAction.OnSuspendReasonChanged("counterfeit"))
        vm.onAction(AdminProductModerationAction.OnShowSuspendDialog)

        vm.onAction(AdminProductModerationAction.OnConfirmSuspend)
        advanceUntilIdle()

        assertThat(product.suspendCalls).containsExactly(Triple("p1", "counterfeit", "admin-7"))
        val state = vm.state.value
        assertThat(state.selectedProduct).isNull()
        assertThat(state.suspendReason).isEmpty()
        assertThat(state.showSuspendDialog).isFalse()
        assertThat(state.isActing).isFalse()
    }

    @Test
    fun `suspendProduct surfaces repo failure`() = runTest {
        val product = FakeProductRepository()
        product.suspendProductResult = Result.failure(RuntimeException("nope"))
        val (vm, _) = newViewModel(product).let { it.first to it.second }
        vm.onAction(AdminProductModerationAction.OnProductSelected(pendingProduct("p1")))
        vm.onAction(AdminProductModerationAction.OnSuspendReasonChanged("counterfeit"))

        vm.onAction(AdminProductModerationAction.OnConfirmSuspend)
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isEqualTo("nope")
    }
}
