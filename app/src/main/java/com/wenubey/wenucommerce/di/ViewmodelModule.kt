package com.wenubey.wenucommerce.di

import com.wenubey.wenucommerce.AuthViewModel
import com.wenubey.wenucommerce.onboard.OnboardingViewModel
import com.wenubey.wenucommerce.screens.tab.TabViewModel
import com.wenubey.wenucommerce.sign_in.SignInViewModel
import com.wenubey.wenucommerce.sign_up.SignUpViewModel
import com.wenubey.wenucommerce.verify_email.VerifyEmailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::SignUpViewModel)
    viewModelOf(::SignInViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::VerifyEmailViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModel {
        TabViewModel(
            authRepository = get(),
            notificationPreferences = get(),
            dispatcherProvider = get(),
            savedStateHandle = get()
        )
    }

}