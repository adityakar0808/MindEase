package com.example.mindease.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.call.CallViewModel
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
    viewModel: AuthViewModel,
    callViewModel: CallViewModel, // ✅ Use the shared CallViewModel from HomeBottomNav
    modifier: Modifier = Modifier,
    navToCallScreen: (sessionId: String) -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val db = FirebaseFirestore.getInstance()
    var stressReason by remember { mutableStateOf("") }
    var waitingUsers by remember { mutableStateOf(listOf<WaitingUser>()) }

    LaunchedEffect(currentUser) {
        currentUser?.let { self ->
            db.collection("waiting_users")
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.let {
                        waitingUsers = it.documents
                            .filter { doc -> doc.id != self.uid }
                            .map { doc ->
                                WaitingUser(
                                    uid = doc.id,
                                    nickname = doc.getString("nickname") ?: "User",
                                    stressReason = doc.getString("stressReason") ?: ""
                                )
                            }
                    }
                }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Welcome, ${currentUser?.name ?: "User"}", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = stressReason,
            onValueChange = { stressReason = it },
            label = { Text("Enter your stress reason") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Text("Users waiting to connect:", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(waitingUsers) { waitingUser ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Nickname: ${waitingUser.nickname}")
                            Text("Stress: ${waitingUser.stressReason}")
                        }
                        Button(onClick = {
                            currentUser?.let { self ->
                                val session = hashMapOf(
                                    "userA_uid" to self.uid,
                                    "userB_uid" to waitingUser.uid,
                                    "consentChatA" to false,
                                    "consentChatB" to false,
                                    "status" to "ringing"
                                )
                                db.collection("call_sessions")
                                    .add(session)
                                    .addOnSuccessListener { docRef ->
                                        // Use the shared CallViewModel
                                        callViewModel.startCall(docRef.id, currentUserUid = self.uid)
                                        navToCallScreen(docRef.id)
                                    }
                            }
                        }) {
                            Text("Connect")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                currentUser?.let { self ->
                    db.collection("waiting_users").document(self.uid)
                        .set(
                            mapOf(
                                "nickname" to (self.name ?: "User"),
                                "stressReason" to stressReason
                            )
                        )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wait for someone")
        }
    }
}
