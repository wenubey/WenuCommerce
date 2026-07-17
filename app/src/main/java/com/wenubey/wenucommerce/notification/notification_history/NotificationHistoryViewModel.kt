package com.wenubey.wenucommerce.notification.notification_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.Notification
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.NotificationRepository
import com.wenubey.wenucommerce.notification.FCM_TYPE_NEW_ORDER
import com.wenubey.wenucommerce.notification.FCM_TYPE_NEW_REVIEW
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Notification History ViewModel (NOTF-08, D-01).
 *
 * - Observes Room via [NotificationRepository.observeNotifications] for the signed-in uid.
 * - Exposes [unreadCount] as a [StateFlow] for the bottom-nav badge (D-01b).
 * - [onAction] handles tap → markAsRead + one-shot [NavigationDestination] effect,
 *   "mark all read", pull-to-refresh, and error dismissal.
 *
 * Threat mitigations applied:
 *   T-08-12: [markAsRead] only operates on the current user's notifications (auth uid guard).
 *   T-08-13: [observeNotifications] is always called with [currentUserId] (never cross-user).
 */
class NotificationHistoryViewModel(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** One-shot navigation events consumed by the Screen's LaunchedEffect. */
    sealed interface NavigationDestination {
        data class OrderDetail(val orderId: String) : NavigationDestination
        data class SellerOrder(val sellerOrderId: String) : NavigationDestination
        data class ProductReviews(val productId: String, val productTitle: String) : NavigationDestination
    }

    private val _state = MutableStateFlow(NotificationHistoryState(isLoading = true))
    val state: StateFlow<NotificationHistoryState> = _state.asStateFlow()

    private val _navigationEffect = Channel<NavigationDestination>(Channel.BUFFERED)
    val navigationEffect = _navigationEffect.receiveAsFlow()

    private val currentUserId: String?
        get() = authRepository.currentUser.value?.uuid

    /** Live unread badge count for the bottom nav (D-01b).
     * Eagerly started so the count is always current regardless of collector presence.
     * The badge composable injects this VM via koinViewModel and subscribes in its own lifecycle. */
    val unreadCount: StateFlow<Int> = notificationRepository
        .observeUnreadCount(authRepository.currentUser.value?.uuid ?: "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0,
        )

    init {
        observeNotifications()
    }

    private fun observeNotifications() {
        val uid = currentUserId ?: run {
            _state.value = _state.value.copy(isLoading = false)
            return
        }
        notificationRepository.observeNotifications(uid)
            .catch { error ->
                Timber.e(error, "NotificationHistoryViewModel: observeNotifications failed")
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Unknown error",
                )
            }
            .onEach { notifications ->
                _state.value = _state.value.copy(
                    notifications = notifications,
                    isLoading = false,
                    hasUnread = notifications.any { !it.isRead },
                )
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: NotificationHistoryAction) {
        when (action) {
            is NotificationHistoryAction.OnItemClick -> handleItemClick(action.item)
            NotificationHistoryAction.OnMarkAllRead -> markAllRead()
            NotificationHistoryAction.OnRefresh -> refresh()
            NotificationHistoryAction.OnDismissError -> {
                _state.value = _state.value.copy(errorMessage = null)
            }
        }
    }

    private fun handleItemClick(item: Notification) {
        viewModelScope.launch {
            notificationRepository.markAsRead(item.id)
                .onFailure { e ->
                    Timber.e(e, "NotificationHistoryViewModel: markAsRead failed for ${item.id}")
                }
            val destination = resolveNavDestination(item)
            _navigationEffect.send(destination)
        }
    }

    private fun resolveNavDestination(item: Notification): NavigationDestination {
        return when (item.type) {
            FCM_TYPE_NEW_ORDER -> NavigationDestination.SellerOrder(item.sellerOrderId)
            FCM_TYPE_NEW_REVIEW -> NavigationDestination.ProductReviews(item.productId, item.productTitle)
            else -> NavigationDestination.OrderDetail(item.orderId) // order_status + fallback
        }
    }

    private fun markAllRead() {
        val unreadItems = _state.value.notifications.filter { !it.isRead }
        if (unreadItems.isEmpty()) return
        viewModelScope.launch {
            unreadItems.forEach { notification ->
                notificationRepository.markAsRead(notification.id)
                    .onFailure { e ->
                        Timber.e(e, "NotificationHistoryViewModel: markAsRead batch failed for ${notification.id}")
                    }
            }
        }
    }

    private fun refresh() {
        @Suppress("UNUSED_VARIABLE")
        val uid = currentUserId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, errorMessage = null)
            // Room-first: the Flow already emits live from DAO; refresh just resets the flag.
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }
}
