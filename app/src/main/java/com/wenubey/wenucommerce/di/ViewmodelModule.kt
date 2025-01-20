package com.wenubey.wenucommerce.di

import com.wenubey.wenucommerce.screens.auth.AuthViewModel
import com.wenubey.wenucommerce.screens.auth.sign_in.SignInViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::AuthViewModel)
    viewModelOf(::SignInViewModel)
}