package com.rohit.voicepause.audio

import android.content.Context
import android.util.Log
import com.rohit.voicepause.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class VoiceEnrollmentManager(
    private val context: Context,
    private val speakerVerifier: SpeakerVerifier
) : AudioCapture.AudioFrameListener {

    companion object {
        private const val TAG = "VoicePause/Enrollment"
        private const val ENROLLMENT_DURATION_MS = 20000L // Increased to 20s for more sentences
        private const val CHUNK_SIZE = 16000 // 1 second at 16kHz
        private const val ENERGY_THRESHOLD = 500 // Min energy to consider a chunk valid
    }

    private val audioCapture = AudioCapture()
    private val _status = MutableStateFlow<EnrollmentStatus>(EnrollmentStatus.Idle)
    val status = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val collectedEmbeddings = mutableListOf<FloatArray>()
    
    // Use a more efficient buffer than MutableList<Short>
    private var audioAccumulator = ShortArray(CHUNK_SIZE * 2)
    private var accumulatorPtr = 0
    
    private var startTime = 0L
    private var enrollmentJob: Job? = null

    sealed class EnrollmentStatus {
        object Idle : EnrollmentStatus()
        object Recording : EnrollmentStatus()
        object Processing : EnrollmentStatus()
        object Success : EnrollmentStatus()
        data class Error(val message: String) : EnrollmentStatus()
    }

    fun startEnrollment() {
        if (speakerVerifier.generateEmbedding(ShortArray(16000)) == null) {
            _status.value = EnrollmentStatus.Error("Model not loaded. Check speaker.onnx in assets.")
            return
        }

        if (!audioCapture.initialize(this)) {
            _status.value = EnrollmentStatus.Error("Microphone initialization failed.")
            return
        }

        if (!audioCapture.startCapture()) {
            _status.value = EnrollmentStatus.Error("Could not start recording.")
            return
        }

        collectedEmbeddings.clear()
        accumulatorPtr = 0
        startTime = System.currentTimeMillis()
        _status.value = EnrollmentStatus.Recording
        _progress.value = 0f

        enrollmentJob = CoroutineScope(Dispatchers.Default).launch {
            while (System.currentTimeMillis() - startTime < ENROLLMENT_DURATION_MS) {
                val elapsed = System.currentTimeMillis() - startTime
                _progress.value = elapsed.toFloat() / ENROLLMENT_DURATION_MS
                delay(100)
            }
            stopAndProcess()
        }
    }

    fun stopEnrollment() {
        enrollmentJob?.cancel()
        stopAndProcess()
    }

    private fun stopAndProcess() {
        if (_status.value != EnrollmentStatus.Recording) return
        
        audioCapture.stopCapture()
        _status.value = EnrollmentStatus.Processing

        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (collectedEmbeddings.size < 3) { // Require at least 3 valid chunks
                    val msg = if (collectedEmbeddings.isEmpty()) "No voice detected." else "Insufficient voice data. Speak louder/longer."
                    _status.value = EnrollmentStatus.Error(msg)
                    return@launch
                }

                // Average embeddings
                val avgEmbedding = averageEmbeddings(collectedEmbeddings)
                Settings.setUserEmbedding(context, avgEmbedding)
                Settings.setSpeakerVerificationEnabled(context, true)

                Log.i(TAG, "Enrollment complete. Chunks: ${collectedEmbeddings.size}")
                _status.value = EnrollmentStatus.Success
            } catch (e: Exception) {
                Log.e(TAG, "Enrollment processing failed", e)
                _status.value = EnrollmentStatus.Error("Processing failed: ${e.message}")
            }
        }
    }

    override fun onAudioFrame(audioFrame: ShortArray, timestamp: Long) {
        if (_status.value != EnrollmentStatus.Recording) return

        // Fill accumulator efficiently
        val toCopy = minOf(audioFrame.size, audioAccumulator.size - accumulatorPtr)
        System.arraycopy(audioFrame, 0, audioAccumulator, accumulatorPtr, toCopy)
        accumulatorPtr += toCopy

        // If we have a full chunk, process it
        if (accumulatorPtr >= CHUNK_SIZE) {
            val chunk = audioAccumulator.copyOfRange(0, CHUNK_SIZE)
            
            // Shift remaining
            val remaining = accumulatorPtr - CHUNK_SIZE
            System.arraycopy(audioAccumulator, CHUNK_SIZE, audioAccumulator, 0, remaining)
            accumulatorPtr = remaining

            // Check if chunk has enough energy (avoid silence)
            if (calculateRms(chunk) > ENERGY_THRESHOLD) {
                val embedding = speakerVerifier.generateEmbedding(chunk)
                if (embedding != null) {
                    collectedEmbeddings.add(embedding)
                    Log.d(TAG, "Collected embedding chunk #${collectedEmbeddings.size}")
                }
            } else {
                Log.d(TAG, "Skipped silent chunk")
            }
        }
    }

    private fun calculateRms(frame: ShortArray): Int {
        var sum = 0.0
        for (s in frame) {
            sum += s * s
        }
        return sqrt(sum / frame.size).toInt()
    }

    override fun onCaptureError(error: String) {
        Log.e(TAG, "Capture Error: $error")
        _status.value = EnrollmentStatus.Error(error)
        audioCapture.stopCapture()
    }

    private fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        val size = embeddings[0].size
        val avg = FloatArray(size)
        for (emb in embeddings) {
            for (i in 0 until size) {
                avg[i] += emb[i]
            }
        }
        for (i in 0 until size) {
            avg[i] /= embeddings.size
        }
        return avg
    }
}
