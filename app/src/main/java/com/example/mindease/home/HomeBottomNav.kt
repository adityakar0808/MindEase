package com.example.mindease.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.call.CallScreen
import com.example.mindease.call.CallViewModel
import com.example.mindease.data.models.User
import com.google.firebase.firestore.FirebaseFirestore

sealed class BottomNavItem(val route: String, val label: String, val icon: @Composable () -> Unit) {
    object Home : BottomNavItem("home_tab", "Home", { Icon(Icons.Filled.Home, contentDescription = "Home") })
    object Inbox : BottomNavItem("inbox_tab", "Inbox", { Icon(Icons.Filled.Mail, contentDescription = "Inbox") })
    object Profile : BottomNavItem("profile_tab", "Profile", { Icon(Icons.Filled.Person, contentDescription = "Profile") })
}

@Composable
fun HomeBottomNav(
    user: User,
    viewModel: AuthViewModel,
    callViewModel: CallViewModel, // ✅ Shared CallViewModel
    onLogout: () -> Unit,
    navController: NavController,
    db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    var selectedItem by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Home) }
    val ongoingCallSessionId by callViewModel.ongoingCallSession.collectAsState()

    Scaffold(
        bottomBar = {
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
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // Banner for ongoing call
            ongoingCallSessionId?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { selectedItem = BottomNavItem.Home },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "Ongoing call - Tap to return",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Main content
            when {
                // Show CallScreen if call is ongoing and Home tab is selected
                ongoingCallSessionId != null && selectedItem == BottomNavItem.Home -> {
                    CallScreen(
                        sessionId = ongoingCallSessionId!!,
                        callViewModel = callViewModel,
                        onChatStart = { selectedItem = BottomNavItem.Inbox }
                    )
                }
                // HomeScreen with shared CallViewModel
                selectedItem == BottomNavItem.Home -> HomeScreen(
                    user = user,
                    viewModel = viewModel,
                    callViewModel = callViewModel, // ✅ pass shared instance
                    modifier = Modifier.fillMaxSize(),
                    navToCallScreen = { sessionId -> callViewModel.startCall(sessionId) }
                )
                selectedItem == BottomNavItem.Inbox -> InboxScreen(
                    currentUser = user,
                    callViewModel = callViewModel,
                    db = db,
                    modifier = Modifier.fillMaxSize(),
                    onReturnToCall = { selectedItem = BottomNavItem.Home }
                )
                selectedItem == BottomNavItem.Profile -> ProfileScreen(
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
