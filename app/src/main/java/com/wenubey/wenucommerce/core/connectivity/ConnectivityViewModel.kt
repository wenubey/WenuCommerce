package com.wenubey.wenucommerce.core.connectivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.data.connectivity.ConnectivityObserver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ConnectivityViewModel(connectivityObserver: ConnectivityObserver) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )
}
