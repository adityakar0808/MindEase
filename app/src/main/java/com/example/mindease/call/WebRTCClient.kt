package com.example.mindease.call

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.*

class WebRTCClient(
    private val context: Context,
    private val sessionId: String,
    private val currentUserUid: String,
    private val db: FirebaseFirestore,
    private val onRemoteStream: (MediaStream) -> Unit = {},
    private val onConnectionStateChange: (String) -> Unit = {}
) {

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localStream: MediaStream? = null

    private var offerListener: ListenerRegistration? = null
    private var answerListener: ListenerRegistration? = null
    private var candidatesListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "WebRTCClient"
    }

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )

        val eglBase = EglBase.create()
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext, true, true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "WebRTCClient initialized for session: $sessionId, user: $currentUserUid")
    }

    fun initPeerConnection() {
        Log.d(TAG, "Initializing peer connection...")

        localAudioSource = factory?.createAudioSource(MediaConstraints())
        localAudioTrack = factory?.createAudioTrack("AUDIO_TRACK", localAudioSource)
        localStream = factory?.createLocalMediaStream("LOCAL_STREAM")?.apply {
            localAudioTrack?.let { addTrack(it) }
        }

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        peerConnection = factory?.createPeerConnection(
            iceServers,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d(TAG, "ICE Candidate generated")
                    db.collection("call_sessions").document(sessionId)
                        .collection("candidates")
                        .add(
                            mapOf(
                                "sdpMid" to (candidate.sdpMid ?: ""),
                                "sdpMLineIndex" to candidate.sdpMLineIndex,
                                "sdp" to candidate.sdp,
                                "sender" to currentUserUid,
                                "timestamp" to com.google.firebase.Timestamp.now()
                            )
                        )
                }

                override fun onAddStream(stream: MediaStream) {
                    Log.d(TAG, "Remote stream added")
                    onRemoteStream(stream)
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState) {
                    Log.d(TAG, "Signaling state changed: $state")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "ICE Connection state changed: $state")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            onConnectionStateChange("Connected")
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            onConnectionStateChange("Disconnected")
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            onConnectionStateChange("Failed")
                        }
                        else -> {
                            onConnectionStateChange("Connecting")
                        }
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    Log.d(TAG, "Peer connection state changed: $newState")
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            onConnectionStateChange("Connected")
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            onConnectionStateChange("Failed")
                        }
                        else -> {
                            onConnectionStateChange("Connecting")
                        }
                    }
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                    Log.d(TAG, "ICE Gathering state: $state")
                }
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(dc: DataChannel) {}
                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed")
                }
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                    Log.d(TAG, "Track added: ${streams.size} streams")
                }
                override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            }
        )

        localStream?.let { peerConnection?.addStream(it) }
        listenForRemoteAnswer()
        listenForRemoteCandidates()

        Log.d(TAG, "Peer connection initialized successfully")
    }

    fun createOffer() {
        Log.d(TAG, "Creating offer...")
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d(TAG, "Offer created successfully")
                peerConnection?.setLocalDescription(this, desc)
                db.collection("call_sessions").document(sessionId)
                    .update(mapOf(
                        "offer" to desc.description,
                        "status" to "offering"
                    ))
            }
            override fun onSetSuccess() {
                Log.d(TAG, "Local description set successfully")
            }
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "Failed to create offer: $error")
            }
            override fun onSetFailure(error: String) {
                Log.e(TAG, "Failed to set local description: $error")
            }
        }, constraints)
    }

    fun createAnswer() {
        Log.d(TAG, "Creating answer...")
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d(TAG, "Answer created successfully")
                peerConnection?.setLocalDescription(this, desc)
                db.collection("call_sessions").document(sessionId)
                    .update(mapOf(
                        "answer" to desc.description,
                        "status" to "answered"
                    ))
            }
            override fun onSetSuccess() {
                Log.d(TAG, "Local description (answer) set successfully")
            }
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "Failed to create answer: $error")
            }
            override fun onSetFailure(error: String) {
                Log.e(TAG, "Failed to set local description (answer): $error")
            }
        }, constraints)
    }

    fun setRemoteDescription(type: SessionDescription.Type, sdp: String) {
        Log.d(TAG, "Setting remote description: $type")
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description: $error")
            }
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, SessionDescription(type, sdp))
    }

    private fun listenForRemoteAnswer() {
        answerListener = db.collection("call_sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for answer: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.getString("answer")?.let { sdp ->
                    if (peerConnection?.remoteDescription == null) {
                        Log.d(TAG, "Received remote answer")
                        setRemoteDescription(SessionDescription.Type.ANSWER, sdp)
                    }
                }
            }
    }

    private fun listenForRemoteCandidates() {
        candidatesListener = db.collection("call_sessions").document(sessionId)
            .collection("candidates")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for candidates: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    val data = change.document.data
                    val sender = data["sender"] as? String
                    if (sender != null && sender != currentUserUid) {
                        val sdpMid = data["sdpMid"] as? String ?: ""
                        val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                        val sdp = data["sdp"] as? String ?: ""

                        Log.d(TAG, "Adding remote ICE candidate from $sender")
                        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                    }
                }
            }
    }

    fun toggleMic(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        Log.d(TAG, "Microphone ${if (enabled) "enabled" else "disabled"}")
    }

    fun close() {
        Log.d(TAG, "Closing WebRTC connection...")

        offerListener?.remove()
        answerListener?.remove()
        candidatesListener?.remove()

        try {
            peerConnection?.close()
            Log.d(TAG, "Peer connection closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing peer connection: ${e.message}")
        }

        peerConnection = null

        try { localAudioTrack?.dispose() } catch (e: Exception) {
            Log.e(TAG, "Error disposing audio track: ${e.message}")
        }
        localAudioTrack = null

        try { localAudioSource?.dispose() } catch (e: Exception) {
            Log.e(TAG, "Error disposing audio source: ${e.message}")
        }
        localAudioSource = null

        localStream = null

        Log.d(TAG, "WebRTC resources cleaned up")
    }
}