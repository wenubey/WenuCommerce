package com.wenubey.wenucommerce.notification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-scoped bus that fans inbound FCM-driven sync triggers out to any
 * number of ViewModels.
 *
 * Producer: [MessagingService.onMessageReceived] when data["type"] ==
 * [FCM_TYPE_ORDER_STATUS].
 *
 * Consumers (06-02): customer order list + detail ViewModels, which
 * collect on this bus and call `OrderRepository.syncCustomerOrders()`
 * so the screen reflects the new aggregate status without waiting for a
 * pull-to-refresh.
 *
 * `replay = 0` — emissions before any collector subscribes are dropped on
 * purpose. The FCM payload is also delivered as a system notification, so a
 * missed bus event is recovered when the user opens the app and the screen's
 * `onStart` sync fires.
 *
 * `extraBufferCapacity = 8` — absorbs a small burst (e.g. multi-seller
 * order where every seller advances within seconds) without suspending
 * [emit] in the service.
 *
 * Singleton: bound in `notificationModule` (di/AppModules.kt) so the
 * service and ViewModels share the same SharedFlow instance.
 */
class SyncBus {
    private val _events: MutableSharedFlow<SyncEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 8,
    )

    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    suspend fun emit(event: SyncEvent) {
        _events.emit(event)
    }
}
