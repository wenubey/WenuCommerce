package com.wenubey.wenucommerce

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.wenubey.wenucommerce.navigation.Graph
import com.wenubey.wenucommerce.navigation.RootNavigationGraph
import com.wenubey.wenucommerce.ui.theme.WenuCommerceTheme

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WenuCommerceTheme {
               navController = rememberNavController()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isAuthenticated = false
                    val startDestination = if (isAuthenticated) {
                        Graph.TabGraph
                    } else {
                        Graph.AuthGraph
                    }
                    RootNavigationGraph(navController = navController, startDestination = startDestination)
                }
            }
        }
    }
}
