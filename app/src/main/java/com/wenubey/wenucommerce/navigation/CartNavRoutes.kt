package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.wenubey.wenucommerce.screens.cart.CartScreen

fun NavGraphBuilder.cartNavRoutes(navController: NavController) {
    composable<Cart> {
        CartScreen()
    }
}
