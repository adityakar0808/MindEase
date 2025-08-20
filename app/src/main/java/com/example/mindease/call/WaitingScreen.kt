package com.example.mindease.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isWaiting) {
                Text(
                    "Waiting for another user to join...",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { callViewModel.cancelWaiting(currentUserUid) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Cancel", color = Color.White)
                }
            }

            if (!timeoutMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    timeoutMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Yellow
                )
            }
        }
    }

    LaunchedEffect(ongoingSession) {
        if (ongoingSession != null) onCallStarted()
    }
}
