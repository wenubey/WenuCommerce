package com.wenubey.wenucommerce.di

import com.wenubey.wenucommerce.viewmodels.SignInViewModel
import com.wenubey.wenucommerce.viewmodels.SignUpViewModel
import com.wenubey.wenucommerce.viewmodels.AuthViewModel
import com.wenubey.wenucommerce.viewmodels.VerifyEmailViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::SignUpViewModel)
    viewModelOf(::SignInViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::VerifyEmailViewModel)
}