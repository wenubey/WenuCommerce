package com.wenubey.wenucommerce.core.connectivity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wenubey.wenucommerce.R

/**
 * Merged offline + pending sync banner.
 *
 * States:
 * - Offline only: "No internet connection" with WifiOff icon
 * - Offline + pending: "Offline -- N items pending sync" with WifiOff icon
 * - Online + pending: "N items pending sync" with CloudUpload icon
 * - Online + syncing: "Syncing N items..." with CircularProgressIndicator
 *
 * Behavior:
 * - Tapping the banner navigates to queue management screen
 * - Close button only shown when pending items exist (offline-only banner is not dismissible)
 */
@Composable
fun PendingSyncBanner(
    isOnline: Boolean,
    pendingCount: Int,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clickable { onTap() },
        color = Color(0xFFFFC107), // Amber
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Left icon: Syncing animation, offline icon, or cloud upload
            when {
                isSyncing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
                !isOnline -> {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }

            // Banner text
            Text(
                text = when {
                    !isOnline && pendingCount > 0 -> stringResource(R.string.offline_pending_sync, pendingCount)
                    !isOnline -> stringResource(R.string.no_internet_connection)
                    isSyncing -> stringResource(R.string.syncing_items, pendingCount)
                    else -> stringResource(R.string.pending_sync_count, pendingCount)
                },
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )

            // Close button (only shown when pending items exist)
            if (pendingCount > 0) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
