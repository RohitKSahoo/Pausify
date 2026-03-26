package com.rohit.voicepause.audio

import android.content.Context
import android.util.Log
import com.rohit.voicepause.Settings
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class VadProcessor :
    AudioCapture.AudioFrameListener,
    SpeechStateMachine.SpeechStateListener {

    companion object {
        private const val TAG = "VoicePause/VadProc"
        private const val NEAR_FACTOR = 1.6f
        
        // Circular buffer for ML validation (approx 1 second of audio at 16kHz)
        private const val ML_BUFFER_SIZE = 16000
        private const val ML_INFERENCE_INTERVAL_MS = 500L
    }

    interface VadProcessorListener {
        fun onSpeechDetected(rms: Int, isNear: Boolean)
        fun onVadError(error: String)
    }

    private val vadWrapper = WebRtcVadWrapper()
    private val audioCapture = AudioCapture()
    private lateinit var speechStateMachine: SpeechStateMachine
    private var speechClassifier: SpeechClassifier? = null
    private var speakerVerifier: SpeakerVerifier? = null

    // ===== ACTIVE PARAMETERS =====
    private var minSpeechMs = 400L
    private var minVoiceEnergy = 300
    private var vadAggressiveness = 2

    // ===== STATE =====
    private val isRunning = AtomicBoolean(false)
    private var listener: VadProcessorListener? = null
    private var context: Context? = null

    private var lastRms = 0
    private var lastIsNear = false
    
    // ML validation state
    private val mlBuffer = ShortArray(ML_BUFFER_SIZE)
    private var mlBufferPtr = 0
    private var lastMlInferenceTime = 0L

    // ======================
    // INIT
    // ======================

    fun initialize(
        context: Context,
        listener: VadProcessorListener,
        profile: AudioProfile
    ): Boolean {
        this.context = context
        this.listener = listener

        if (!vadWrapper.initialize(AudioCapture.SAMPLE_RATE)) {
            listener.onVadError("VAD init failed")
            return false
        }

        if (!profile.isCustom) {
            minSpeechMs = profile.minSpeechMs
            minVoiceEnergy = profile.minVoiceEnergy
            vadAggressiveness = profile.vadAggressiveness
        }

        speechStateMachine = SpeechStateMachine(vadWrapper, this)
        speechStateMachine.configure(minSpeechMs)

        if (!audioCapture.initialize(this)) {
            listener.onVadError("AudioCapture init failed")
            return false
        }
        
        // Initialize ML Classifier (Plan 1)
        if (Settings.isMlValidationEnabled(context)) {
            speechClassifier = SpeechClassifier(context)
            if (!speechClassifier!!.initialize()) {
                Log.w(TAG, "ML Classifier failed to initialize, will fallback to VAD-only")
            }
        }

        // Initialize Speaker Verifier (Plan 2)
        speakerVerifier = SpeakerVerifier(context)
        if (!speakerVerifier!!.initialize()) {
            Log.w(TAG, "Speaker Verifier failed to initialize, Plan 2 disabled")
        }

        Log.i(
            TAG,
            "Initialized: Profile=${profile.displayName} " +
                    "speech=$minSpeechMs energy=$minVoiceEnergy vad=$vadAggressiveness ML=${speechClassifier != null} SPK=${speakerVerifier != null}"
        )

        return true
    }


    // ======================
    // CUSTOM APPLY
    // ======================

    fun applyCustomProfile(
        minSpeech: Long,
        minEnergy: Int,
        vadMode: Int,
        sensitivity: Float
    ) {
        minSpeechMs = (minSpeech / sensitivity)
            .toLong()
            .coerceAtLeast(100)

        minVoiceEnergy = minEnergy
        vadAggressiveness = vadMode

        if (::speechStateMachine.isInitialized) {
            speechStateMachine.configure(minSpeechMs)
        }

        Log.i(
            TAG,
            "[CUSTOM APPLIED] speech=$minSpeechMs energy=$minVoiceEnergy vad=$vadAggressiveness"
        )
    }


    // ======================
    // CONTROL
    // ======================

    fun start(): Boolean {
        if (!audioCapture.startCapture()) {
            listener?.onVadError("Start capture failed")
            return false
        }

        isRunning.set(true)
        speechStateMachine.reset()
        speechStateMachine.configure(minSpeechMs)
        
        mlBufferPtr = 0
        lastMlInferenceTime = 0

        Log.i(TAG, "Started")
        return true
    }

    fun stop() {
        isRunning.set(false)
        audioCapture.stopCapture()
        speechStateMachine.reset()
        Log.i(TAG, "Stopped")
    }

    fun release() {
        stop()
        audioCapture.release()
        vadWrapper.destroy()
        speechClassifier?.release()
        speechClassifier = null
        speakerVerifier?.release()
        speakerVerifier = null
        listener = null
        context = null
    }

    fun isRunning(): Boolean = isRunning.get()


    // ======================
    // AUDIO CALLBACK
    // ======================

    override fun onAudioFrame(
        audioFrame: ShortArray,
        timestamp: Long
    ) {
        // Update circular buffer for ML
        updateMlBuffer(audioFrame)

        val rms = calculateRms(audioFrame)
        lastRms = rms

        if (rms < minVoiceEnergy) return

        val nearLimit = minVoiceEnergy * NEAR_FACTOR
        val isNear = rms >= nearLimit

        lastIsNear = isNear

        speechStateMachine.processFrame(audioFrame, timestamp)
    }

    private fun updateMlBuffer(frame: ShortArray) {
        if (frame.size > ML_BUFFER_SIZE) return
        
        val remaining = ML_BUFFER_SIZE - mlBufferPtr
        if (frame.size <= remaining) {
            System.arraycopy(frame, 0, mlBuffer, mlBufferPtr, frame.size)
            mlBufferPtr += frame.size
        } else {
            System.arraycopy(frame, 0, mlBuffer, mlBufferPtr, remaining)
            val leftover = frame.size - remaining
            System.arraycopy(frame, remaining, mlBuffer, 0, leftover)
            mlBufferPtr = leftover
        }
    }
    
    private fun getLatestAudioForMl(): ShortArray {
        val result = ShortArray(ML_BUFFER_SIZE)
        // Copy in chronological order: [mlBufferPtr...end] then [0...mlBufferPtr-1]
        val lenFromPtrToEnd = ML_BUFFER_SIZE - mlBufferPtr
        System.arraycopy(mlBuffer, mlBufferPtr, result, 0, lenFromPtrToEnd)
        System.arraycopy(mlBuffer, 0, result, lenFromPtrToEnd, mlBufferPtr)
        return result
    }

    override fun onCaptureError(error: String) {
        listener?.onVadError(error)
        stop()
    }


    // ======================
    // SPEECH CALLBACK
    // ======================

    override fun onSpeechStarted() {
        validateAndNotify()
    }

    override fun onSpeechActive() {
        val now = System.currentTimeMillis()
        if (now - lastMlInferenceTime >= ML_INFERENCE_INTERVAL_MS) {
            validateAndNotify()
        } else {
            // If ML recently confirmed, we can continue to report active speech
            // without re-running ML every frame.
            listener?.onSpeechDetected(lastRms, lastIsNear)
        }
    }

    override fun onSpeechEnded() {
        Log.d(TAG, "Speech ended")
    }
    
    private fun validateAndNotify() {
        val classifier = speechClassifier
        val verifier = speakerVerifier
        val ctx = context
        
        if (classifier != null && ctx != null && Settings.isMlValidationEnabled(ctx)) {
            val audioData = getLatestAudioForMl()
            val isConfirmed = classifier.confirmSpeech(audioData)
            
            if (isConfirmed) {
                // Speech confirmed by ML, now run Speaker Verification (Plan 2)
                if (verifier != null && Settings.isSpeakerVerificationEnabled(ctx)) {
                    verifier.verify(audioData) { isUser ->
                        if (isUser) {
                            lastMlInferenceTime = System.currentTimeMillis()
                            listener?.onSpeechDetected(lastRms, lastIsNear)
                        } else {
                            Log.d(TAG, "[SPK] Rejected Speech (Not the registered user)")
                        }
                    }
                } else {
                    // Plan 2 disabled or failed to init, fallback to Plan 1 (confirmed ML)
                    lastMlInferenceTime = System.currentTimeMillis()
                    listener?.onSpeechDetected(lastRms, lastIsNear)
                }
            } else {
                Log.d(TAG, "[ML] Rejected VAD trigger (False Positive)")
            }
        } else {
            // ML disabled or failed to init, fallback to VAD
            listener?.onSpeechDetected(lastRms, lastIsNear)
        }
    }


    // ======================
    // UTIL
    // ======================

    private fun calculateRms(frame: ShortArray): Int {
        var sum = 0.0
        for (s in frame) {
            sum += s * s
        }
        return sqrt(sum / frame.size).toInt()
    }
}
