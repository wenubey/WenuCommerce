package com.wenubey.wenucommerce.sign_up

sealed interface SignUpAction {
    data class OnEmailChange(val email: String): SignUpAction
    data class OnPasswordChange(val password: String): SignUpAction
    data object OnSignUpEmailPassword: SignUpAction
    data object OnSignUpClicked: SignUpAction
    data object OnToggleCredentials: SignUpAction
}