package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.Notification
import com.wenubey.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeNotificationRepository : NotificationRepository {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())

    var markAsReadResult: Result<Unit> = Result.success(Unit)
    val markAsReadCalls = mutableListOf<String>()
    var observeNotificationsCalls = 0
    var observeUnreadCountCalls = 0

    fun emitNotifications(notifications: List<Notification>) {
        _notifications.value = notifications
    }

    override fun observeNotifications(userId: String): Flow<List<Notification>> {
        observeNotificationsCalls++
        return _notifications
    }

    override fun observeUnreadCount(userId: String): Flow<Int> {
        observeUnreadCountCalls++
        return _notifications.map { list -> list.count { !it.isRead } }
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> {
        markAsReadCalls.add(notificationId)
        if (markAsReadResult.isSuccess) {
            _notifications.value = _notifications.value.map { n ->
                if (n.id == notificationId) n.copy(isRead = true) else n
            }
        }
        return markAsReadResult
    }

    override fun startListener(userId: String) {}
    override fun stopListener() {}
}
