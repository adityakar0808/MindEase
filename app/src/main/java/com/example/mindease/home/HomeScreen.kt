package com.example.mindease.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.call.CallViewModel
import com.example.mindease.data.models.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class WaitingUser(
    val uid: String,
    val nickname: String,
    val stressReason: String,
    val timestamp: Timestamp?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: User,
    viewModel: AuthViewModel,
    callViewModel: CallViewModel,
    modifier: Modifier = Modifier,
    navToCallScreen: (sessionId: String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var stressReason by remember { mutableStateOf("") }
    var waitingUsers by remember { mutableStateOf(listOf<WaitingUser>()) }
    var isConnecting by remember { mutableStateOf(false) }

    val isWaiting by callViewModel.isWaiting.collectAsState(initial = false)

    val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    // Safe Firestore listener for waiting users
    DisposableEffect(user.uid) {
        val listener = db.collection("waiting_users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                snapshot?.let {
                    waitingUsers = it.documents
                        .filter { doc -> doc.id != user.uid }
                        .mapNotNull { doc ->
                            try {
                                WaitingUser(
                                    uid = doc.id,
                                    nickname = doc.getString("nickname") ?: "User",
                                    stressReason = doc.getString("stressReason") ?: "",
                                    timestamp = doc.getTimestamp("timestamp")
                                )
                            } catch (e: Exception) {
                                null // Skip malformed documents
                            }
                        }
                }
            }
        onDispose { listener.remove() }
    }

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
            .padding(20.dp)
    ) {
        // Header Section
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
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Welcome back,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = user.name ?: "User",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Only show stress reason input when not waiting
        if (!isWaiting) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "What's on your mind?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = stressReason,
                        onValueChange = { stressReason = it },
                        placeholder = {
                            Text(
                                "Share what you'd like to talk about...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (isWaiting) {
            // Modern waiting status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Circle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "You're waiting for someone to connect...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Your profile is visible to other users looking for a chat",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                    if (stressReason.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "\"$stressReason\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "People looking to connect",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (waitingUsers.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            "${waitingUsers.size} online",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (!isWaiting) {
            if (waitingUsers.isEmpty()) {
                // Modern empty state
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "🔍",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "No one is waiting right now",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Be the first to start waiting, or check back in a few minutes",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(waitingUsers) { waitingUser ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
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
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Circle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                waitingUser.nickname,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        if (waitingUser.stressReason.isNotBlank()) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    "\"${waitingUser.stressReason}\"",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Waiting since ${
                                                    waitingUser.timestamp?.toDate()?.let { sdf.format(it) } ?: "Unknown"
                                                }",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            if (!isConnecting) {
                                                isConnecting = true
                                                // Find and join the session
                                                db.collection("call_sessions")
                                                    .whereEqualTo("userA_uid", waitingUser.uid)
                                                    .whereEqualTo("status", "waiting")
                                                    .get()
                                                    .addOnSuccessListener { query ->
                                                        if (!query.isEmpty) {
                                                            val sessionDoc = query.documents.first()
                                                            val sessionId = sessionDoc.id
                                                            db.collection("call_sessions").document(sessionId)
                                                                .update(
                                                                    mapOf(
                                                                        "userB_uid" to user.uid,
                                                                        "status" to "ringing"
                                                                    )
                                                                )
                                                                .addOnSuccessListener {
                                                                    navToCallScreen(sessionId)
                                                                    isConnecting = false
                                                                }
                                                                .addOnFailureListener {
                                                                    isConnecting = false
                                                                }
                                                        } else {
                                                            isConnecting = false
                                                        }
                                                    }
                                                    .addOnFailureListener {
                                                        isConnecting = false
                                                    }
                                            }
                                        },
                                        enabled = !isConnecting,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text(
                                            if (isConnecting) "Connecting..." else "Connect",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // Modern action button
        if (!isWaiting) {
            Button(
                onClick = {
                    if (stressReason.isNotBlank()) {
                        callViewModel.startWaiting(
                            uid = user.uid,
                            nickname = user.name ?: "User",
                            stressReason = stressReason
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = stressReason.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (stressReason.isBlank())
                        "Enter a topic to start waiting"
                    else
                        "Start Waiting for Connection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Button(
                onClick = { callViewModel.cancelWaiting(user.uid) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    "Cancel Waiting",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}