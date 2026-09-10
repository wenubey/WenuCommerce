package com.wenubey.wenucommerce.customer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.data.repository.NotificationPreferences
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.CartRepository
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationBannerViewModel
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationNotificationBar
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryScreen
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryViewModel
import com.wenubey.wenucommerce.notification.permission.NotificationPermissionRationaleDialog
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerTabScreen(
    tabIndex: Int,
    onProductClick: (String) -> Unit = {},
    onNavigateToCheckout: () -> Unit = {},
    onNavigateToOrderHistory: () -> Unit = {},
    onNavigateToOrderDetail: (String) -> Unit = {},
    emailBannerVm: EmailVerificationBannerViewModel = koinViewModel(),
    notificationVm: NotificationHistoryViewModel = koinViewModel(),
    cartRepository: CartRepository = koinInject(),
    authRepository: AuthRepository = koinInject(),
    notificationPreferences: NotificationPreferences = koinInject(),
) {
    val context = LocalContext.current
    val emailBannerState by emailBannerVm.emailVerificationBannerState.collectAsStateWithLifecycle()
    val unreadCount by notificationVm.unreadCount.collectAsStateWithLifecycle()

    // Permission state — rechecked on every ON_RESUME (LifecycleResumeEffect pattern)
    var notificationsEnabled by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true // Pre-API-33: always considered "enabled"
            }
        )
    }

    LifecycleResumeEffect(Unit) {
        emailBannerVm.recheckEmailVerification()
        // Recheck notification permission on resume (user may have toggled in system settings)
        notificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        onPauseOrDispose { }
    }

    // One-time rationale dialog state — gated by DataStore key (NOTF-05 / D-02 / T-08-14)
    var showRationaleDialog by remember { mutableStateOf(false) }
    var permissionAlreadyRequested by remember { mutableStateOf(true) } // pessimistic default

    // Load the DataStore gate on composition
    LaunchedEffect(Unit) {
        permissionAlreadyRequested = notificationPreferences.isNotificationPermissionRequested()
        // Show dialog if: API 33+, not granted, and not previously requested
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationsEnabled &&
            !permissionAlreadyRequested
        ) {
            showRationaleDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        notificationsEnabled = granted
    }

    // Collect cart badge count
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

    // Rationale dialog
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

    Scaffold(
        topBar = {
            Column {
                AnimatedVisibility(visible = emailBannerState.isVisible) {
                    EmailVerificationNotificationBar(
                        onNavigateToProfile = { /* TODO add navigation to Customer Profile */ },
                    )
                }
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(
                                id = CustomerTabs.entries[currentTabIndex].text
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        },
        bottomBar = {
            CustomerNavigationBar(
                currentTabIndex = currentTabIndex,
                cartCount = cartCount,
                unreadCount = unreadCount,
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
                3 -> NotificationHistoryScreen(
                    onBack = {
                        scope.launch { pagerState.animateScrollToPage(CustomerTabs.Home.ordinal) }
                    },
                    onNavigateToOrderDetail = onNavigateToOrderDetail,
                    viewModel = notificationVm,
                )
                4 -> CustomerProfileScreen(
                    onNavigateToOrderHistory = onNavigateToOrderHistory,
                    notificationsEnabled = notificationsEnabled,
                    onNotificationsClick = {
                        if (!notificationsEnabled) {
                            // Open system app-notification settings. Guard the launch — some OEM
                            // ROMs / restricted profiles don't resolve the action and would
                            // otherwise crash with ActivityNotFoundException (WR-08).
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            runCatching { context.startActivity(intent) }
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(CustomerTabs.Notifications.ordinal)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomerNavigationBar(
    currentTabIndex: Int,
    cartCount: Int,
    unreadCount: Int,
    onTabSelected: (CustomerTabs) -> Unit,
) {
    NavigationBar {
        CustomerTabs.entries.forEachIndexed { index, tab ->
            val isSelected = currentTabIndex == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    when {
                        tab == CustomerTabs.Cart && cartCount > 0 -> {
                            BadgedBox(badge = { Badge { Text("$cartCount") } }) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(id = tab.text),
                                )
                            }
                        }
                        tab == CustomerTabs.Notifications && unreadCount > 0 -> {
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
                        }
                        else -> {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(id = tab.text),
                            )
                        }
                    }
                },
                label = {
                    Text(
                        text = stringResource(id = tab.text),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}
