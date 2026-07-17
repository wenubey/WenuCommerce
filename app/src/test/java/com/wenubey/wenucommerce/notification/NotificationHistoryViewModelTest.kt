package com.wenubey.wenucommerce.notification

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.Notification
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryAction
import com.wenubey.wenucommerce.notification.notification_history.NotificationHistoryViewModel
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import com.wenubey.wenucommerce.testing.fakes.FakeAuthRepository
import com.wenubey.wenucommerce.testing.fakes.FakeNotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userId = "test-user-123"

    private fun notification(
        id: String,
        isRead: Boolean = false,
        type: String = "order_status",
        orderId: String = "",
        sellerOrderId: String = "",
        productId: String = "",
        productTitle: String = "",
    ) = Notification(
        id = id,
        userId = userId,
        type = type,
        title = "Test title $id",
        body = "Test body $id",
        orderId = orderId,
        sellerOrderId = sellerOrderId,
        productId = productId,
        productTitle = productTitle,
        isRead = isRead,
        // Epoch-millis String — the format the data layer actually produces (NotificationMapper).
        createdAt = "1752750000000",
    )

    private fun buildViewModel(
        repo: FakeNotificationRepository = FakeNotificationRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = User(uuid = userId)),
    ): Pair<NotificationHistoryViewModel, FakeNotificationRepository> {
        val vm = NotificationHistoryViewModel(repo, auth)
        return vm to repo
    }

    // --- Behavior 1: repository emissions flow into state ---

    @Test
    fun `init observes notifications and sets state correctly`() = runTest {
        val (vm, repo) = buildViewModel()

        repo.emitNotifications(
            listOf(
                notification("n1", isRead = false),
                notification("n2", isRead = true),
            )
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.notifications).hasSize(2)
        assertThat(state.isLoading).isFalse()
        assertThat(state.hasUnread).isTrue()
    }

    @Test
    fun `state hasUnread is false when all notifications are read`() = runTest {
        val (vm, repo) = buildViewModel()

        repo.emitNotifications(
            listOf(
                notification("n1", isRead = true),
                notification("n2", isRead = true),
            )
        )
        advanceUntilIdle()

        assertThat(vm.state.value.hasUnread).isFalse()
    }

    // --- Behavior 2: OnItemClick calls markAsRead + emits nav effect ---

    @Test
    fun `OnItemClick calls markAsRead and emits NavigateToDestination effect for order_status`() =
        runTest {
            val (vm, repo) = buildViewModel()
            val n1 = notification("n1", isRead = false, type = "order_status", orderId = "order-1")
            repo.emitNotifications(listOf(n1))
            advanceUntilIdle()

            vm.navigationEffect.test {
                vm.onAction(NotificationHistoryAction.OnItemClick(n1))
                advanceUntilIdle()

                assertThat(repo.markAsReadCalls).contains("n1")
                val effect = awaitItem()
                assertThat(effect).isInstanceOf(
                    NotificationHistoryViewModel.NavigationDestination.OrderDetail::class.java
                )
                val dest = effect as NotificationHistoryViewModel.NavigationDestination.OrderDetail
                assertThat(dest.orderId).isEqualTo("order-1")
            }
        }

    @Test
    fun `OnItemClick emits SellerOrder navigation effect for new_order type`() = runTest {
        val (vm, repo) = buildViewModel()
        val n = notification("n2", isRead = false, type = "new_order", sellerOrderId = "seller-42")
        repo.emitNotifications(listOf(n))
        advanceUntilIdle()

        vm.navigationEffect.test {
            vm.onAction(NotificationHistoryAction.OnItemClick(n))
            advanceUntilIdle()

            assertThat(repo.markAsReadCalls).contains("n2")
            val effect = awaitItem()
            assertThat(effect).isInstanceOf(
                NotificationHistoryViewModel.NavigationDestination.SellerOrder::class.java
            )
            val dest = effect as NotificationHistoryViewModel.NavigationDestination.SellerOrder
            assertThat(dest.sellerOrderId).isEqualTo("seller-42")
        }
    }

    @Test
    fun `OnItemClick emits ProductReviews navigation effect for new_review type`() = runTest {
        val (vm, repo) = buildViewModel()
        val n = notification(
            "n3",
            isRead = false,
            type = "new_review",
            productId = "prod-99",
            productTitle = "Cool Widget"
        )
        repo.emitNotifications(listOf(n))
        advanceUntilIdle()

        vm.navigationEffect.test {
            vm.onAction(NotificationHistoryAction.OnItemClick(n))
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(
                NotificationHistoryViewModel.NavigationDestination.ProductReviews::class.java
            )
            val dest = effect as NotificationHistoryViewModel.NavigationDestination.ProductReviews
            assertThat(dest.productId).isEqualTo("prod-99")
            assertThat(dest.productTitle).isEqualTo("Cool Widget")
        }
    }

    // --- Behavior 3: OnMarkAllRead clears hasUnread ---

    @Test
    fun `OnMarkAllRead marks all unread items and hasUnread becomes false`() = runTest {
        val (vm, repo) = buildViewModel()
        repo.emitNotifications(
            listOf(
                notification("n1", isRead = false),
                notification("n2", isRead = false),
                notification("n3", isRead = true),
            )
        )
        advanceUntilIdle()

        assertThat(vm.state.value.hasUnread).isTrue()

        vm.onAction(NotificationHistoryAction.OnMarkAllRead)
        advanceUntilIdle()

        assertThat(repo.markAsReadCalls).containsAtLeast("n1", "n2")
        assertThat(vm.state.value.hasUnread).isFalse()
    }

    // --- Behavior 4: unreadCount StateFlow reflects repository ---

    @Test
    fun `unreadCount reflects observeUnreadCount from repository`() = runTest {
        val (vm, repo) = buildViewModel()

        // unreadCount is WhileSubscribed — collect it so the upstream is active.
        vm.unreadCount.test {
            assertThat(awaitItem()).isEqualTo(0) // initial (empty repo)

            repo.emitNotifications(
                listOf(
                    notification("n1", isRead = false),
                    notification("n2", isRead = false),
                    notification("n3", isRead = false), // 3 unread
                )
            )
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(3)

            // After two are marked read, count drops to 1
            repo.emitNotifications(
                listOf(
                    notification("n1", isRead = true),
                    notification("n2", isRead = false),
                    notification("n3", isRead = true),
                )
            )
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- CR-02 regression: list + badge rebind when the uid arrives after construction ---

    @Test
    fun `list and badge rebind when auth uid arrives after construction (cold-start race)`() =
        runTest {
            val repo = FakeNotificationRepository()
            // Firebase authed but profile not yet loaded → currentUser is null at construction.
            val auth = FakeAuthRepository(initialUser = null)
            val vm = NotificationHistoryViewModel(repo, auth)
            repo.emitNotifications(listOf(notification("n1", isRead = false)))
            advanceUntilIdle()

            // While the uid is absent the VM must NOT bind to "" — the list stays empty
            // rather than silently querying WHERE userId = "" forever.
            assertThat(vm.state.value.notifications).isEmpty()

            vm.unreadCount.test {
                assertThat(awaitItem()).isEqualTo(0) // no uid yet → 0

                // Profile finishes loading and the uid appears.
                auth.emitUser(User(uuid = userId))
                advanceUntilIdle()

                // Both surfaces self-heal without a process restart.
                assertThat(vm.state.value.notifications).hasSize(1)
                assertThat(awaitItem()).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- WR-05 regression: id-less / unknown types do not deep-link to an empty-key screen ---

    @Test
    fun `OnItemClick on device_login notification marks read but emits no navigation`() = runTest {
        val (vm, repo) = buildViewModel()
        val n = notification("n-login", isRead = false, type = "device_login")
        repo.emitNotifications(listOf(n))
        advanceUntilIdle()

        vm.navigationEffect.test {
            vm.onAction(NotificationHistoryAction.OnItemClick(n))
            advanceUntilIdle()

            assertThat(repo.markAsReadCalls).contains("n-login")
            expectNoEvents() // id-less/unknown type stays on the list
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- OnDismissError clears error state ---

    @Test
    fun `OnDismissError clears errorMessage`() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationHistoryViewModel(
            notificationRepository = repo,
            authRepository = FakeAuthRepository(initialUser = User(uuid = userId)),
        )
        advanceUntilIdle()

        // Force an error into state manually via OnRefresh with repo returning error
        vm.onAction(NotificationHistoryAction.OnDismissError)
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isNull()
    }
}
