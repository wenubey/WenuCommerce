package com.wenubey.wenucommerce.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.CartRepository
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationBannerViewModel
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationNotificationBar
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun CustomerTabScreen(
    tabIndex: Int,
    onProductClick: (String) -> Unit = {},
    onNavigateToCheckout: () -> Unit = {},
    emailBannerVm: EmailVerificationBannerViewModel = koinViewModel(),
    cartRepository: CartRepository = koinInject(),
    authRepository: AuthRepository = koinInject(),
) {
    val emailBannerState by emailBannerVm.emailVerificationBannerState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        emailBannerVm.recheckEmailVerification()
        onPauseOrDispose { }
    }

    // Collect cart badge count — stays live as long as this screen is in composition
    val userId = authRepository.currentUser.value?.uuid
    val cartCountFlow = remember(userId) {
        if (userId != null) cartRepository.observeUniqueProductCount(userId) else flowOf(0)
    }
    val cartCount by cartCountFlow.collectAsStateWithLifecycle(initialValue = 0)

    val pagerState = rememberPagerState(
        initialPage = tabIndex,
        pageCount = { CustomerTabs.entries.size }
    )
    val currentTabIndex by remember { derivedStateOf { pagerState.currentPage } }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = emailBannerState.isVisible) {
                EmailVerificationNotificationBar(
                    onNavigateToProfile = { /* TODO add navigation to Customer Profile */ },
                )
            }
        },
        bottomBar = {
            CustomerNavigationBar(
                currentTabIndex = currentTabIndex,
                cartCount = cartCount,
                onTabSelected = { tab ->
                    scope.launch {
                        pagerState.animateScrollToPage(tab.ordinal)
                    }
                }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = pagerState,
            userScrollEnabled = false,
        ) { page ->
            when (page) {
                0 -> CustomerHomeScreen(onProductClick = onProductClick)
                1 -> CustomerCartScreen(
                    onNavigateToHome = {
                        scope.launch { pagerState.animateScrollToPage(CustomerTabs.Home.ordinal) }
                    },
                    onNavigateToProduct = onProductClick,
                    onNavigateToCheckout = onNavigateToCheckout,
                )
                2 -> CustomerWishlistScreen(
                    onNavigateToProduct = onProductClick,
                    onNavigateToHome = {
                        scope.launch { pagerState.animateScrollToPage(CustomerTabs.Home.ordinal) }
                    },
                )
                3 -> CustomerProfileScreen()
            }
        }
    }
}

@Composable
private fun CustomerNavigationBar(
    currentTabIndex: Int,
    cartCount: Int,
    onTabSelected: (CustomerTabs) -> Unit,
) {
    NavigationBar {
        CustomerTabs.entries.forEachIndexed { index, tab ->
            val isSelected = currentTabIndex == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    if (tab == CustomerTabs.Cart && cartCount > 0) {
                        BadgedBox(badge = { Badge { Text("$cartCount") } }) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(id = tab.text)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(id = tab.text)
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(id = tab.text),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}
