package com.wenubey.data.util

import com.wenubey.data.BuildConfig
import com.wenubey.domain.model.user.UserRole

// TODO refactor this for adding more admins
object AdminUtils {
    // Replace with your actual admin email
    private const val ADMIN_EMAIL = BuildConfig.ADMIN_EMAIL

    fun isAdminUser(email: String?): Boolean {
        return email?.lowercase() == ADMIN_EMAIL.lowercase()
    }

    fun shouldShowAdminFeatures(userRole: UserRole?, email: String?): Boolean {
        return userRole == UserRole.ADMIN || isAdminUser(email)
    }
}