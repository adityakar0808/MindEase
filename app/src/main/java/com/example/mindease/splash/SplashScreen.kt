package com.example.mindease.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
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
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }

    // Multiple animation states for staggered effects
    val logoScaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = EaseOutBounce
        ),
        label = "logoScale"
    )

    val logoAlphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "logoAlpha"
    )

    val textAlphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1000,
            delayMillis = 600
        ),
        label = "textAlpha"
    )

    val taglineAlphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 800,
            delayMillis = 1000
        ),
        label = "taglineAlpha"
    )

    val loadingAlphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = 1400
        ),
        label = "loadingAlpha"
    )

    // Floating animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val floatingOffset = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingOffset"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(3000) // Show splash for 3 seconds
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6366F1), // Indigo
                        Color(0xFF8B5CF6), // Purple
                        Color(0xFFA855F7)  // Violet
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo/Icon Container
            Card(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScaleAnim.value)
                    .alpha(logoAlphaAnim.value)
                    .offset(y = (-floatingOffset.value).dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.15f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.3f),
                                    Color.White.copy(alpha = 0.1f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Psychology, // Brain/Psychology icon for mental health
                        contentDescription = "MindEase Logo",
                        modifier = Modifier.size(60.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // App Name
            Text(
                text = "MindEase",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(textAlphaAnim.value),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tagline
            Card(
                modifier = Modifier.alpha(taglineAlphaAnim.value),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Find peace through connection",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Loading indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(loadingAlphaAnim.value)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    strokeWidth = 3.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Loading your safe space...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Decorative elements
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.1f)
        ) {
            // Top left decoration
            Card(
                modifier = Modifier
                    .size(100.dp)
                    .offset((-20).dp, (-20).dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {}

            // Bottom right decoration
            Card(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.BottomEnd)
                    .offset(50.dp, 50.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                )
            ) {}

            // Top right decoration
            Card(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopEnd)
                    .offset(30.dp, 100.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f)
                )
            ) {}
        }
    }
}