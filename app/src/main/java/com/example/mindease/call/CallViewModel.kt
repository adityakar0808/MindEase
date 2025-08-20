package com.example.mindease.call

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription

// Add enum for call states
enum class CallState {
    IDLE,           // Normal state
    WAITING,        // Waiting for someone to connect
    IN_CALL,        // Actively in a call
    CALL_BACKGROUND // In call but navigated to different screen
}

class CallViewModel(
    private val context: Context,
    private val db: FirebaseFirestore
) : ViewModel() {

    private var webRTCClient: WebRTCClient? = null
    private var callListener: ListenerRegistration? = null

    // Replace individual boolean states with a single state
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    private val _isWaiting = MutableStateFlow(false)
    val isWaiting: StateFlow<Boolean> = _isWaiting

    private val _ongoingCallSession = MutableStateFlow<String?>(null)
    val ongoingCallSession: StateFlow<String?> = _ongoingCallSession

    private val _currentUserUid = MutableStateFlow<String?>(null)
    val currentUserUid: StateFlow<String?> = _currentUserUid

    private val _timeoutMessage = MutableStateFlow<String?>(null)
    val timeoutMessage: StateFlow<String?> = _timeoutMessage

    private val _isMicEnabled = MutableStateFlow(true)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled

    private val _chatRequestSent = MutableStateFlow(false)
    val chatRequestSent: StateFlow<Boolean> = _chatRequestSent

    private val _chatConnected = MutableStateFlow(false)
    val chatConnected: StateFlow<Boolean> = _chatConnected

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus

    // Add method to check if banner should be shown
    fun shouldShowBanner(): Boolean {
        return _callState.value == CallState.CALL_BACKGROUND
    }

    fun startWaiting(uid: String, nickname: String, stressReason: String) {
        _isWaiting.value = true
        _callState.value = CallState.WAITING

        // Create session but don't set as ongoing call yet
        val sessionId = db.collection("call_sessions").document().id

        // Add to waiting users
        db.collection("waiting_users").document(uid).set(
            mapOf(
                "nickname" to nickname,
                "stressReason" to stressReason,
                "timestamp" to com.google.firebase.Timestamp.now(),
                "sessionId" to sessionId
            )
        )

        // Create the call session in waiting state
        db.collection("call_sessions").document(sessionId).set(
            mapOf(
                "userA_uid" to uid,
                "userB_uid" to null,
                "status" to "waiting",
                "timestamp" to com.google.firebase.Timestamp.now()
            )
        )

        // Listen for someone joining the call
        listenForCallConnection(sessionId, uid)
    }

    // Add listener for incoming call connections
    private fun listenForCallConnection(sessionId: String, currentUserUid: String) {
        callListener?.remove() // Remove any existing listener
        callListener = db.collection("call_sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                snapshot?.let { doc ->
                    val userB = doc.getString("userB_uid")
                    val status = doc.getString("status")

                    // If someone joined and status changed to ringing
                    if (userB != null && status == "ringing" && _callState.value == CallState.WAITING) {
                        // Start the call automatically
                        startCall(sessionId, currentUserUid)
                    }
                }
            }
    }

    fun cancelWaiting(uid: String) {
        _isWaiting.value = false
        _callState.value = CallState.IDLE
        _ongoingCallSession.value = null

        // Remove listeners
        callListener?.remove()
        callListener = null

        // Remove from waiting users and clean up session
        db.collection("waiting_users").document(uid).delete()

        // Clean up the session if it exists
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
        _isWaiting.value = false
        _callState.value = CallState.IN_CALL
        _ongoingCallSession.value = sessionId
        _currentUserUid.value = currentUserUid

        // Remove from waiting users if was waiting
        db.collection("waiting_users").document(currentUserUid).delete()

        // Remove call listener since we're now in call
        callListener?.remove()
        callListener = null

        // Automatically initialize WebRTC connection when call starts
        initializeWebRTC(sessionId, currentUserUid)
    }

    // Add method to initialize WebRTC connection
    private fun initializeWebRTC(sessionId: String, currentUserUid: String) {
        viewModelScope.launch {
            if (webRTCClient == null) {
                webRTCClient = WebRTCClient(
                    context,
                    sessionId,
                    currentUserUid,
                    db,
                    onRemoteStream = {
                        _chatConnected.value = true
                    },
                    onConnectionStateChange = { status ->
                        _connectionStatus.value = status
                        if (status == "Connected") {
                            _chatConnected.value = true
                        }
                    }
                )
                webRTCClient?.initPeerConnection()

                // Check if this is the caller or receiver
                db.collection("call_sessions").document(sessionId).get()
                    .addOnSuccessListener { doc ->
                        val userA = doc.getString("userA_uid")
                        val userB = doc.getString("userB_uid")
                        val existingOffer = doc.getString("offer")

                        when {
                            // If current user is userA and no offer exists, create offer
                            userA == currentUserUid && existingOffer.isNullOrEmpty() -> {
                                webRTCClient?.createOffer()
                            }
                            // If current user is userB and offer exists, create answer
                            userB == currentUserUid && !existingOffer.isNullOrEmpty() -> {
                                webRTCClient?.setRemoteDescription(
                                    org.webrtc.SessionDescription.Type.OFFER,
                                    existingOffer
                                )
                                webRTCClient?.createAnswer()
                            }
                            // If current user is userA and offer exists, wait for answer
                            userA == currentUserUid && !existingOffer.isNullOrEmpty() -> {
                                // Just wait for the answer, WebRTC client will handle it
                            }
                        }
                    }
            }
        }
    }

    // Add method to handle navigation away from call
    fun setCallBackground() {
        if (_callState.value == CallState.IN_CALL) {
            _callState.value = CallState.CALL_BACKGROUND
        }
    }

    // Add method to handle navigation back to call
    fun setCallForeground() {
        if (_callState.value == CallState.CALL_BACKGROUND) {
            _callState.value = CallState.IN_CALL
        }
    }

    fun requestChat() {
        // This method is now simplified since WebRTC is already initialized
        if (!_chatRequestSent.value) {
            _chatRequestSent.value = true
            // WebRTC connection should already be established
            if (webRTCClient == null) {
                // Fallback: initialize if not already done
                val sessionId = _ongoingCallSession.value
                val uid = _currentUserUid.value
                if (sessionId != null && uid != null) {
                    initializeWebRTC(sessionId, uid)
                }
            }
        }
    }

    fun toggleMic() {
        _isMicEnabled.value = !_isMicEnabled.value
        webRTCClient?.toggleMic(_isMicEnabled.value)
    }

    fun answerCall(sessionId: String, currentUserUid: String) {
        _ongoingCallSession.value = sessionId
        _callState.value = CallState.IN_CALL
        _currentUserUid.value = currentUserUid

        // Initialize WebRTC for answering
        initializeWebRTC(sessionId, currentUserUid)
    }

    fun endCall() {
        webRTCClient?.close()
        webRTCClient = null
        _ongoingCallSession.value = null
        _isWaiting.value = false
        _currentUserUid.value = null
        _chatRequestSent.value = false
        _chatConnected.value = false
        _callState.value = CallState.IDLE
        _connectionStatus.value = "Disconnected"

        // Remove listeners
        callListener?.remove()
        callListener = null

        // Clean up any remaining session data
        _currentUserUid.value?.let { uid ->
            db.collection("waiting_users").document(uid).delete()
        }
    }

    fun setTimeoutMessage(msg: String) {
        _timeoutMessage.value = msg
        _isWaiting.value = false
        _callState.value = CallState.IDLE
    }

    fun clearTimeoutMessage() {
        _timeoutMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        callListener?.remove()
        webRTCClient?.close()
    }
}