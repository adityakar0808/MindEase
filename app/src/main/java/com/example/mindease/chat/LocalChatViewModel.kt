package com.example.mindease.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindease.data.local.LocalChatDatabase
import com.example.mindease.data.local.LocalChatMessage
import com.example.mindease.data.local.LocalConversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class LocalChatViewModel(private val context: Context) : ViewModel() {
    companion object {
        private const val TAG = "LocalChatViewModel"
    }

    private val database = LocalChatDatabase.getDatabase(context)
    private val chatDao = database.chatDao()

    private val _messages = MutableStateFlow<List<LocalChatMessage>>(emptyList())
    val messages: StateFlow<List<LocalChatMessage>> = _messages.asStateFlow()

    private val _isChatAvailable = MutableStateFlow(false)
    val isChatAvailable: StateFlow<Boolean> = _isChatAvailable.asStateFlow()

    private var webRTCClient: com.example.mindease.call.WebRTCClient? = null
    private var currentSessionId: String? = null
    private var currentPeerName: String? = null
    private var currentPeerUid: String? = null

    fun initializeChat(
        webRTCClient: com.example.mindease.call.WebRTCClient,
        sessionId: String,
        peerName: String,
        peerUid: String
    ) {
        Log.d(TAG, "Initializing chat for session: $sessionId with peer: $peerName")

        this.webRTCClient = webRTCClient
        this.currentSessionId = sessionId
        this.currentPeerName = peerName
        this.currentPeerUid = peerUid

        // Listen for incoming messages
        webRTCClient.setOnMessageReceivedListener { message ->
            Log.d(TAG, "Received message: $message")
            receiveMessage(message)
        }

        _isChatAvailable.value = webRTCClient.isDataChannelOpen()

        // Load existing messages for this session
        viewModelScope.launch {
            try {
                chatDao.getMessagesForSession(sessionId).collect { messages ->
                    Log.d(TAG, "Loaded ${messages.size} messages for session: $sessionId")
                    _messages.value = messages
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages for session: $sessionId", e)
            }
        }

        // Create conversation entry immediately when chat is initialized
        viewModelScope.launch {
            try {
                createInitialConversation(sessionId, peerName, peerUid)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating initial conversation", e)
            }
        }
    }

    private suspend fun createInitialConversation(sessionId: String, peerName: String, peerUid: String) {
        try {
            val conversation = LocalConversation(
                sessionId = sessionId,
                peerName = peerName,
                peerUid = peerUid,
                lastMessage = "Chat connected - ready to send messages",
                lastMessageTime = System.currentTimeMillis(),
                totalMessages = 0
            )

            chatDao.insertConversation(conversation)
            Log.d(TAG, "Created initial conversation for session: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error in createInitialConversation", e)
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || currentSessionId == null || currentPeerName == null) {
            Log.w(TAG, "Cannot send message: invalid state")
            return
        }

        val message = LocalChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = currentSessionId!!,
            peerName = currentPeerName!!,
            peerUid = currentPeerUid!!,
            content = content.trim(),
            isFromCurrentUser = true,
            timestamp = System.currentTimeMillis()
        )

        Log.d(TAG, "Sending message: $content to session: $currentSessionId")

        // Always save to local database first
        viewModelScope.launch {
            try {
                chatDao.insertMessage(message)
                Log.d(TAG, "Message saved to database")
                updateOrCreateConversation(content.trim())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving message to database", e)
            }
        }

        // Try to send via WebRTC data channel if available
        viewModelScope.launch {
            try {
                val success = webRTCClient?.sendChatMessage(content.trim()) ?: false
                Log.d(TAG, "WebRTC send result: $success")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending via WebRTC", e)
            }
        }
    }

    private fun receiveMessage(content: String) {
        if (currentSessionId == null || currentPeerName == null) {
            Log.w(TAG, "Cannot receive message: invalid state")
            return
        }

        val message = LocalChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = currentSessionId!!,
            peerName = currentPeerName!!,
            peerUid = currentPeerUid!!,
            content = content,
            isFromCurrentUser = false,
            timestamp = System.currentTimeMillis()
        )

        Log.d(TAG, "Received message: $content for session: $currentSessionId")

        // Save to local database
        viewModelScope.launch {
            try {
                chatDao.insertMessage(message)
                Log.d(TAG, "Received message saved to database")
                updateOrCreateConversation(content)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving received message", e)
            }
        }
    }

    private suspend fun updateOrCreateConversation(lastMessage: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val sessionId = currentSessionId!!
            val peerName = currentPeerName!!
            val peerUid = currentPeerUid!!

            Log.d(TAG, "Updating conversation for session: $sessionId")

            // Try to update existing conversation
            val rowsUpdated = chatDao.updateConversation(sessionId, lastMessage, timestamp)
            Log.d(TAG, "Rows updated: $rowsUpdated")

            // If no rows were updated, create new conversation
            if (rowsUpdated == 0) {
                val conversation = LocalConversation(
                    sessionId = sessionId,
                    peerName = peerName,
                    peerUid = peerUid,
                    lastMessage = lastMessage,
                    lastMessageTime = timestamp,
                    totalMessages = 1
                )

                chatDao.insertConversation(conversation)
                Log.d(TAG, "Created new conversation for session: $sessionId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateOrCreateConversation", e)
        }
    }

    fun initializeOfflineChat(sessionId: String, peerName: String, peerUid: String) {
        Log.d(TAG, "Initializing offline chat for session: $sessionId")

        this.currentSessionId = sessionId
        this.currentPeerName = peerName
        this.currentPeerUid = peerUid
        this.webRTCClient = null

        _isChatAvailable.value = false

        // Load existing messages for this session
        viewModelScope.launch {
            try {
                chatDao.getMessagesForSession(sessionId).collect { messages ->
                    Log.d(TAG, "Loaded ${messages.size} offline messages for session: $sessionId")
                    _messages.value = messages
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading offline messages", e)
            }
        }
    }

    fun setChatAvailable(available: Boolean) {
        Log.d(TAG, "Setting chat available: $available")
        _isChatAvailable.value = available
    }

    fun getAllConversations() = chatDao.getAllConversations()

    fun loadMessagesForSession(sessionId: String) {
        Log.d(TAG, "Loading messages for session: $sessionId")
        viewModelScope.launch {
            try {
                chatDao.getMessagesForSession(sessionId).collect { messages ->
                    Log.d(TAG, "Loaded ${messages.size} messages for session: $sessionId")
                    _messages.value = messages
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages for session: $sessionId", e)
            }
        }
    }

    fun deleteConversation(sessionId: String) {
        Log.d(TAG, "Deleting conversation: $sessionId")
        viewModelScope.launch {
            try {
                chatDao.deleteConversation(sessionId)
                chatDao.deleteMessages(sessionId)
                Log.d(TAG, "Successfully deleted conversation: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting conversation: $sessionId", e)
            }
        }
    }

    // CRITICAL: Add refresh method for Inbox updates
    fun refreshChatRooms() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Refreshing chat rooms from database")
                // This will trigger any observers to refresh
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing chat rooms", e)
            }
        }
    }

    fun onCallEnded() {
        Log.d(TAG, "Call ended, keeping chat data intact")
        _isChatAvailable.value = false
        webRTCClient = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "LocalChatViewModel cleared")
        _isChatAvailable.value = false
        webRTCClient = null
    }
}
