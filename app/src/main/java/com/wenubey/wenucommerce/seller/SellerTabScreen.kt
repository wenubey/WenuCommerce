package com.wenubey.wenucommerce.seller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.data.repository.NotificationPreferences
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.AuthViewModel
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationBannerViewModel
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationNotificationBar
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryScreen
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryViewModel
import com.wenubey.wenucommerce.notification.permission.NotificationPermissionRationaleDialog
import com.wenubey.wenucommerce.seller.orders.SellerOrdersScreen
import com.wenubey.wenucommerce.seller.seller_dashboard.SellerDashboardScreen
import com.wenubey.wenucommerce.seller.seller_discounts.SellerDiscountListScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// TODO Refactor Later
@Composable
fun SellerTabScreen(
    tabIndex: Int,
    onNavigateToSellerVerification: (user: User?) -> Unit,
    onNavigateToCreateProduct: () -> Unit = {},
    onNavigateToEditProduct: (String) -> Unit = {},
    onNavigateToCreateDiscount: () -> Unit = {},
    onNavigateToEditDiscount: (String) -> Unit = {},
    onNavigateToSellerOrderDetail: (String) -> Unit = {},
    onViewReviews: (productId: String, productTitle: String) -> Unit = { _, _ -> },
    onNavigateToOrderDetail: (String) -> Unit = {},
    authViewModel: AuthViewModel = koinViewModel(),
    emailBannerVm: EmailVerificationBannerViewModel = koinViewModel(),
    notificationVm: NotificationHistoryViewModel = koinViewModel(),
    notificationPreferences: NotificationPreferences = koinInject(),
) {
    val context = LocalContext.current
    val user by authViewModel.currentUser.collectAsStateWithLifecycle()
    val emailBannerState by emailBannerVm.emailVerificationBannerState.collectAsStateWithLifecycle()
    val unreadCount by notificationVm.unreadCount.collectAsStateWithLifecycle()

    val isApproved by remember(user) {
        derivedStateOf {
            user?.businessInfo?.verificationStatus == VerificationStatus.APPROVED
        }
    }

    // Permission state — rechecked on every ON_RESUME
    var notificationsEnabled by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    LifecycleResumeEffect(Unit) {
        emailBannerVm.recheckEmailVerification()
        notificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        onPauseOrDispose { }
    }

    var showRationaleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val requested = notificationPreferences.isNotificationPermissionRequested()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationsEnabled &&
            !requested
        ) {
            showRationaleDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        notificationsEnabled = granted
    }
    val scope = rememberCoroutineScope()

    if (showRationaleDialog) {
        NotificationPermissionRationaleDialog(
            onEnable = {
                showRationaleDialog = false
                scope.launch { notificationPreferences.setNotificationPermissionRequested(true) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = {
                showRationaleDialog = false
                scope.launch { notificationPreferences.setNotificationPermissionRequested(true) }
            },
        )
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
            SellerTabRow(
                pagerState = pagerState,
                currentTabIndex = currentTabIndex,
                unreadCount = unreadCount,
            )
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
                        onViewReviews = onViewReviews,
                    )
                    2 -> SellerOrdersScreen(
                        onOrderClick = onNavigateToSellerOrderDetail,
                    )
                    3 -> SellerDiscountListScreen(
                        onNavigateToCreate = onNavigateToCreateDiscount,
                        onNavigateToEdit = onNavigateToEditDiscount,
                    )
                    4 -> NotificationHistoryScreen(
                        onBack = {
                            scope.launch { pagerState.animateScrollToPage(SellerTabs.Dashboard.ordinal) }
                        },
                        onNavigateToOrderDetail = onNavigateToOrderDetail,
                        onNavigateToSellerOrder = onNavigateToSellerOrderDetail,
                        viewModel = notificationVm,
                    )
                    5 -> SellerProfileScreen(
                        user = user,
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsClick = {
                            if (!notificationsEnabled) {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(SellerTabs.Notifications.ordinal)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SellerTabRow(
    pagerState: PagerState,
    currentTabIndex: Int,
    unreadCount: Int = 0,
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
                    if (tab == SellerTabs.Notifications && unreadCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(if (unreadCount > 9) "9+" else "$unreadCount")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(id = tab.text),
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(id = tab.text),
                        )
                    }
                    Text(
                        text = stringResource(id = tab.text),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
