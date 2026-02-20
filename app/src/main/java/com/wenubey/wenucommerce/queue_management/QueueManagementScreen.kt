package com.wenubey.wenucommerce.queue_management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.data.local.entity.OperationStatus
import com.wenubey.wenucommerce.R
import org.koin.androidx.compose.koinViewModel

/**
 * Full-screen queue management screen showing all pending operations.
 *
 * Features:
 * - Shows operation type, status, and timestamp
 * - Provides retry and discard buttons for FAILED operations
 * - Empty state when no operations exist
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueManagementScreen(
    viewModel: QueueManagementViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val operations by viewModel.operations.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pending_sync_queue_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (operations.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_pending_operations),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Operations list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(operations, key = { it.id }) { operation ->
                    OperationItem(
                        operation = operation,
                        onRetry = { viewModel.retryOperation(it.id) },
                        onDiscard = { viewModel.discardOperation(it.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationItem(
    operation: PendingOperationUiModel,
    onRetry: (PendingOperationUiModel) -> Unit,
    onDiscard: (PendingOperationUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Operation details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = operation.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${operation.statusText} • ${operation.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (operation.status == OperationStatus.FAILED) {
                    Text(
                        text = stringResource(R.string.operation_failed_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Right: Action buttons (only for FAILED operations)
            if (operation.status == OperationStatus.FAILED) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onRetry(operation) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry"
                        )
                    }
                    IconButton(onClick = { onDiscard(operation) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Discard"
                        )
                    }
                }
            }
        }
    }
}
