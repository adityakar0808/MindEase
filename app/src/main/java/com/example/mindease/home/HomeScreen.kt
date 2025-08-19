package com.example.mindease.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mindease.auth.AuthViewModel

@Composable
fun HomeScreen(viewModel: AuthViewModel, onLogout: () -> Unit) {
    val user = viewModel.currentUser.collectAsState().value
    var newName by remember { mutableStateOf(user?.name ?: "") }
    val isLoading = viewModel.isLoading.collectAsState().value
    val error = viewModel.error.collectAsState().value
    val successMessage = viewModel.successMessage.collectAsState().value

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error or success in Snackbar
    LaunchedEffect(error, successMessage) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(padding)
        ) {
            Text(
                text = "Welcome, ${user?.name ?: user?.email ?: "Guest"}",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(20.dp))

            // --------- Edit Name Field ----------
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Update Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    if (newName.isNotBlank() && user != null) {
                        viewModel.updateUserName(user.uid, newName)
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save Name")
                }
            }

            Spacer(Modifier.height(30.dp))

            Button(onClick = {
                viewModel.logout()
                onLogout()
            }) {
                Text("Logout")
            }
        }
    }
}
