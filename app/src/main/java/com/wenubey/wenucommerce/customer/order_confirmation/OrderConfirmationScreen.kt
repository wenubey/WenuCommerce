package com.wenubey.wenucommerce.customer.order_confirmation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.repository.PaymentRepository
import com.wenubey.wenucommerce.R
import org.koin.compose.koinInject

@Composable
fun OrderConfirmationScreen(
    orderId: String,
    onContinueShopping: () -> Unit = {},
    onViewOrder: (String) -> Unit = {},
    paymentRepository: PaymentRepository = koinInject(),
) {
    var order by remember { mutableStateOf<Order?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(orderId) {
        order = paymentRepository.getOrderById(orderId)
        isLoading = false
    }

    // Lottie composition for success checkmark
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.success_checkmark)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
    )

    // Scale-in animation for the checkmark icon fallback
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationStarted = true }
    val iconScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "checkmark_scale",
    )

    if (isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Animated checkmark: Lottie if composition loaded, else scale-in icon
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(120.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Order placed successfully",
                modifier = Modifier
                    .size(120.dp)
                    .scale(iconScale),
                tint = Color(0xFF2ECC70),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Order Placed!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Order #${orderId.take(8).uppercase()}...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        val itemCount = order?.items?.size ?: 0
        val total = order?.totalAmount ?: 0.0
        Text(
            text = "$itemCount ${if (itemCount == 1) "item" else "items"} | Total: ${"$%.2f".format(total)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Savings line for coupon discount
        val discountCode = order?.discountCode ?: ""
        val discountAmount = order?.discountAmount ?: 0.0
        if (discountCode.isNotEmpty() && discountAmount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You saved ${"$%.2f".format(discountAmount)} with $discountCode",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinueShopping,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text("Continue Shopping")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onViewOrder(orderId) },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text("View Order")
        }
    }
}
