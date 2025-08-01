package com.wenubey.domain.model.user

// TODO Use in the screens
data class UserManagementData(
    val users: List<User> = emptyList(),
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val bannedUsers: Int = 0
)
