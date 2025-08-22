package com.example.mindease.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class AIChatViewModel : ViewModel() {
    private val openAIService = OpenAIService()

    private val _messages = MutableStateFlow<List<AIChatMessage>>(emptyList())
    val messages: StateFlow<List<AIChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Add welcome message
        addWelcomeMessage()
    }

    private fun addWelcomeMessage() {
        val welcomeMessage = AIChatMessage(
            content = "Hello! I'm your AI companion, here to listen and support you. How are you feeling today? 😊",
            isFromUser = false
        )
        _messages.value = listOf(welcomeMessage)
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _isLoading.value) return

        // Add user message
        val userMessage = AIChatMessage(
            content = content.trim(),
            isFromUser = true
        )

        _messages.value = _messages.value + userMessage
        _isLoading.value = true

        // Add typing indicator
        val typingMessage = AIChatMessage(
            content = "",
            isFromUser = false,
            isTyping = true
        )
        _messages.value = _messages.value + typingMessage

        viewModelScope.launch {
            try {
                // Get AI response
                val response = openAIService.sendMessage(_messages.value.filter { !it.isTyping })

                // Remove typing indicator
                _messages.value = _messages.value.filter { !it.isTyping }

                // Add AI response with typing effect
                addAIResponseWithTypingEffect(response)

            } catch (e: Exception) {
                Log.e("AIChatViewModel", "Error getting AI response", e)

                // Remove typing indicator
                _messages.value = _messages.value.filter { !it.isTyping }

                // Add error message
                val errorMessage = AIChatMessage(
                    content = "I'm having trouble connecting right now, but I'm still here for you. Sometimes talking through your thoughts can help even without my responses. What's on your mind?",
                    isFromUser = false
                )
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun addAIResponseWithTypingEffect(fullResponse: String) {
        val aiMessage = AIChatMessage(
            content = "",
            isFromUser = false
        )

        _messages.value = _messages.value + aiMessage

        // Simulate typing by gradually adding characters
        val words = fullResponse.split(" ")
        var currentContent = ""

        for (word in words) {
            currentContent += if (currentContent.isEmpty()) word else " $word"

            // Update the last message with current content
            val updatedMessages = _messages.value.toMutableList()
            val lastIndex = updatedMessages.size - 1
            updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(content = currentContent)
            _messages.value = updatedMessages

            // Add small delay for typing effect
            delay(100)
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        addWelcomeMessage()
    }

    fun getSupportiveResponse(userInput: String): String {
        // Fallback responses based on keywords when AI is not available
        return when {
            userInput.contains("sad", true) || userInput.contains("down", true) ->
                "I hear that you're feeling sad. It's okay to feel this way - your emotions are valid. Would you like to share what's making you feel down?"

            userInput.contains("anxious", true) || userInput.contains("anxiety", true) ->
                "Anxiety can be really overwhelming. Try taking a few deep breaths with me. What's making you feel anxious right now?"

            userInput.contains("stress", true) || userInput.contains("stressed", true) ->
                "Stress can be really difficult to manage. What's been causing you the most stress lately? Sometimes talking about it can help."

            userInput.contains("lonely", true) ->
                "Feeling lonely is one of the hardest emotions to experience. I want you to know that you're not alone - I'm here with you. What would help you feel more connected right now?"

            userInput.contains("angry", true) || userInput.contains("mad", true) ->
                "It sounds like something has really upset you. Anger is a natural emotion, and it's okay to feel this way. What happened that made you angry?"

            userInput.contains("help", true) ->
                "I'm here to help you. What kind of support do you need right now? Whether it's just listening or helping you think through something, I'm here for you."

            else ->
                "Thank you for sharing that with me. I'm here to listen and support you. Can you tell me more about what you're going through?"
        }
    }
}