package com.wenubey.wenucommerce.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationBannerViewModel
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationNotificationBar
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// TODO Refactor Later for Requirements
@Composable
fun CustomerTabScreen(
    tabIndex: Int,
    onProductClick: (String) -> Unit = {},
    emailBannerVm: EmailVerificationBannerViewModel = koinViewModel(),
) {
    val emailBannerState by emailBannerVm.emailVerificationBannerState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        emailBannerVm.recheckEmailVerification()
        onPauseOrDispose { }
    }

    val pagerState = rememberPagerState(
        initialPage = tabIndex,
        pageCount = { CustomerTabs.entries.size }
    )
    val currentTabIndex by remember { derivedStateOf { pagerState.currentPage } }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = emailBannerState.isVisible) {
                EmailVerificationNotificationBar(
                    onNavigateToProfile = { /* TODO add navigation to Customer Profile */ },
                )
            }
        },
        bottomBar = {
            CustomerTabRow(pagerState = pagerState, currentTabIndex = currentTabIndex)
        }
    ) { paddingValues ->
        HorizontalPager(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = pagerState
        ) { page ->
            when (page) {
                0 -> CustomerHomeScreen(onProductClick = onProductClick)
                1 -> CustomerCartScreen()
                2 -> CustomerProfileScreen()
            }
        }
    }
}

@Composable
fun CustomerTabRow(
    pagerState: PagerState,
    currentTabIndex: Int
) {
    val scope = rememberCoroutineScope()

    TabRow(
        modifier = Modifier.padding(vertical = 8.dp),
        selectedTabIndex = currentTabIndex,
    ) {
        CustomerTabs.entries.forEachIndexed { index, tab ->
            val isSelected = currentTabIndex == index
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
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(id = tab.text)
                    )
                    Text(
                        text = stringResource(id = tab.text),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}