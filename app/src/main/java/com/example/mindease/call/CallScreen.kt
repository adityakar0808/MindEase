package com.example.mindease.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log

@Composable
fun CallScreen(
    callViewModel: CallViewModel,
    modifier: Modifier = Modifier,
    onChatStart: () -> Unit
) {
    // Modern StateFlow collection with lifecycle awareness
    val isMicEnabled by callViewModel.isMicEnabled.collectAsState()
    val chatConnected by callViewModel.chatConnected.collectAsState()
    val chatRequestSent by callViewModel.chatRequestSent.collectAsState()
    val connectionStatus by callViewModel.connectionStatus.collectAsState()
    val chatConsentRequested by callViewModel.chatConsentRequested.collectAsState()
    val chatConsentGranted by callViewModel.chatConsentGranted.collectAsState()
    val bothUsersConsented by callViewModel.bothUsersConsented.collectAsState()

    // Modern animation approach with updated APIs
    val infiniteTransition = rememberInfiniteTransition(label = "call_animation")
    val pulsing by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Modern UI composable structure
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Header with status - Modern Card composable
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Voice Call",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Modern functional approach to determine display status
                    val displayStatus = remember(chatConnected, connectionStatus) {
                        when {
                            chatConnected -> "Connected - Call Active" to Color(0xFF27AE60)
                            connectionStatus == "Connected" -> "Connected!" to Color(0xFF27AE60)
                            connectionStatus == "Failed" -> "Connection failed" to Color(0xFFE74C3C)
                            connectionStatus == "Connecting" -> "Establishing connection..." to Color(0xFFF1C40F)
                            else -> "Preparing call..." to Color(0xFF95A5A6)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(displayStatus.second)
                                .alpha(pulsing)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = displayStatus.first,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = displayStatus.second,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Main call indicator with modern composable structure
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    chatConnected -> {
                        CallActiveIndicator(
                            scale = scale,
                            pulsing = pulsing
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        CallActiveCard()

                        Spacer(modifier = Modifier.height(20.dp))

                        ChatConsentSection(
                            bothUsersConsented = bothUsersConsented,
                            chatConsentGranted = chatConsentGranted,
                            chatConsentRequested = chatConsentRequested,
                            onRequestConsent = { callViewModel.requestChatConsent() },
                            onAcceptConsent = { callViewModel.acceptChatConsent() }
                        )
                    }

                    chatRequestSent -> {
                        ConnectingIndicator(
                            scale = scale,
                            connectionStatus = connectionStatus
                        )
                    }

                    else -> {
                        PreparingIndicator(pulsing = pulsing)
                    }
                }
            }

            // Controls section - Modern composable extraction
            CallControls(
                isMicEnabled = isMicEnabled,
                chatConnected = chatConnected,
                onToggleMic = { callViewModel.toggleMic() },
                onEndCall = { callViewModel.endCall() }
            )
        }
    }
}

// Modern extracted composables for better composition
@Composable
private fun CallActiveIndicator(
    scale: Float,
    pulsing: Float
) {
    Card(
        modifier = Modifier
            .size(180.dp)
            .scale(scale)
            .alpha(pulsing),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF27AE60).copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(90.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.size(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF27AE60).copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(50.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF27AE60),
                                        Color(0xFF2ECC71)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Voice call active",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallActiveCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "Voice call is active\nSpeak freely with your peer",
            modifier = Modifier.padding(20.dp),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

// UPDATED: Enhanced ChatConsentSection with better state management and debug logging
@Composable
private fun ChatConsentSection(
    bothUsersConsented: Boolean,
    chatConsentGranted: Boolean,
    chatConsentRequested: Boolean,
    onRequestConsent: () -> Unit,
    onAcceptConsent: () -> Unit
) {
    when {
        bothUsersConsented -> {
            // DEBUG: Add logging when both users consent
            LaunchedEffect(Unit) {
                Log.d("CallScreen", "Both users consented - chat should be available now")
            }

            // Both users have consented - show success state
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF27AE60).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        tint = Color(0xFF27AE60),
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Chat Connected!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF27AE60),
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "You can now message each other privately",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        chatConsentGranted && !bothUsersConsented -> {
            // Current user has granted consent, waiting for peer
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF39C12).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFFF39C12),
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Waiting for peer to accept...",
                        color = Color(0xFFF39C12),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        chatConsentRequested && !chatConsentGranted -> {
            // Peer has requested, current user needs to accept
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3498DB).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Your peer wants to connect for chat!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF3498DB),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onAcceptConsent,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF27AE60)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Accept Chat")
                    }
                }
            }
        }

        else -> {
            // Default state - show connect button
            Button(
                onClick = onRequestConsent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3498DB).copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Connect to Chat")
            }
        }
    }
}

@Composable
private fun ConnectingIndicator(
    scale: Float,
    connectionStatus: String
) {
    Card(
        modifier = Modifier
            .size(180.dp)
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF1C40F).copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(90.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.size(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF1C40F).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(50.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFF1C40F),
                                        Color(0xFFF39C12)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Connecting",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Establishing secure voice connection...",
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This may take a few seconds.",
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PreparingIndicator(pulsing: Float) {
    Card(
        modifier = Modifier
            .size(180.dp)
            .alpha(pulsing),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF95A5A6).copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(90.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.size(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF95A5A6).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(50.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF95A5A6),
                                        Color(0xFF7F8C8D)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Preparing",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = "Getting ready...",
        color = Color.White.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun CallControls(
    isMicEnabled: Boolean,
    chatConnected: Boolean,
    onToggleMic: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Microphone toggle button
            Card(
                modifier = Modifier.size(80.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMicEnabled)
                        Color(0xFF4A90E2).copy(alpha = 0.8f)
                    else
                        Color(0xFFE74C3C).copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(40.dp)
            ) {
                IconButton(
                    onClick = onToggleMic,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = if (isMicEnabled) "Mute Microphone" else "Unmute Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // End call button
            Card(
                modifier = Modifier.size(80.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE74C3C).copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(40.dp)
            ) {
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Filled.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Status cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isMicEnabled)
                        Color(0xFF27AE60).copy(alpha = 0.2f)
                    else
                        Color(0xFFE74C3C).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isMicEnabled) "🎤 Microphone: On" else "🔇 Microphone: Off",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (isMicEnabled) Color(0xFF27AE60) else Color(0xFFE74C3C),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            if (chatConnected) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF27AE60).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Call quality: Good",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFF27AE60),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
