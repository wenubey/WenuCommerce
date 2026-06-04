package com.wenubey.wenucommerce.customer.checkout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.wenucommerce.customer.checkout.components.AddressStepContent
import com.wenubey.wenucommerce.customer.checkout.components.CheckoutProgressDots
import com.wenubey.wenucommerce.customer.checkout.components.PaymentStepContent
import com.wenubey.wenucommerce.customer.checkout.components.ReviewStepContent
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onNavigateToAddAddress: () -> Unit = {},
    onNavigateToConfirmation: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: CheckoutViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLeaveDialog by remember { mutableStateOf(false) }

    // Collect navigation events to proceed to confirmation screen
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { orderId ->
            onNavigateToConfirmation(orderId)
        }
    }

    // Back handler — show leave dialog
    BackHandler {
        showLeaveDialog = true
    }

    // Leave checkout confirmation dialog
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave checkout?") },
            text = { Text("Your progress will be lost if you leave now.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    onNavigateBack()
                }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                Button(onClick = { showLeaveDialog = false }) {
                    Text("Stay")
                }
            },
        )
    }

    // Offline gate: do not show wizard if offline
    if (!state.isOnline) {
        OfflineCheckoutState(onBack = onNavigateBack)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (state.currentStep) {
                            0 -> "Shipping Address"
                            1 -> "Review Order"
                            2 -> "Payment"
                            else -> "Checkout"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showLeaveDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // Progress dots
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckoutProgressDots(
                        currentStep = state.currentStep,
                        onStepClick = { step ->
                            viewModel.onAction(CheckoutAction.GoToStep(step))
                        },
                    )
                }

                // Step content with animated transitions
                AnimatedContent(
                    targetState = state.currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            // Forward: slide in from right, slide out to left
                            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                        } else {
                            // Backward: slide in from left, slide out to right
                            slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                        }
                    },
                    label = "checkout_step_content",
                    modifier = Modifier.fillMaxSize(),
                ) { step ->
                    when (step) {
                        0 -> AddressStepContent(
                            savedAddresses = state.savedAddresses,
                            selectedAddress = state.selectedAddress,
                            onSelectAddress = { address ->
                                viewModel.onAction(CheckoutAction.SelectAddress(address))
                            },
                            onAddNewAddress = onNavigateToAddAddress,
                            onContinue = {
                                viewModel.onAction(CheckoutAction.NextStep)
                            },
                        )
                        1 -> ReviewStepContent(
                            cartItems = state.cartItems,
                            selectedAddress = state.selectedAddress,
                            subtotal = state.subtotal,
                            shippingTotal = state.shippingTotal,
                            total = state.total,
                            amountCents = state.amountCents,
                            isCreatingPaymentIntent = state.isCreatingPaymentIntent,
                            stockError = state.stockError,
                            onDismissStockError = {
                                viewModel.onAction(CheckoutAction.DismissStockError)
                            },
                            onContinue = {
                                viewModel.onAction(CheckoutAction.NextStep)
                            },
                            onBack = {
                                viewModel.onAction(CheckoutAction.PreviousStep)
                            },
                            couponInput = state.couponInput,
                            appliedCouponCode = state.appliedCouponCode,
                            appliedCouponType = state.appliedCouponType,
                            discountAmountCents = state.discountAmountCents,
                            couponError = state.couponError,
                            isValidatingCoupon = state.isValidatingCoupon,
                            onCouponInputChange = { code ->
                                viewModel.onAction(CheckoutAction.UpdateCouponInput(code))
                            },
                            onApplyCoupon = {
                                viewModel.onAction(CheckoutAction.ApplyCoupon)
                            },
                            onRemoveCoupon = {
                                viewModel.onAction(CheckoutAction.RemoveCoupon)
                            },
                        )
                        2 -> PaymentStepContent(
                            total = state.total,
                            clientSecret = state.clientSecret,
                            paymentError = state.paymentError,
                            isProcessingPayment = state.isProcessingPayment,
                            onPaymentResult = { result ->
                                viewModel.onPaymentResult(result)
                            },
                            onRetry = {
                                viewModel.onAction(CheckoutAction.RetryPayment)
                            },
                            onDismissError = {
                                viewModel.onAction(CheckoutAction.DismissPaymentError)
                            },
                            appliedCouponCode = state.appliedCouponCode,
                            discountAmountCents = state.discountAmountCents,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineCheckoutState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "You need internet to complete checkout",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Please check your connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onBack) {
                Text("Go Back")
            }
        }
    }
}
