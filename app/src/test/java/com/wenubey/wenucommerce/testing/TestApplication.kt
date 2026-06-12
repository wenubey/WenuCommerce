package com.wenubey.wenucommerce.testing

import android.app.Application

/**
 * Empty Application for Robolectric tests. The production Application
 * (com.wenubey.wenucommerce.WenuCommerce) starts Koin in onCreate(), which
 * collides with Robolectric instantiating it per test class. Tests that need
 * Robolectric should use:
 *
 *     @Config(sdk = [33], application = TestApplication::class)
 *
 * to bypass that startup entirely. ViewModel tests don't need DI — they
 * receive collaborators through constructors.
 */
class TestApplication : Application()
