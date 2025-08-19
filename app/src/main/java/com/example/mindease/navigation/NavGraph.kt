package com.example.mindease.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mindease.auth.LoginScreen
import com.example.mindease.auth.SignupScreen
import com.example.mindease.home.HomeScreen
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.splash.SplashScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Home : Screen("home")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val startDestination = if (currentUser != null) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        // Splash screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    if (currentUser == null) {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
            )
        }
        // Login screen
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignup = { navController.navigate(Screen.Signup.route) }
            )
        }

        // Signup screen
        composable(Screen.Signup.route) {
            SignupScreen(
                viewModel = viewModel,
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        // Home screen
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
