package com.example.mindease.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mindease.call.CallViewModel
import com.example.mindease.chat.LocalChatViewModel
import com.example.mindease.chat.LocalChatScreen
import com.example.mindease.data.local.LocalConversation
import com.example.mindease.data.models.User
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun InboxScreen(
    currentUser: User,
    callViewModel: CallViewModel,
    localChatViewModel: LocalChatViewModel, // Accept as parameter instead of creating new one
    modifier: Modifier = Modifier,
    onReturnToCall: (() -> Unit)? = null
) {
    val db = remember { FirebaseFirestore.getInstance() }

    // Use the passed localChatViewModel instead of creating a new one
    val conversations by localChatViewModel.getAllConversations().collectAsState(initial = emptyList())
    var selectedConversation by remember { mutableStateOf<LocalConversation?>(null) }
    val scrollState = rememberScrollState()
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    // CRITICAL: Listen to Firestore chat_rooms collection for real-time updates
    DisposableEffect(currentUser.uid) {
        Log.d("InboxScreen", "Setting up Firestore listener for user: ${currentUser.uid}")

        val listener = db.collection("chat_rooms")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("InboxScreen", "Error listening to chat rooms", error)
                    return@addSnapshotListener
                }

                snapshot?.documents?.forEach { doc ->
                    val userA = doc.getString("userA_uid")
                    val userB = doc.getString("userB_uid")
                    val sessionId = doc.id

                    Log.d("InboxScreen", "Found chat room: $sessionId, userA: $userA, userB: $userB")

                    // Check if current user is part of this chat room
                    if (userA == currentUser.uid || userB == currentUser.uid) {
                        Log.d("InboxScreen", "Current user is part of chat room: $sessionId")

                        // Force refresh of LocalChatViewModel
                        localChatViewModel.refreshChatRooms()
                    }
                }
            }

        onDispose {
            Log.d("InboxScreen", "Removing Firestore listener")
            listener.remove()
        }
    }

    if (selectedConversation == null) {
        // Show conversations list
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerLowest,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Private Chats",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Your secure conversations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Privacy notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔒", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "All chats are stored locally on your device only. They are never uploaded to any server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show conversations
            if (conversations.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "💬",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "No chats yet",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Start a voice call and exchange private messages to see them here",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your Conversations",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            "${conversations.size} ${if (conversations.size == 1) "chat" else "chats"}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    conversations.forEach { conversation ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedConversation = conversation },
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(
                                                    Brush.radialGradient(
                                                        colors = listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                conversation.peerName,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                conversation.lastMessage ?: "No messages yet",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        IconButton(
                                            onClick = {
                                                // Use the shared localChatViewModel
                                                localChatViewModel.deleteConversation(conversation.sessionId)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete conversation",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Text(
                                            sdf.format(Date(conversation.lastMessageTime ?: 0)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )

                                        if (conversation.totalMessages > 0) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    "${conversation.totalMessages}",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Show chat screen for selected conversation
        LocalChatScreen(
            peerName = selectedConversation!!.peerName,
            chatViewModel = localChatViewModel, // Use shared instance
            onBackPressed = { selectedConversation = null },
            modifier = modifier
        )

        // Initialize offline chat when opening conversation
        LaunchedEffect(selectedConversation) {
            if (selectedConversation != null) {
                localChatViewModel.initializeOfflineChat(
                    sessionId = selectedConversation!!.sessionId,
                    peerName = selectedConversation!!.peerName,
                    peerUid = selectedConversation!!.peerUid
                )
                localChatViewModel.loadMessagesForSession(selectedConversation!!.sessionId)
            }
        }
    }
}
