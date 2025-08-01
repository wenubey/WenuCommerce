package com.wenubey.wenucommerce.onboard.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.vector.ImageVector
import com.wenubey.domain.model.user.UserRole

data class UserRoleUiModel(
    val name: String,
    val icon: ImageVector,
    val description: String,
) {
    companion object {
        fun default(): UserRoleUiModel {
            return UserRoleUiModel(
                name = "Customer",
                icon = Icons.Default.Person,
                description = "Browse and purchase products"
            )
        }

        // Only return user-selectable roles (no ADMIN)
        fun getSelectableRoles(): List<UserRoleUiModel> {
            return listOf(
                UserRole.CUSTOMER.toUiModel(),
                UserRole.SELLER.toUiModel()
            )
        }
    }
}

fun UserRole.toUiModel(): UserRoleUiModel = when (this) {
    UserRole.CUSTOMER -> UserRoleUiModel(
        name = "Customer",
        icon = Icons.Default.Person,
        description = "Browse and purchase products"
    )
    UserRole.SELLER -> UserRoleUiModel(
        name = "Seller",
        icon = Icons.Default.Store,
        description = "Sell products and manage inventory"
    )
    UserRole.ADMIN -> UserRoleUiModel(
        name = "Admin",
        icon = Icons.Default.AdminPanelSettings,
        description = "Platform administrator"
    )
}

fun UserRoleUiModel.toDomainModel(): UserRole = when(this.name) {
    "Customer" -> UserRole.CUSTOMER
    "Seller" -> UserRole.SELLER
    "Admin" -> UserRole.ADMIN
    else -> UserRole.CUSTOMER
}