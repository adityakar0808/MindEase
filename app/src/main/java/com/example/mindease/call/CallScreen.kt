package com.example.mindease.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
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

@Composable
fun CallScreen(
    callViewModel: CallViewModel,
    modifier: Modifier = Modifier,
    onChatStart: () -> Unit
) {
    val isMicEnabled by callViewModel.isMicEnabled.collectAsState()
    val chatConnected by callViewModel.chatConnected.collectAsState()
    val chatRequestSent by callViewModel.chatRequestSent.collectAsState()
    val connectionStatus by callViewModel.connectionStatus.collectAsState()

    // Auto-request chat when call screen appears
    LaunchedEffect(Unit) {
        callViewModel.requestChat()
    }

    // Animations for the call indicator
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

    // Rotating animation for connecting state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

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
            // Header with status
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

                    // Connection status with styled indicators
                    when {
                        chatConnected -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF27AE60))
                                        .alpha(pulsing)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connected - Call Active",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF27AE60),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        chatRequestSent -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            when (connectionStatus) {
                                                "Connected" -> Color(0xFF27AE60)
                                                "Failed" -> Color(0xFFE74C3C)
                                                else -> Color(0xFFF1C40F)
                                            }
                                        )
                                        .alpha(pulsing)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (connectionStatus) {
                                        "Connecting" -> "Establishing connection..."
                                        "Connected" -> "Connected!"
                                        "Failed" -> "Connection failed"
                                        else -> "Connecting to peer..."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = when (connectionStatus) {
                                        "Connected" -> Color(0xFF27AE60)
                                        "Failed" -> Color(0xFFE74C3C)
                                        else -> Color(0xFFF1C40F)
                                    }
                                )
                            }
                        }

                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF95A5A6))
                                        .alpha(pulsing)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Preparing call...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF95A5A6)
                                )
                            }
                        }
                    }
                }
            }

            // Main call indicator with animations
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    chatConnected -> {
                        // Active call indicator
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

                        Spacer(modifier = Modifier.height(20.dp))

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

                    chatRequestSent -> {
                        // Connecting indicator
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

                    else -> {
                        // Preparing indicator
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
                }
            }

            // Controls section
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
                            onClick = { callViewModel.toggleMic() },
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
                            onClick = {
                                callViewModel.endCall()
                            },
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
    }
}