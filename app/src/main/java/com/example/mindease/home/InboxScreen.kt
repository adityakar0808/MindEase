package com.example.mindease.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mindease.data.models.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class ChatMessage(
    val senderUid: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun InboxScreen(
    currentUser: User,
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    modifier: Modifier = Modifier,
) {
    var activeChats by remember { mutableStateOf(listOf<String>()) }
    var selectedChatId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }

    var chatListener: ListenerRegistration? by remember { mutableStateOf(null) }

    // Listen to call_sessions where both consented and status is "connected"
    LaunchedEffect(currentUser) {
        db.collection("call_sessions")
            .whereEqualTo("status", "connected")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    activeChats = it.documents
                        .filter { doc ->
                            doc.getString("userA_uid") == currentUser.uid ||
                                    doc.getString("userB_uid") == currentUser.uid
                        }
                        .map { doc -> doc.id }
                }
            }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

        Text("Active Chats", style = MaterialTheme.typography.headlineSmall)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(activeChats) { chatId ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Chat: $chatId")
                        Button(onClick = {
                            selectedChatId = chatId

                            // Listen to chat messages for this chat
                            chatListener?.remove()
                            chatListener = db.collection("chats")
                                .document(chatId)
                                .collection("messages")
                                .orderBy("timestamp")
                                .addSnapshotListener { snapshot, _ ->
                                    messages = snapshot?.documents?.map { doc ->
                                        doc.toObject(ChatMessage::class.java)!!
                                    } ?: listOf()
                                }
                        }) {
                            Text("Open")
                        }
                    }
                }
            }
        }

        selectedChatId?.let { chatId ->
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(messages) { msg ->
                    Text("${msg.senderUid}: ${msg.text}")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                if (inputText.isNotBlank()) {
                    val msg = ChatMessage(senderUid = currentUser.uid, text = inputText)
                    db.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .add(msg)
                    inputText = ""
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Send")
            }
        }
    }
}
