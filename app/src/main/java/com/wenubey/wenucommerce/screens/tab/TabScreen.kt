package com.wenubey.wenucommerce.screens.tab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.wenubey.wenucommerce.components.Tabs
import com.wenubey.wenucommerce.screens.cart.CartScreen
import com.wenubey.wenucommerce.screens.home.HomeScreen
import com.wenubey.wenucommerce.screens.home.components.HomeScreenTabRow
import com.wenubey.wenucommerce.screens.profile.ProfileScreen


@Composable
fun TabScreen(tabIndex: Int) {
    val pagerState =
        rememberPagerState(initialPage = tabIndex, pageCount = { Tabs.entries.size })
    val currentTabIndex by remember { derivedStateOf { pagerState.currentPage } }

    Scaffold(
        bottomBar = {
            HomeScreenTabRow(pagerState = pagerState, currentTabIndex = currentTabIndex)
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
                    ProfileScreen()
                }
                else -> {
                }
            }

        }
    }
}

