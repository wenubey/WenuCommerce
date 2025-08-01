package com.wenubey.wenucommerce.screens.admin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

//TODO Refactor Later for requirements
@Composable
fun AdminTabScreen(
    tabIndex: Int,
) {

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
        bottomBar = {
            AdminTabRow(
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
                    AdminDashboardScreen()
                }
                1 -> {
                    AdminUsersScreen()
                }
                2 -> {
                    AdminAnalyticsScreen()
                }
                3 -> {
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
) {
    val scope = rememberCoroutineScope()

    TabRow(
        modifier = Modifier.padding(vertical = 8.dp),
        selectedTabIndex = currentTabIndex,
    ) {
        AdminTabs.entries.forEachIndexed { index, tab ->
            val isSelected = currentTabIndex == index
            Tab(
                selected = isSelected,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(tab.ordinal)
                    }
                },
            ) {
                Icon(
                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(id = tab.text)
                )
                Text(text = stringResource(id = tab.text))
            }
        }
    }
}
