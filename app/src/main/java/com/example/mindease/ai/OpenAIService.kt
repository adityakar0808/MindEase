package com.example.mindease.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.example.mindease.BuildConfig


@Serializable
data class ChatRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<ChatMessage>,
    val max_tokens: Int = 150,
    val temperature: Double = 0.7
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: ChatMessage
)

class OpenAIService {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // Replace with your OpenAI API key
    private val apiKey = BuildConfig.API_KEY
    private val baseUrl = "https://api.openai.com/v1/chat/completions"

    suspend fun sendMessage(messages: List<AIChatMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                // Convert AIChatMessage to ChatMessage format
                val chatMessages = mutableListOf<ChatMessage>()

                // Add system prompt for mental health support
                chatMessages.add(
                    ChatMessage(
                        role = "system",
                        content = """You are a compassionate AI companion designed to provide emotional support and mental wellness guidance. 
                        You should:
                        - Be empathetic and understanding
                        - Provide helpful coping strategies
                        - Encourage professional help when needed
                        - Never provide medical diagnosis or treatment
                        - Keep responses concise but warm
                        - Focus on mental wellness and emotional support"""
                    )
                )

                // Add conversation history (last 10 messages to stay within token limits)
                chatMessages.addAll(
                    messages.takeLast(10).map { message ->
                        ChatMessage(
                            role = if (message.isFromUser) "user" else "assistant",
                            content = message.content
                        )
                    }
                )

                val requestBody = ChatRequest(
                    messages = chatMessages,
                    max_tokens = 150,
                    temperature = 0.7
                )

                val requestBodyJson = json.encodeToString(ChatRequest.serializer(), requestBody)

                val request = Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val chatResponse = json.decodeFromString(ChatResponse.serializer(), responseBody)
                        return@withContext chatResponse.choices.firstOrNull()?.message?.content
                            ?: "I'm here to help. Could you tell me more about what's on your mind?"
                    }
                }

                Log.e("OpenAI", "Request failed: ${response.code} - ${response.message}")
                return@withContext getDefaultResponse()

            } catch (e: Exception) {
                Log.e("OpenAI", "Error sending message", e)
                return@withContext getDefaultResponse()
            }
        }
    }

    private fun getDefaultResponse(): String {
        val defaultResponses = listOf(
            "I'm here to listen. What would you like to talk about?",
            "It sounds like you're going through something difficult. I'm here to support you.",
            "Thank you for sharing with me. How are you feeling right now?",
            "I understand this might be challenging. Would you like to talk about what's bothering you?",
            "Remember that it's okay to not be okay sometimes. What's on your mind today?"
        )
        return defaultResponses.random()
    }
}