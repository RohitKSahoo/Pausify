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
        fun onSpeechActive()
        fun onSpeechEnded()
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

        Log.i(
            TAG,
            "Configured → minSpeechMs=$minSpeechDurationMs"
        )
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

            // ======================
            // IDLE
            // ======================
            State.IDLE -> {

                if (isSpeech) {
                    state = State.SPEECH_PENDING
                    speechStartTime = timestamp

                    Log.d(TAG, "→ SPEECH_PENDING")
                }
            }

            // ======================
            // CONFIRMING SPEECH
            // ======================
            State.SPEECH_PENDING -> {

                if (isSpeech) {

                    if (timestamp - speechStartTime >= minSpeechDurationMs) {

                        state = State.SPEECH_ACTIVE

                        Log.d(TAG, "→ SPEECH_ACTIVE (confirmed)")

                        listener.onSpeechStarted()
                    }

                } else {
                    // False alarm
                    state = State.IDLE

                    Log.d(TAG, "→ IDLE (pending cancelled)")
                }
            }

            // ======================
            // ACTIVE SPEECH
            // ======================
            State.SPEECH_ACTIVE -> {

                if (isSpeech) {

                    // 🔥 Continuous callback
                    listener.onSpeechActive()

                } else {

                    // Speech ended
                    state = State.IDLE

                    Log.d(TAG, "→ IDLE (speech ended)")

                    listener.onSpeechEnded()
                }
            }
        }
    }
}
