package com.rohit.voicepause.audio

import android.content.Context
import android.util.Log
import com.rohit.voicepause.Settings
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.core.BaseOptions

/**
 * ML-based speech classifier using TFLite (YAMNet).
 * Provides a secondary validation layer to reduce false positives from VAD.
 */
class SpeechClassifier(private val context: Context) {

    companion object {
        private const val TAG = "VoicePause/ML"
        private const val MODEL_FILE = "yamnet.tflite"
        
        // Strict labels for actual human speaking. 
        // We exclude "Vocal" because it often includes coughs, sneezes, and screams.
        private val SPEECH_LABELS = listOf("Speech", "Conversation", "Narration")
        
        // Non-speech human sounds that we want to explicitly ignore.
        private val IGNORE_LABELS = listOf(
            "Cough", "Sneeze", "Laughter", "Chuckle", 
            "Throat clearing", "Sniff", "Screaming", 
            "Shout", "Yell", "Crying", "Whistling",
            "Snoring", "Gasp"
        )
    }

    private var classifier: AudioClassifier? = null
    private var isInitialized = false

    fun initialize(): Boolean {
        if (isInitialized) return true

        try {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setNumThreads(2)
                        .build()
                )
                .setMaxResults(10) // Increased to see more overlapping categories
                .build()

            classifier = AudioClassifier.createFromFileAndOptions(context, MODEL_FILE, options)
            isInitialized = true
            Log.i(TAG, "ML Neural Filter Ready (Strict Mode)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "ML Init Failed: ${e.message}")
            return false
        }
    }

    /**
     * Validates if the provided audio buffer contains actual human speech.
     * 
     * @return true if speech is confirmed and not a cough/sneeze/noise.
     */
    fun confirmSpeech(audioData: ShortArray): Boolean {
        val currentClassifier = classifier
        if (!isInitialized || currentClassifier == null) {
            return true // Fallback to VAD-only mode
        }

        try {
            val tensorAudio = currentClassifier.createInputTensorAudio()
            tensorAudio.load(audioData)
            
            val results = currentClassifier.classify(tensorAudio)
            val allCategories = results?.flatMap { it.categories } ?: emptyList()

            // 1. Find the highest score for actual speech
            val speechCategory = allCategories
                .filter { cat -> SPEECH_LABELS.any { label -> cat.label.contains(label, ignoreCase = true) } }
                .maxByOrNull { it.score }
            val speechScore = speechCategory?.score ?: 0f

            // 2. Find the highest score for sounds we want to ignore
            val ignoreCategory = allCategories
                .filter { cat -> IGNORE_LABELS.any { label -> cat.label.contains(label, ignoreCase = true) } }
                .maxByOrNull { it.score }
            val ignoreScore = ignoreCategory?.score ?: 0f

            val threshold = Settings.getMlConfidenceThreshold(context) 

            // Logic: Confirmed only if Speech is above the threshold 
            // AND Speech is more likely than any "Ignore" sounds (cough/sneeze)
            val isConfirmed = speechScore >= threshold && speechScore > ignoreScore

            if (isConfirmed) {
                Log.d(TAG, "[ML] CONFIRMED: Speech(${String.format("%.2f", speechScore)}) > Ignore(${String.format("%.2f", ignoreScore)})")
            } else if (ignoreScore > speechScore && ignoreScore > 0.1f) {
                Log.d(TAG, "[ML] REJECTED: Likely ${ignoreCategory?.label} (${String.format("%.2f", ignoreScore)})")
            } else if (speechScore < threshold) {
                Log.d(TAG, "[ML] REJECTED: Low confidence (${String.format("%.2f", speechScore)})")
            }
            
            return isConfirmed

        } catch (e: Exception) {
            Log.e(TAG, "Inference Error: ${e.message}")
            return true // Fallback
        }
    }

    fun release() {
        classifier?.close()
        classifier = null
        isInitialized = false
        Log.i(TAG, "ML Filter Released")
    }
}
