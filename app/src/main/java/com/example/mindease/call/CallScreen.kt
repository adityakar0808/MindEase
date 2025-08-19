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
import androidx.compose.ui.unit.dp

@Composable
fun CallScreen(
    sessionId: String,
    callViewModel: CallViewModel,
    modifier: Modifier = Modifier,
    onChatStart: () -> Unit
) {
    val isMicEnabled by callViewModel.isMicEnabled.collectAsState()
    val chatConnected by callViewModel.chatConnected.collectAsState()
    val chatRequestSent by callViewModel.chatRequestSent.collectAsState()
    val currentUserUid = callViewModel.currentUserUid

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
            Text(
                text = "Ongoing Call",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Session: $sessionId",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.weight(1f))

            if (!chatConnected) {
                Button(
                    onClick = { callViewModel.requestChat(sessionId, currentUserUid) },
                    enabled = !chatRequestSent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (chatRequestSent) "Waiting for the other user..." else "Connect to Chat"
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { callViewModel.toggleMic() },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.DarkGray, shape = MaterialTheme.shapes.extraLarge)
                ) {
                    Icon(
                        imageVector = if (isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = "Toggle Mic",
                        tint = if (isMicEnabled) Color.White else Color.Red,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = {
                        callViewModel.endCall()
                        if (chatConnected) onChatStart()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.Red, shape = MaterialTheme.shapes.extraLarge)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }

    LaunchedEffect(chatConnected) {
        if (chatConnected) onChatStart()
    }
}
