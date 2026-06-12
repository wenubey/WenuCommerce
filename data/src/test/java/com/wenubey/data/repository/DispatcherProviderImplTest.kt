package com.wenubey.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import org.junit.Test

class DispatcherProviderImplTest {

    private val provider = DispatcherProviderImpl()

    @Test
    fun `main returns Dispatchers Main`() {
        assertThat(provider.main()).isSameInstanceAs(Dispatchers.Main)
    }

    @Test
    fun `io returns Dispatchers IO`() {
        assertThat(provider.io()).isSameInstanceAs(Dispatchers.IO)
    }

    @Test
    fun `default returns Dispatchers Default`() {
        assertThat(provider.default()).isSameInstanceAs(Dispatchers.Default)
    }
}
