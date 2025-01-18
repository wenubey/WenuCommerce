package com.wenubey.wenucommerce.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.wenubey.wenucommerce.screens.cart.CartScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.cartNavGraph(navController: NavController) {
    navigation<Graph.CartGraph>(startDestination = CartScreens.Cart) {
        composable<CartScreens.Cart> {
            CartScreen()
        }
    }
}

@Serializable
sealed class CartScreens {
    @Serializable
    data object Cart : CartScreens()
}