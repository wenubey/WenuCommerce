package com.wenubey.wenucommerce

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.wenubey.data.local.SyncEvent
import com.wenubey.data.local.SyncManager
import com.wenubey.wenucommerce.core.connectivity.ConnectivityViewModel
import com.wenubey.wenucommerce.core.connectivity.OfflineConnectivityBanner
import com.wenubey.wenucommerce.navigation.RootNavigationGraph
import com.wenubey.wenucommerce.ui.theme.WenuCommerceTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController
    private val viewModel: AuthViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO research this topBar issue and fix it
        //enableEdgeToEdge()
        handleSplashScreen()
        setContent {
            KoinContext {
                WenuCommerceTheme {
                    navController = rememberNavController()

                    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                    val isInitialized by viewModel.isInitialized.collectAsStateWithLifecycle()

                    // Connectivity banner
                    val connectivityVm: ConnectivityViewModel = koinViewModel()
                    val isOnline by connectivityVm.isOnline.collectAsStateWithLifecycle()

                    // Sync failure snackbar
                    // Per locked decision: "On sync failure: brief snackbar
                    // 'Sync failed — showing cached data' then continue with cached content"
                    val syncManager: SyncManager = koinInject()
                    val snackbarHostState = remember { SnackbarHostState() }
                    val syncFailedMessage = stringResource(R.string.sync_failed_snackbar)
                    LaunchedEffect(Unit) {
                        syncManager.syncEvents.collect { event ->
                            when (event) {
                                is SyncEvent.SyncFailed -> {
                                    snackbarHostState.showSnackbar(
                                        message = syncFailedMessage,
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

                                // Global connectivity banner overlay — per user decision:
                                // "Top banner, visible on every screen", "Overlays on top of content (no layout shift)"
                                // "Animates in (slide down) and out (slide up)"
                                // "Auto-dismisses immediately when connectivity returns"
                                AnimatedVisibility(
                                    visible = !isOnline,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    enter = slideInVertically(initialOffsetY = { -it }),
                                    exit = slideOutVertically(targetOffsetY = { -it }),
                                ) {
                                    OfflineConnectivityBanner()
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
