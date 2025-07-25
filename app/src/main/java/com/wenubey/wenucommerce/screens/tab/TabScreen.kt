package com.wenubey.wenucommerce.screens.tab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.wenucommerce.screens.cart.CartScreen
import com.wenubey.wenucommerce.screens.home.HomeScreen
import com.wenubey.wenucommerce.screens.home.components.HomeScreenTabRow
import com.wenubey.wenucommerce.screens.home.components.Tabs
import com.wenubey.wenucommerce.screens.profile.ProfileScreen
import com.wenubey.wenucommerce.screens.tab.components.EmailVerificationNotificationBar
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel


@Composable
fun TabScreen(
    tabIndex: Int,
    viewModel: TabViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val currentTabIndex by viewModel.currentTabIndex.collectAsStateWithLifecycle()
    val notificationBarState by viewModel.notificationBarState.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = currentTabIndex,
        pageCount = { Tabs.entries.size }
    )

    // Sync pager state with ViewModel
    LaunchedEffect(currentTabIndex) {
        viewModel.initializeTabIfNeeded(tabIndex)
        if (pagerState.currentPage != currentTabIndex) {
            pagerState.animateScrollToPage(currentTabIndex)
        }
    }

    // Update ViewModel when user swipes
    LaunchedEffect(pagerState.currentPage) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.updateCurrentTabIndex(page)
        }
    }

    Scaffold(
        topBar = {
            // Show notification bar if needed
            if (notificationBarState.isVisible) {
                EmailVerificationNotificationBar(
                    onNavigateToProfile = {
                        scope.launch {
                            pagerState.animateScrollToPage(2)
                        }
                    },
                    onHideForSession = {
                        viewModel.hideNotificationForSession()
                    },
                    onDoNotShowAgain = {
                        viewModel.doNotShowAgain()
                    }
                )
            }
        },
        bottomBar = {
            HomeScreenTabRow(
                pagerState = pagerState,
                currentTabIndex = currentTabIndex,
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
                    HomeScreen()
                }

                1 -> {
                    CartScreen()
                }

                2 -> {
                    ProfileScreen(
//                        onEmailVerificationStatusChanged = {
//                            // Refresh the notification bar when email verification status changes
//                            viewModel.refreshEmailVerificationStatus()
//                        }
                    )
                }

                else -> {
                    // Handle other pages
                }
            }
        }
    }
}