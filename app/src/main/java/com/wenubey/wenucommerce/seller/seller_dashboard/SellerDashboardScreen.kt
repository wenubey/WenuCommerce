package com.wenubey.wenucommerce.seller.seller_dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.core.generateDummyUser
import org.koin.androidx.compose.koinViewModel

// TODO Refactor Later
@Composable
fun SellerDashboardScreen(
    modifier: Modifier = Modifier,
    isApproved: Boolean = false,
    onNavigateToSellerVerification: (user: User?) -> Unit,
    viewModel: SellerDashboardViewModel = koinViewModel(),
) {

    val state by viewModel.sellerDashboardState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Seller Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            val status = state.user?.businessInfo?.verificationStatus
            status?.let {
                val bannerVisible = if (!isApproved) true else state.isBannerVisible
                AnimatedVisibility(
                    visible = bannerVisible
                ) {
                    VerificationStatusBanner(
                        status = status,
                        onViewDetails = {onNavigateToSellerVerification(state.user)},
                        onHide = {
                            if (isApproved) {
                                viewModel.onAction(SellerDashboardAction.HideBanner)
                            }
                        },
                        showHideButton = isApproved,
                    )
                }
            }
        }

        // Stats overview
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SellerStatsCard(
                        title = "Total Sales",
                        value = "$2,847",
                        icon = Icons.Default.AttachMoney,
                        color = Color(0xFF4CAF50)
                    )
                }
                item {
                    SellerStatsCard(
                        title = "Orders",
                        value = "23",
                        icon = Icons.Default.Receipt,
                        color = Color(0xFF2196F3)
                    )
                }
                item {
                    SellerStatsCard(
                        title = "Products",
                        value = "15",
                        icon = Icons.Default.Inventory,
                        color = Color(0xFF9C27B0)
                    )
                }
                item {
                    SellerStatsCard(
                        title = "Rating",
                        value = "4.8",
                        icon = Icons.Default.Star,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        }

        // Recent orders
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Orders",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = { /* Navigate to orders */ }) {
                            Text("View All")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    repeat(3) { index ->
                        SellerOrderItem(
                            orderId = "#${1000 + index}",
                            customerName = "Customer ${index + 1}",
                            amount = "$${(index + 1) * 25}.99",
                            status = if (index % 2 == 0) "Pending" else "Shipped"
                        )
                        if (index < 2) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        // Quick actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val onGatedAction: () -> Unit = {
                        if (!isApproved) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Account verification required")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        QuickActionButton(
                            icon = Icons.Default.Add,
                            label = "Add Product",
                            enabled = isApproved,
                            modifier = Modifier.weight(1f)
                        ) { onGatedAction() }

                        Spacer(modifier = Modifier.width(12.dp))

                        QuickActionButton(
                            icon = Icons.Default.Inventory,
                            label = "Manage Inventory",
                            enabled = isApproved,
                            modifier = Modifier.weight(1f)
                        ) { onGatedAction() }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        QuickActionButton(
                            icon = Icons.Default.Analytics,
                            label = "View Analytics",
                            modifier = Modifier.weight(1f)
                        ) { /* View analytics */ }

                        Spacer(modifier = Modifier.width(12.dp))

                        QuickActionButton(
                            icon = Icons.Default.Settings,
                            label = "Shop Settings",
                            enabled = isApproved,
                            modifier = Modifier.weight(1f)
                        ) { onGatedAction() }
                    }
                }
            }
        }

        item {
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        enabled = enabled
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SellerStatsCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.width(140.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SellerOrderItem(
    orderId: String,
    customerName: String,
    amount: String,
    status: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = orderId,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = customerName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = amount,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
            Badge(
                containerColor = if (status == "Shipped") Color(0xFF4CAF50) else Color(0xFFFF9800)
            ) {
                Text(text = status, color = Color.White)
            }
        }
    }
}

@Composable
fun VerificationStatusBanner(
    status: VerificationStatus,
    onViewDetails: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    showHideButton: Boolean = true,
) {
    val (backgroundColor, textColor, icon, title, message) = when (status) {
        VerificationStatus.PENDING -> {
            Tuple5(
                Color(0xFFFFF3CD),
                Color(0xFF856404),
                Icons.Default.HourglassEmpty,
                "Verification Pending",
                "Your seller application is under review. We'll notify you once it's processed."
            )
        }
        VerificationStatus.REQUEST_MORE_INFO -> {
            Tuple5(
                Color(0xFFD1ECF1),
                Color(0xFF0C5460),
                Icons.Default.Info,
                "More Information Required",
                "Admin has requested additional information. Please update your application."
            )
        }
        VerificationStatus.REJECTED -> {
            Tuple5(
                Color(0xFFF8D7DA),
                Color(0xFF721C24),
                Icons.Default.Cancel,
                "Application Rejected",
                "Unfortunately, your seller application was not approved. View details to learn more."
            )
        }
        VerificationStatus.APPROVED -> {
            Tuple5(
                Color(0xFFD4EDDA),
                Color(0xFF155724),
                Icons.Default.CheckCircle,
                "Verified Seller",
                "Your account is verified. You can now manage your products and sales."
            )
        }
        VerificationStatus.RESUBMITTED -> {
            Tuple5(
                Color(0xFFE1BEE7),
                Color(0xFF6A1B9A),
                Icons.Default.Autorenew,
                "Application Resubmitted",
                "Your updated application is under review. We'll notify you once it's processed."
            )
        }
        VerificationStatus.CANCELLED -> {
            Tuple5(
                Color(0xFFEEEEEE),
                Color(0xFF757575),
                Icons.Default.Cancel,
                "Application Cancelled",
                "You cancelled your seller application. You can reapply when ready."
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor
                        )
                    }
                }

                if (status != VerificationStatus.APPROVED) {
                    TextButton(onClick = onViewDetails) {
                        Text(
                            text = "View Details",
                            color = textColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (showHideButton) {
                    Row(
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onHide) {
                            Text(
                                text = "Hide",
                                color = textColor.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

        }
    }
}

// Helper data class for destructuring
private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
