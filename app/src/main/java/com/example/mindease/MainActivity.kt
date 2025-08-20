package com.example.mindease

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.navigation.AppNavGraph

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            AppNavGraph(
                navController = navController,
                viewModel = authViewModel,
                modifier = Modifier
            )
        }
    }
}
