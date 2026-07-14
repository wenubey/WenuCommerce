package com.wenubey.wenucommerce

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wenubey.data.local.SyncEvent
import com.wenubey.data.local.SyncManager
import com.wenubey.wenucommerce.core.connectivity.PendingSyncViewModel
import com.wenubey.wenucommerce.core.connectivity.PendingSyncBanner
import com.wenubey.wenucommerce.navigation.OrderDetail
import com.wenubey.wenucommerce.navigation.QueueManagement
import com.wenubey.wenucommerce.navigation.RootNavigationGraph
import com.wenubey.wenucommerce.notification.EXTRA_NAV_TARGET
import com.wenubey.wenucommerce.notification.EXTRA_ORDER_ID
import com.wenubey.wenucommerce.notification.NAV_TARGET_ORDER_DETAIL
import com.wenubey.wenucommerce.ui.theme.WenuCommerceTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController
    private val viewModel: AuthViewModel by viewModel()

    /**
     * Phase 6 Plan 04 — deep-link version key.
     *
     * Increments every time [onNewIntent] receives a new intent (FCM tap
     * while the activity is in the back stack). The `LaunchedEffect`
     * inside `setContent` keys on this so it re-runs and consumes the
     * fresh extras. Starts at 0 so the cold-start case (extras attached
     * to the launching intent) still fires once on first composition.
     */
    private var intentVersion by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // setIntent() is critical — without it, `this.intent` keeps returning
        // the original launching intent and Compose never sees the new extras.
        setIntent(intent)
        intentVersion++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO research this topBar issue and fix it
        //enableEdgeToEdge()
        handleSplashScreen()
        setContent {
            KoinContext {
                WenuCommerceTheme {
                    navController = rememberNavController()

                    // Phase 6 Plan 04 — FCM deep-link consumer.
                    // Keyed on intentVersion so onNewIntent reliably retriggers.
                    // Cold-start case is covered because intentVersion starts
                    // at 0 and LaunchedEffect runs once on first composition.
                    LaunchedEffect(intentVersion) {
                        // Two paths deliver an order-status deep-link:
                        //   1) Foreground push → MessagingService builds a
                        //      launch intent with our namespaced extras
                        //      (EXTRA_NAV_TARGET, EXTRA_ORDER_ID).
                        //   2) Background / killed → FCM system tray shows
                        //      the notification; tap opens the launcher
                        //      activity with `data` payload keyed by raw
                        //      FCM keys ("orderId", "type", ...).
                        // Check both.
                        val namespacedOrderId = intent.getStringExtra(EXTRA_ORDER_ID)
                        val fcmRawOrderId = intent.getStringExtra("orderId")
                        val fcmRawType = intent.getStringExtra("type")
                        val target = intent.getStringExtra(EXTRA_NAV_TARGET)

                        val orderId = namespacedOrderId
                            ?: fcmRawOrderId?.takeIf { fcmRawType == "order_status" }

                        if (!orderId.isNullOrBlank() &&
                            (target == NAV_TARGET_ORDER_DETAIL || namespacedOrderId == null)
                        ) {
                            navController.navigate(OrderDetail(orderId))
                            // Clear so rotation / recomposition does not
                            // re-navigate.
                            intent.removeExtra(EXTRA_NAV_TARGET)
                            intent.removeExtra(EXTRA_ORDER_ID)
                            intent.removeExtra("orderId")
                            intent.removeExtra("type")
                        }
                    }

                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val isOnQueueManagementScreen = with(NavDestination) {
                        currentBackStackEntry?.destination?.hasRoute(QueueManagement::class) == true
                    }

                    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                    val isInitialized by viewModel.isInitialized.collectAsStateWithLifecycle()

                    // Pending sync banner
                    val pendingSyncVm: PendingSyncViewModel = koinViewModel()
                    val isOnline by pendingSyncVm.isOnline.collectAsStateWithLifecycle()
                    val pendingCount by pendingSyncVm.pendingCount.collectAsStateWithLifecycle()
                    val shouldShowBanner by pendingSyncVm.shouldShowBanner.collectAsStateWithLifecycle()
                    val isSyncing by pendingSyncVm.isSyncing.collectAsStateWithLifecycle()

                    // Sync event snackbars
                    val syncManager: SyncManager = koinInject()
                    val snackbarHostState = remember { SnackbarHostState() }
                    val syncFailedMessage = stringResource(R.string.sync_failed_snackbar)
                    val savedLocallyMessage = stringResource(R.string.saved_locally_snackbar)
                    val partialFailureMessage = stringResource(R.string.some_items_failed_to_sync)
                    LaunchedEffect(Unit) {
                        syncManager.syncEvents.collect { event ->
                            when (event) {
                                is SyncEvent.SyncFailed -> {
                                    snackbarHostState.showSnackbar(
                                        message = syncFailedMessage,
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                                is SyncEvent.OfflineWriteQueued -> {
                                    snackbarHostState.showSnackbar(
                                        message = savedLocallyMessage,
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                                is SyncEvent.SyncPartialFailure -> {
                                    snackbarHostState.showSnackbar(
                                        message = event.message.ifEmpty { partialFailureMessage },
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                        ) { _ ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (isInitialized) {
                                    RootNavigationGraph(
                                        navController = navController,
                                        startDestination = startDestination
                                    )
                                }

                                // Global pending sync banner overlay — merged offline + pending sync states
                                // "Top banner, visible on every screen", "Overlays on top of content (no layout shift)"
                                // "Animates in (slide down) and out (slide up)"
                                // Shows: offline-only, pending-only, or combined states
                                AnimatedVisibility(
                                    visible = shouldShowBanner && !isOnQueueManagementScreen,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    enter = slideInVertically(initialOffsetY = { -it }),
                                    exit = slideOutVertically(targetOffsetY = { -it }),
                                ) {
                                    PendingSyncBanner(
                                        isOnline = isOnline,
                                        pendingCount = pendingCount,
                                        isSyncing = isSyncing,
                                        onDismiss = { pendingSyncVm.dismissBanner() },
                                        onTap = { navController.navigate(QueueManagement) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleSplashScreen() {
        installSplashScreen().setKeepOnScreenCondition {
            !viewModel.isInitialized.value
        }
    }
}
