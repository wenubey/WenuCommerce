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
import com.wenubey.wenucommerce.navigation.RootNavigationGraph
import com.wenubey.wenucommerce.screens.auth.AuthViewModel
import com.wenubey.wenucommerce.ui.theme.WenuCommerceTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController

    private val authViewModel: AuthViewModel by viewModel()


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoinContext {
                WenuCommerceTheme {
                    navController = rememberNavController()

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val startDestination = authViewModel.getStartDestination()
                        RootNavigationGraph(navController = navController, startDestination = startDestination)
                    }
                }
            }
        }
    }
}
