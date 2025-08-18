package com.example.mindease.auth

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.example.mindease.R

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    googleSignInLauncher: ActivityResultLauncher<Intent>,
    activity: Activity,
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit
) {
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.error.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Login", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    authViewModel.login(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Login with Email")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { authViewModel.loginAnonymously() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Login as Guest")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(activity.getString(R.string.default_web_client_id)) // from google-services.json
                    .requestEmail()
                    .build()
                val googleSignInClient = GoogleSignIn.getClient(activity, gso)
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Login with Google")
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onNavigateToSignup) {
            Text("Don’t have an account? Sign up")
        }

        if (isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
