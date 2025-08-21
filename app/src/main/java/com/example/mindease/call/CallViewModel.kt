package com.example.mindease.call

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindease.data.models.ChatRoom
import com.example.mindease.data.models.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.SessionDescription
import android.util.Log
import kotlinx.coroutines.delay

data class WaitingUser(
    val uid: String,
    val nickname: String,
    val stressReason: String,
    val timestamp: Timestamp?
)

enum class CallState {
    IDLE, WAITING, IN_CALL, CALL_BACKGROUND
}

// Extension function for ChatRoom to Map conversion
fun ChatRoom.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "userA_uid" to userA_uid,
        "userB_uid" to userB_uid,
        "userA_name" to userA_name,
        "userB_name" to userB_name,
        "createdAt" to createdAt,
        "lastMessage" to (lastMessage ?: "Call completed"),
        "lastMessageTime" to (lastMessageTime ?: Timestamp.now()),
        "userA_chatConsent" to userA_chatConsent,
        "userB_chatConsent" to userB_chatConsent,
        "isActive" to isActive
    )
}

class CallViewModel(
    private val context: Context,
    private val db: FirebaseFirestore
) : ViewModel() {

    companion object {
        private const val TAG = "CallViewModel"
        private const val WAITING_TIMEOUT = 60000L
    }

    // Modern StateFlow approach - all states are immutable and reactive
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isWaiting = MutableStateFlow(false)
    val isWaiting: StateFlow<Boolean> = _isWaiting.asStateFlow()

    private val _ongoingCallSession = MutableStateFlow<String?>(null)
    val ongoingCallSession: StateFlow<String?> = _ongoingCallSession.asStateFlow()

    private val _currentUserUid = MutableStateFlow<String?>(null)
    val currentUserUid: StateFlow<String?> = _currentUserUid.asStateFlow()

    private val _timeoutMessage = MutableStateFlow<String?>(null)
    val timeoutMessage: StateFlow<String?> = _timeoutMessage.asStateFlow()

    private val _isMicEnabled = MutableStateFlow(true)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

    private val _chatRequestSent = MutableStateFlow(false)
    val chatRequestSent: StateFlow<Boolean> = _chatRequestSent.asStateFlow()

    private val _chatConnected = MutableStateFlow(false)
    val chatConnected: StateFlow<Boolean> = _chatConnected.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _currentPeerName = MutableStateFlow<String?>(null)
    val currentPeerName: StateFlow<String?> = _currentPeerName.asStateFlow()

    private val _currentPeerUid = MutableStateFlow<String?>(null)
    val currentPeerUid: StateFlow<String?> = _currentPeerUid.asStateFlow()

    // Chat consent states
    private val _chatConsentRequested = MutableStateFlow(false)
    val chatConsentRequested: StateFlow<Boolean> = _chatConsentRequested.asStateFlow()

    private val _chatConsentGranted = MutableStateFlow(false)
    val chatConsentGranted: StateFlow<Boolean> = _chatConsentGranted.asStateFlow()

    private val _bothUsersConsented = MutableStateFlow(false)
    val bothUsersConsented: StateFlow<Boolean> = _bothUsersConsented.asStateFlow()

    private var webRTCClient: WebRTCClient? = null
    private var callListener: ListenerRegistration? = null
    private var chatConsentListener: ListenerRegistration? = null
    private var callEndListener: ListenerRegistration? = null

    // LocalChatViewModel integration
    private var localChatViewModel: com.example.mindease.chat.LocalChatViewModel? = null

    // Method to set LocalChatViewModel
    fun setLocalChatViewModel(localChatViewModel: com.example.mindease.chat.LocalChatViewModel) {
        this.localChatViewModel = localChatViewModel
        Log.d(TAG, "LocalChatViewModel connected to CallViewModel")
    }

    // Initialize chat after WebRTC connection is established
    fun initializeChatAfterConnection(sessionId: String, peerName: String, peerUid: String) {
        viewModelScope.launch {
            delay(2000) // Wait for WebRTC to stabilize
            webRTCClient?.let { client ->
                localChatViewModel?.initializeChat(client, sessionId, peerName, peerUid)
                Log.d(TAG, "Chat initialized after WebRTC connection established")
            }
        }
    }

    fun connectToWaitingUser(
        waitingUser: WaitingUser,
        currentUser: User,
        onSuccess: (String) -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Attempting to connect to user: ${waitingUser.uid}")

                // Force server fetch first to ensure fresh data
                val waitingUserDoc = db.collection("waiting_users")
                    .document(waitingUser.uid)
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()

                if (!waitingUserDoc.exists()) {
                    Log.e(TAG, "Waiting user document not found")
                    onError()
                    return@launch
                }

                val sessionId = waitingUserDoc.getString("sessionId")
                if (sessionId == null) {
                    Log.e(TAG, "No session ID found for waiting user")
                    onError()
                    return@launch
                }

                val sessionDoc = db.collection("call_sessions")
                    .document(sessionId)
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()

                if (!sessionDoc.exists()) {
                    Log.e(TAG, "Session document does not exist")
                    onError()
                    return@launch
                }

                val sessionStatus = sessionDoc.getString("status")
                if (sessionStatus != "waiting") {
                    Log.e(TAG, "Session not in waiting state: $sessionStatus")
                    onError()
                    return@launch
                }

                // Update session with userB information
                db.collection("call_sessions").document(sessionId)
                    .update(
                        mapOf(
                            "userB_uid" to currentUser.uid,
                            "userB_name" to currentUser.name,
                            "status" to "connecting",
                            "connectedAt" to Timestamp.now()
                        )
                    )
                    .await()

                Log.d(TAG, "Successfully updated session: $sessionId")

                // Store peer information
                val userAName = waitingUserDoc.getString("nickname") ?: "User"
                _currentPeerName.update { userAName }
                _currentPeerUid.update { waitingUser.uid }

                // Update states
                _callState.update { CallState.IN_CALL }
                _ongoingCallSession.update { sessionId }
                _currentUserUid.update { currentUser.uid }
                _isWaiting.update { false }
                _connectionStatus.update { "Initializing" }
                _chatRequestSent.update { true } // Auto-enable chat functionality

                // Remove waiting user document
                db.collection("waiting_users").document(waitingUser.uid).delete()

                // Initialize WebRTC with proper delay
                delay(1000)
                initializeWebRTC(sessionId, currentUser.uid)
                listenForCallEnd(sessionId)
                listenForChatConsent(sessionId, currentUser.uid)

                onSuccess(sessionId)
                Log.d(TAG, "Connection process completed successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to user: ${waitingUser.uid}", e)
                resetCallState()
                onError()
            }
        }
    }

    fun startWaiting(uid: String, nickname: String, stressReason: String) {
        Log.d(TAG, "Starting waiting for user: $uid")

        _isWaiting.update { true }
        _callState.update { CallState.WAITING }
        _connectionStatus.update { "Waiting for connection" }

        val sessionId = db.collection("call_sessions").document().id

        db.collection("waiting_users").document(uid).set(
            mapOf(
                "nickname" to nickname,
                "stressReason" to stressReason,
                "timestamp" to Timestamp.now(),
                "sessionId" to sessionId
            )
        ).addOnSuccessListener {
            Log.d(TAG, "Successfully added to waiting users")

            db.collection("call_sessions").document(sessionId).set(
                mapOf(
                    "userA_uid" to uid,
                    "userA_name" to nickname,
                    "userB_uid" to null,
                    "userB_name" to null,
                    "status" to "waiting",
                    "timestamp" to Timestamp.now()
                )
            ).addOnSuccessListener {
                Log.d(TAG, "Successfully created call session")
                listenForCallConnection(sessionId, uid)
                startWaitingTimeout(uid)
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to create call session", e)
                cancelWaiting(uid)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to add to waiting users", e)
            resetCallState()
        }
    }

    private fun startWaitingTimeout(uid: String) {
        viewModelScope.launch {
            delay(WAITING_TIMEOUT)
            if (_isWaiting.value && _callState.value == CallState.WAITING) {
                Log.d(TAG, "Waiting timeout reached for user: $uid")
                setTimeoutMessage("No one connected within the time limit. Please try again.")
                cancelWaiting(uid)
            }
        }
    }

    // ENHANCED listener with better error handling
    private fun listenForCallConnection(sessionId: String, currentUserUid: String) {
        callListener?.remove()

        callListener = db.collection("call_sessions").document(sessionId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for call connection", error)
                    viewModelScope.launch {
                        delay(2000)
                        if (_callState.value == CallState.WAITING) {
                            Log.d(TAG, "Retrying call connection listener")
                            listenForCallConnection(sessionId, currentUserUid)
                        }
                    }
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    if (!doc.exists()) {
                        Log.w(TAG, "Session document no longer exists")
                        return@addSnapshotListener
                    }

                    val userB = doc.getString("userB_uid")
                    val userBName = doc.getString("userB_name")
                    val status = doc.getString("status")

                    Log.d(TAG, "Session update - userB: $userB, status: $status, fromCache: ${doc.metadata.isFromCache}")

                    if (userB != null && userBName != null && status == "connecting" && _callState.value == CallState.WAITING) {
                        Log.d(TAG, "Someone joined the call, transitioning to IN_CALL")

                        // Store peer information
                        _currentPeerName.update { userBName }
                        _currentPeerUid.update { userB }

                        // Update states
                        _isWaiting.update { false }
                        _callState.update { CallState.IN_CALL }
                        _ongoingCallSession.update { sessionId }
                        _currentUserUid.update { currentUserUid }
                        _connectionStatus.update { "Initializing" }
                        _chatRequestSent.update { true } // Auto-enable chat functionality

                        // Clean up waiting state
                        db.collection("waiting_users").document(currentUserUid).delete()
                        callListener?.remove()
                        callListener = null

                        viewModelScope.launch {
                            delay(1000)
                            initializeWebRTC(sessionId, currentUserUid)
                            listenForCallEnd(sessionId)
                            listenForChatConsent(sessionId, currentUserUid)
                        }
                    }
                }
            }
    }

    // Enhanced listeners with error handling
    private fun listenForCallEnd(sessionId: String) {
        callEndListener?.remove()
        callEndListener = db.collection("call_sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for call end", error)
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    if (doc.exists()) {
                        val status = doc.getString("status")
                        if (status == "ended" && _callState.value != CallState.IDLE) {
                            Log.d(TAG, "Call ended by peer, cleaning up")
                            webRTCClient?.close()
                            webRTCClient = null
                            localChatViewModel?.onCallEnded()
                            resetCallState()
                        }
                    }
                }
            }
    }

    private fun listenForChatConsent(sessionId: String, currentUserUid: String) {
        chatConsentListener?.remove()
        chatConsentListener = db.collection("call_sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for chat consent", error)
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    if (!doc.exists()) {
                        Log.w(TAG, "Session document no longer exists for chat consent")
                        return@addSnapshotListener
                    }

                    val userA = doc.getString("userA_uid")
                    val userB = doc.getString("userB_uid")
                    val chatConsentA = doc.getBoolean("chatConsentA") ?: false
                    val chatConsentB = doc.getBoolean("chatConsentB") ?: false
                    val chatConsentRequestedA = doc.getBoolean("chatConsentRequestedA") ?: false
                    val chatConsentRequestedB = doc.getBoolean("chatConsentRequestedB") ?: false

                    val isUserA = currentUserUid == userA
                    val otherUserRequestedConsent = if (isUserA) chatConsentRequestedB else chatConsentRequestedA
                    val currentUserGrantedConsent = if (isUserA) chatConsentA else chatConsentB
                    val bothConsented = chatConsentA && chatConsentB

                    _chatConsentRequested.update { otherUserRequestedConsent && !currentUserGrantedConsent && !bothConsented }
                    _chatConsentGranted.update { currentUserGrantedConsent }
                    _bothUsersConsented.update { bothConsented }

                    if (bothConsented && userA != null && userB != null) {
                        createChatRoom(sessionId, userA, userB)
                    }
                }
            }
    }

    fun cancelWaiting(uid: String) {
        Log.d(TAG, "Canceling waiting for user: $uid")

        resetCallState()

        callListener?.remove()
        callListener = null

        // Clean up Firestore documents
        db.collection("waiting_users").document(uid).delete()

        db.collection("call_sessions")
            .whereEqualTo("userA_uid", uid)
            .whereEqualTo("status", "waiting")
            .get()
            .addOnSuccessListener { querySnapshot ->
                querySnapshot.documents.forEach { doc ->
                    doc.reference.delete()
                }
            }
    }

    fun startCall(sessionId: String, currentUserUid: String) {
        Log.d(TAG, "Starting call - sessionId: $sessionId, userUid: $currentUserUid")

        _isWaiting.update { false }
        _callState.update { CallState.IN_CALL }
        _ongoingCallSession.update { sessionId }
        _currentUserUid.update { currentUserUid }
        _connectionStatus.update { "Initializing" }
        _chatRequestSent.update { true } // Auto-enable chat functionality

        db.collection("waiting_users").document(currentUserUid).delete()
        callListener?.remove()
        callListener = null

        viewModelScope.launch {
            delay(500)
            initializeWebRTC(sessionId, currentUserUid)
            listenForCallEnd(sessionId)
            listenForChatConsent(sessionId, currentUserUid)
        }
    }

    // CRITICAL FIX: Enhanced WebRTC initialization
    private fun initializeWebRTC(sessionId: String, currentUserUid: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Initializing WebRTC for session: $sessionId, user: $currentUserUid")

                // Close existing connection
                webRTCClient?.close()
                webRTCClient = null

                _connectionStatus.update { "Connecting..." }

                webRTCClient = WebRTCClient(
                    context,
                    sessionId,
                    currentUserUid,
                    db,
                    onRemoteStream = { stream ->
                        Log.d(TAG, "Remote stream connected")
                        _chatConnected.update { true }
                        _connectionStatus.update { "Connected!" }
                    },
                    onConnectionStateChange = { status ->
                        Log.d(TAG, "WebRTC Connection status: $status")
                        _connectionStatus.update {
                            when (status) {
                                "Connected" -> "Connected!"
                                "Connecting" -> "Establishing connection..."
                                "Failed" -> "Connection failed"
                                "Retrying" -> "Retrying connection..."
                                "Disconnected" -> "Disconnected"
                                else -> status
                            }
                        }

                        when (status) {
                            "Connected" -> {
                                _chatConnected.update { true }

                                // Initialize chat after successful connection
                                val peerName = _currentPeerName.value
                                val peerUid = _currentPeerUid.value
                                if (peerName != null && peerUid != null) {
                                    initializeChatAfterConnection(sessionId, peerName, peerUid)
                                }
                            }
                            "Failed" -> {
                                _chatConnected.update { false }
                                // Attempt one more initialization
                                viewModelScope.launch {
                                    delay(3000)
                                    if (_callState.value == CallState.IN_CALL) {
                                        Log.d(TAG, "Retrying WebRTC initialization...")
                                        _connectionStatus.update { "Retrying..." }
                                        initializeWebRTC(sessionId, currentUserUid)
                                    }
                                }
                            }
                        }
                    }
                )

                // Initialize peer connection
                webRTCClient?.initPeerConnection()

                // Give time for initialization
                delay(2000)

                // Determine role and start appropriate signaling
                val sessionDoc = db.collection("call_sessions").document(sessionId).get().await()
                if (!sessionDoc.exists()) {
                    Log.e(TAG, "Session document does not exist")
                    throw Exception("Session not found")
                }

                val userA = sessionDoc.getString("userA_uid")
                val userB = sessionDoc.getString("userB_uid")
                val userAName = sessionDoc.getString("userA_name") ?: "User"
                val userBName = sessionDoc.getString("userB_name") ?: "User"

                // Update peer information if not already set
                if (_currentPeerName.value == null) {
                    val peerName = if (currentUserUid == userA) userBName else userAName
                    val peerUid = if (currentUserUid == userA) userB else userA
                    _currentPeerName.update { peerName }
                    _currentPeerUid.update { peerUid }
                }

                val existingOffer = sessionDoc.getString("offer")
                val existingAnswer = sessionDoc.getString("answer")

                Log.d(TAG, "WebRTC Role - userA: $userA, userB: $userB, currentUser: $currentUserUid")
                Log.d(TAG, "Existing offer: ${!existingOffer.isNullOrEmpty()}, answer: ${!existingAnswer.isNullOrEmpty()}")

                when {
                    userA == currentUserUid && existingOffer.isNullOrEmpty() -> {
                        Log.d(TAG, "Creating offer as UserA")
                        delay(1000)
                        webRTCClient?.createOffer()
                    }
                    userB == currentUserUid && !existingOffer.isNullOrEmpty() && existingAnswer.isNullOrEmpty() -> {
                        Log.d(TAG, "Processing offer and creating answer as UserB")
                        webRTCClient?.setRemoteDescription(SessionDescription.Type.OFFER, existingOffer)
                        delay(1000)
                        webRTCClient?.createAnswer()
                    }
                    userA == currentUserUid && !existingAnswer.isNullOrEmpty() -> {
                        Log.d(TAG, "Processing answer as UserA")
                        webRTCClient?.setRemoteDescription(SessionDescription.Type.ANSWER, existingAnswer)
                    }
                    else -> {
                        Log.d(TAG, "Waiting for signaling to complete...")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing WebRTC", e)
                _connectionStatus.update { "Connection failed" }
                _chatConnected.update { false }
            }
        }
    }

    fun requestChatConsent() {
        val sessionId = _ongoingCallSession.value ?: return
        val currentUserUid = _currentUserUid.value ?: return

        db.collection("call_sessions").document(sessionId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val userA = doc.getString("userA_uid")
                val isUserA = currentUserUid == userA
                val requestField = if (isUserA) "chatConsentRequestedA" else "chatConsentRequestedB"
                val consentField = if (isUserA) "chatConsentA" else "chatConsentB"

                val updates = mapOf(
                    requestField to true,
                    consentField to true
                )

                db.collection("call_sessions").document(sessionId)
                    .update(updates)
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully requested chat consent")
                    }
            }
    }

    fun acceptChatConsent() {
        val sessionId = _ongoingCallSession.value ?: return
        val currentUserUid = _currentUserUid.value ?: return

        db.collection("call_sessions").document(sessionId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val userA = doc.getString("userA_uid")
                val isUserA = currentUserUid == userA
                val consentField = if (isUserA) "chatConsentA" else "chatConsentB"

                db.collection("call_sessions").document(sessionId)
                    .update(mapOf(consentField to true))
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully accepted chat consent")
                        _chatConsentRequested.update { false }
                    }
            }
    }

    fun initializeChatStorage(sessionId: String, peerName: String, peerUid: String) {
        _currentPeerName.update { peerName }
        _currentPeerUid.update { peerUid }
    }

    fun getWebRTCClient(): WebRTCClient? = webRTCClient

    // Create chat room with improved error handling
    private fun createChatRoom(sessionId: String, userAUid: String, userBUid: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Creating chat room for session: $sessionId")

                val userADoc = db.collection("users").document(userAUid).get().await()
                val userBDoc = db.collection("users").document(userBUid).get().await()

                val userAName = userADoc.getString("name") ?: "User"
                val userBName = userBDoc.getString("name") ?: "User"

                val chatRoom = ChatRoom(
                    id = sessionId,
                    userA_uid = userAUid,
                    userB_uid = userBUid,
                    userA_name = userAName,
                    userB_name = userBName,
                    createdAt = Timestamp.now(),
                    lastMessage = "Call completed - chat available",
                    lastMessageTime = Timestamp.now(),
                    userA_chatConsent = true,
                    userB_chatConsent = true,
                    isActive = true
                )

                db.collection("chat_rooms").document(sessionId)
                    .set(chatRoom.toMap(), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully created chat room in Firestore")
                        localChatViewModel?.refreshChatRooms()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to create chat room in Firestore", e)
                    }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create chat room", e)
            }
        }
    }

    fun setCallBackground() {
        if (_callState.value == CallState.IN_CALL) {
            _callState.update { CallState.CALL_BACKGROUND }
        }
    }

    fun setCallForeground() {
        if (_callState.value == CallState.CALL_BACKGROUND) {
            _callState.update { CallState.IN_CALL }
        }
    }

    fun requestChat() {
        if (!_chatRequestSent.value) {
            _chatRequestSent.update { true }

            if (webRTCClient == null) {
                val sessionId = _ongoingCallSession.value
                val uid = _currentUserUid.value
                if (sessionId != null && uid != null) {
                    initializeWebRTC(sessionId, uid)
                }
            }
        }
    }

    fun toggleMic() {
        _isMicEnabled.update { !it }
        webRTCClient?.toggleMic(_isMicEnabled.value)
    }

    fun answerCall(sessionId: String, currentUserUid: String) {
        _ongoingCallSession.update { sessionId }
        _callState.update { CallState.IN_CALL }
        _currentUserUid.update { currentUserUid }
        _connectionStatus.update { "Initializing" }
        _chatRequestSent.update { true }

        viewModelScope.launch {
            delay(500)
            initializeWebRTC(sessionId, currentUserUid)
            listenForCallEnd(sessionId)
            listenForChatConsent(sessionId, currentUserUid)
        }
    }

    fun endCall() {
        Log.d(TAG, "Ending call")
        val sessionId = _ongoingCallSession.value

        sessionId?.let { id ->
            db.collection("call_sessions").document(id)
                .update(
                    mapOf(
                        "status" to "ended",
                        "endedAt" to Timestamp.now()
                    )
                )
        }

        webRTCClient?.close()
        webRTCClient = null
        localChatViewModel?.onCallEnded()
        resetCallState()
    }

    private fun resetCallState() {
        _ongoingCallSession.update { null }
        _isWaiting.update { false }
        _currentUserUid.update { null }
        _chatRequestSent.update { false }
        _chatConnected.update { false }
        _callState.update { CallState.IDLE }
        _connectionStatus.update { "Disconnected" }
        _isMicEnabled.update { true }
        _chatConsentRequested.update { false }
        _chatConsentGranted.update { false }
        _bothUsersConsented.update { false }
        _currentPeerName.update { null }
        _currentPeerUid.update { null }

        callListener?.remove()
        callListener = null
        chatConsentListener?.remove()
        chatConsentListener = null
        callEndListener?.remove()
        callEndListener = null
    }

    fun setTimeoutMessage(msg: String) {
        _timeoutMessage.update { msg }
        _isWaiting.update { false }
        _callState.update { CallState.IDLE }
    }

    fun clearTimeoutMessage() {
        _timeoutMessage.update { null }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "CallViewModel cleared")
        callListener?.remove()
        chatConsentListener?.remove()
        callEndListener?.remove()
        webRTCClient?.close()
        localChatViewModel = null
    }
}