package com.wenubey.wenucommerce.core.connectivity

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.wenubey.data.connectivity.ConnectivityObserver
import com.wenubey.data.local.dao.PendingOperationDao
import com.wenubey.data.worker.SyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the merged offline + pending sync banner.
 *
 * Combines three data sources:
 * - ConnectivityObserver: isOnline state
 * - PendingOperationDao: pending operation count
 * - WorkManager: sync-in-progress state
 *
 * Banner visibility logic:
 * - Show if offline (regardless of pending count)
 * - Show if pending count > 0 AND pending count > dismissed count
 * - Hide if online AND (no pending operations OR user dismissed at this count)
 *
 * Dismiss behavior:
 * - Stores the current pending count in DataStore
 * - Banner reappears when new writes are queued (pending count increases)
 */
class PendingSyncViewModel(
    connectivityObserver: ConnectivityObserver,
    pendingOperationDao: PendingOperationDao,
    private val dataStore: DataStore<Preferences>,
    application: Application
) : ViewModel() {

    private val dismissedCountKey = intPreferencesKey("dismissed_pending_count")

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true
    )

    val pendingCount: StateFlow<Int> = pendingOperationDao.observePendingCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    private val dismissedCount: StateFlow<Int> = dataStore.data.map { prefs ->
        prefs[dismissedCountKey] ?: 0
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    val shouldShowBanner: StateFlow<Boolean> = combine(
        isOnline,
        pendingCount,
        dismissedCount
    ) { online, pending, dismissed ->
        // Show banner if:
        // 1. Offline (regardless of pending count)
        // 2. Online but pending items exist AND count > dismissed count
        !online || (pending > 0 && pending > dismissed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val isSyncing: StateFlow<Boolean> = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(SyncWorker.UNIQUE_WORK_NAME)
        .map { workInfos ->
            workInfos.any { it.state == WorkInfo.State.RUNNING }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun dismissBanner() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[dismissedCountKey] = pendingCount.value
            }
        }
    }
}
