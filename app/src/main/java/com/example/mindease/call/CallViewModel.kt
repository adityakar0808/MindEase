package com.example.mindease.call

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private var webRTCClient: WebRTCClient? = null
    private var snapshotListener: ListenerRegistration? = null

    // Ongoing call session ID
    private val _ongoingCallSession = MutableStateFlow<String?>(null)
    val ongoingCallSession: StateFlow<String?> = _ongoingCallSession

    // Dedicated chat session ID
    private val _chatSessionId = MutableStateFlow<String?>(null)
    val chatSessionId: StateFlow<String?> = _chatSessionId

    // Current user's UID in the ongoing call
    private var _currentUserUid: String = "unknown"
    val currentUserUid: String get() = _currentUserUid

    // Microphone state
    private val _isMicEnabled = MutableStateFlow(true)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled

    // Chat request sent
    private val _chatRequestSent = MutableStateFlow(false)
    val chatRequestSent: StateFlow<Boolean> = _chatRequestSent

    // Chat fully connected (both consented)
    private val _chatConnected = MutableStateFlow(false)
    val chatConnected: StateFlow<Boolean> = _chatConnected

    /** 🔹 Start a new call */
    fun startCall(sessionId: String, currentUserUid: String = "unknown") {
        _ongoingCallSession.value = sessionId
        _currentUserUid = currentUserUid

        webRTCClient = WebRTCClient(
            context = getApplication(),
            sessionId = sessionId,
            currentUserUid = currentUserUid,
            db = db
        )

        webRTCClient?.initPeerConnection()

        webRTCClient?.createOffer { offerSdp ->
            db.collection("call_sessions").document(sessionId).set(
                mapOf(
                    "offer" to offerSdp,
                    "from" to currentUserUid
                )
            )
        }

        listenForCallUpdates(sessionId, currentUserUid)
    }

    /** 🔹 Answer incoming call */
    fun answerCall(sessionId: String, offerSdp: String, currentUserUid: String = "unknown") {
        _ongoingCallSession.value = sessionId
        _currentUserUid = currentUserUid

        webRTCClient = WebRTCClient(
            context = getApplication(),
            sessionId = sessionId,
            currentUserUid = currentUserUid,
            db = db
        )

        webRTCClient?.initPeerConnection()
        webRTCClient?.setRemoteDescription(SessionDescription.Type.OFFER, offerSdp)

        webRTCClient?.createAnswer { answerSdp ->
            db.collection("call_sessions").document(sessionId)
                .update("answer", answerSdp)
        }

        listenForCallUpdates(sessionId, currentUserUid)
    }

    /** 🔹 Listen for answer, ICE candidates & chat consent */
    private fun listenForCallUpdates(sessionId: String, currentUserUid: String) {
        snapshotListener?.remove()
        snapshotListener = db.collection("call_sessions").document(sessionId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    snapshot.getString("answer")?.let { answerSdp ->
                        webRTCClient?.setRemoteDescription(SessionDescription.Type.ANSWER, answerSdp)
                    }

                    val chatA = snapshot.getBoolean("consentChatA") ?: false
                    val chatB = snapshot.getBoolean("consentChatB") ?: false
                    _chatConnected.value = chatA && chatB

                    // Set chat session when both users consent
                    if (chatA && chatB) _chatSessionId.value = sessionId
                }
            }

        db.collection("call_sessions").document(sessionId)
            .collection("candidates")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { dc ->
                    val data = dc.document.data
                    val sender = data["sender"] as? String
                    if (sender != null && sender != currentUserUid) {
                        val sdpMid = data["sdpMid"] as? String ?: return@forEach
                        val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: return@forEach
                        val sdp = data["sdp"] as? String ?: return@forEach
                        webRTCClient?.addRemoteIceCandidate(sdpMid, sdpMLineIndex, sdp)
                    }
                }
            }
    }

    /** 🔹 Toggle mic mute/unmute */
    fun toggleMic() {
        val newValue = !_isMicEnabled.value
        _isMicEnabled.value = newValue
        webRTCClient?.toggleMic(newValue)
    }

    /** 🔹 Request chat connection */
    fun requestChat(sessionId: String, currentUserUid: String = _currentUserUid) {
        if (_chatRequestSent.value) return
        _chatRequestSent.value = true

        val consentField = if (currentUserUid == "userA") "consentChatA" else "consentChatB"
        db.collection("call_sessions").document(sessionId)
            .update(consentField, true)
    }

    /** 🔹 End call session */
    fun endCall() {
        val sessionId = _ongoingCallSession.value ?: return
        viewModelScope.launch {
            try { db.collection("call_sessions").document(sessionId).delete() } catch (_: Exception) {}
        }

        webRTCClient?.close()
        webRTCClient = null

        snapshotListener?.remove()
        snapshotListener = null

        _ongoingCallSession.value = null
        _chatConnected.value = false
        _chatRequestSent.value = false
        _chatSessionId.value = null
    }
}
