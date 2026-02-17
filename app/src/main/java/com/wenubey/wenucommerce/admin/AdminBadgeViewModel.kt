package com.wenubey.wenucommerce.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdminBadgeViewModel(
    dispatcherProvider: DispatcherProvider,
    private val firestoreRepository: FirestoreRepository,
): ViewModel() {

    private val _badgeState = MutableStateFlow(AdminBadgeState())
    val badgeState: StateFlow<AdminBadgeState> = _badgeState.asStateFlow()

    private val mainDispatcher = dispatcherProvider.main()

    private var badgeObserverJob: Job? = null

    init {
        observePendingApprovals()
    }

    private fun observePendingApprovals() {
        badgeObserverJob?.cancel()

        badgeObserverJob = viewModelScope.launch(mainDispatcher) {
            firestoreRepository.observePendingResubmittedSellerCount().collect { count ->
                _badgeState.update {
                    it.copy(
                        pendingApprovals = count
                    )
                }
            }
        }
    }
}