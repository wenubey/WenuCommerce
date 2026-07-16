package com.wenubey.wenucommerce.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wenubey.domain.model.product.ProductReview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Fixed amber for read-only display stars (UI-SPEC Color). */
private val StarAmber = Color(0xFFFFC107)

/**
 * Read-only star row (UI-SPEC C-01). Fills `index < rating` with the amber
 * filled star; the rest are outlined `onSurfaceVariant`. The row carries the
 * "$rating out of 5 stars" accessibility label; the icons are decorative.
 */
@Composable
fun StarRatingDisplay(
    rating: Int,
    modifier: Modifier = Modifier,
    starSize: Int = 14,
) {
    Row(
        modifier = modifier.semantics { contentDescription = "$rating out of 5 stars" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { index ->
            Icon(
                imageVector = if (index < rating) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(starSize.dp),
                tint = if (index < rating) StarAmber else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Individual review card (UI-SPEC C-06). Header row shows the avatar, reviewer
 * name, star display, a Verified Purchase badge (when [ProductReview.isVerifiedPurchase]
 * — REVW-05), and a relative date. Below: optional title, body, and a Helpful
 * TextButton that disables optimistically once [hasVoted] is true (D-04).
 *
 * Set [showHelpful] to `false` for read-only surfaces (e.g. the seller-facing
 * reviews list, 07-03) where the Helpful vote — a customer action — must NOT be
 * exposed. When hidden, no interactive controls remain on the card.
 */
@Composable
fun ReviewCard(
    review: ProductReview,
    hasVoted: Boolean,
    onHelpful: () -> Unit,
    modifier: Modifier = Modifier,
    showHelpful: Boolean = true,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (review.reviewerPhotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = review.reviewerPhotoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = review.reviewerName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = review.reviewerName, style = MaterialTheme.typography.labelMedium)
                    StarRatingDisplay(rating = review.rating, starSize = 14)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (review.isVerifiedPurchase) {
                    VerifiedPurchaseBadge()
                    Spacer(modifier = Modifier.width(4.dp))
                }
                val relative = relativeDate(review.createdAt)
                if (relative.isNotBlank()) {
                    Text(
                        text = relative,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            contentDescription = "Reviewed ${absoluteDate(review.createdAt)}"
                        },
                    )
                }
            }

            if (review.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = review.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = review.body,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (showHelpful) {
                Spacer(modifier = Modifier.height(8.dp))
                val helpfulCd = if (hasVoted) {
                    "You already marked this review as helpful"
                } else {
                    "Mark review as helpful, ${review.helpfulCount} people found this helpful"
                }
                TextButton(
                    onClick = onHelpful,
                    enabled = !hasVoted,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = helpfulCd },
                ) {
                    Icon(
                        Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Helpful (${review.helpfulCount})",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * Human-friendly relative time from an epoch-millis string (the review
 * createdAt format). Returns "" for unparseable input.
 */
private fun relativeDate(createdAt: String): String {
    val millis = createdAt.toLongOrNull() ?: return ""
    val now = System.currentTimeMillis()
    val diff = (now - millis).coerceAtLeast(0)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        days >= 365 -> "${days / 365} year${if (days / 365 > 1) "s" else ""} ago"
        days >= 30 -> "${days / 30} month${if (days / 30 > 1) "s" else ""} ago"
        days >= 1 -> "$days day${if (days > 1) "s" else ""} ago"
        hours >= 1 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        minutes >= 1 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
        else -> "Just now"
    }
}

/** ISO date for talkback (Accessibility Contract). Returns "" for bad input. */
private fun absoluteDate(createdAt: String): String {
    val millis = createdAt.toLongOrNull() ?: return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
}
