package com.rohit.voicepause.audio

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.rohit.voicepause.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.sqrt

class SpeakerVerifier(private val context: Context) {

    companion object {
        private const val TAG = "VoicePause/SpeakerVer"
        private const val MODEL_FILE = "speaker.onnx"
        private const val MIN_SAMPLES = 8000
        private const val IDEAL_SAMPLES = 16000
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun initialize(): Boolean {
        return try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_FILE).readBytes()
            ortSession = ortEnv?.createSession(modelBytes)
            Log.i(TAG, "ONNX Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX model: ${e.message}")
            false
        }
    }

    /**
     * Verifies if the speaker in the audio buffer matches the enrolled user.
     * Runs asynchronously on Dispatchers.Default.
     */
    fun verify(audioBuffer: ShortArray, onResult: (Boolean) -> Unit) {
        if (!Settings.isSpeakerVerificationEnabled(context)) {
            onResult(true) // Fallback: allow all speech if disabled
            return
        }

        val userEmbedding = Settings.getUserEmbedding(context)
        if (userEmbedding == null || ortSession == null) {
            Log.d(TAG, "Fallback: No user embedding or model not loaded")
            onResult(true) // Fallback to Plan 1
            return
        }

        scope.launch {
            val isUser = try {
                val currentEmbedding = generateEmbedding(audioBuffer)
                if (currentEmbedding != null) {
                    val similarity = calculateCosineSimilarity(currentEmbedding, userEmbedding)
                    val threshold = Settings.getSpeakerThreshold(context)
                    val accepted = similarity >= threshold
                    
                    val status = if (accepted) "ACCEPT" else "REJECT"
                    Log.i(TAG, "[SPK] similarity=%.2f → $status".format(similarity))
                    
                    accepted
                } else {
                    Log.w(TAG, "[SPK] Embedding generation failed, fallback to ACCEPT")
                    true // Fallback
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference error: ${e.message}")
                true // Fallback
            }

            withContext(Dispatchers.Main) {
                onResult(isUser)
            }
        }
    }

    /**
     * Generates an embedding for a given audio buffer.
     * Can be used for both verification and enrollment.
     */
    fun generateEmbedding(audioBuffer: ShortArray): FloatArray? {
        val session = ortSession ?: return null
        val env = ortEnv ?: return null

        // 1. Preprocess: Pad/Trim to IDEAL_SAMPLES (16000)
        val processedBuffer = FloatArray(IDEAL_SAMPLES)
        for (i in 0 until IDEAL_SAMPLES) {
            if (i < audioBuffer.size) {
                // Normalize to [-1.0, 1.0]
                processedBuffer[i] = audioBuffer[i].toFloat() / 32768f
            } else {
                processedBuffer[i] = 0f // Padding
            }
        }

        // 2. Prepare Input Tensor [1, samples]
        val shape = longArrayOf(1, IDEAL_SAMPLES.toLong())
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(processedBuffer), shape)

        return try {
            // 3. Run Inference
            val output = session.run(Collections.singletonMap(session.inputNames.first(), tensor))
            val result = output[0].value as Array<FloatArray>
            result[0] // Return the embedding
        } catch (e: Exception) {
            Log.e(TAG, "ONNX Inference failed: ${e.message}")
            null
        } finally {
            tensor.close()
        }
    }

    private fun calculateCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }

    fun release() {
        ortSession?.close()
        ortEnv?.close()
    }
}
