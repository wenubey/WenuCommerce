package com.wenubey.wenucommerce.sign_in

sealed interface SignInAction {
    data class OnEmailChange(val email: String): SignInAction
    data class OnPasswordChange(val password: String): SignInAction
    data object OnSignInClicked: SignInAction
    data object OnSignWithEmailPassword: SignInAction
    data object OnToggleCredentials: SignInAction
}