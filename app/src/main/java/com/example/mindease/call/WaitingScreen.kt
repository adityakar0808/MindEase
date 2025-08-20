package com.example.mindease.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.dp

@Composable
fun WaitingScreen(
    callViewModel: CallViewModel,
    currentUserUid: String,
    modifier: Modifier = Modifier,
    onCallStarted: () -> Unit
) {
    val isWaiting by callViewModel.isWaiting.collectAsState()
    val timeoutMessage by callViewModel.timeoutMessage.collectAsState()
    val ongoingSession by callViewModel.ongoingCallSession.collectAsState()

    // Pulsing animation for the waiting indicator
    val infiniteTransition = rememberInfiniteTransition(label = "waiting_animation")
    val pulsing by infiniteTransition.animateFloat(
        initialValue = 0.3f,
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
            modifier = Modifier.padding(32.dp)
        ) {
            if (isWaiting) {
                // Animated waiting indicator
                Card(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(scale)
                        .alpha(pulsing),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4A90E2).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.size(120.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF4A90E2).copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(60.dp)
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
                                                    Color(0xFF4A90E2),
                                                    Color(0xFF357ABD)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Searching for connection...",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "We're finding someone who wants to chat",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Loading dots animation
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
                        val delay = index * 200
                        val dotAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 600,
                                    delayMillis = delay,
                                    easing = EaseInOutSine
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_$index"
                        )

                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = dotAlpha))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Cancel button
                Button(
                    onClick = { callViewModel.cancelWaiting(currentUserUid) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE74C3C)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                ) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Cancel Search",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!timeoutMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF39C12).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        timeoutMessage ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF39C12),
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
    }

    LaunchedEffect(ongoingSession) {
        if (ongoingSession != null) onCallStarted()
    }
}