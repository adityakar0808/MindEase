package com.example.mindease.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mindease.call.CallViewModel
import com.example.mindease.data.models.User
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun InboxScreen(
    currentUser: User,
    callViewModel: CallViewModel, // ✅ shared CallViewModel
    modifier: Modifier = Modifier,
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    onReturnToCall: (() -> Unit)? = null
) {
    val ongoingCallSessionId by callViewModel.ongoingCallSession.collectAsState()
    var connectedUsers by remember { mutableStateOf(listOf<User>()) }

    // Listen for connected users in active chat sessions
    LaunchedEffect(Unit) {
        db.collection("call_sessions")
            .whereEqualTo("status", "connected")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let { docs ->
                    val userIds = docs.documents.flatMap { doc ->
                        listOfNotNull(
                            if (doc.getString("userA_uid") != currentUser.uid) doc.getString("userA_uid") else null,
                            if (doc.getString("userB_uid") != currentUser.uid) doc.getString("userB_uid") else null
                        )
                    }.distinct()

                    if (userIds.isNotEmpty()) {
                        db.collection("users")
                            .whereIn("uid", userIds)
                            .get()
                            .addOnSuccessListener { userDocs ->
                                connectedUsers = userDocs.documents.map { u ->
                                    User(
                                        uid = u.id,
                                        name = u.getString("name") ?: "User",
                                        email = u.getString("email") ?: ""
                                    )
                                }
                            }
                    } else {
                        connectedUsers = emptyList()
                    }
                }
            }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Show return to call banner if there is an ongoing call
        ongoingCallSessionId?.let {
            Button(
                onClick = { onReturnToCall?.invoke() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Return to Call", color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Connected Users", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (connectedUsers.isEmpty()) {
            Text("No connected users yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn {
                items(connectedUsers) { user ->
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Nickname: ${user.name}", style = MaterialTheme.typography.titleMedium)
                            Text("Email: ${user.email}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
