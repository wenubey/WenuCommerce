package com.wenubey.wenucommerce.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

//TODO Refactor Later
@Composable
fun AdminDashboardScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Admin Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Platform overview cards
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AdminStatsCard(
                        title = "Total Revenue",
                        value = "$45,892",
                        change = "+12.5%",
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        color = Color(0xFF4CAF50)
                    )
                }
                item {
                    AdminStatsCard(
                        title = "Total Orders",
                        value = "1,247",
                        change = "+8.3%",
                        icon = Icons.Default.Receipt,
                        color = Color(0xFF2196F3)
                    )
                }
                item {
                    AdminStatsCard(
                        title = "Active Users",
                        value = "892",
                        change = "+5.7%",
                        icon = Icons.Default.People,
                        color = Color(0xFF9C27B0)
                    )
                }
                item {
                    AdminStatsCard(
                        title = "Active Sellers",
                        value = "127",
                        change = "+15.2%",
                        icon = Icons.Default.Store,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        }

        // Platform health
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Platform Health",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            HealthMetric(
                                label = "System Status",
                                value = "Operational",
                                color = Color(0xFF4CAF50)
                            )
                        }
                        item {
                            HealthMetric(
                                label = "Server Load",
                                value = "68%",
                                color = Color(0xFFFF9800)
                            )
                        }
                        item {
                            HealthMetric(
                                label = "Response Time",
                                value = "245ms",
                                color = Color(0xFF4CAF50)
                            )
                        }
                        item {
                            HealthMetric(
                                label = "Uptime",
                                value = "99.9%",
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }

        // Recent activities
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
                            text = "Recent Activities",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = { /* View all activities */ }) {
                            Text("View All")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AdminActivityItem(
                        activity = "New seller registration",
                        details = "TechStore registered as Premium Seller",
                        time = "5 minutes ago",
                        type = "seller",
                        icon = Icons.Default.Store
                    )

                    AdminActivityItem(
                        activity = "Payment dispute reported",
                        details = "Order #1234 - Customer vs ElectronicsHub",
                        time = "15 minutes ago",
                        type = "dispute",
                        icon = Icons.Default.Report
                    )

                    AdminActivityItem(
                        activity = "Product review flagged",
                        details = "Inappropriate content detected",
                        time = "1 hour ago",
                        type = "moderation",
                        icon = Icons.Default.Flag
                    )

                    AdminActivityItem(
                        activity = "System maintenance completed",
                        details = "Database optimization finished",
                        time = "2 hours ago",
                        type = "system",
                        icon = Icons.Default.Build
                    )
                }
            }
        }

        // Quick admin actions
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

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        item {
                            AdminQuickAction(
                                icon = Icons.Default.People,
                                label = "Manage Users",
                                onClick = { }
                            )
                        }
                        item {
                            AdminQuickAction(
                                icon = Icons.Default.Store,
                                label = "Manage Sellers",
                                onClick = { }
                            )
                        }
                        item {
                            AdminQuickAction(
                                icon = Icons.Default.Report,
                                label = "Review Reports",
                                onClick = { }
                            )
                        }
                        item {
                            AdminQuickAction(
                                icon = Icons.Default.Analytics,
                                label = "View Analytics",
                                onClick = { }
                            )
                        }
                    }
                }
            }
        }

        // Pending approvals
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
                            text = "Pending Approvals",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Badge(
                            containerColor = Color(0xFFE53E3E)
                        ) {
                            Text("7", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AdminPendingItem(
                        title = "Seller Application",
                        subtitle = "GadgetWorld - Electronics Category",
                        type = "seller",
                        onApprove = { },
                        onReject = { }
                    )

                    AdminPendingItem(
                        title = "Product Review",
                        subtitle = "Flagged review on iPhone 15",
                        type = "review",
                        onApprove = { },
                        onReject = { }
                    )

                    AdminPendingItem(
                        title = "Refund Request",
                        subtitle = "Order #5678 - $299.99",
                        type = "refund",
                        onApprove = { },
                        onReject = { }
                    )
                }
            }
        }
    }
}

@Composable
fun AdminStatsCard(
    title: String,
    value: String,
    change: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.width(160.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = color
                )
                Text(
                    text = change,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (change.startsWith("+")) Color(0xFF4CAF50) else Color(0xFFE53E3E),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HealthMetric(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AdminActivityItem(
    activity: String,
    details: String,
    time: String,
    type: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    when (type) {
                        "seller" -> Color(0xFF2196F3).copy(alpha = 0.1f)
                        "dispute" -> Color(0xFFE53E3E).copy(alpha = 0.1f)
                        "moderation" -> Color(0xFFFF9800).copy(alpha = 0.1f)
                        "system" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        else -> Color.Gray.copy(alpha = 0.1f)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = activity,
                modifier = Modifier.size(20.dp),
                tint = when (type) {
                    "seller" -> Color(0xFF2196F3)
                    "dispute" -> Color(0xFFE53E3E)
                    "moderation" -> Color(0xFFFF9800)
                    "system" -> Color(0xFF4CAF50)
                    else -> Color.Gray
                }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = activity,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AdminQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AdminPendingItem(
    title: String,
    subtitle: String,
    type: String,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (type) {
                    "seller" -> Icons.Default.Store
                    "review" -> Icons.Default.RateReview
                    "refund" -> Icons.Default.MoneyOff
                    else -> Icons.Default.Pending
                },
                contentDescription = type,
                modifier = Modifier.size(24.dp),
                tint = Color(0xFFFF9800)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onReject,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reject",
                        tint = Color(0xFFE53E3E),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onApprove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Approve",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
