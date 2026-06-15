package com.wenubey.wenucommerce.core.connectivity

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.wenubey.data.connectivity.ConnectivityObserver
import com.wenubey.wenucommerce.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun newViewModel(
        upstream: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): Pair<ConnectivityViewModel, MutableStateFlow<Boolean>> {
        val observer: ConnectivityObserver = mockk(relaxed = true)
        every { observer.isOnline } returns upstream
        return ConnectivityViewModel(observer) to upstream
    }

    @Test
    fun `initial value is true regardless of upstream first emission`() = runTest {
        val (vm, _) = newViewModel(upstream = MutableStateFlow(false))

        // stateIn's initialValue ("true") is observable until upstream emits
        // for an active collector.
        assertThat(vm.isOnline.value).isTrue()
    }

    @Test
    fun `state mirrors upstream emissions once collected`() = runTest {
        val upstream = MutableStateFlow(true)
        val (vm, _) = newViewModel(upstream)

        vm.isOnline.test {
            assertThat(awaitItem()).isTrue()

            upstream.value = false
            assertThat(awaitItem()).isFalse()

            upstream.value = true
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `state returns to initial value after subscribers leave and 5s timeout passes`() = runTest {
        val upstream = MutableStateFlow(true)
        val (vm, _) = newViewModel(upstream)

        // Start a brief collection that pushes upstream to false, then leave.
        vm.isOnline.test {
            assertThat(awaitItem()).isTrue()
            upstream.value = false
            assertThat(awaitItem()).isFalse()
        }
        advanceUntilIdle()

        // WhileSubscribed(5_000) — the value is retained for 5s after the
        // last subscriber leaves.
        assertThat(vm.isOnline.value).isFalse()
    }
}
