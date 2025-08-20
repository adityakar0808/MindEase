package com.example.mindease.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.call.CallScreen
import com.example.mindease.call.CallState
import com.example.mindease.call.CallViewModel
import com.example.mindease.call.WaitingScreen
import com.example.mindease.data.models.User
import com.google.firebase.firestore.FirebaseFirestore

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
) {
    object Home : BottomNavItem("home_tab", "Home", { Icon(Icons.Filled.Home, contentDescription = "Home") })
    object Inbox : BottomNavItem("inbox_tab", "Inbox", { Icon(Icons.Filled.Mail, contentDescription = "Inbox") })
    object Profile : BottomNavItem("profile_tab", "Profile", { Icon(Icons.Filled.Person, contentDescription = "Profile") })
}

@Composable
fun HomeBottomNav(
    user: User?,
    viewModel: AuthViewModel,
    callViewModel: CallViewModel,
    onLogout: () -> Unit,
    navController: NavController,
    db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    var selectedItem by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Home) }
    val callState by callViewModel.callState.collectAsState()
    val isWaiting by callViewModel.isWaiting.collectAsState()
    val ongoingCallSessionId by callViewModel.ongoingCallSession.collectAsState(initial = null)

    // Handle call/waiting state changes for navigation
    LaunchedEffect(selectedItem, callState, isWaiting) {
        when {
            // When in call and user navigates away from home, set to background
            callState == CallState.IN_CALL && selectedItem != BottomNavItem.Home -> {
                callViewModel.setCallBackground()
            }
            // When in call background and user navigates back to home, set to foreground
            callState == CallState.CALL_BACKGROUND && selectedItem == BottomNavItem.Home -> {
                callViewModel.setCallForeground()
            }
        }
    }

    // Null-safe: show loading until user is ready
    if (user == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            // Show banner when in call background OR when waiting and not on home tab
            when {
                callState == CallState.CALL_BACKGROUND -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedItem = BottomNavItem.Home
                                callViewModel.setCallForeground()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📞 Ongoing call - Tap to return",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "●",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                isWaiting && selectedItem != BottomNavItem.Home -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedItem = BottomNavItem.Home
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🟢 Waiting for connection - Tap to return",
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                            Text(
                                text = "●",
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Show bottom navigation in all states except when actively in call screen
            if (callState != CallState.IN_CALL || selectedItem != BottomNavItem.Home) {
                NavigationBar {
                    listOf(BottomNavItem.Home, BottomNavItem.Inbox, BottomNavItem.Profile).forEach { item ->
                        NavigationBarItem(
                            icon = item.icon,
                            label = { Text(item.label) },
                            selected = selectedItem.route == item.route,
                            onClick = { selectedItem = item }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                // Show call screen when actively in call and on home tab
                callState == CallState.IN_CALL && selectedItem == BottomNavItem.Home -> {
                    CallScreen(
                        callViewModel = callViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onChatStart = { /* Optional: can switch to inbox if needed */ }
                    )
                }

                // Show waiting screen when waiting and on home tab
                (callState == CallState.WAITING || isWaiting) && selectedItem == BottomNavItem.Home -> {
                    WaitingScreen(
                        callViewModel = callViewModel,
                        currentUserUid = user.uid,
                        modifier = Modifier.fillMaxSize(),
                        onCallStarted = {
                            // Stay on home tab when call starts
                            selectedItem = BottomNavItem.Home
                        }
                    )
                }

                // Show regular home screen when idle and on home tab, or when call/waiting is in background
                selectedItem == BottomNavItem.Home -> {
                    HomeScreen(
                        user = user,
                        viewModel = viewModel,
                        callViewModel = callViewModel,
                        modifier = Modifier.fillMaxSize(),
                        navToCallScreen = { sessionId ->
                            callViewModel.startCall(sessionId, user.uid)
                            selectedItem = BottomNavItem.Home
                        }
                    )
                }

                // Show inbox tab
                selectedItem == BottomNavItem.Inbox -> {
                    InboxScreen(
                        currentUser = user,
                        callViewModel = callViewModel,
                        db = db,
                        modifier = Modifier.fillMaxSize(),
                        onReturnToCall = {
                            selectedItem = BottomNavItem.Home
                            if (callState == CallState.CALL_BACKGROUND) {
                                callViewModel.setCallForeground()
                            }
                        }
                    )
                }

                // Show profile tab
                selectedItem == BottomNavItem.Profile -> {
                    ProfileScreen(
                        user = user,
                        viewModel = viewModel,
                        onLogout = onLogout,
                        modifier = Modifier.fillMaxSize(),
                        db = db
                    )
                }
            }
        }
    }
}