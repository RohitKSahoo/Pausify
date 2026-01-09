package com.rohit.voicepause.audio

import android.util.Log
import kotlin.math.max

class SpeechStateMachine(
    private val vadWrapper: WebRtcVadWrapper,
    private val listener: SpeechStateListener
) {

    companion object {
        private const val TAG = "SpeechStateMachine"
        private const val DEFAULT_MIN_SPEECH_MS = 400L
        private const val DEFAULT_SILENCE_DELAY_MS = 800L
    }

    interface SpeechStateListener {
        fun onSpeechStarted()
        fun onSpeechEnded()
    }

    private enum class State {
        IDLE,
        SPEECH_PENDING,
        SPEECH_ACTIVE,
        SILENCE_PENDING
    }

    private var state = State.IDLE
    private var stateStartTime = 0L
    private var speechStartTime = 0L

    // 🔑 CONFIGURABLE PARAMETERS
    private var minSpeechDurationMs = DEFAULT_MIN_SPEECH_MS
    private var silenceDelayMs = DEFAULT_SILENCE_DELAY_MS

    fun configure(
        minSpeechMs: Long = minSpeechDurationMs,
        silenceDelayMs: Long = this.silenceDelayMs
    ) {
        this.minSpeechDurationMs = max(100L, minSpeechMs)
        this.silenceDelayMs = max(100L, silenceDelayMs)

        Log.i(
            TAG,
            "Configured → minSpeech=${this.minSpeechDurationMs}ms, silence=${this.silenceDelayMs}ms"
        )
    }

    fun getMinSpeechMs(): Long = minSpeechDurationMs
    fun getSilenceDelayMs(): Long = silenceDelayMs

    fun reset() {
        state = State.IDLE
        stateStartTime = 0L
        speechStartTime = 0L
    }

    fun processFrame(audioFrame: ShortArray, timestamp: Long) {
        val isSpeech = try {
            vadWrapper.processFrame(audioFrame)
        } catch (e: Exception) {
            Log.e(TAG, "VAD error", e)
            false
        }

        when (state) {
            State.IDLE -> {
                if (isSpeech) {
                    state = State.SPEECH_PENDING
                    speechStartTime = timestamp
                }
            }

            State.SPEECH_PENDING -> {
                if (isSpeech) {
                    if (timestamp - speechStartTime >= minSpeechDurationMs) {
                        state = State.SPEECH_ACTIVE
                        listener.onSpeechStarted()
                    }
                } else {
                    state = State.IDLE
                }
            }

            State.SPEECH_ACTIVE -> {
                if (!isSpeech) {
                    state = State.SILENCE_PENDING
                    stateStartTime = timestamp
                }
            }

            State.SILENCE_PENDING -> {
                if (isSpeech) {
                    state = State.SPEECH_ACTIVE
                } else if (timestamp - stateStartTime >= silenceDelayMs) {
                    state = State.IDLE
                    listener.onSpeechEnded()
                }
            }
        }
    }
}
