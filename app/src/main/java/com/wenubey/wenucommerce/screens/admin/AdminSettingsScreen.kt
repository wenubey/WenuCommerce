package com.wenubey.wenucommerce.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Support
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AdminSettingsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Admin Settings",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Platform Settings
        item {
            SettingsSection(
                title = "Platform Settings",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Default.Store,
                        title = "Store Configuration",
                        subtitle = "Configure store name, logo, and branding",
                        action = { /* Navigate to store config */ }
                    ),
                    SettingsItem(
                        icon = Icons.Default.Payment,
                        title = "Payment Settings",
                        subtitle = "Manage payment gateways and fees",
                        action = { /* Navigate to payment settings */ }
                    ),
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = "Localization",
                        subtitle = "Languages, currencies, and regions",
                        action = { /* Navigate to localization */ }
                    )
                )
            )
        }

        // Security Settings
        item {
            SettingsSection(
                title = "Security & Privacy",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Security Policies",
                        subtitle = "Password requirements, 2FA settings",
                        action = { /* Navigate to security */ }
                    ),
                    SettingsItem(
                        icon = Icons.Default.Backup,
                        title = "Data Backup",
                        subtitle = "Automated backups and recovery",
                        action = { /* Navigate to backup */ }
                    )
                )
            )
        }

        // Notification Settings
        item {
            var notificationsEnabled by remember { mutableStateOf(true) }
            var emailNotifications by remember { mutableStateOf(true) }
            var pushNotifications by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    SettingsToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "Admin Notifications",
                        subtitle = "Receive alerts for important events",
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )

                    SettingsToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "Email Notifications",
                        subtitle = "Get notifications via email",
                        checked = emailNotifications,
                        onCheckedChange = { emailNotifications = it }
                    )

                    SettingsToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "Push Notifications",
                        subtitle = "Real-time push notifications",
                        checked = pushNotifications,
                        onCheckedChange = { pushNotifications = it }
                    )
                }
            }
        }

        // System Settings
        item {
            SettingsSection(
                title = "System",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Default.Update,
                        title = "System Updates",
                        subtitle = "Check for platform updates",
                        action = { /* Check for updates */ }
                    ),
                    SettingsItem(
                        icon = Icons.Default.Settings,
                        title = "Advanced Settings",
                        subtitle = "Database, cache, and performance",
                        action = { /* Navigate to advanced settings */ }
                    ),
                    SettingsItem(
                        icon = Icons.Default.Support,
                        title = "Support & Help",
                        subtitle = "Contact support or view documentation",
                        action = { /* Navigate to support */ }
                    )
                )
            )
        }

        // Platform Information
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Platform Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    InfoItem("Version", "2.1.0")
                    InfoItem("Database", "PostgreSQL 14.2")
                    InfoItem("Server", "AWS EC2 t3.large")
                    InfoItem("Uptime", "99.9%")
                    InfoItem("Last Backup", "2 hours ago")
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    items: List<SettingsItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            items.forEach { item ->
                SettingsClickableItem(
                    icon = item.icon,
                    title = item.title,
                    subtitle = item.subtitle,
                    onClick = item.action
                )
            }
        }
    }
}

@Composable
fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun InfoItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val action: () -> Unit
)