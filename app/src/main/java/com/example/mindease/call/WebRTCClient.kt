package com.example.mindease.call

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import org.webrtc.*

/**
 * Audio-only WebRTC helper. Handles:
 * - PeerConnection + local mic track
 * - Offer/Answer setLocal/setRemote
 * - Publishing ICE candidates to Firestore (with senderUid)
 * - Mic mute/unmute
 **/
class WebRTCClient(
    private val context: Context, // Removed private val
    private val sessionId: String,
    private val currentUserUid: String,
    private val db: FirebaseFirestore,
    private val onRemoteStream: (MediaStream) -> Unit = {}
) {

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localStream: MediaStream? = null

    init {
        // Required initialization with encoder/decoder factories
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )

        val eglBase = EglBase.create()
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            /* enableIntelVp8Encoder */ true,
            /* enableH264HighProfile */ true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    /** Create a mic-only local stream and attach to PeerConnection. */
    fun initPeerConnection() {
        // 1) Create local audio stream
        localAudioSource = factory?.createAudioSource(MediaConstraints())
        localAudioTrack = factory?.createAudioTrack("AUDIO_TRACK", localAudioSource)
        localStream = factory?.createLocalMediaStream("LOCAL_STREAM")?.apply {
            localAudioTrack?.let { addTrack(it) }
        }

        // 2) Create PC + observers
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        peerConnection = factory?.createPeerConnection(
            iceServers,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    db.collection("call_sessions").document(sessionId)
                        .collection("candidates")
                        .add(
                            mapOf(
                                "sdpMid" to (candidate.sdpMid ?: ""),
                                "sdpMLineIndex" to candidate.sdpMLineIndex,
                                "sdp" to candidate.sdp,
                                "sender" to currentUserUid
                            )
                        )
                }

                override fun onAddStream(stream: MediaStream) {
                    onRemoteStream(stream) // remote audio will play automatically
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(dc: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(
                    receiver: RtpReceiver,
                    streams: Array<out MediaStream>
                ) {}

                // ✅ New required methods in latest WebRTC
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {}
                override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {} // Added
            }
        )

        // 3) Attach local stream
        localStream?.let { peerConnection?.addStream(it) }
    }

    fun createOffer(updateToFirestore: (String) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(this, desc)
                updateToFirestore(desc.description)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

    fun createAnswer(updateToFirestore: (String) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(this, desc)
                updateToFirestore(desc.description)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

    fun setRemoteDescription(type: SessionDescription.Type, sdp: String) {
        val remote = SessionDescription(type, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remote)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
    }

    fun toggleMic(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    /** Close PC + dispose tracks/sources. Call before deleting Firestore doc. */
    fun close() {
        try { peerConnection?.close() } catch (_: Exception) {}
        peerConnection = null

        try { localAudioTrack?.dispose() } catch (_: Exception) {}
        localAudioTrack = null

        try { localAudioSource?.dispose() } catch (_: Exception) {}
        localAudioSource = null

        localStream = null
    }
}
