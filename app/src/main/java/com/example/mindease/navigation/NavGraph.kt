package com.example.mindease.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.auth.LoginScreen
import com.example.mindease.auth.SignupScreen
import com.example.mindease.call.CallViewModel
import com.example.mindease.call.CallViewModelFactory
import com.example.mindease.home.HomeBottomNav
import com.example.mindease.splash.SplashScreen
import com.google.firebase.firestore.FirebaseFirestore

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
    val startDestination = Screen.Splash.route

    // Use factory to pass context and Firestore
    val context = LocalContext.current
    val callViewModel: CallViewModel = viewModel(
        factory = CallViewModelFactory(context, FirebaseFirestore.getInstance())
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

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

        composable(Screen.Signup.route) {
            SignupScreen(
                viewModel = viewModel,
                onNavigateToLogin = { navController.popBackStack() },
                onSignupSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Signup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            currentUser?.let { user ->
                HomeBottomNav(
                    user = user,
                    viewModel = viewModel,
                    navController = navController,
                    onLogout = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    callViewModel = callViewModel
                )
            }
        }
    }
}