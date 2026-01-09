package com.rohit.voicepause.audio

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class VadProcessor :
    AudioCapture.AudioFrameListener,
    SpeechStateMachine.SpeechStateListener {

    companion object {
        private const val TAG = "VadProcessor"
    }

    interface VadProcessorListener {
        fun onSpeechDetected()
        fun onSpeechEnded()
        fun onVadError(error: String)
    }

    private val vadWrapper = WebRtcVadWrapper()
    private val audioCapture = AudioCapture()
    private lateinit var speechStateMachine: SpeechStateMachine

    // 🔑 PROFILE BASE VALUES
    private var baseMinSpeechMs = 400L
    private var baseSilenceDelayMs = 800L

    private val isRunning = AtomicBoolean(false)
    private var listener: VadProcessorListener? = null

    fun initialize(
        listener: VadProcessorListener,
        profile: AudioProfile
    ): Boolean {
        this.listener = listener

        if (!vadWrapper.initialize(AudioCapture.SAMPLE_RATE)) {
            listener.onVadError("VAD initialization failed")
            return false
        }

        baseMinSpeechMs = profile.minSpeechMs
        baseSilenceDelayMs = profile.silenceDelayMs

        speechStateMachine = SpeechStateMachine(vadWrapper, this).apply {
            configure(
                minSpeechMs = baseMinSpeechMs,
                silenceDelayMs = baseSilenceDelayMs
            )
        }

        if (!audioCapture.initialize(this)) {
            listener.onVadError("AudioCapture initialization failed")
            return false
        }

        Log.i(TAG, "Initialized with profile: ${profile.displayName}")
        return true
    }

    fun start(): Boolean {
        if (isRunning.get()) return true

        if (!audioCapture.startCapture()) {
            listener?.onVadError("Failed to start audio capture")
            return false
        }

        isRunning.set(true)
        speechStateMachine.reset()
        Log.i(TAG, "VAD started")
        return true
    }

    fun stop() {
        if (!isRunning.get()) return

        isRunning.set(false)
        audioCapture.stopCapture()
        speechStateMachine.reset()
        Log.i(TAG, "VAD stopped")
    }

    fun release() {
        stop()
        audioCapture.release()
        vadWrapper.destroy()
        listener = null
        Log.i(TAG, "VAD released")
    }

    fun isRunning(): Boolean = isRunning.get()

    /**
     * 🔁 Apply user resume delay ON TOP of profile base delay
     */
    fun applyUserResumeDelay(extraDelayMs: Long) {
        if (!::speechStateMachine.isInitialized) return

        val effectiveDelay = baseSilenceDelayMs + extraDelayMs

        speechStateMachine.configure(
            minSpeechMs = baseMinSpeechMs,
            silenceDelayMs = effectiveDelay
        )

        Log.i(TAG, "Effective silence delay → ${effectiveDelay}ms")
    }

    // ===== AudioCapture callbacks =====

    override fun onAudioFrame(audioFrame: ShortArray, timestamp: Long) {
        if (!isRunning.get()) return
        speechStateMachine.processFrame(audioFrame, timestamp)
    }

    override fun onCaptureError(error: String) {
        Log.e(TAG, "Audio capture error: $error")
        listener?.onVadError(error)
        stop()
    }

    // ===== Speech callbacks =====

    override fun onSpeechStarted() {
        listener?.onSpeechDetected()
    }

    override fun onSpeechEnded() {
        listener?.onSpeechEnded()
    }
}
