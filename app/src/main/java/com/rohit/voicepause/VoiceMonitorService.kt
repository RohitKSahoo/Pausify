package com.rohit.voicepause

import android.app.*
import android.content.Intent
import android.media.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class VoiceMonitorService : Service() {

    private lateinit var audioManager: AudioManager

    // ===== Mic =====
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var micThread: Thread? = null
    @Volatile private var micRunning = false

    // ===== Voice logic =====
    @Volatile private var voiceThreshold = 1200
    @Volatile private var silenceTimeoutMs = 10_000L
    private var lastVoiceTime = 0L
    private var pausedByVoice = false

    // ===== Noise adaptation =====
    private var noiseFloor = 0.0
    private val NOISE_SMOOTHING = 0.95
    private val MIN_ABSOLUTE_RMS = 250.0

    // ===== Music inactivity =====
    private var lastMusicActiveTime = 0L
    private val AUTO_STOP_TIMEOUT_MS = 60_000L

    // ===== Polling =====
    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler

    companion object {
        const val ACTION_SERVICE_STOPPED =
            "com.rohit.voicepause.ACTION_SERVICE_STOPPED"
        private const val NOTIFICATION_ID = 100
        private const val ACTION_STOP_SERVICE =
            "com.rohit.voicepause.ACTION_STOP_SERVICE"
    }

    // ======================
    // Lifecycle
    // ======================

    override fun onCreate() {
        super.onCreate()
        Log.d("VoicePause", "Service created")

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        startForeground(NOTIFICATION_ID, createNotification())

        workerThread = HandlerThread("VoicePauseWorker")
        workerThread.start()
        handler = Handler(workerThread.looper)

        handler.post(musicStateChecker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopServiceCompletely()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ======================
    // Music polling (RESTORED)
    // ======================

    private val musicStateChecker = object : Runnable {
        override fun run() {

            voiceThreshold = Settings.getVoiceThreshold(applicationContext)
            silenceTimeoutMs = Settings.getSilenceDuration(applicationContext)

            if (audioManager.isMusicActive) {
                lastMusicActiveTime = System.currentTimeMillis()
                startMicIfNeeded()
            } else {
                stopMicIfNeeded()
                checkAutoStop()
            }

            handler.postDelayed(this, 1000)
        }
    }

    private fun checkAutoStop() {
        if (System.currentTimeMillis() - lastMusicActiveTime > AUTO_STOP_TIMEOUT_MS) {
            stopServiceCompletely()
        }
    }

    // ======================
    // Mic handling
    // ======================

    private fun startMicIfNeeded() {
        if (micRunning) return

        micRunning = true
        noiseFloor = 0.0
        pausedByVoice = false

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()

        micThread = Thread {
            val buffer = ShortArray(bufferSize)

            while (micRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    val rms = calculateRms(buffer, read)
                    updateNoiseFloor(rms)

                    val threshold = maxOf(
                        noiseFloor * 1.6,
                        MIN_ABSOLUTE_RMS,
                        voiceThreshold.toDouble()
                    )

                    if (rms > threshold && !pausedByVoice) {
                        pauseMusic()
                    }

                    checkForSilence()
                }
            }

            releaseMic()
        }

        micThread?.start()
        Log.d("VoicePause", "Mic started")
    }

    private fun stopMicIfNeeded() {
        if (!micRunning) return
        micRunning = false
    }

    private fun releaseMic() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        Log.d("VoicePause", "Mic released")
    }

    // ======================
    // Pause / Resume (FIXED)
    // ======================

    private fun pauseMusic() {
        val result = audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            pausedByVoice = true
            lastVoiceTime = System.currentTimeMillis()
            Log.d("VoicePause", "Music paused by voice")
        }
    }

    private fun checkForSilence() {
        if (!pausedByVoice) return

        if (System.currentTimeMillis() - lastVoiceTime > silenceTimeoutMs) {
            audioManager.abandonAudioFocus(null)
            pausedByVoice = false
            Log.d("VoicePause", "Music resumed after silence")
        }
    }

    // ======================
    // Helpers
    // ======================

    private fun updateNoiseFloor(rms: Double) {
        noiseFloor =
            if (noiseFloor == 0.0) rms
            else NOISE_SMOOTHING * noiseFloor + (1 - NOISE_SMOOTHING) * rms
    }

    private fun calculateRms(buffer: ShortArray, read: Int): Double {
        var sum = 0.0
        for (i in 0 until read) sum += buffer[i] * buffer[i]
        return sqrt(sum / read)
    }

    // ======================
    // Stop
    // ======================

    private fun stopServiceCompletely() {
        micRunning = false
        handler.removeCallbacksAndMessages(null)
        stopForeground(true)
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        micRunning = false
    }

    // ======================
    // Notification
    // ======================

    private fun createNotification(): Notification {
        val channelId = "voice_pause_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "VoicePause",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
        }

        val stopIntent = Intent(this, VoiceMonitorService::class.java)
            .apply { action = ACTION_STOP_SERVICE }

        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VoicePause running")
            .setContentText("Listening for voice")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(stopPendingIntent)
            .build()
    }
}
