package com.example.mindease.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mindease.auth.AuthViewModel

@Composable
fun HomeScreen(viewModel: AuthViewModel, onLogout: () -> Unit) {
    val user = viewModel.currentUser.collectAsState().value

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Welcome, ${user?.name ?: user?.email ?: "Guest"}",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(20.dp))

        Button(onClick = {
            viewModel.logout()
            onLogout()
        }) {
            Text("Logout")
        }
    }
}
