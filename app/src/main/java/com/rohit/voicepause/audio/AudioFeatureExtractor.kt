package com.rohit.voicepause.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Extract deterministic features from raw PCM data to identify human speech patterns.
 */
object AudioFeatureExtractor {

    /**
     * Pre-emphasis filter: y[n] = x[n] - 0.97 * x[n-1]
     * Boosts high frequencies where speech consonants (s, t, f) live.
     */
    fun applyPreEmphasis(samples: ShortArray): FloatArray {
        val output = FloatArray(samples.size)
        output[0] = samples[0].toFloat()
        for (i in 1 until samples.size) {
            output[i] = samples[i] - 0.97f * samples[i - 1]
        }
        return output
    }

    /**
     * Zero Crossing Rate (ZCR): How often the signal crosses 0.
     * Speech: Moderate ZCR (vowels low, consonants high).
     * Impacts/Thumps: Very low ZCR.
     * White Noise/Hiss: Very high ZCR.
     */
    fun calculateZCR(samples: ShortArray): Float {
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / samples.size
    }

    fun calculateRms(samples: ShortArray): Int {
        var sum = 0.0
        for (s in samples) {
            sum += s * s
        }
        return sqrt(sum / samples.size).toInt()
    }
}
