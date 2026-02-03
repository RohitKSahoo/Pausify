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
    }

    interface SpeechStateListener {
        fun onSpeechStarted()
    }

    private enum class State {
        IDLE,
        SPEECH_PENDING,
        SPEECH_ACTIVE
    }

    private var state = State.IDLE
    private var speechStartTime = 0L
    private var minSpeechDurationMs = DEFAULT_MIN_SPEECH_MS

    fun configure(minSpeechMs: Long = minSpeechDurationMs) {
        minSpeechDurationMs = max(100L, minSpeechMs)
    }

    fun getMinSpeechMs(): Long = minSpeechDurationMs

    fun reset() {
        state = State.IDLE
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
                if (isSpeech &&
                    timestamp - speechStartTime >= minSpeechDurationMs
                ) {
                    state = State.SPEECH_ACTIVE
                    listener.onSpeechStarted()
                } else if (!isSpeech) {
                    state = State.IDLE
                }
            }

            State.SPEECH_ACTIVE -> {
                if (!isSpeech) {
                    state = State.IDLE
                }
            }
        }
    }
}
