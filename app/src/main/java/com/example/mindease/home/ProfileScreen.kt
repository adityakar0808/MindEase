package com.example.mindease.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.data.models.User
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun ProfileScreen(
    user: User,
    viewModel: AuthViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    var nickname by remember { mutableStateOf(user.name ?: "") }


    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Nickname") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            db.collection("users").document(user.uid)
                .update("name", nickname)
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Save Nickname")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Email: ${user.email}")

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }
    }
}
