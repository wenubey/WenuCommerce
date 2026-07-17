package com.wenubey.wenucommerce.notification.notification_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.Notification
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.NotificationRepository
import com.wenubey.wenucommerce.notification.FCM_TYPE_NEW_ORDER
import com.wenubey.wenucommerce.notification.FCM_TYPE_NEW_REVIEW
import com.wenubey.wenucommerce.notification.FCM_TYPE_ORDER_STATUS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
 * Both the history list and the badge are driven off the auth [AuthRepository.currentUser]
 * stream via `flatMapLatest` so they rebind when the uid appears (cold-start auth race,
 * CR-02) or changes (sign-out/in) — never latched to a stale/empty uid captured at
 * construction time.
 *
 * Threat mitigations applied:
 *   T-08-12: [markAsRead] only operates on the current user's notifications (auth uid guard).
 *   T-08-13: [observeNotifications] is always called with the live current uid (never cross-user).
 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    /** Live signed-in uid stream — the single source that rebinds the notification and
     *  badge flows whenever the profile loads or the user switches. */
    private val userIdFlow = authRepository.currentUser
        .map { it?.uuid }
        .distinctUntilChanged()

    /** Live unread badge count for the bottom nav (D-01b). Rebinds to the current uid via
     *  flatMapLatest, so a VM built during the cold-start auth race still populates once the
     *  profile loads; WhileSubscribed bounds the Room listener to actual UI presence. */
    val unreadCount: StateFlow<Int> = userIdFlow
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(0)
            else notificationRepository.observeUnreadCount(uid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    init {
        userIdFlow
            .flatMapLatest { uid ->
                if (uid.isNullOrBlank()) flowOf(emptyList())
                else notificationRepository.observeNotifications(uid)
            }
            .onEach { notifications ->
                _state.value = _state.value.copy(
                    notifications = notifications,
                    isLoading = false,
                    hasUnread = notifications.any { !it.isRead },
                )
            }
            .catch { error ->
                Timber.e(error, "NotificationHistoryViewModel: observeNotifications failed")
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Unknown error",
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
            // Only navigate when the notification carries a valid destination id; an id-less
            // or unknown type (e.g. device_login) stays on the list rather than deep-linking
            // to a broken empty-key detail screen (WR-05).
            resolveNavDestination(item)?.let { _navigationEffect.send(it) }
        }
    }

    private fun resolveNavDestination(item: Notification): NavigationDestination? = when (item.type) {
        FCM_TYPE_NEW_ORDER -> item.sellerOrderId.ifBlank { null }
            ?.let { NavigationDestination.SellerOrder(it) }
        FCM_TYPE_NEW_REVIEW -> item.productId.ifBlank { null }
            ?.let { NavigationDestination.ProductReviews(it, item.productTitle) }
        FCM_TYPE_ORDER_STATUS -> item.orderId.ifBlank { null }
            ?.let { NavigationDestination.OrderDetail(it) }
        else -> null
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
