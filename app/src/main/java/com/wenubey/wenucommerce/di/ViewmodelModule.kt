package com.wenubey.wenucommerce.di

import com.wenubey.wenucommerce.AuthViewModel
import com.wenubey.wenucommerce.admin.AdminBadgeViewModel
import com.wenubey.wenucommerce.core.connectivity.ConnectivityViewModel
import com.wenubey.wenucommerce.admin.admin_seller_approval.AdminApprovalViewModel
import com.wenubey.wenucommerce.onboard.OnboardingViewModel
import com.wenubey.wenucommerce.core.email_verification_banner.EmailVerificationBannerViewModel
import com.wenubey.wenucommerce.admin.admin_categories.AdminCategoryViewModel
import com.wenubey.wenucommerce.admin.admin_products.AdminProductModerationViewModel
import com.wenubey.wenucommerce.admin.admin_products.AdminProductSearchViewModel
import com.wenubey.wenucommerce.customer.customer_home.CustomerHomeViewModel
import com.wenubey.wenucommerce.customer.customer_products.CustomerProductDetailViewModel
import com.wenubey.wenucommerce.seller.seller_categories.SellerCategoryViewModel
import com.wenubey.wenucommerce.seller.seller_products.SellerProductCreateViewModel
import com.wenubey.wenucommerce.seller.seller_products.SellerProductEditViewModel
import com.wenubey.wenucommerce.seller.seller_products.SellerProductListViewModel
import com.wenubey.wenucommerce.seller.seller_storefront.SellerStorefrontViewModel
import com.wenubey.wenucommerce.seller.seller_dashboard.SellerDashboardViewModel
import com.wenubey.wenucommerce.seller.seller_verification.SellerVerificationViewModel
import com.wenubey.wenucommerce.sign_in.SignInViewModel
import com.wenubey.wenucommerce.sign_up.SignUpViewModel
import com.wenubey.wenucommerce.verify_email.VerifyEmailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::ConnectivityViewModel)
    viewModelOf(::SignUpViewModel)
    viewModelOf(::SignInViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::VerifyEmailViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::AdminApprovalViewModel)
    viewModelOf(::SellerDashboardViewModel)
    viewModelOf(::SellerVerificationViewModel)
    viewModelOf(::AdminBadgeViewModel)
    viewModelOf(::AdminCategoryViewModel)
    viewModelOf(::SellerCategoryViewModel)
    viewModelOf(::CustomerHomeViewModel)
    // Product ViewModels
    viewModelOf(::SellerProductListViewModel)
    viewModelOf(::SellerProductCreateViewModel)
    viewModelOf(::SellerProductEditViewModel)
    viewModelOf(::AdminProductModerationViewModel)
    viewModelOf(::AdminProductSearchViewModel)
    viewModelOf(::CustomerProductDetailViewModel)
    viewModelOf(::SellerStorefrontViewModel)
    viewModel {
        EmailVerificationBannerViewModel(
            authRepository = get(),
            notificationPreferences = get(),
            dispatcherProvider = get(),
        )
    }

}