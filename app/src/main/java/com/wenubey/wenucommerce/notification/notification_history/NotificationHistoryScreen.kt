package com.wenubey.wenucommerce.notification.notification_history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.Notification
import com.wenubey.wenucommerce.notification.FCM_TYPE_NEW_ORDER
import com.wenubey.wenucommerce.notification.FCM_TYPE_NEW_REVIEW
import com.wenubey.wenucommerce.notification.FCM_TYPE_ORDER_STATUS
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Notification History screen (NOTF-08, D-01).
 *
 * Shows a Room-backed, newest-first list of notifications for the signed-in user.
 * Row tap → markAsRead + deep-link to order detail / seller order / product reviews.
 * TopAppBar "Mark all read" button visible only when [NotificationHistoryState.hasUnread].
 * Pull-to-refresh resets the isRefreshing flag.
 *
 * Navigation callbacks must be provided by the NavGraphBuilder composable registration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    onBack: () -> Unit,
    onNavigateToOrderDetail: (orderId: String) -> Unit = {},
    onNavigateToSellerOrder: (sellerOrderId: String) -> Unit = {},
    onNavigateToProductReviews: (productId: String, productTitle: String) -> Unit = { _, _ -> },
    viewModel: NotificationHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Consume one-shot navigation effect
    LaunchedEffect(viewModel.navigationEffect) {
        viewModel.navigationEffect.collect { destination ->
            when (destination) {
                is NotificationHistoryViewModel.NavigationDestination.OrderDetail ->
                    onNavigateToOrderDetail(destination.orderId)
                is NotificationHistoryViewModel.NavigationDestination.SellerOrder ->
                    onNavigateToSellerOrder(destination.sellerOrderId)
                is NotificationHistoryViewModel.NavigationDestination.ProductReviews ->
                    onNavigateToProductReviews(destination.productId, destination.productTitle)
            }
        }
    }

    // Error snackbar
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar("Couldn't load notifications. Pull down to retry.")
            viewModel.onAction(NotificationHistoryAction.OnDismissError)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state.hasUnread) {
                        TextButton(
                            onClick = { viewModel.onAction(NotificationHistoryAction.OnMarkAllRead) }
                        ) {
                            Text("Mark all read")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onAction(NotificationHistoryAction.OnRefresh) },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when {
                state.isLoading && state.notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Loading notifications" },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.notifications.isEmpty() -> {
                    EmptyNotifications()
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = state.notifications.sortedByDescending { it.createdAt },
                            key = { it.id },
                        ) { item ->
                            NotificationRow(
                                item = item,
                                onClick = { viewModel.onAction(NotificationHistoryAction.OnItemClick(item)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotifications() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No notifications yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Order updates and alerts will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun NotificationRow(
    item: Notification,
    onClick: () -> Unit,
) {
    val isUnread = !item.isRead
    val containerColor = if (isUnread) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnread) 2.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Unread indicator dot + type icon
            Box(contentAlignment = Alignment.TopStart) {
                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .align(Alignment.TopStart),
                    )
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // Type icon
            val (icon, tint) = typeIconAndTint(item.type)
            Icon(
                imageVector = icon,
                contentDescription = typeContentDescription(item.type),
                modifier = Modifier.size(24.dp),
                tint = tint,
            )

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                if (item.createdAt.isNotBlank()) {
                    Text(
                        text = relativeTimestamp(item.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Trailing chevron
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun typeIconAndTint(type: String): Pair<ImageVector, Color> {
    return when (type) {
        FCM_TYPE_ORDER_STATUS -> Icons.Filled.ShoppingBag to MaterialTheme.colorScheme.primary
        FCM_TYPE_NEW_ORDER -> Icons.Filled.Receipt to MaterialTheme.colorScheme.primary
        FCM_TYPE_NEW_REVIEW -> Icons.Filled.Star to Color(0xFFFF9800)
        "device_login" -> Icons.Filled.AccountCircle to MaterialTheme.colorScheme.secondary
        else -> Icons.Filled.Notifications to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun typeContentDescription(type: String): String {
    return when (type) {
        FCM_TYPE_ORDER_STATUS -> "Order status notification"
        FCM_TYPE_NEW_ORDER -> "New order notification"
        FCM_TYPE_NEW_REVIEW -> "New review notification"
        "device_login" -> "Account notification"
        else -> "Notification"
    }
}

/**
 * Relative timestamp string per 08-UI-SPEC Copywriting Contract.
 *
 * ISO-8601 input (e.g. "2026-07-17T12:00:00Z"). Falls back to the
 * date string on parse error.
 */
internal fun relativeTimestamp(isoTimestamp: String): String {
    return try {
        val instant = Instant.parse(isoTimestamp)
        val now = Instant.now()
        val minutesAgo = ChronoUnit.MINUTES.between(instant, now)
        val hoursAgo = ChronoUnit.HOURS.between(instant, now)
        val daysAgo = ChronoUnit.DAYS.between(instant, now)
        when {
            minutesAgo < 1 -> "Just now"
            minutesAgo < 60 -> "$minutesAgo min ago"
            hoursAgo < 24 -> "$hoursAgo hours ago"
            daysAgo == 1L -> "Yesterday"
            else -> {
                val formatter = DateTimeFormatter
                    .ofPattern("MMM d", Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
        }
    } catch (_: Exception) {
        isoTimestamp
    }
}
