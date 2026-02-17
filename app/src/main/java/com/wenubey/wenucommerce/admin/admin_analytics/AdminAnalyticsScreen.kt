package com.wenubey.wenucommerce.admin.admin_analytics

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AdminAnalyticsScreen(modifier: Modifier = Modifier) {
    var selectedPeriod by remember { mutableStateOf("This Month") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Analytics Dashboard",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Time period filter
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedPeriod == "Today",
                        onClick = { selectedPeriod = "Today" },
                        label = { Text("Today") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedPeriod == "This Week",
                        onClick = { selectedPeriod = "This Week" },
                        label = { Text("This Week") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedPeriod == "This Month",
                        onClick = { selectedPeriod = "This Month" },
                        label = { Text("This Month") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedPeriod == "This Year",
                        onClick = { selectedPeriod = "This Year" },
                        label = { Text("This Year") }
                    )
                }
            }
        }

        // Key metrics
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AnalyticsMetricCard(
                        title = "Revenue",
                        value = "$45,892",
                        change = "+12.5%",
                        isPositive = true,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        color = Color(0xFF4CAF50)
                    )
                }
                item {
                    AnalyticsMetricCard(
                        title = "Orders",
                        value = "1,247",
                        change = "+8.3%",
                        isPositive = true,
                        icon = Icons.Default.Receipt,
                        color = Color(0xFF2196F3)
                    )
                }
                item {
                    AnalyticsMetricCard(
                        title = "Conversion",
                        value = "3.2%",
                        change = "-0.5%",
                        isPositive = false,
                        icon = Icons.Default.Analytics,
                        color = Color(0xFFFF9800)
                    )
                }
                item {
                    AnalyticsMetricCard(
                        title = "Avg Order",
                        value = "$68.45",
                        change = "+5.1%",
                        isPositive = true,
                        icon = Icons.Default.MoneyOff,
                        color = Color(0xFF9C27B0)
                    )
                }
            }
        }

        // Sales by category
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Sales by Category",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    SalesCategoryItem("Electronics", "$18,450", "40.2%", Color(0xFF2196F3))
                    SalesCategoryItem("Fashion", "$12,340", "26.9%", Color(0xFF9C27B0))
                    SalesCategoryItem("Home & Garden", "$8,750", "19.1%", Color(0xFF4CAF50))
                    SalesCategoryItem("Sports", "$4,230", "9.2%", Color(0xFFFF9800))
                    SalesCategoryItem("Books", "$2,122", "4.6%", Color(0xFFE91E63))
                }
            }
        }

        // Top performing sellers
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Top Performing Sellers",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    TopSellerItem("TechStore", "$8,450", "127 orders", 1)
                    TopSellerItem("FashionHub", "$6,340", "89 orders", 2)
                    TopSellerItem("ElectronicsWorld", "$5,750", "76 orders", 3)
                    TopSellerItem("HomeDecor", "$4,230", "65 orders", 4)
                    TopSellerItem("SportGear", "$3,890", "58 orders", 5)
                }
            }
        }

        // Traffic sources
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Traffic Sources",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    TrafficSourceItem("Direct", "2,450 visits", "35.2%", Color(0xFF4CAF50))
                    TrafficSourceItem("Search Engines", "1,890 visits", "27.1%", Color(0xFF2196F3))
                    TrafficSourceItem("Social Media", "1,340 visits", "19.3%", Color(0xFFE91E63))
                    TrafficSourceItem("Email", "780 visits", "11.2%", Color(0xFF9C27B0))
                    TrafficSourceItem("Referrals", "490 visits", "7.2%", Color(0xFFFF9800))
                }
            }
        }
    }
}

@Composable
fun AnalyticsMetricCard(
    title: String,
    value: String,
    change: String,
    isPositive: Boolean,
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
                    color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFE53E3E),
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
fun SalesCategoryItem(
    category: String,
    amount: String,
    percentage: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(12.dp),
                colors = CardDefaults.cardColors(containerColor = color)
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = category,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Row {
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = percentage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TopSellerItem(
    sellerName: String,
    revenue: String,
    orders: String,
    rank: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp)
        )
        Icon(
            imageVector = Icons.Default.Store,
            contentDescription = "Seller",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = sellerName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = orders,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = revenue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
    }
}

@Composable
fun TrafficSourceItem(
    source: String,
    visits: String,
    percentage: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(12.dp),
                colors = CardDefaults.cardColors(containerColor = color)
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = visits,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = percentage,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}