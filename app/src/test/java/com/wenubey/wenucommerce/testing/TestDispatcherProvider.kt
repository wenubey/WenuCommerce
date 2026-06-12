package com.wenubey.wenucommerce.testing

import com.wenubey.domain.repository.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * DispatcherProvider that hands out one TestDispatcher for every role. Pair with
 * [MainDispatcherRule] sharing the same dispatcher so that viewModelScope work
 * and explicit withContext(dispatcherProvider.io()) hops both run under the
 * same virtual clock — advanceUntilIdle() then drains everything coherently.
 */
class TestDispatcherProvider(
    private val dispatcher: TestDispatcher,
) : DispatcherProvider {
    override fun main(): CoroutineDispatcher = dispatcher
    override fun io(): CoroutineDispatcher = dispatcher
    override fun default(): CoroutineDispatcher = dispatcher
}
