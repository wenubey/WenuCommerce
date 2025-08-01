package com.wenubey.domain.model.user

// TODO Use in the screens
data class AdminDashboardData(
    val totalUsers: Int = 0,
    val totalCustomers: Int = 0,
    val totalSellers: Int = 0,
    val totalSales: Double = 0.0,
    val totalOrders: Int = 0,
    val todaySales: Double = 0.0,
    val todayOrders: Int = 0,
    val recentUsers: List<User> = emptyList()
)

