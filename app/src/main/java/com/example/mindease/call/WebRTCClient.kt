package com.example.mindease.call

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.*
import java.nio.ByteBuffer
import kotlinx.coroutines.*

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

    // Data Channel for private chat
    private var dataChannel: DataChannel? = null
    private var onMessageReceivedListener: ((String) -> Unit)? = null
    private var isOfferer = false
    private var isInitialized = false
    private var connectionAttempts = 0
    private val maxConnectionAttempts = 3
    private var isConnected = false
    private var lastOfferSdp: String? = null
    private var remoteDescriptionSet = false
    private var localDescriptionSet = false

    // Modern coroutine scope for cleanup
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "WebRTCClient"
        private var isFactoryInitialized = false
        private var globalFactory: PeerConnectionFactory? = null

        @Synchronized
        fun initializeFactory(context: Context): PeerConnectionFactory? {
            if (!isFactoryInitialized) {
                try {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions.builder(context)
                            .setEnableInternalTracer(false)
                            .createInitializationOptions()
                    )
                    isFactoryInitialized = true

                    val eglBase = EglBase.create()
                    val encoderFactory = DefaultVideoEncoderFactory(
                        eglBase.eglBaseContext, true, true
                    )
                    val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

                    globalFactory = PeerConnectionFactory.builder()
                        .setVideoEncoderFactory(encoderFactory)
                        .setVideoDecoderFactory(decoderFactory)
                        .createPeerConnectionFactory()

                    Log.d(TAG, "WebRTC Factory initialized successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing WebRTC Factory", e)
                }
            }
            return globalFactory
        }
    }

    init {
        factory = initializeFactory(context)
        Log.d(TAG, "WebRTCClient initialized for session: $sessionId, user: $currentUserUid")
    }

    fun initPeerConnection() {
        if (isInitialized) {
            Log.w(TAG, "Peer connection already initialized")
            return
        }

        clientScope.launch {
            try {
                Log.d(TAG, "Initializing peer connection...")

                // Enhanced audio constraints for better quality
                val audioConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
                }

                localAudioSource = factory?.createAudioSource(audioConstraints)
                localAudioTrack = factory?.createAudioTrack("AUDIO_TRACK_${System.currentTimeMillis()}", localAudioSource)
                localStream = factory?.createLocalMediaStream("LOCAL_STREAM_${System.currentTimeMillis()}")?.apply {
                    localAudioTrack?.let { addTrack(it) }
                }

                // SIMPLIFIED ICE servers - use reliable ones only
                val iceServers = listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
                )

                // SIMPLIFIED RTC configuration - remove problematic settings
                val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                    bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                    rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                    iceTransportsType = PeerConnection.IceTransportsType.ALL
                }

                peerConnection = factory?.createPeerConnection(
                    rtcConfig,
                    object : PeerConnection.Observer {
                        override fun onIceCandidate(candidate: IceCandidate) {
                            Log.d(TAG, "ICE Candidate generated: ${candidate.sdp}")
                            addIceCandidateToFirestore(candidate)
                        }

                        override fun onAddStream(stream: MediaStream) {
                            Log.d(TAG, "Remote stream added with ${stream.audioTracks.size} audio tracks")
                            handleConnectionEstablished(stream)
                        }

                        override fun onSignalingChange(state: PeerConnection.SignalingState) {
                            Log.d(TAG, "Signaling state changed: $state")
                            when (state) {
                                PeerConnection.SignalingState.STABLE -> {
                                    Log.d(TAG, "Signaling is STABLE - connection should be ready")
                                    // Check if we have both local and remote descriptions set
                                    if (localDescriptionSet && remoteDescriptionSet && !isConnected) {
                                        Log.d(TAG, "Both descriptions set and signaling stable - marking as connected")
                                        handleConnectionEstablished(null)
                                    }
                                }
                                else -> Log.d(TAG, "Signaling state: $state")
                            }
                        }

                        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                            Log.d(TAG, "ICE Connection state changed: $state")
                            handleConnectionStateChange(state)
                        }

                        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                            Log.d(TAG, "Peer connection state changed: $newState")
                            handlePeerConnectionStateChange(newState)
                        }

                        override fun onIceConnectionReceivingChange(receiving: Boolean) {
                            Log.d(TAG, "ICE connection receiving change: $receiving")
                            if (receiving && !isConnected && localDescriptionSet && remoteDescriptionSet) {
                                Log.d(TAG, "ICE is receiving data - connection established")
                                handleConnectionEstablished(null)
                            }
                        }

                        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                            Log.d(TAG, "ICE Gathering state: $state")
                        }

                        override fun onRemoveStream(stream: MediaStream) {
                            Log.d(TAG, "Remote stream removed")
                        }

                        override fun onDataChannel(dc: DataChannel) {
                            Log.d(TAG, "Data channel received from peer: ${dc.label()}")
                            dataChannel = dc
                            setupDataChannelListener(dc)
                        }

                        override fun onRenegotiationNeeded() {
                            Log.d(TAG, "Renegotiation needed")
                        }

                        override fun onAddTrack(receiver: RtpReceiver, streams: Array<MediaStream>) {
                            Log.d(TAG, "Track added: ${streams.size} streams, track kind: ${receiver.track()?.kind()}")
                            if (streams.isNotEmpty()) {
                                handleConnectionEstablished(streams[0])
                            }
                        }

                        override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {
                            Log.d(TAG, "Selected candidate pair changed - LOCAL: ${event.local} REMOTE: ${event.remote}")
                            // This is a strong indicator that connection is working
                            if (!isConnected && localDescriptionSet && remoteDescriptionSet) {
                                Log.d(TAG, "Candidate pair selected - connection established")
                                handleConnectionEstablished(null)
                            }
                        }

                        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
                            Log.d(TAG, "ICE candidates removed: ${candidates.size}")
                        }
                    }
                )

                // Add audio track to peer connection
                localAudioTrack?.let { track ->
                    try {
                        localStream?.let { stream ->
                            val rtpSender = peerConnection?.addTrack(track, listOf(stream.id))
                            Log.d(TAG, "Added audio track to peer connection: ${rtpSender != null}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding audio track", e)
                    }
                }

                // Start listeners AFTER peer connection is set up
                listenForRemoteOffer()
                listenForRemoteAnswer()
                listenForRemoteCandidates()

                isInitialized = true
                Log.d(TAG, "Peer connection initialized successfully")

                // Set initial connection state
                onConnectionStateChange("Connecting")

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing peer connection", e)
                onConnectionStateChange("Failed")
            }
        }
    }

    // CRITICAL FIX: Centralized connection establishment handler
    private fun handleConnectionEstablished(stream: MediaStream?) {
        if (!isConnected) {
            isConnected = true
            connectionAttempts = 0

            clientScope.launch(Dispatchers.Main) {
                Log.d(TAG, "✅ CONNECTION ESTABLISHED")
                onConnectionStateChange("Connected")
                stream?.let { onRemoteStream(it) }
            }
        }
    }

    private fun handleConnectionStateChange(state: PeerConnection.IceConnectionState) {
        clientScope.launch(Dispatchers.Main) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    Log.d(TAG, "ICE Connection established successfully")
                    handleConnectionEstablished(null)
                }
                PeerConnection.IceConnectionState.COMPLETED -> {
                    Log.d(TAG, "ICE Connection completed")
                    handleConnectionEstablished(null)
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.w(TAG, "ICE Connection disconnected")
                    if (isConnected) {
                        onConnectionStateChange("Disconnected")
                        isConnected = false
                    }
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.e(TAG, "ICE Connection failed - attempt $connectionAttempts")
                    connectionAttempts++
                    if (connectionAttempts < maxConnectionAttempts) {
                        Log.d(TAG, "Retrying connection (attempt $connectionAttempts/$maxConnectionAttempts)")
                        onConnectionStateChange("Retrying")
                        attemptIceRestart()
                    } else {
                        onConnectionStateChange("Failed")
                        isConnected = false
                    }
                }
                PeerConnection.IceConnectionState.CHECKING -> {
                    Log.d(TAG, "ICE Connection checking")
                    if (!isConnected) {
                        onConnectionStateChange("Connecting")
                    }
                }
                else -> {
                    Log.d(TAG, "ICE Connection state: $state")
                    if (!isConnected) {
                        onConnectionStateChange("Connecting")
                    }
                }
            }
        }
    }

    private fun handlePeerConnectionStateChange(state: PeerConnection.PeerConnectionState) {
        clientScope.launch(Dispatchers.Main) {
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    Log.d(TAG, "Peer connection established successfully")
                    handleConnectionEstablished(null)
                }
                PeerConnection.PeerConnectionState.FAILED -> {
                    Log.e(TAG, "Peer connection failed")
                    onConnectionStateChange("Failed")
                    isConnected = false
                }
                PeerConnection.PeerConnectionState.DISCONNECTED -> {
                    Log.w(TAG, "Peer connection disconnected")
                    if (isConnected) {
                        onConnectionStateChange("Disconnected")
                        isConnected = false
                    }
                }
                else -> {
                    Log.d(TAG, "Peer connection state: $state")
                    if (!isConnected) {
                        onConnectionStateChange("Connecting")
                    }
                }
            }
        }
    }

    private fun attemptIceRestart() {
        clientScope.launch {
            try {
                Log.d(TAG, "Attempting ICE restart...")
                delay(2000)

                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                }

                if (isOfferer) {
                    peerConnection?.createOffer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription) {
                            Log.d(TAG, "ICE restart offer created successfully")
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    Log.d(TAG, "ICE restart local description set")
                                    localDescriptionSet = true
                                    saveOfferToFirestore(desc.description)
                                }
                                override fun onSetFailure(error: String) {
                                    Log.e(TAG, "Failed to set ICE restart local description: $error")
                                }
                                override fun onCreateSuccess(desc: SessionDescription?) {}
                                override fun onCreateFailure(error: String?) {}
                            }, desc)
                        }
                        override fun onCreateFailure(error: String) {
                            Log.e(TAG, "Failed to create ICE restart offer: $error")
                        }
                        override fun onSetSuccess() {}
                        override fun onSetFailure(error: String) {}
                    }, constraints)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during ICE restart", e)
            }
        }
    }

    private fun addIceCandidateToFirestore(candidate: IceCandidate) {
        clientScope.launch {
            val candidateData = mapOf(
                "sdpMid" to (candidate.sdpMid ?: ""),
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "sdp" to candidate.sdp,
                "sender" to currentUserUid,
                "timestamp" to com.google.firebase.Timestamp.now()
            )

            try {
                db.collection("call_sessions").document(sessionId)
                    .collection("candidates")
                    .add(candidateData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully added ICE candidate to Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to add ICE candidate to Firestore", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding ICE candidate", e)
            }
        }
    }

    private fun setupDataChannel() {
        if (dataChannel != null) {
            Log.w(TAG, "Data channel already exists")
            return
        }

        Log.d(TAG, "Setting up data channel as offerer")
        val init = DataChannel.Init().apply {
            ordered = true
            maxRetransmitTimeMs = 3000
            maxRetransmits = 3
        }

        dataChannel = peerConnection?.createDataChannel("chat", init)
        dataChannel?.let { setupDataChannelListener(it) }
        isOfferer = true
    }

    private fun setupDataChannelListener(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {
                Log.d(TAG, "Data channel buffered amount: $amount")
            }

            override fun onStateChange() {
                Log.d(TAG, "Data channel state: ${dc.state()}")
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                try {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val message = String(bytes, Charsets.UTF_8)
                    Log.d(TAG, "Received message: $message")
                    onMessageReceivedListener?.invoke(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing received message", e)
                }
            }
        })
    }

    fun createOffer() {
        if (peerConnection == null) {
            Log.e(TAG, "Cannot create offer: peer connection is null")
            return
        }

        clientScope.launch {
            Log.d(TAG, "Creating offer...")
            setupDataChannel()

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    Log.d(TAG, "Offer created successfully, type: ${desc.type}")
                    lastOfferSdp = desc.description

                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description (offer) set successfully")
                            localDescriptionSet = true
                            saveOfferToFirestore(desc.description)
                        }

                        override fun onSetFailure(error: String) {
                            Log.e(TAG, "Failed to set local description (offer): $error")
                            onConnectionStateChange("Failed")
                        }

                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onCreateFailure(error: String?) {}
                    }, desc)
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String) {
                    Log.e(TAG, "Failed to create offer: $error")
                    onConnectionStateChange("Failed")
                }

                override fun onSetFailure(error: String) {
                    Log.e(TAG, "Failed to set description during offer creation: $error")
                }
            }, constraints)
        }
    }

    private fun saveOfferToFirestore(sdp: String) {
        clientScope.launch {
            try {
                db.collection("call_sessions").document(sessionId)
                    .update(mapOf(
                        "offer" to sdp,
                        "status" to "offering"
                    ))
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully saved offer to Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to save offer to Firestore", e)
                        onConnectionStateChange("Failed")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving offer", e)
            }
        }
    }

    fun createAnswer() {
        if (peerConnection == null) {
            Log.e(TAG, "Cannot create answer: peer connection is null")
            return
        }

        clientScope.launch {
            Log.d(TAG, "Creating answer...")

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    Log.d(TAG, "Answer created successfully, type: ${desc.type}")

                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description (answer) set successfully")
                            localDescriptionSet = true
                            saveAnswerToFirestore(desc.description)
                        }

                        override fun onSetFailure(error: String) {
                            Log.e(TAG, "Failed to set local description (answer): $error")
                            onConnectionStateChange("Failed")
                        }

                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onCreateFailure(error: String?) {}
                    }, desc)
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String) {
                    Log.e(TAG, "Failed to create answer: $error")
                    onConnectionStateChange("Failed")
                }

                override fun onSetFailure(error: String) {
                    Log.e(TAG, "Failed to set description during answer creation: $error")
                }
            }, constraints)
        }
    }

    private fun saveAnswerToFirestore(sdp: String) {
        clientScope.launch {
            try {
                db.collection("call_sessions").document(sessionId)
                    .update(mapOf(
                        "answer" to sdp,
                        "status" to "answered"
                    ))
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully saved answer to Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to save answer to Firestore", e)
                        onConnectionStateChange("Failed")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving answer", e)
            }
        }
    }

    fun setRemoteDescription(type: SessionDescription.Type, sdp: String) {
        if (peerConnection == null) {
            Log.e(TAG, "Cannot set remote description: peer connection is null")
            return
        }

        clientScope.launch {
            Log.d(TAG, "Setting remote description: $type")
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote description set successfully for type: $type")
                    remoteDescriptionSet = true

                    // Check if both descriptions are set and we should be connected
                    if (localDescriptionSet && remoteDescriptionSet && !isConnected) {
                        // Give a moment for ICE to work, then check connection status
                        clientScope.launch {
                            delay(2000) // Wait for ICE gathering

                            // Check various connection indicators
                            val iceState = peerConnection?.iceConnectionState()
                            val connectionState = peerConnection?.connectionState()

                            Log.d(TAG, "Connection check - ICE: $iceState, Peer: $connectionState, Connected: $isConnected")

                            if (!isConnected && (
                                        iceState == PeerConnection.IceConnectionState.CONNECTED ||
                                                iceState == PeerConnection.IceConnectionState.COMPLETED ||
                                                connectionState == PeerConnection.PeerConnectionState.CONNECTED
                                        )) {
                                Log.d(TAG, "Connection established after remote description set")
                                handleConnectionEstablished(null)
                            }
                        }
                    }
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Failed to set remote description: $error")
                    onConnectionStateChange("Failed")
                }

                override fun onCreateSuccess(desc: SessionDescription?) {}
                override fun onCreateFailure(error: String?) {}
            }, SessionDescription(type, sdp))
        }
    }

    // SIMPLIFIED offer listener - avoid duplicate processing
    private fun listenForRemoteOffer() {
        offerListener = db.collection("call_sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for offer: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.getString("offer")?.let { sdp ->
                    // Only process if we haven't set a remote description yet and we're not the offerer
                    if (!remoteDescriptionSet && !isOfferer && sdp != lastOfferSdp) {
                        Log.d(TAG, "Received new remote offer, processing...")
                        lastOfferSdp = sdp
                        setRemoteDescription(SessionDescription.Type.OFFER, sdp)

                        // Create answer after a short delay
                        clientScope.launch {
                            delay(1000)
                            if (remoteDescriptionSet && !localDescriptionSet) {
                                Log.d(TAG, "Creating answer after remote offer set")
                                createAnswer()
                            }
                        }
                    }
                }
            }
    }

    private fun listenForRemoteAnswer() {
        answerListener = db.collection("call_sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for answer: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.getString("answer")?.let { sdp ->
                    if (!remoteDescriptionSet && isOfferer) {
                        Log.d(TAG, "Received remote answer, setting as remote description")
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
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val sender = data["sender"] as? String

                        if (sender != null && sender != currentUserUid) {
                            val sdpMid = data["sdpMid"] as? String ?: ""
                            val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                            val sdp = data["sdp"] as? String ?: ""

                            Log.d(TAG, "Adding ICE candidate from $sender")
                            val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)

                            clientScope.launch {
                                try {
                                    peerConnection?.addIceCandidate(candidate)
                                    Log.d(TAG, "Successfully added ICE candidate")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to add ICE candidate: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
    }

    fun setOnMessageReceivedListener(listener: (String) -> Unit) {
        onMessageReceivedListener = listener
    }

    suspend fun sendChatMessage(message: String): Boolean = withContext(Dispatchers.Main) {
        dataChannel?.let { dc ->
            if (dc.state() == DataChannel.State.OPEN) {
                try {
                    val buffer = DataChannel.Buffer(
                        ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8)),
                        false
                    )
                    val result = dc.send(buffer)
                    if (result) {
                        Log.d(TAG, "Successfully sent message: $message")
                    } else {
                        Log.w(TAG, "Failed to send message: $message")
                    }
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending message", e)
                    false
                }
            } else {
                Log.w(TAG, "Data channel not open, state: ${dc.state()}")
                false
            }
        } ?: run {
            Log.w(TAG, "Data channel not available")
            false
        }
    }

    fun isDataChannelOpen(): Boolean {
        return dataChannel?.state() == DataChannel.State.OPEN
    }

    fun toggleMic(enabled: Boolean) {
        clientScope.launch {
            peerConnection?.senders?.find { it.track()?.kind() == "audio" }?.track()?.setEnabled(enabled)
                ?: localAudioTrack?.setEnabled(enabled)
            Log.d(TAG, "Microphone ${if (enabled) "enabled" else "disabled"}")
        }
    }

    fun close() {
        Log.d(TAG, "Closing WebRTC connection...")
        clientScope.launch {
            // Remove all listeners
            offerListener?.remove()
            answerListener?.remove()
            candidatesListener?.remove()

            // Close connections
            dataChannel?.close()
            peerConnection?.close()

            // Dispose resources
            localAudioTrack?.dispose()
            localAudioSource?.dispose()

            // Reset state
            isInitialized = false
            isOfferer = false
            connectionAttempts = 0
            isConnected = false
            localDescriptionSet = false
            remoteDescriptionSet = false

            Log.d(TAG, "WebRTC resources cleaned up")
        }

        clientScope.cancel()
    }
}