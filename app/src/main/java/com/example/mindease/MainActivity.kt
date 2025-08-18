package com.example.mindease

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.navigation.AppNavGraph
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    // Google Sign-In launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                authViewModel.firebaseAuthWithGoogle(account)
            }
        } catch (e: ApiException) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            AppNavGraph(
                navController = navController,
                viewModel = authViewModel,
                googleSignInLauncher = googleSignInLauncher,
                activity = this
            )
        }
    }
}
