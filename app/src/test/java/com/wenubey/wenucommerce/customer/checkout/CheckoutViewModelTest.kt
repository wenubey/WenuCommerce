package com.wenubey.wenucommerce.customer.checkout

import com.google.common.truth.Truth.assertThat
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.wenubey.data.connectivity.ConnectivityObserver
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.discount.CouponValidationResult
import com.wenubey.domain.model.discount.DiscountType
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.ShippingAddress
import com.wenubey.domain.model.user.User
import com.wenubey.domain.repository.PaymentIntentResult
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.TestDispatcherProvider
import com.wenubey.wenucommerce.testing.fakes.FakeAddressRepository
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeCartRepository
import com.wenubey.wenucommerce.testing.fakes.FakeDiscountRepository
import com.wenubey.wenucommerce.testing.fakes.FakePaymentRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private val testUserId = "u-1"
    private val testUser = User(uuid = testUserId)

    private val sampleAddress = ShippingAddress(
        id = "addr-1", fullName = "Alice", line1 = "1 Main", city = "Istanbul",
        state = "IS", postalCode = "34000", country = "TR",
    )

    private fun cartItem(id: String, qty: Int = 1, price: Double = 10.0) = CartItem(
        productId = id, productTitle = id, quantity = qty, snapshotPrice = price, availableStock = 10,
    )

    private fun connectivity(online: Boolean = true): ConnectivityObserver {
        val mock: ConnectivityObserver = mockk(relaxed = true)
        val flow = MutableStateFlow(online).asStateFlow()
        every { mock.isOnline } returns flow
        return mock
    }

    private fun newViewModel(
        payment: FakePaymentRepository = FakePaymentRepository(),
        cart: FakeCartRepository = FakeCartRepository(),
        address: FakeAddressRepository = FakeAddressRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = testUser),
        connectivity: ConnectivityObserver = connectivity(),
        discount: FakeDiscountRepository = FakeDiscountRepository(),
    ) = CheckoutViewModel(payment, cart, address, auth, connectivity, discount, dispatcherProvider)

    // --- Init / observer wiring ---

    @Test
    fun `null user stops loading without subscribing to cart`() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val cart = FakeCartRepository()
        val vm = newViewModel(auth = auth, cart = cart)
        advanceUntilIdle()

        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `cart items propagate to state with computed subtotal`() = runTest {
        val cart = FakeCartRepository()
        val vm = newViewModel(cart = cart)
        cart.emitCartItems(testUserId, listOf(
            cartItem("p-1", qty = 2, price = 5.0),
            cartItem("p-2", qty = 1, price = 10.0),
        ))
        advanceUntilIdle()

        assertThat(vm.state.value.cartItems).hasSize(2)
        assertThat(vm.state.value.subtotal).isEqualTo(20.0)
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `cart observe error stops loading without crashing`() = runTest {
        val cart = FakeCartRepository().apply {
            observeCartItemsFlow = flow { throw RuntimeException("network") }
        }
        val vm = newViewModel(cart = cart)
        advanceUntilIdle()

        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `savedAddresses populates and auto-selects first when none selected`() = runTest {
        val address = FakeAddressRepository()
        val vm = newViewModel(address = address)
        address.emit(testUserId, listOf(sampleAddress, sampleAddress.copy(id = "addr-2")))
        advanceUntilIdle()

        assertThat(vm.state.value.savedAddresses).hasSize(2)
        assertThat(vm.state.value.selectedAddress).isEqualTo(sampleAddress)
    }

    @Test
    fun `savedAddresses does NOT overwrite an already selected address`() = runTest {
        val address = FakeAddressRepository()
        val vm = newViewModel(address = address)
        val explicit = sampleAddress.copy(id = "explicit", fullName = "Explicit")
        // User selects an address before the saved list arrives.
        vm.onAction(CheckoutAction.SelectAddress(explicit))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedAddress).isEqualTo(explicit)
    }

    @Test
    fun `connectivity flow drives isOnline flag`() = runTest {
        val online = connectivity(online = false)
        val vm = newViewModel(connectivity = online)
        advanceUntilIdle()
        assertThat(vm.state.value.isOnline).isFalse()
    }

    // --- Step navigation ---

    @Test
    fun `GoToStep backward navigation is unconditional`() = runTest {
        val vm = newViewModel()
        vm.onAction(CheckoutAction.SelectAddress(sampleAddress))
        vm.onAction(CheckoutAction.GoToStep(1))
        advanceUntilIdle()
        assertThat(vm.state.value.currentStep).isEqualTo(1)

        vm.onAction(CheckoutAction.GoToStep(0))
        advanceUntilIdle()
        assertThat(vm.state.value.currentStep).isEqualTo(0)
    }

    @Test
    fun `cannot advance to review step (1) without a selected address`() = runTest {
        val vm = newViewModel()
        // No address selected.
        vm.onAction(CheckoutAction.GoToStep(1))
        advanceUntilIdle()
        assertThat(vm.state.value.currentStep).isEqualTo(0)
    }

    @Test
    fun `advance to step 2 creates a PaymentIntent`() = runTest {
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val payment = FakePaymentRepository()
        val vm = newViewModel(cart = cart, address = address, payment = payment)

        cart.emitCartItems(testUserId, listOf(cartItem("p-1", qty = 1, price = 50.0)))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()

        vm.onAction(CheckoutAction.GoToStep(2))
        advanceUntilIdle()

        assertThat(vm.state.value.currentStep).isEqualTo(2)
        assertThat(payment.createPaymentIntentCalls).hasSize(1)
        assertThat(vm.state.value.clientSecret).isEqualTo("pi_secret_123")
        assertThat(vm.state.value.amountCents).isEqualTo(5_000)
    }

    @Test
    fun `PreviousStep decrements but never below 0`() = runTest {
        val vm = newViewModel()
        vm.onAction(CheckoutAction.PreviousStep)
        advanceUntilIdle()
        assertThat(vm.state.value.currentStep).isEqualTo(0)
    }

    // --- PaymentIntent ---

    @Test
    fun `createPaymentIntent is no-op when cart empty`() = runTest {
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val payment = FakePaymentRepository()
        val vm = newViewModel(cart = cart, address = address, payment = payment)
        // Address selected but cart empty.
        address.emit(testUserId, listOf(sampleAddress))
        cart.emitCartItems(testUserId, emptyList())
        advanceUntilIdle()

        vm.onAction(CheckoutAction.CreatePaymentIntent)
        advanceUntilIdle()
        assertThat(payment.createPaymentIntentCalls).isEmpty()
    }

    @Test
    fun `createPaymentIntent stock error redirects to step 1 and surfaces stockError`() = runTest {
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val payment = FakePaymentRepository().apply {
            createPaymentIntentResult = Result.failure(RuntimeException("Out of stock for p-1"))
        }
        val vm = newViewModel(cart = cart, address = address, payment = payment)

        cart.emitCartItems(testUserId, listOf(cartItem("p-1")))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()

        vm.onAction(CheckoutAction.GoToStep(2))
        advanceUntilIdle()

        assertThat(vm.state.value.stockError).contains("Out of stock")
        assertThat(vm.state.value.currentStep).isEqualTo(1)
        assertThat(vm.state.value.paymentError).isNull()
    }

    @Test
    fun `createPaymentIntent generic error surfaces paymentError and keeps step`() = runTest {
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val payment = FakePaymentRepository().apply {
            createPaymentIntentResult = Result.failure(RuntimeException("Server unreachable"))
        }
        val vm = newViewModel(cart = cart, address = address, payment = payment)

        cart.emitCartItems(testUserId, listOf(cartItem("p-1")))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()

        vm.onAction(CheckoutAction.GoToStep(2))
        advanceUntilIdle()

        assertThat(vm.state.value.paymentError).isEqualTo("Server unreachable")
        assertThat(vm.state.value.stockError).isNull()
        assertThat(vm.state.value.currentStep).isEqualTo(2) // not rolled back
    }

    @Test
    fun `RetryPayment clears paymentError and re-issues PaymentIntent`() = runTest {
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val payment = FakePaymentRepository()
        val vm = newViewModel(cart = cart, address = address, payment = payment)

        cart.emitCartItems(testUserId, listOf(cartItem("p-1")))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()

        // First attempt fails.
        payment.createPaymentIntentResult = Result.failure(RuntimeException("temp"))
        vm.onAction(CheckoutAction.GoToStep(2))
        advanceUntilIdle()
        assertThat(vm.state.value.paymentError).isEqualTo("temp")

        // Recovery: server back, user retries.
        payment.createPaymentIntentResult = Result.success(
            PaymentIntentResult("pi_2", 8_000, "order-9", 0)
        )
        vm.onAction(CheckoutAction.RetryPayment)
        advanceUntilIdle()

        assertThat(vm.state.value.paymentError).isNull()
        assertThat(vm.state.value.clientSecret).isEqualTo("pi_2")
        assertThat(vm.state.value.amountCents).isEqualTo(8_000)
        assertThat(payment.createPaymentIntentCalls).hasSize(2)
    }

    // --- Coupon ---

    @Test
    fun `UpdateCouponInput stores the input and clears couponError`() = runTest {
        val vm = newViewModel()
        vm.onAction(CheckoutAction.UpdateCouponInput("SAVE20"))
        advanceUntilIdle()
        assertThat(vm.state.value.couponInput).isEqualTo("SAVE20")
        assertThat(vm.state.value.couponError).isNull()
    }

    @Test
    fun `ApplyCoupon with blank input is no-op`() = runTest {
        val discount = FakeDiscountRepository()
        val vm = newViewModel(discount = discount)
        vm.onAction(CheckoutAction.UpdateCouponInput("   "))
        vm.onAction(CheckoutAction.ApplyCoupon)
        advanceUntilIdle()
        assertThat(discount.validateCalls).isEmpty()
    }

    @Test
    fun `ApplyCoupon success stores result fields, clears input, sends cents subtotal`() = runTest {
        val cart = FakeCartRepository()
        val discount = FakeDiscountRepository().apply {
            validateResult = Result.success(
                CouponValidationResult(
                    code = "SAVE20",
                    type = DiscountType.PERCENTAGE,
                    discountAmountCents = 400,
                    description = "20% off",
                )
            )
        }
        val vm = newViewModel(cart = cart, discount = discount)
        cart.emitCartItems(testUserId, listOf(cartItem("p-1", qty = 2, price = 10.0)))
        advanceUntilIdle()

        vm.onAction(CheckoutAction.UpdateCouponInput("save20"))
        vm.onAction(CheckoutAction.ApplyCoupon)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.appliedCouponCode).isEqualTo("SAVE20")
        assertThat(state.appliedCouponType).isEqualTo(DiscountType.PERCENTAGE)
        assertThat(state.discountAmountCents).isEqualTo(400)
        assertThat(state.couponInput).isEmpty()
        assertThat(state.couponError).isNull()
        assertThat(state.isValidatingCoupon).isFalse()
        assertThat(discount.validateCalls).hasSize(1)
        assertThat(discount.validateCalls[0].first).isEqualTo("save20")
        assertThat(discount.validateCalls[0].third).isEqualTo(2_000) // 2 * $10 * 100 cents
    }

    @Test
    fun `ApplyCoupon failure surfaces error and leaves discount untouched`() = runTest {
        val discount = FakeDiscountRepository().apply {
            validateResult = Result.failure(RuntimeException("expired"))
        }
        val vm = newViewModel(discount = discount)
        vm.onAction(CheckoutAction.UpdateCouponInput("EXPIRED"))
        vm.onAction(CheckoutAction.ApplyCoupon)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.couponError).isEqualTo("expired")
        assertThat(state.appliedCouponCode).isNull()
        assertThat(state.discountAmountCents).isEqualTo(0)
        assertThat(state.isValidatingCoupon).isFalse()
    }

    @Test
    fun `RemoveCoupon resets applied coupon AND invalidates clientSecret (Pitfall 4)`() = runTest {
        // Critical regression test: removing a coupon must clear the clientSecret
        // because the PaymentIntent was created for the discounted amount.
        val discount = FakeDiscountRepository().apply {
            validateResult = Result.success(
                CouponValidationResult(
                    code = "SAVE", type = DiscountType.FIXED_AMOUNT,
                    discountAmountCents = 200, description = "$2 off",
                )
            )
        }
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val payment = FakePaymentRepository().apply {
            createPaymentIntentResult = Result.success(
                PaymentIntentResult("pi_disc", 800, "order-1", 200)
            )
        }
        val vm = newViewModel(cart = cart, address = address, payment = payment, discount = discount)

        cart.emitCartItems(testUserId, listOf(cartItem("p-1", qty = 1, price = 10.0)))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()

        // Apply coupon then advance to payment (creates discounted PI).
        vm.onAction(CheckoutAction.UpdateCouponInput("SAVE"))
        vm.onAction(CheckoutAction.ApplyCoupon)
        advanceUntilIdle()
        vm.onAction(CheckoutAction.GoToStep(2))
        advanceUntilIdle()
        assertThat(vm.state.value.clientSecret).isEqualTo("pi_disc")
        assertThat(vm.state.value.discountAmountCents).isEqualTo(200)

        // Remove coupon: discount cleared AND clientSecret + orderId + amountCents wiped.
        vm.onAction(CheckoutAction.RemoveCoupon)
        advanceUntilIdle()
        val s = vm.state.value
        assertThat(s.appliedCouponCode).isNull()
        assertThat(s.appliedCouponType).isNull()
        assertThat(s.discountAmountCents).isEqualTo(0)
        assertThat(s.clientSecret).isEmpty()
        assertThat(s.orderId).isEmpty()
        assertThat(s.amountCents).isEqualTo(0)
    }

    @Test
    fun `DismissCouponError clears couponError only`() = runTest {
        val discount = FakeDiscountRepository().apply {
            validateResult = Result.failure(RuntimeException("nope"))
        }
        val vm = newViewModel(discount = discount)
        vm.onAction(CheckoutAction.UpdateCouponInput("X"))
        vm.onAction(CheckoutAction.ApplyCoupon)
        advanceUntilIdle()
        assertThat(vm.state.value.couponError).isEqualTo("nope")

        vm.onAction(CheckoutAction.DismissCouponError)
        advanceUntilIdle()
        assertThat(vm.state.value.couponError).isNull()
    }

    @Test
    fun `DismissPaymentError and DismissStockError clear their flags`() = runTest {
        val payment = FakePaymentRepository().apply {
            createPaymentIntentResult = Result.failure(RuntimeException("Server down"))
        }
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val vm = newViewModel(payment = payment, cart = cart, address = address)
        cart.emitCartItems(testUserId, listOf(cartItem("p-1")))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()

        vm.onAction(CheckoutAction.GoToStep(2))
        advanceUntilIdle()
        assertThat(vm.state.value.paymentError).isEqualTo("Server down")

        vm.onAction(CheckoutAction.DismissPaymentError)
        advanceUntilIdle()
        assertThat(vm.state.value.paymentError).isNull()

        // Force a stockError next.
        payment.createPaymentIntentResult = Result.failure(RuntimeException("Inventory issue"))
        vm.onAction(CheckoutAction.RetryPayment)
        advanceUntilIdle()
        assertThat(vm.state.value.stockError).contains("Inventory")

        vm.onAction(CheckoutAction.DismissStockError)
        advanceUntilIdle()
        assertThat(vm.state.value.stockError).isNull()
    }

    // --- PaymentSheetResult handling ---
    // PaymentSheetResult.{Completed, Canceled, Failed} are classes (not objects)
    // and PaymentSheetResult.Failed.<init> is internal — we cannot construct them
    // directly from a test module. We use mockk to produce instances of the
    // correct concrete subtype; onPaymentResult only branches on `is X` so an
    // empty mock of the right type is sufficient.
    private val resultCompleted: PaymentSheetResult.Completed get() = mockk(relaxed = true)
    private val resultCanceled: PaymentSheetResult.Canceled get() = mockk(relaxed = true)
    private fun resultFailed(message: String): PaymentSheetResult.Failed {
        val failed: PaymentSheetResult.Failed = mockk(relaxed = true)
        every { failed.error } returns RuntimeException(message)
        return failed
    }

    @Test
    fun `onPaymentResult Completed persists order, decrements coupon, clears cart, emits navigation`() = runTest {
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val payment = FakePaymentRepository().apply {
            createPaymentIntentResult = Result.success(
                PaymentIntentResult("pi_done", 800, "order-7", 200)
            )
        }
        val discount = FakeDiscountRepository().apply {
            validateResult = Result.success(
                CouponValidationResult("SAVE", DiscountType.FIXED_AMOUNT, 200, "")
            )
        }
        val vm = newViewModel(cart = cart, address = address, payment = payment, discount = discount)

        cart.emitCartItems(testUserId, listOf(cartItem("p-1", qty = 1, price = 10.0)))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()
        vm.onAction(CheckoutAction.UpdateCouponInput("SAVE"))
        vm.onAction(CheckoutAction.ApplyCoupon)
        advanceUntilIdle()
        vm.onAction(CheckoutAction.GoToStep(2))
        advanceUntilIdle()

        val emitted = mutableListOf<String>()
        val collectJob = launch { vm.navigationEvent.collect { emitted.add(it) } }
        advanceUntilIdle()

        vm.onPaymentResult(resultCompleted)
        advanceUntilIdle()
        collectJob.cancel()

        // Order persisted to Room with CONFIRMED status and coupon details.
        assertThat(payment.createOrderInRoomCalls).hasSize(1)
        val saved = payment.createOrderInRoomCalls[0]
        assertThat(saved.id).isEqualTo("order-7")
        assertThat(saved.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(saved.discountCode).isEqualTo("SAVE")
        assertThat(saved.discountAmount).isEqualTo(2.0)
        assertThat(saved.items).hasSize(1)
        assertThat(saved.items[0].lineTotal).isEqualTo(10.0)

        // Firestore order status updated, coupon usage decremented, cart cleared.
        assertThat(payment.updateOrderStatusCalls).contains("order-7" to OrderStatus.CONFIRMED)
        assertThat(discount.decrementCalls).contains("SAVE")
        assertThat(cart.clearCartCalls).contains(testUserId)

        // Navigation event fired with orderId.
        assertThat(emitted).contains("order-7")
    }

    @Test
    fun `onPaymentResult Completed without coupon does not decrement usage`() = runTest {
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val payment = FakePaymentRepository().apply {
            createPaymentIntentResult = Result.success(
                PaymentIntentResult("pi_done", 1_000, "order-8", 0)
            )
        }
        val discount = FakeDiscountRepository()
        val vm = newViewModel(cart = cart, address = address, payment = payment, discount = discount)

        cart.emitCartItems(testUserId, listOf(cartItem("p-1")))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()
        vm.onAction(CheckoutAction.GoToStep(2))
        advanceUntilIdle()

        vm.onPaymentResult(resultCompleted)
        advanceUntilIdle()

        assertThat(discount.decrementCalls).isEmpty()
        assertThat(payment.createOrderInRoomCalls).hasSize(1)
        assertThat(cart.clearCartCalls).contains(testUserId)
    }

    @Test
    fun `onPaymentResult Failed surfaces paymentError and clears isProcessingPayment`() = runTest {
        val vm = newViewModel()
        vm.onPaymentResult(resultFailed("card declined"))
        advanceUntilIdle()

        assertThat(vm.state.value.paymentError).isEqualTo("card declined")
        assertThat(vm.state.value.isProcessingPayment).isFalse()
    }

    @Test
    fun `onPaymentResult Canceled does not change state`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        val snapshot = vm.state.value
        vm.onPaymentResult(resultCanceled)
        advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(snapshot)
    }

    // --- Computed properties ---

    @Test
    fun `canProceedToReview requires selected address`() = runTest {
        val vm = newViewModel()
        assertThat(vm.state.value.canProceedToReview).isFalse()
        vm.onAction(CheckoutAction.SelectAddress(sampleAddress))
        advanceUntilIdle()
        assertThat(vm.state.value.canProceedToReview).isTrue()
    }

    @Test
    fun `canProceedToPayment requires non-empty clientSecret and not currently creating intent`() = runTest {
        val cart = FakeCartRepository()
        val address = FakeAddressRepository()
        val payment = FakePaymentRepository()
        val vm = newViewModel(cart = cart, address = address, payment = payment)

        assertThat(vm.state.value.canProceedToPayment).isFalse()

        cart.emitCartItems(testUserId, listOf(cartItem("p-1")))
        address.emit(testUserId, listOf(sampleAddress))
        advanceUntilIdle()
        vm.onAction(CheckoutAction.GoToStep(2))
        advanceUntilIdle()

        assertThat(vm.state.value.canProceedToPayment).isTrue()
    }
}
