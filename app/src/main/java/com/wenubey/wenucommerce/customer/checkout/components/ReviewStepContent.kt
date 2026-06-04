package com.wenubey.wenucommerce.customer.checkout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDecoration
import com.wenubey.domain.model.CartItem
import com.wenubey.domain.model.discount.DiscountType
import com.wenubey.domain.model.order.ShippingAddress

@Composable
fun ReviewStepContent(
    cartItems: List<CartItem>,
    selectedAddress: ShippingAddress?,
    subtotal: Double,
    shippingTotal: Double,
    total: Double,
    amountCents: Int,
    isCreatingPaymentIntent: Boolean,
    stockError: String?,
    onDismissStockError: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    couponInput: String = "",
    appliedCouponCode: String? = null,
    appliedCouponType: DiscountType? = null,
    discountAmountCents: Int = 0,
    couponError: String? = null,
    isValidatingCoupon: Boolean = false,
    onCouponInputChange: (String) -> Unit = {},
    onApplyCoupon: () -> Unit = {},
    onRemoveCoupon: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Stock error card
                if (stockError != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stock Issue",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = stockError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            TextButton(onClick = onDismissStockError) {
                                Text(
                                    text = "Dismiss",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }

                // Shipping address summary
                if (selectedAddress != null) {
                    Text(
                        text = "Shipping Address",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = selectedAddress.fullName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = selectedAddress.line1,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (selectedAddress.line2.isNotBlank()) {
                                Text(
                                    text = selectedAddress.line2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${selectedAddress.city}, ${selectedAddress.state} ${selectedAddress.postalCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = selectedAddress.country,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Item list
                Text(
                    text = "Order Items",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        cartItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${item.productTitle} x${item.quantity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "$%.2f".format(item.snapshotPrice * item.quantity),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (index < cartItems.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }

                // Coupon section (above totals)
                CouponSection(
                    couponInput = couponInput,
                    appliedCouponCode = appliedCouponCode,
                    appliedCouponType = appliedCouponType,
                    discountAmountCents = discountAmountCents,
                    couponError = couponError,
                    isValidatingCoupon = isValidatingCoupon,
                    onCouponInputChange = onCouponInputChange,
                    onApply = onApplyCoupon,
                    onRemove = onRemoveCoupon,
                )

                // Totals section
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Subtotal",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "$%.2f".format(subtotal),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        // Discount line (only when coupon applied and not FREE_SHIPPING)
                        if (appliedCouponCode != null && discountAmountCents > 0 && appliedCouponType != DiscountType.FREE_SHIPPING) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Discount ($appliedCouponCode)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = "-${"$%.2f".format(discountAmountCents / 100.0)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }

                        // Shipping row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Shipping",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (appliedCouponType == DiscountType.FREE_SHIPPING && appliedCouponCode != null) {
                                // Free shipping: show strikethrough on original + "Free" label
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "$%.2f".format(shippingTotal),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = TextDecoration.LineThrough,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "Free",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            } else {
                                Text(
                                    text = if (amountCents > 0) {
                                        "$%.2f".format(shippingTotal)
                                    } else {
                                        "Calculated at next step"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (amountCents > 0) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }

                        // Free shipping coupon line
                        if (appliedCouponType == DiscountType.FREE_SHIPPING && appliedCouponCode != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Free Shipping ($appliedCouponCode)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = "-${"$%.2f".format(shippingTotal)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }

                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (amountCents > 0) {
                                    "$%.2f".format(amountCents / 100.0)
                                } else if (discountAmountCents > 0) {
                                    "$%.2f".format(subtotal - discountAmountCents / 100.0)
                                } else {
                                    "$%.2f".format(subtotal)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Continue button
            Surface(
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onContinue,
                    enabled = !isCreatingPaymentIntent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    if (isCreatingPaymentIntent) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Continue to Payment")
                    }
                }
            }
        }
    }
}
