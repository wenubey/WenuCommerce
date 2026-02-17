package com.wenubey.wenucommerce.admin.admin_seller_approval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.wenucommerce.admin.admin_seller_approval.components.ApprovalDialog
import com.wenubey.wenucommerce.admin.admin_seller_approval.components.SellerApplicationCard
import com.wenubey.wenucommerce.seller.StatItem
import org.koin.androidx.compose.koinViewModel

@Composable
fun AdminApprovalScreen(
    viewModel: AdminApprovalViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.approvalState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Seller Approvals",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(VerificationStatus.entries.toTypedArray()) { status ->
                        FilterChip(
                            selected = state.selectedFilter == status,
                            onClick = {
                                viewModel.onAction(
                                    AdminSellerApprovalAction.OnFilterChange(status)
                                )
                            },
                            label = {
                                Text(
                                    text = when (status) {
                                        VerificationStatus.PENDING -> "Pending"
                                        VerificationStatus.APPROVED -> "Approved"
                                        VerificationStatus.REJECTED -> "Rejected"
                                        VerificationStatus.REQUEST_MORE_INFO -> "Request More Info"
                                        VerificationStatus.RESUBMITTED -> "Resubmitted"
                                        VerificationStatus.CANCELLED -> "Cancelled"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Statistics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ApprovalStatItem(
                            label = "Total",
                            count = state.sellers.size,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ApprovalStatItem(
                            label = state.selectedFilter.name,
                            count = state.sellers.size,
                            color = when (state.selectedFilter) {
                                VerificationStatus.PENDING -> Color(0xFFFF9800)
                                VerificationStatus.RESUBMITTED -> Color(0xFF9C27B0)
                                VerificationStatus.APPROVED -> Color(0xFF4CAF50)
                                VerificationStatus.REJECTED -> Color(0xFFE53E3E)
                                VerificationStatus.REQUEST_MORE_INFO -> Color(0xFF2196F3)
                                VerificationStatus.CANCELLED -> Color(0xFF757575)
                            }
                        )
                    }
                }
            }

            // Error message
            state.errorMessage?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE53E3E).copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = Color(0xFFE53E3E)
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE53E3E)
                            )
                        }
                    }
                }
            }

            // Loading Indicator
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Sellers List
            if (!state.isLoading && state.sellers.isNotEmpty()) {
                items(state.sellers) { seller ->
                    SellerApplicationCard(
                        seller = seller,
                        onClick = {
                            viewModel.onAction(
                                AdminSellerApprovalAction.OnSellerSelected(seller)
                            )
                        },
                    )
                }
            }

            // Empty state
            if (!state.isLoading && state.sellers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "No applications",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No ${state.selectedFilter.name.lowercase()} applications",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Approval Dialog
        if (state.showApprovalDialog && state.selectedSeller != null && state.dialogType != null) {
            ApprovalDialog(
                seller = state.selectedSeller!!,
                dialogType = state.dialogType!!,
                onDismiss = {
                    viewModel.onAction(AdminSellerApprovalAction.OnDismissDialog)
                },
                onApprove = { notes ->
                    viewModel.onAction(
                        AdminSellerApprovalAction.OnApprove(
                            sellerId = state.selectedSeller!!.uuid ?: "",
                            notes = notes
                        )
                    )
                },
                onReject = { notes ->
                    viewModel.onAction(
                        AdminSellerApprovalAction.OnReject(
                            sellerId = state.selectedSeller!!.uuid ?: "",
                            notes = notes
                        )
                    )
                },
                onRequestMoreInfo = { notes ->
                    viewModel.onAction(
                        AdminSellerApprovalAction.OnRequestMoreInfo(
                            sellerId = state.selectedSeller!!.uuid ?: "",
                            notes = notes
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun ApprovalStatItem(
    label: String,
    count: Int,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

