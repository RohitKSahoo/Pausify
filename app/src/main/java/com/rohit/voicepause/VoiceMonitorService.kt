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

    // ===== MIC THREAD =====
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var micThread: Thread? = null
    @Volatile private var micRunning = false

    // ===== VOICE LOGIC =====
    @Volatile private var voiceThreshold = 200          // VERY sensitive
    @Volatile private var silenceTimeoutMs = 10_000L    // 5 seconds
    private var lastVoiceTime = 0L
    private var hasAudioFocus = false
    private var pausedByVoice = false

    private var silenceStartTime: Long = 0L


    // ===== MUSIC IDLE =====
    private var lastMusicActiveTime = 0L
    private val AUTO_STOP_TIMEOUT_MS = 60_000L // 1 min

    // ===== POLLING =====
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
    // LIFECYCLE
    // ======================

    override fun onCreate() {
        super.onCreate()
        Log.d("VoicePause", "Service created")

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        workerThread = HandlerThread("VoicePauseWorker")
        workerThread.start()
        handler = Handler(workerThread.looper)

        startForeground(NOTIFICATION_ID, createNotification())
        handler.post(musicChecker)
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
    // MUSIC CHECK LOOP
    // ======================

    private val musicChecker = object : Runnable {
        override fun run() {

            // 🔄 Apply sliders instantly
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
            Log.d("VoicePause", "Auto-stop after idle")
            stopServiceCompletely()
        }
    }

    // ======================
    // MIC HANDLING (SAFE)
    // ======================

    private fun startMicIfNeeded() {
        if (micRunning) return

        micRunning = true
        pausedByVoice = false
        lastVoiceTime = 0L

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

                    // 🔊 VOICE DETECTION
                    if (rms > voiceThreshold) {
                        onVoiceDetected()
                    }

                    checkForSilence()
                }
            }

            cleanupMic()
        }

        micThread?.start()
        Log.d("VoicePause", "Mic started")
    }

    private fun stopMicIfNeeded() {
        if (!micRunning) return
        micRunning = false
    }

    private fun cleanupMic() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null

        if (hasAudioFocus) {
            audioManager.abandonAudioFocus(null)
            hasAudioFocus = false
        }

        Log.d("VoicePause", "Mic released")
    }

    // ======================
    // PAUSE / RESUME
    // ======================

    private fun onVoiceDetected() {
        val now = System.currentTimeMillis()

        // 🔴 If music already paused, RESET silence window
        if (pausedByVoice) {
            silenceStartTime = 0L
            return
        }

        // First voice that triggers pause
        val result = audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            pausedByVoice = true
            hasAudioFocus = true
            silenceStartTime = 0L   // silence window not started yet
            Log.d("VoicePause", "Music paused by voice")
        }
    }


    private fun checkForSilence() {
        if (!pausedByVoice) return

        val now = System.currentTimeMillis()

        // Start silence timer only once
        if (silenceStartTime == 0L) {
            silenceStartTime = now
            return
        }

        val silenceDuration = now - silenceStartTime

        if (silenceDuration >= silenceTimeoutMs) {
            audioManager.abandonAudioFocus(null)
            hasAudioFocus = false
            pausedByVoice = false
            silenceStartTime = 0L

            Log.d(
                "VoicePause",
                "Music resumed after ${silenceDuration}ms of silence"
            )
        }
    }


    // ======================
    // STOP EVERYTHING
    // ======================

    private fun stopServiceCompletely() {
        Log.d("VoicePause", "Stopping service")

        micRunning = false
        handler.removeCallbacksAndMessages(null)
        stopForeground(true)

        sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        micRunning = false
        workerThread.quitSafely()
        Log.d("VoicePause", "Service destroyed")
    }

    // ======================
    // UTIL
    // ======================

    private fun calculateRms(buffer: ShortArray, read: Int): Double {
        var sum = 0.0
        for (i in 0 until read) sum += buffer[i] * buffer[i]
        return sqrt(sum / read)
    }

    // ======================
    // NOTIFICATION
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
