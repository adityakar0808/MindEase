package com.example.mindease.call

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class CallState {
    IDLE, WAITING, IN_CALL, CALL_BACKGROUND
}

class CallViewModel(
    private val context: Context,
    private val db: FirebaseFirestore
) : ViewModel() {

    private var webRTCClient: WebRTCClient? = null
    private var callListener: ListenerRegistration? = null

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

    fun shouldShowBanner(): Boolean {
        return _callState.value == CallState.CALL_BACKGROUND
    }

    fun startWaiting(uid: String, nickname: String, stressReason: String) {
        Log.d("CallViewModel", "Starting waiting for user: $uid")
        _isWaiting.value = true
        _callState.value = CallState.WAITING

        // Create session ID
        val sessionId = db.collection("call_sessions").document().id

        // Add to waiting users with better error handling
        val waitingUserData = mapOf(
            "nickname" to nickname,
            "stressReason" to stressReason,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "sessionId" to sessionId
        )

        db.collection("waiting_users").document(uid).set(waitingUserData)
            .addOnSuccessListener {
                Log.d("CallViewModel", "Successfully added to waiting_users: $uid")

                // Create the call session
                val sessionData = mapOf(
                    "userA_uid" to uid,
                    "userB_uid" to null,
                    "status" to "waiting",
                    "timestamp" to com.google.firebase.Timestamp.now()
                )

                db.collection("call_sessions").document(sessionId).set(sessionData)
                    .addOnSuccessListener {
                        Log.d("CallViewModel", "Call session created: $sessionId")
                        listenForCallConnection(sessionId, uid)
                    }
                    .addOnFailureListener { e ->
                        Log.e("CallViewModel", "Failed to create call session", e)
                        _isWaiting.value = false
                        _callState.value = CallState.IDLE
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CallViewModel", "Failed to add to waiting_users", e)
                _isWaiting.value = false
                _callState.value = CallState.IDLE
            }
    }

    private fun listenForCallConnection(sessionId: String, currentUserUid: String) {
        callListener?.remove()
        callListener = db.collection("call_sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CallViewModel", "Error listening for call connection", error)
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val userB = doc.getString("userB_uid")
                    val status = doc.getString("status")

                    Log.d("CallViewModel", "Call status update: userB=$userB, status=$status")

                    if (userB != null && status == "ringing" && _callState.value == CallState.WAITING) {
                        Log.d("CallViewModel", "Someone joined! Starting call...")
                        startCall(sessionId, currentUserUid)
                    }
                }
            }
    }

    fun cancelWaiting(uid: String) {
        Log.d("CallViewModel", "Cancelling waiting for user: $uid")
        _isWaiting.value = false
        _callState.value = CallState.IDLE
        _ongoingCallSession.value = null

        callListener?.remove()
        callListener = null

        // Remove from waiting users
        db.collection("waiting_users").document(uid).delete()
            .addOnSuccessListener {
                Log.d("CallViewModel", "Removed from waiting_users: $uid")
            }

        // Clean up session
        db.collection("call_sessions")
            .whereEqualTo("userA_uid", uid)
            .whereEqualTo("status", "waiting")
            .get()
            .addOnSuccessListener { querySnapshot ->
                querySnapshot.documents.forEach { doc ->
                    doc.reference.delete()
                        .addOnSuccessListener {
                            Log.d("CallViewModel", "Deleted call session: ${doc.id}")
                        }
                }
            }
    }

    fun startCall(sessionId: String, currentUserUid: String) {
        Log.d("CallViewModel", "Starting call: $sessionId for user: $currentUserUid")
        _isWaiting.value = false
        _callState.value = CallState.IN_CALL
        _ongoingCallSession.value = sessionId
        _currentUserUid.value = currentUserUid

        // Remove from waiting users
        db.collection("waiting_users").document(currentUserUid).delete()

        callListener?.remove()
        callListener = null

        // Initialize WebRTC connection
        initializeWebRTC(sessionId, currentUserUid)
    }

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
                            userA == currentUserUid && existingOffer.isNullOrEmpty() -> {
                                webRTCClient?.createOffer()
                            }
                            userB == currentUserUid && !existingOffer.isNullOrEmpty() -> {
                                webRTCClient?.setRemoteDescription(
                                    org.webrtc.SessionDescription.Type.OFFER,
                                    existingOffer
                                )
                                webRTCClient?.createAnswer()
                            }
                        }
                    }
            }
        }
    }

    fun setCallBackground() {
        if (_callState.value == CallState.IN_CALL) {
            _callState.value = CallState.CALL_BACKGROUND
        }
    }

    fun setCallForeground() {
        if (_callState.value == CallState.CALL_BACKGROUND) {
            _callState.value = CallState.IN_CALL
        }
    }

    fun requestChat() {
        if (!_chatRequestSent.value) {
            _chatRequestSent.value = true
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
        _isMicEnabled.value = !_isMicEnabled.value
        webRTCClient?.toggleMic(_isMicEnabled.value)
    }

    fun answerCall(sessionId: String, currentUserUid: String) {
        _ongoingCallSession.value = sessionId
        _callState.value = CallState.IN_CALL
        _currentUserUid.value = currentUserUid
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

        callListener?.remove()
        callListener = null

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
