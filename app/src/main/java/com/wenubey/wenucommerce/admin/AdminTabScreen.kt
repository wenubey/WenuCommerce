package com.wenubey.wenucommerce.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.wenucommerce.admin.admin_analytics.AdminAnalyticsScreen
import com.wenubey.wenucommerce.admin.admin_dashboard.AdminDashboardScreen
import com.wenubey.wenucommerce.admin.admin_seller_approval.AdminApprovalScreen
import com.wenubey.wenucommerce.admin.admin_settings.AdminSettingsScreen
import com.wenubey.wenucommerce.admin.admin_users.AdminUsersScreen
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationBannerViewModel
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationNotificationBar
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

//TODO Refactor Later for requirements
@Composable
fun AdminTabScreen(
    tabIndex: Int,
    emailBannerVm: EmailVerificationBannerViewModel = koinViewModel(),
    badgeVm: AdminBadgeViewModel = koinViewModel(),
) {

    val emailBannerState by emailBannerVm.emailVerificationBannerState.collectAsStateWithLifecycle()
    val badgeCounts by badgeVm.badgeState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        emailBannerVm.recheckEmailVerification()
        onPauseOrDispose { }
    }

    val pagerState = rememberPagerState(
        initialPage = tabIndex,
        pageCount = { AdminTabs.entries.size }
    )


    val currentTabIndex by remember { derivedStateOf { pagerState.currentPage } }

    // Sync pager state with ViewModel
    LaunchedEffect(tabIndex) {
        if (pagerState.currentPage != currentTabIndex) {
            pagerState.animateScrollToPage(currentTabIndex)
        }
    }

    Scaffold(
        topBar = {
            // TODO research the TopBar issue and change this
            Column {
                Text("WenuCommerce")
                AnimatedVisibility(
                    visible = emailBannerState.isVisible
                ) {
                    EmailVerificationNotificationBar(
                        onNavigateToProfile = { /* TODO add navigation mechanism for Admin Profile Screen */ },
                    )
                }

            }

        },
        bottomBar = {
            AdminTabRow(
                pagerState = pagerState,
                currentTabIndex = currentTabIndex,
                pendingApprovalBadge = badgeCounts.pendingApprovals
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = pagerState
        ) { page ->
            when (page) {
                0 -> {
                    AdminDashboardScreen()
                }

                1 -> {
                    AdminUsersScreen()
                }

                2 -> {
                    AdminAnalyticsScreen()
                }

                3 -> {
                    AdminApprovalScreen()
                }

                4 -> {
                    AdminSettingsScreen()
                }

                else -> {
                    // Handle other pages if needed
                }
            }
        }
    }
}

//TODO extract to components
@Composable
fun AdminTabRow(
    pagerState: PagerState,
    currentTabIndex: Int,
    pendingApprovalBadge: Int,
) {
    val scope = rememberCoroutineScope()

    TabRow(
        selectedTabIndex = currentTabIndex,
    ) {
        AdminTabs.entries.forEachIndexed { index, tab ->
            val isSelected = currentTabIndex == index

            val badgeCount = when (tab) {
                AdminTabs.Approvals -> pendingApprovalBadge
                else -> 0
            }

            Tab(
                selected = isSelected,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(tab.ordinal)
                    }
                },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge(
                                    modifier = Modifier.offset(x = 4.dp, y = (-4).dp),
                                ) { Text(badgeCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(id = tab.text)
                        )
                    }
                    Text(text = stringResource(id = tab.text))
                }
            }
        }
    }
}
