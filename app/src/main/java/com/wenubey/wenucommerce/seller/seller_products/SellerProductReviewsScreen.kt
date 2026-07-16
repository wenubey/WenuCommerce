package com.wenubey.wenucommerce.seller.seller_products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.wenucommerce.core.components.ReviewCard
import org.koin.androidx.compose.koinViewModel

/**
 * Read-only, seller-facing list of the reviews left on ONE of the seller's own
 * products (07-03, ROADMAP 07-03). Reuses the customer [ReviewCard] with the
 * Helpful vote hidden (`showHelpful = false`) — this screen is display-only and
 * has NO submit/edit/moderation controls (threat T-07-03-03). Only visible
 * reviews are shown; the ViewModel filters `isVisible == false` (T-07-03-01).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerProductReviewsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SellerProductReviewsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.productTitle.isNotBlank()) {
                            "Reviews · ${state.productTitle}"
                        } else {
                            "Reviews"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage ?: "Couldn't load reviews.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )
                }

                state.reviews.isEmpty() -> {
                    Text(
                        text = "No reviews yet for this product.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                    ) {
                        items(state.reviews, key = { it.id }) { review ->
                            ReviewCard(
                                review = review,
                                hasVoted = false,
                                onHelpful = {},
                                showHelpful = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
