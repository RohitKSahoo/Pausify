package com.rohit.voicepause.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance audio capture for real-time voice activity detection
 * 
 * Captures 16kHz mono PCM audio in 20ms frames optimized for WebRTC VAD processing.
 * Handles proper AudioRecord lifecycle and provides frame-based audio data.
 */
class AudioCapture {
    
    companion object {
        private const val TAG = "AudioCapture"
        
        // Audio configuration optimized for VAD
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_DURATION_MS = 20
        
        // Calculate frame size: 16kHz * 20ms = 320 samples
        const val FRAME_SIZE_SAMPLES = (SAMPLE_RATE * FRAME_DURATION_MS) / 1000
        const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2 // 16-bit = 2 bytes per sample
    }
    
    interface AudioFrameListener {
        /**
         * Called when a new audio frame is available
         * @param audioFrame PCM 16-bit audio samples (320 samples for 20ms at 16kHz)
         * @param timestamp System timestamp when frame was captured
         */
        fun onAudioFrame(audioFrame: ShortArray, timestamp: Long)
        
        /**
         * Called when audio capture encounters an error
         * @param error Error description
         */
        fun onCaptureError(error: String)
    }
    
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val isCapturing = AtomicBoolean(false)
    private var frameListener: AudioFrameListener? = null
    
    private val audioBuffer = ShortArray(FRAME_SIZE_SAMPLES)
    private var bufferSizeBytes = 0
    
    /**
     * Initialize audio capture with the specified listener
     * 
     * @param listener Callback for audio frames and errors
     * @return true if initialization successful
     */
    fun initialize(listener: AudioFrameListener): Boolean {
        if (isCapturing.get()) {
            Log.w(TAG, "Audio capture already running")
            return false
        }
        
        frameListener = listener
        
        try {
            // Calculate minimum buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
                Log.e(TAG, "Invalid audio configuration")
                frameListener?.onCaptureError("Invalid audio configuration")
                return false
            }
            
            // Use buffer size that's at least 4x our frame size for smooth capture
            bufferSizeBytes = maxOf(minBufferSize, FRAME_SIZE_BYTES * 4)
            
            // Create AudioRecord instance
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeBytes
            )
            
            // Verify AudioRecord was created successfully
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                frameListener?.onCaptureError("AudioRecord initialization failed")
                cleanup()
                return false
            }
            
            Log.d(TAG, "AudioCapture initialized: ${SAMPLE_RATE}Hz, buffer=${bufferSizeBytes}bytes")
            return true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied", e)
            frameListener?.onCaptureError("Microphone permission denied")
            cleanup()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "AudioCapture initialization failed", e)
            frameListener?.onCaptureError("AudioCapture initialization failed: ${e.message}")
            cleanup()
            return false
        }
    }
    
    /**
     * Start audio capture
     * 
     * @return true if capture started successfully
     */
    fun startCapture(): Boolean {
        if (audioRecord == null) {
            Log.e(TAG, "AudioRecord not initialized")
            frameListener?.onCaptureError("AudioRecord not initialized")
            return false
        }
        
        if (isCapturing.get()) {
            Log.w(TAG, "Audio capture already running")
            return true
        }
        
        try {
            audioRecord?.startRecording()

            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "Failed to start audio recording")
                frameListener?.onCaptureError("Failed to start audio recording")
                return false
            }
            
            isCapturing.set(true)
            
            // Start capture thread
            captureThread = Thread(::captureLoop, "AudioCaptureThread").apply {
                priority = Thread.MAX_PRIORITY // High priority for real-time audio
                start()
            }
            
            Log.d(TAG, "Audio capture started")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            frameListener?.onCaptureError("Failed to start capture: ${e.message}")
            isCapturing.set(false)
            return false
        }
    }
    
    /**
     * Stop audio capture
     */
    fun stopCapture() {
        if (!isCapturing.get()) {
            return
        }
        
        Log.d(TAG, "Stopping audio capture")
        isCapturing.set(false)
        
        // Wait for capture thread to finish
        captureThread?.let { thread ->
            try {
                thread.join(1000) // Wait up to 1 second
                if (thread.isAlive) {
                    Log.w(TAG, "Capture thread did not stop gracefully")
                    thread.interrupt()
                }
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while waiting for capture thread")
                Thread.currentThread().interrupt()
            }
        }
        captureThread = null
        
        // Stop AudioRecord
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        
        Log.d(TAG, "Audio capture stopped")
    }
    
    /**
     * Release all resources
     */
    fun release() {
        stopCapture()
        cleanup()
        frameListener = null
        Log.d(TAG, "AudioCapture released")
    }
    
    /**
     * Check if currently capturing audio
     */
    fun isCapturing(): Boolean = isCapturing.get()
    
    /**
     * Main capture loop - runs in dedicated thread
     */
    private fun captureLoop() {
        Log.d(TAG, "Audio capture loop started")
        
        val tempBuffer = ShortArray(FRAME_SIZE_SAMPLES * 2) // Larger temp buffer
        var samplesInBuffer = 0
        
        try {
            while (isCapturing.get()) {
                val audioRecord = this.audioRecord ?: break
                
                // Read audio data
                val samplesRead = audioRecord.read(
                    tempBuffer, 
                    samplesInBuffer, 
                    tempBuffer.size - samplesInBuffer
                )
                
                when {
                    samplesRead > 0 -> {
                        samplesInBuffer += samplesRead
                        
                        // Process complete frames
                        while (samplesInBuffer >= FRAME_SIZE_SAMPLES) {
                            // Copy frame to output buffer
                            System.arraycopy(tempBuffer, 0, audioBuffer, 0, FRAME_SIZE_SAMPLES)
                            
                            // Shift remaining samples to beginning of buffer
                            val remainingSamples = samplesInBuffer - FRAME_SIZE_SAMPLES
                            if (remainingSamples > 0) {
                                System.arraycopy(
                                    tempBuffer, FRAME_SIZE_SAMPLES,
                                    tempBuffer, 0,
                                    remainingSamples
                                )
                            }
                            samplesInBuffer = remainingSamples
                            
                            // Deliver frame to listener
                            frameListener?.onAudioFrame(audioBuffer.clone(), System.currentTimeMillis())
                        }
                    }
                    
                    samplesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                        Log.e(TAG, "AudioRecord invalid operation")
                        frameListener?.onCaptureError("AudioRecord invalid operation")
                        break
                    }
                    
                    samplesRead == AudioRecord.ERROR_BAD_VALUE -> {
                        Log.e(TAG, "AudioRecord bad value")
                        frameListener?.onCaptureError("AudioRecord bad value")
                        break
                    }
                    
                    samplesRead == AudioRecord.ERROR_DEAD_OBJECT -> {
                        Log.e(TAG, "AudioRecord dead object")
                        frameListener?.onCaptureError("AudioRecord dead object")
                        break
                    }
                    
                    samplesRead == 0 -> {
                        // No data available, brief pause to avoid busy waiting
                        Thread.sleep(1)
                    }
                    
                    else -> {
                        Log.w(TAG, "Unexpected AudioRecord.read() result: $samplesRead")
                    }
                }
            }
        } catch (e: InterruptedException) {
            Log.d(TAG, "Capture loop interrupted")
        } catch (e: Exception) {
            Log.e(TAG, "Error in capture loop", e)
            frameListener?.onCaptureError("Capture loop error: ${e.message}")
        }
        
        Log.d(TAG, "Audio capture loop ended")
    }
    
    /**
     * Clean up AudioRecord resources
     */
    private fun cleanup() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
    }
}