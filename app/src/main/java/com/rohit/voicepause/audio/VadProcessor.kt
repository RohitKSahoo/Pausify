package com.rohit.voicepause.audio

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class VadProcessor :
    AudioCapture.AudioFrameListener,
    SpeechStateMachine.SpeechStateListener {

    companion object {
        private const val TAG = "VadProcessor"

        // Near-field detection multiplier
        private const val NEAR_FACTOR = 1.6f
        private var lastRms = 0

    }

    interface VadProcessorListener {

        /**
         * @param isNear true = likely user voice (close to mic)
         */
        fun onSpeechDetected(rms: Int, isNear: Boolean)

        fun onVadError(error: String)
    }

    private val vadWrapper = WebRtcVadWrapper()
    private val audioCapture = AudioCapture()
    private lateinit var speechStateMachine: SpeechStateMachine

    // ===== PROFILE PARAMETERS =====
    private var baseMinSpeechMs = 400L
    private var minVoiceEnergy = 300
    private var vadAggressiveness = 2

    // ===== STATE =====
    private val isRunning = AtomicBoolean(false)
    private var listener: VadProcessorListener? = null

    // Last detected near-voice state
    private var lastIsNear = false

    fun initialize(
        listener: VadProcessorListener,
        profile: AudioProfile
    ): Boolean {

        this.listener = listener

        if (!vadWrapper.initialize(AudioCapture.SAMPLE_RATE)) {
            listener.onVadError("VAD initialization failed")
            return false
        }

        // Apply profile parameters (CUSTOM handled elsewhere)
        if (!profile.isCustom) {
            vadAggressiveness = profile.vadAggressiveness
            baseMinSpeechMs = profile.minSpeechMs
            minVoiceEnergy = profile.minVoiceEnergy
        }

        speechStateMachine = SpeechStateMachine(vadWrapper, this).apply {
            configure(minSpeechMs = baseMinSpeechMs)
        }

        if (!audioCapture.initialize(this)) {
            listener.onVadError("AudioCapture initialization failed")
            return false
        }

        Log.i(
            TAG,
            "[PROFILE APPLIED] ${profile.displayName} → " +
                    "vad=$vadAggressiveness, " +
                    "minSpeech=$baseMinSpeechMs, " +
                    "minEnergy=$minVoiceEnergy"
        )

        return true
    }

    fun start(): Boolean {

        if (!audioCapture.startCapture()) {
            listener?.onVadError("Failed to start audio capture")
            return false
        }

        isRunning.set(true)

        speechStateMachine.reset()
        speechStateMachine.configure(minSpeechMs = baseMinSpeechMs)

        Log.i(TAG, "[VAD] Started")
        return true
    }

    fun stop() {
        isRunning.set(false)
        audioCapture.stopCapture()
        speechStateMachine.reset()

        Log.i(TAG, "[VAD] Stopped")
    }

    fun release() {
        stop()
        audioCapture.release()
        vadWrapper.destroy()
        listener = null
    }

    fun isRunning(): Boolean = isRunning.get()

    /**
     * CUSTOM profile only
     */
    fun applyUserSensitivity(multiplier: Float) {

        if (!::speechStateMachine.isInitialized) return

        val safeMultiplier = multiplier.coerceIn(0.8f, 1.8f)

        val effectiveMinSpeech =
            (baseMinSpeechMs / safeMultiplier)
                .toLong()
                .coerceAtLeast(200L)

        speechStateMachine.configure(minSpeechMs = effectiveMinSpeech)

        Log.i(
            TAG,
            "[CUSTOM] Sensitivity → minSpeechMs=$effectiveMinSpeech"
        )
    }

    // ======================
    // AUDIO CALLBACK
    // ======================

    override fun onAudioFrame(audioFrame: ShortArray, timestamp: Long) {

        val rms = calculateRms(audioFrame)
        lastRms = rms


        // Energy gate
        if (rms < minVoiceEnergy) {
            Log.d(
                TAG,
                "[VOICE REJECTED] RMS=$rms < threshold=$minVoiceEnergy"
            )
            return
        }

        // Near-field detection
        val nearThreshold = minVoiceEnergy * NEAR_FACTOR
        val isNear = rms >= nearThreshold

        lastIsNear = isNear

        Log.d(
            TAG,
            "[VOICE OK] RMS=$rms, near=$isNear (limit=$nearThreshold)"
        )

        speechStateMachine.processFrame(audioFrame, timestamp)
    }

    override fun onCaptureError(error: String) {
        listener?.onVadError(error)
        stop()
    }

    // ======================
    // SPEECH CALLBACK
    // ======================

    override fun onSpeechStarted() {

        Log.i(
            TAG,
            "[VAD] Speech confirmed → near=$lastIsNear"
        )

        listener?.onSpeechDetected(lastRms, lastIsNear)

    }

    override fun onSpeechActive() {
        // Forward continuous speech
        listener?.onSpeechDetected(lastRms, lastIsNear)
    }

    override fun onSpeechEnded() {

        Log.i(TAG, "[VAD] Speech ended")

        // No direct action needed yet
        // Pause logic is handled by service timers
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
