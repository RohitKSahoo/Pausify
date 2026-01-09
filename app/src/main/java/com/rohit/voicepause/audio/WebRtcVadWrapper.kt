package com.rohit.voicepause.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Lightweight Voice Activity Detector (VAD)
 *
 * Uses energy + speech-likeness heuristics.
 * Designed to reject coughs, taps, and impulsive noise.
 */
class WebRtcVadWrapper {

    companion object {
        private const val ENERGY_THRESHOLD = 30.0

        // Speech-like ZCR range (empirically safe)
        private const val ZCR_MIN = 0.015
        private const val ZCR_MAX = 0.18

        // Impulse rejection
        private const val SPIKE_MULTIPLIER = 3.5
    }

    private var initialized = false
    private var lastRms = 0.0

    fun initialize(sampleRate: Int, aggressiveness: Int = 1): Boolean {
        initialized = true
        lastRms = 0.0
        return true
    }

    fun processFrame(audioFrame: ShortArray): Boolean {
        check(initialized)

        val rms = calculateRms(audioFrame)
        val zcr = calculateZeroCrossingRate(audioFrame)

        // 1️⃣ Energy gate
        if (rms < ENERGY_THRESHOLD) {
            lastRms = rms
            return false
        }

        // 2️⃣ Impulse rejection (coughs, taps, knocks)
        if (lastRms > 0 && rms > lastRms * SPIKE_MULTIPLIER) {
            lastRms = rms
            return false
        }

        // 3️⃣ Speech-likeness check (soft)
        val zcrOk = zcr in ZCR_MIN..ZCR_MAX

        lastRms = rms
        return zcrOk
    }

    fun isReady(): Boolean = initialized

    fun destroy() {
        initialized = false
        lastRms = 0.0
    }

    // ======================
    // Internal helpers
    // ======================

    private fun calculateRms(frame: ShortArray): Double {
        var sum = 0.0
        for (s in frame) {
            sum += s * s
        }
        return sqrt(sum / frame.size)
    }

    private fun calculateZeroCrossingRate(frame: ShortArray): Double {
        var crossings = 0
        for (i in 1 until frame.size) {
            if ((frame[i - 1] >= 0 && frame[i] < 0) ||
                (frame[i - 1] < 0 && frame[i] >= 0)
            ) {
                crossings++
            }
        }
        return crossings.toDouble() / frame.size
    }
}
