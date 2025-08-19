package com.example.mindease.auth

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mindease.R
import com.example.mindease.data.models.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = viewModel(),
    onLoginSuccess: (User) -> Unit,
    onNavigateToSignup: () -> Unit
) {
    val context = LocalContext.current

    val currentUser by viewModel.currentUser.collectAsState()
    val error by viewModel.error.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // ✅ Google Sign-In setup
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))

        // 🔥 replace with Web client ID from Firebase console
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    // ✅ Launcher for Google Sign-In result
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.let {
                    viewModel.firebaseAuthWithGoogle(it) // send account to ViewModel
                }
            } catch (e: ApiException) {
                Toast.makeText(context, "Google sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ✅ Handle Toasts for errors & success
    LaunchedEffect(error, successMessage) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    // ✅ Navigate if login success
    LaunchedEffect(currentUser) {
        currentUser?.let {
            if (!it.isAnonymous) {
                onLoginSuccess(it)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Login", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { viewModel.login(email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Logging in..." else "Login")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.loginAnonymously() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Continue as Guest")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ Google Sign-In button
            Button(
                onClick = {
                    val signInIntent: Intent = googleSignInClient.signInIntent
                    googleSignInLauncher.launch(signInIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Sign in with Google")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ Navigate to Signup
            TextButton(onClick = { onNavigateToSignup() }) {
                Text("Don't have an account? Sign up")
            }
        }
    }
}
