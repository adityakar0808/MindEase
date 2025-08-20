package com.example.mindease.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Voice Call",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Connection status
                when {
                    chatConnected -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        Color.Green,
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connected - Call Active",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Green,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    chatRequestSent -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Yellow,
                                strokeWidth = 2.dp
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
                                color = when (connectionStatus) {
                                    "Connected" -> Color.Green
                                    "Failed" -> Color.Red
                                    else -> Color.Yellow
                                }
                            )
                        }
                    }

                    else -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Gray,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Preparing call...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Connection instructions/status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    chatConnected -> {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Voice call active",
                            tint = Color.Green,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Voice call is active\nSpeak freely with your peer",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    chatRequestSent -> {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Connecting",
                            tint = Color.Yellow,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                        ) {
                            Text(
                                text = "Establishing secure voice connection...\nThis may take a few seconds.",
                                modifier = Modifier.padding(16.dp),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    else -> {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Preparing",
                            tint = Color.Gray,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Getting ready...",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Microphone toggle
                    IconButton(
                        onClick = { callViewModel.toggleMic() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                if (isMicEnabled) Color.DarkGray else Color.Red,
                                shape = MaterialTheme.shapes.extraLarge
                            )
                    ) {
                        Icon(
                            imageVector = if (isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = if (isMicEnabled) "Mute Microphone" else "Unmute Microphone",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // End call button
                    IconButton(
                        onClick = {
                            callViewModel.endCall()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Red, shape = MaterialTheme.shapes.extraLarge)
                    ) {
                        Icon(
                            Icons.Filled.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status text
                Text(
                    text = if (isMicEnabled) "🎤 Microphone: On" else "🔇 Microphone: Off",
                    color = if (isMicEnabled) Color.Green else Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                if (chatConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Call quality: Good",
                        color = Color.Green,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}