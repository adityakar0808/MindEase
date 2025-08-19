package com.example.mindease.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mindease.auth.AuthViewModel
import com.example.mindease.data.models.User
import com.google.firebase.firestore.FirebaseFirestore

sealed class BottomNavItem(val route: String, val label: String, val icon: @Composable () -> Unit) {
    object Home : BottomNavItem("home_tab", "Home", { Icon(Icons.Filled.Home, contentDescription = "Home") })
    object Inbox : BottomNavItem("inbox_tab", "Inbox", { Icon(Icons.Filled.Mail, contentDescription = "Inbox") })
    object Profile : BottomNavItem("profile_tab", "Profile", { Icon(Icons.Filled.Person, contentDescription = "Profile") })
}

@Composable
fun HomeBottomNav(
    viewModel: AuthViewModel,
    onLogout: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    var selectedItem by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Home) }

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
        // Return early if currentUser is not ready
        val user = currentUser ?: return@Scaffold

        when (selectedItem) {
            BottomNavItem.Home -> {
                HomeScreen(
                    viewModel = viewModel,
                    onStartChat = { chatId -> /* optional callback for starting chat */ },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            BottomNavItem.Inbox -> {
                InboxScreen(
                    currentUser = user,
                    db = FirebaseFirestore.getInstance(),
                    modifier = Modifier.padding(innerPadding)
                )
            }
            BottomNavItem.Profile -> {
                ProfileScreen(
                    user = user,
                    viewModel = viewModel,
                    onLogout = onLogout,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
