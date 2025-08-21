package com.example.mindease.data.models

import com.google.firebase.Timestamp

data class ChatRoom(
    val id: String = "",
    val userA_uid: String = "",
    val userB_uid: String = "",
    val userA_name: String = "",
    val userB_name: String = "",
    val createdAt: Timestamp? = null,
    val lastMessage: String = "",
    val lastMessageTime: Timestamp? = null,
    val userA_chatConsent: Boolean = false,
    val userB_chatConsent: Boolean = false,
    val isActive: Boolean = false
)

data class Message(
    val id: String = "",
    val chatRoomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null,
    val isRead: Boolean = false
)

