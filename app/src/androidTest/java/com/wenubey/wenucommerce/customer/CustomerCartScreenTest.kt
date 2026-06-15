package com.wenubey.wenucommerce.customer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.GetCredentialResponse
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.auth.SignInResult
import com.wenubey.domain.auth.SignUpResult
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.user.User
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.CartRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.wenucommerce.customer.customer_cart.CartViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class CustomerCartScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun io(): CoroutineDispatcher = Dispatchers.Unconfined
        override fun default(): CoroutineDispatcher = Dispatchers.Unconfined
    }

    private fun renderScreen(
        cart: FakeCartRepo = FakeCartRepo(),
        auth: FakeAuthRepo = FakeAuthRepo(seedUser = User(uuid = "u-1")),
        onHome: () -> Unit = {},
        onCheckout: () -> Unit = {},
    ): CartViewModel {
        val vm = CartViewModel(cart, auth, dispatcherProvider)
        composeTestRule.setContent {
            CustomerCartScreen(
                viewModel = vm,
                onNavigateToHome = onHome,
                onNavigateToCheckout = onCheckout,
            )
        }
        return vm
    }

    @Test
    fun renders_empty_state_when_cart_has_no_items() {
        renderScreen()

        composeTestRule.onNodeWithText("Your cart feels lonely!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Shopping").assertIsDisplayed()
    }

    @Test
    fun start_shopping_button_invokes_navigateToHome() {
        var clicked = false
        renderScreen(onHome = { clicked = true })

        composeTestRule.onNodeWithText("Start Shopping").performClick()
        composeTestRule.waitForIdle()

        assertThat(clicked).isTrue()
    }

    @Test
    fun renders_login_error_when_no_user_signed_in() {
        renderScreen(auth = FakeAuthRepo(seedUser = null))

        composeTestRule.onNodeWithText("Please log in to view your cart").assertIsDisplayed()
    }

    private class FakeCartRepo(
        private val items: List<CartItem> = emptyList(),
    ) : CartRepository {
        override fun observeCartItems(userId: String): Flow<List<CartItem>> = flowOf(items)
        override fun observeUniqueProductCount(userId: String): Flow<Int> = flowOf(items.size)
        override suspend fun getCartItem(userId: String, productId: String): CartItem? =
            items.find { it.productId == productId }
        override suspend fun addToCart(userId: String, product: Product, quantity: Int) {}
        override suspend fun restoreCartItem(userId: String, cartItem: CartItem) {}
        override suspend fun updateQuantity(userId: String, productId: String, newQuantity: Int) {}
        override suspend fun removeFromCart(userId: String, productId: String) {}
        override suspend fun clearCart(userId: String) {}
        override suspend fun syncAddToCart(
            userId: String,
            productId: String,
            quantity: Int,
            snapshotPrice: Double,
        ) {}
        override suspend fun syncUpdateQuantity(userId: String, productId: String, quantity: Int) {}
        override suspend fun syncRemoveFromCart(userId: String, productId: String) {}
    }

    private class FakeAuthRepo(private val seedUser: User?) : AuthRepository {
        private val _currentUser = MutableStateFlow(seedUser)
        override val isAuthenticated: Boolean = seedUser != null
        override val currentAuthEmail: String? = seedUser?.email
        override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
        override suspend fun signIn(credentialResponse: GetCredentialResponse): Result<User> =
            Result.failure(IllegalStateException())
        override suspend fun getCredential(): Result<GetCredentialResponse?> = Result.success(null)
        override suspend fun signInWithEmailPassword(
            email: String, password: String, saveCredentials: Boolean,
        ): SignInResult = SignInResult.Failure("nope")
        override suspend fun signUpWithEmailPassword(
            email: String, password: String, saveCredentials: Boolean,
        ): SignUpResult = SignUpResult.Failure("nope")
        override suspend fun logOut(): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun resendVerificationEmail(): Result<Unit> = Result.success(Unit)
        override suspend fun isUserAuthenticated(): Result<Boolean> = Result.success(seedUser != null)
        override suspend fun isEmailVerified(): Result<Boolean> = Result.success(true)
        override suspend fun isPhoneNumberVerified(): Result<Boolean> = Result.success(false)
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(seedUser)
        override suspend fun setCurrentUserAfterOnboarding(user: User) {
            _currentUser.value = user
        }
    }

}
