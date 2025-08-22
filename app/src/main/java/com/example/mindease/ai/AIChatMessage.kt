package com.example.mindease.ai

import java.util.Date
import java.util.UUID

data class AIChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Date = Date(),
    val isTyping: Boolean = false
)

enum class MessageType {
    USER,
    AI,
    TYPING
}