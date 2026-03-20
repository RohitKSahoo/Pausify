package com.rohit.voicepause.audio

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class VadProcessor :
    AudioCapture.AudioFrameListener,
    SpeechStateMachine.SpeechStateListener {

    companion object {
        private const val TAG = "VadProcessor"

        private const val NEAR_FACTOR = 1.6f
    }

    interface VadProcessorListener {

        fun onSpeechDetected(rms: Int, isNear: Boolean)

        fun onVadError(error: String)
    }

    private val vadWrapper = WebRtcVadWrapper()
    private val audioCapture = AudioCapture()
    private lateinit var speechStateMachine: SpeechStateMachine

    // ===== ACTIVE PARAMETERS =====
    private var minSpeechMs = 400L
    private var minVoiceEnergy = 300
    private var vadAggressiveness = 2

    // ===== STATE =====
    private val isRunning = AtomicBoolean(false)
    private var listener: VadProcessorListener? = null

    private var lastRms = 0
    private var lastIsNear = false


    // ======================
    // INIT
    // ======================

    fun initialize(
        listener: VadProcessorListener,
        profile: AudioProfile
    ): Boolean {

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

        Log.i(
            TAG,
            "Profile=${profile.displayName} " +
                    "speech=$minSpeechMs energy=$minVoiceEnergy vad=$vadAggressiveness"
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

        listener = null
    }

    fun isRunning(): Boolean = isRunning.get()


    // ======================
    // AUDIO CALLBACK
    // ======================

    override fun onAudioFrame(
        audioFrame: ShortArray,
        timestamp: Long
    ) {

        val rms = calculateRms(audioFrame)
        lastRms = rms

        if (rms < minVoiceEnergy) return

        val nearLimit = minVoiceEnergy * NEAR_FACTOR
        val isNear = rms >= nearLimit

        lastIsNear = isNear

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

        listener?.onSpeechDetected(lastRms, lastIsNear)
    }

    override fun onSpeechActive() {

        listener?.onSpeechDetected(lastRms, lastIsNear)
    }

    override fun onSpeechEnded() {

        Log.d(TAG, "Speech ended")
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
