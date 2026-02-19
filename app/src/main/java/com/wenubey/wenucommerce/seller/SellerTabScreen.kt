package com.wenubey.wenucommerce.seller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
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
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.AuthViewModel
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationBannerViewModel
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationNotificationBar
import com.wenubey.wenucommerce.seller.seller_dashboard.SellerDashboardScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// TODO Refactor Later
@Composable
fun SellerTabScreen(
    tabIndex: Int,
    onNavigateToSellerVerification: (user: User?) -> Unit,
    onNavigateToCreateProduct: () -> Unit = {},
    onNavigateToEditProduct: (String) -> Unit = {},
    authViewModel: AuthViewModel = koinViewModel(),
    emailBannerVm: EmailVerificationBannerViewModel = koinViewModel(),
) {
    val user by authViewModel.currentUser.collectAsStateWithLifecycle()
    val emailBannerState by emailBannerVm.emailVerificationBannerState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        emailBannerVm.recheckEmailVerification()
        onPauseOrDispose { }
    }

    val isApproved by remember(user) {
        derivedStateOf {
            user?.businessInfo?.verificationStatus == VerificationStatus.APPROVED
        }
    }

    val pagerState = rememberPagerState(
        initialPage = tabIndex,
        pageCount = { SellerTabs.entries.size }
    )
    val currentTabIndex by remember { derivedStateOf { pagerState.currentPage } }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = emailBannerState.isVisible) {
                EmailVerificationNotificationBar(
                    onNavigateToProfile = { /* TODO add navigation to Seller Profile */ },
                )
            }
        },
        bottomBar = {
            SellerTabRow(pagerState = pagerState, currentTabIndex = currentTabIndex)
        }
    ) { paddingValues ->
        HorizontalPager(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = pagerState,
            verticalAlignment = Alignment.Top,
            pageSize = androidx.compose.foundation.pager.PageSize.Fill,
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    0 -> SellerDashboardScreen(
                        isApproved = isApproved,
                        onNavigateToSellerVerification = onNavigateToSellerVerification
                    )
                    1 -> SellerProductsScreen(
                        modifier = Modifier.fillMaxSize(),
                        onAddProduct = onNavigateToCreateProduct,
                        onEditProduct = onNavigateToEditProduct,
                    )
                    2 -> SellerOrdersScreen()
                    3 -> SellerProfileScreen(user = user)
                }
            }
        }
    }
}

@Composable
fun SellerTabRow(
    pagerState: PagerState,
    currentTabIndex: Int
) {
    val scope = rememberCoroutineScope()

    TabRow(
        modifier = Modifier.padding(vertical = 8.dp),
        selectedTabIndex = currentTabIndex,
    ) {
        SellerTabs.entries.forEachIndexed { index, tab ->
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