package com.example.mindease.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.mindease.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500), label = ""
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2000) // Show splash for 2s
        onSplashFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // 🔥 replace with your app logo
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(120.dp)
                    .alpha(alphaAnim.value)
            )

            Spacer(modifier = Modifier.height(24.dp))

            CircularProgressIndicator(
                modifier = Modifier.alpha(alphaAnim.value)
            )
        }
    }
}


