package com.wenubey.wenucommerce.seller.seller_dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SellerDashboardViewModel(
    dispatcherProvider: DispatcherProvider,
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _sellerDashboardState = MutableStateFlow(SellerDashboardState(
        isBannerVisible = savedStateHandle.get<Boolean>(KEY_BANNER_VISIBLE) ?: true
    ))
    val sellerDashboardState: StateFlow<SellerDashboardState> = _sellerDashboardState.asStateFlow()

    private var userObserverJob: Job? = null

    init {
        observeCurrentUser()
    }

    private fun observeCurrentUser() {
        userObserverJob?.cancel()

        userObserverJob = viewModelScope.launch(mainDispatcher) {
            authRepository.currentUser.collect { user ->
                _sellerDashboardState.update {
                    it.copy(user = user)
                }
            }
        }
    }

    fun onAction(action: SellerDashboardAction) {
        when (action) {
            is SellerDashboardAction.OnAddProduct -> addProduct()
            is SellerDashboardAction.HideBanner -> hideBanner()
        }
    }

    private fun hideBanner() {
        savedStateHandle[KEY_BANNER_VISIBLE] = false
        viewModelScope.launch(mainDispatcher) {
            _sellerDashboardState.update {
                it.copy(
                    isBannerVisible = false
                )
            }
        }
    }

    // TODO add product later
    private fun addProduct() {

    }

    companion object {
        private const val KEY_BANNER_VISIBLE = "banner_visible"
    }
}


