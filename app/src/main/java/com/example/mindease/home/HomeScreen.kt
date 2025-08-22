package com.example.mindease.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mindease.ai.AIChatScreen
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.call.CallViewModel
import com.example.mindease.call.WaitingUser
import com.example.mindease.chat.LocalChatViewModel
import com.example.mindease.data.models.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Test Firebase connection function
private fun testFirebaseConnection(db: FirebaseFirestore, userId: String) {
    Log.d("FirebaseTest", "Testing Firebase connection...")
    // Test write
    db.collection("test").document("connection")
        .set(mapOf("userId" to userId, "timestamp" to System.currentTimeMillis()))
        .addOnSuccessListener {
            Log.d("FirebaseTest", "✅ Firebase write successful")
        }
        .addOnFailureListener { e ->
            Log.e("FirebaseTest", "❌ Firebase write failed", e)
        }

    // Test read
    db.collection("waiting_users")
        .get()
        .addOnSuccessListener { querySnapshot ->
            Log.d("FirebaseTest", "✅ Firebase read successful. Found ${querySnapshot.size()} waiting users")
            querySnapshot.documents.forEach { doc ->
                Log.d("FirebaseTest", "Waiting user: ${doc.id} -> ${doc.data}")
            }
        }
        .addOnFailureListener { e ->
            Log.e("FirebaseTest", "❌ Firebase read failed", e)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: User,
    viewModel: AuthViewModel,
    callViewModel: CallViewModel,
    localChatViewModel: LocalChatViewModel, // PRESERVED: Keep this parameter
    modifier: Modifier = Modifier,
    navToCallScreen: (sessionId: String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scrollState = rememberScrollState()
    var stressReason by remember { mutableStateOf("") }
    var waitingUsers by remember { mutableStateOf<List<WaitingUser>>(listOf()) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectingToUserId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isWaiting by callViewModel.isWaiting.collectAsState(initial = false)
    val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    // NEW: State for AI Chat
    var showAIChat by remember { mutableStateOf(false) }

    // Clear error message after 5 seconds
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            delay(5000)
            errorMessage = null
        }
    }

    // Test Firebase connection
    LaunchedEffect(user.uid) {
        testFirebaseConnection(db, user.uid)
    }

    // PRESERVED: Keep your original working Firestore listener exactly as it was
    DisposableEffect(user.uid) {
        Log.d("HomeScreen", "Setting up listener for waiting users")
        val listener = db.collection("waiting_users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HomeScreen", "Error listening for waiting users", error)
                    errorMessage = "Error loading users: ${error.message}"
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    Log.d("HomeScreen", "Received ${querySnapshot.documents.size} waiting users")
                    waitingUsers = querySnapshot.documents
                        .filter { doc ->
                            val docId = doc.id
                            val isCurrentUser = docId == user.uid
                            Log.d("HomeScreen", "Processing doc: $docId, isCurrentUser: $isCurrentUser")
                            !isCurrentUser
                        }
                        .mapNotNull { doc ->
                            try {
                                val waitingUser = WaitingUser(
                                    uid = doc.id,
                                    nickname = doc.getString("nickname") ?: "User",
                                    stressReason = doc.getString("stressReason") ?: "",
                                    timestamp = doc.getTimestamp("timestamp")
                                )
                                Log.d("HomeScreen", "Created waiting user: ${waitingUser.nickname}")
                                waitingUser
                            } catch (e: Exception) {
                                Log.e("HomeScreen", "Error parsing waiting user doc: ${doc.id}", e)
                                null
                            }
                        }
                    Log.d("HomeScreen", "Final waiting users count: ${waitingUsers.size}")
                }
            }
        onDispose {
            Log.d("HomeScreen", "Removing waiting users listener")
            listener.remove()
        }
    }

    // NEW: Show AI Chat if selected
    if (showAIChat) {
        AIChatScreen(
            onBackPressed = { showAIChat = false },
            modifier = Modifier.fillMaxSize()
        )
        return
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
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // Error message display
        errorMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

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

        // NEW: AI Friend Quick Access Button (always visible)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AI Friend",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Chat with your AI companion anytime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { showAIChat = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        "Chat",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

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

            if (!isWaiting) {
                if (waitingUsers.isEmpty()) {
                    // Modern empty state
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
                    // Display waiting users
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        waitingUsers.forEach { waitingUser ->
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
                                                    // PRESERVED: Use the working connection method
                                                    callViewModel.connectToWaitingUser(
                                                        waitingUser = waitingUser,
                                                        currentUser = user,
                                                        onSuccess = { sessionId ->
                                                            navToCallScreen(sessionId)
                                                            isConnecting = false
                                                        },
                                                        onError = {
                                                            isConnecting = false
                                                            errorMessage = "Failed to connect to user"
                                                        }
                                                    )
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
            }
        }

        Spacer(Modifier.height(20.dp))

        // Modern action button
        if (!isWaiting) {
            Button(
                onClick = {
                    if (stressReason.isNotBlank() && !isConnecting) {
                        try {
                            callViewModel.startWaiting(
                                uid = user.uid,
                                nickname = user.name ?: "User",
                                stressReason = stressReason
                            )
                        } catch (e: Exception) {
                            Log.e("HomeScreen", "Error starting waiting", e)
                            errorMessage = "Failed to start waiting: ${e.message}"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = stressReason.isNotBlank() && !isConnecting,
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
                onClick = {
                    try {
                        callViewModel.cancelWaiting(user.uid)
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Error canceling waiting", e)
                        errorMessage = "Failed to cancel waiting: ${e.message}"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isConnecting,
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

        // Add bottom padding to prevent content cutoff
        Spacer(modifier = Modifier.height(20.dp))
    }
}