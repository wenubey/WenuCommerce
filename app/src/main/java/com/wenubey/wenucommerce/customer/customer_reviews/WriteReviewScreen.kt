package com.wenubey.wenucommerce.customer.customer_reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.wenucommerce.customer.customer_products.CustomerProductDetailAction
import com.wenubey.wenucommerce.customer.customer_products.CustomerProductDetailViewModel
import org.koin.androidx.compose.koinViewModel

private const val TITLE_MAX = 100
private const val BODY_MAX = 1000

/**
 * Full-screen write/edit review form (UI-SPEC C-07). Reuses
 * [CustomerProductDetailViewModel] (scoped to the productId) for the submit
 * path — no business logic is duplicated here. When editing (D-05), the fields
 * are pre-filled from `state.existingReview`. On successful submit the screen
 * pops back; the success snackbar is shown on the product-detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteReviewScreen(
    modifier: Modifier = Modifier,
    viewModel: CustomerProductDetailViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val existingReview = state.existingReview
    val isEditMode = existingReview != null

    var selectedRating by rememberSaveable { mutableIntStateOf(0) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }

    // Pre-fill once the existing review is available (D-05).
    var prefilled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(existingReview) {
        if (existingReview != null && !prefilled) {
            selectedRating = existingReview.rating
            title = existingReview.title
            body = existingReview.body
            prefilled = true
        }
    }

    // Pop back when a submit completes without error.
    var submitInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(state.isSubmittingReview, state.reviewSubmitError) {
        if (state.isSubmittingReview) {
            submitInFlight = true
        } else if (submitInFlight && state.reviewSubmitError == null) {
            submitInFlight = false
            onNavigateBack()
        } else if (state.reviewSubmitError != null) {
            submitInFlight = false
        }
    }

    // Submit error snackbar (Screen States).
    val submitError = state.reviewSubmitError
    LaunchedEffect(submitError) {
        if (submitError != null) {
            snackbarHostState.showSnackbar(message = submitError)
            viewModel.onAction(CustomerProductDetailAction.DismissReviewForm)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Review" else "Write a Review") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                StarRatingSelector(
                    selectedRating = selectedRating,
                    onRatingSelected = { selectedRating = it },
                    enabled = !state.isSubmittingReview,
                )
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= TITLE_MAX) title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Summarise your experience") },
                    singleLine = true,
                    maxLines = 1,
                    enabled = !state.isSubmittingReview,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = body,
                    onValueChange = { if (it.length <= BODY_MAX) body = it },
                    label = { Text("Review") },
                    placeholder = { Text("What did you like or dislike?") },
                    minLines = 4,
                    maxLines = 8,
                    enabled = !state.isSubmittingReview,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${body.length}/1000",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Button(
                    onClick = {
                        viewModel.onAction(
                            CustomerProductDetailAction.SubmitReview(
                                rating = selectedRating,
                                title = title,
                                body = body,
                            )
                        )
                    },
                    enabled = selectedRating > 0 && !state.isSubmittingReview,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmittingReview) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (isEditMode) "Update Review" else "Submit Review")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
