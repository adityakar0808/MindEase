package com.example.mindease.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.data.models.User
import com.google.firebase.firestore.FirebaseFirestore

data class WaitingUser(
    val uid: String = "",
    val nickname: String = "",
    val stressReason: String = ""
)

@Composable
fun HomeScreen(
    user: User? = null,
    viewModel: AuthViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onStartChat: (chatId: String) -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val db = FirebaseFirestore.getInstance()
    var stressReason by remember { mutableStateOf("") }
    var waitingUsers by remember { mutableStateOf(listOf<WaitingUser>()) }

    // Listen to waiting users only if currentUser is not null
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            db.collection("waiting_users")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        waitingUsers = snapshot.documents
                            .filter { it.id != user.uid } // Exclude self
                            .map {
                                WaitingUser(
                                    uid = it.id,
                                    nickname = it.getString("nickname") ?: "User",
                                    stressReason = it.getString("stressReason") ?: ""
                                )
                            }
                    }
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Welcome, ${currentUser?.name ?: "User"}",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = stressReason,
            onValueChange = { stressReason = it },
            label = { Text("Enter your stress reason") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Users waiting to connect:", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(waitingUsers) { waitingUser ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Nickname: ${waitingUser.nickname}")
                            Text("Stress: ${waitingUser.stressReason}")
                        }
                        Button(onClick = {
                            currentUser?.let { self ->
                                val session = hashMapOf(
                                    "userA_uid" to self.uid,
                                    "userB_uid" to waitingUser.uid,
                                    "consentA" to false,
                                    "consentB" to false,
                                    "status" to "pending"
                                )
                                db.collection("call_sessions")
                                    .add(session)
                                    .addOnSuccessListener { docRef ->
                                        println("Call session created: ${docRef.id}")
                                        // Chat will only start when both consent
                                    }
                            }
                        }) {
                            Text("Connect")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                currentUser?.let { self ->
                    db.collection("waiting_users").document(self.uid)
                        .set(
                            mapOf(
                                "nickname" to self.name,
                                "stressReason" to stressReason
                            )
                        )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wait for someone")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                // Placeholder for AI Friend
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect with AI Friend (Coming Soon)")
        }
    }
}
