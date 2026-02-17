package com.wenubey.wenucommerce.core.email_verification_banner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailVerificationNotificationBar(
    onNavigateToProfile: () -> Unit,
    viewModel: EmailVerificationBannerViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier.fillMaxWidth(),
        title = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, end = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Main message with clickable Profile link
                    val annotatedString = buildAnnotatedString {
                        append("Actions Needed! Go To ")
                        pushStringAnnotation(tag = "SETTINGS", annotation = "settings")
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("Settings")
                        }
                        pop()
                    }

                    Text(
                        text = annotatedString,
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                annotatedString.getStringAnnotations(
                                    tag = "PROFILE",
                                    start = 0,
                                    end = annotatedString.length
                                ).firstOrNull()?.let {
                                    onNavigateToProfile()
                                }
                            },
                    )

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Hide button
                        TextButton(
                            onClick = {
                                viewModel.onAction(EmailVerificationBannerAction.HideNotificationForSession)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        ) {
                            Text("Hide", fontSize = 12.sp)
                        }

                        // Do not show again button
                        TextButton(
                            onClick = {
                                viewModel.onAction(EmailVerificationBannerAction.DoNotShowAgain)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Don't show again", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    )


}